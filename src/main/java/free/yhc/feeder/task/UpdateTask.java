/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.feeder.task;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.URL;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.HelperHandler;
import free.yhc.baselib.async.ThreadEx;
import free.yhc.baselib.async.TmTask;
import free.yhc.baselib.net.NetDownloadTask;
import free.yhc.baselib.net.NetReadTask;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.feed.Feed;
import free.yhc.feeder.feed.FeedParser;
import free.yhc.feeder.feed.FeedPolicy;
import free.yhc.feeder.core.FeederException;


public class UpdateTask extends TmTask<Err> {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(UpdateTask.class, Logger.LOGLV_DEFAULT);

    private final DBPolicy mDbp = DBPolicy.get();

    private final AtomicReference<NetDownloadTask> mDnTask = new AtomicReference<>(null);
    private final long mCid;
    private final String mCustomIconRef;
    private Err mErr = Err.UNKNOWN;

    private void
    checkCancel() throws InterruptedException {
        if (isCancel())
            throw new InterruptedException();
    }

    public UpdateTask(
            long cid,
            @Nullable String customIconRef) {
        super("UpdateTask(" + cid + ")",
              HelperHandler.get(),
              ThreadEx.TASK_PRIORITY_MIDLOW,
              true);
        mCid = cid;
        mCustomIconRef = customIconRef;
    }


    @Override
    protected void
    onEarlyCancel(boolean started, Object param) {
        super.onEarlyCancel(started, param);
        NetDownloadTask t = mDnTask.get();
        if (null != t)
            t.cancel();
    }

    private Err
    doAsyncTaskInternal()
            throws InterruptedException, FeederException{
        String url = mDbp.getChannelInfoString(mCid, ColumnChannel.URL);
        P.bug(null != url);
        if (DBG) P.v("Loading Items: " + url);
        FeedParser.Result parD;
        assert url != null;
        parD = FeedParser.parse(url);
        // set to given value in force due to this is 'update' - Not new insertion.

        // decide action type.
        // Actually deciding only once is enough in general case.
        // But, rarely whole channel item type may be changed and requires different action.
        // So, whenever update is executed try to adjust 'action' type.
        long oldAction = mDbp.getChannelInfoLong(mCid, ColumnChannel.ACTION);
        long action = FeedPolicy.decideActionType(oldAction,
                                                  parD.channel,
                                                  parD.items.length > 0? parD.items[0]: null);
        if (action != oldAction)
            mDbp.updateChannel(mCid, ColumnChannel.ACTION, action);

        if (null == mDbp.getChannelImageBitmap(mCid)) {
            // Kind Of Policy!!
            // Original image reference always has priority!
            byte[] bmdata = null;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()){
                if (Util.isValidValue(parD.channel.imageref)) {
                    NetReadTask.Builder<NetReadTask.Builder> b = new NetReadTask.Builder<>(
                            Util.createNetConn(new URL(parD.channel.imageref)),
                            baos);
                    b.create().startSync();
                    bmdata = baos.toByteArray();
                }
            } catch (IOException ignored) {
            } catch (Exception e) {
                throw new RuntimeException(e);
            }


            if (null == bmdata && null != mCustomIconRef) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()){
                    NetReadTask.Builder<NetReadTask.Builder> b = new NetReadTask.Builder<>(
                            Util.createNetConn(new URL(mCustomIconRef)),
                            baos);
                    b.create().startSync();
                    bmdata = baos.toByteArray();
                } catch (IOException ignored) {
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            if (null != bmdata) {
                Bitmap bm = Util.decodeImage(
                        bmdata,
                        Feed.Channel.ICON_MAX_WIDTH,
                        Feed.Channel.ICON_MAX_HEIGHT);
                if (null != bm) {
                    byte[] imageblob = Util.compressBitmap(bm);
                    bm.recycle();
                    mDbp.updateChannel(mCid, ColumnChannel.IMAGEBLOB, imageblob);
                }
            }
            //logI("TIME: Handle Image : " + (System.currentTimeMillis() - time));
            checkCancel();
        }

        LinkedList<Feed.Item.ParD> newItems = new LinkedList<>();
        mDbp.getNewItems(mCid, parD.items, newItems);

        DBPolicy.ItemDataOpInterface idop = null;
        // NOTE
        // Information in "ch.dynD" is not available in case update.
        // ('imageblob' and 'action' is exception case controlled with argument.)
        // This is dynamically assigned variable.
        long updateMode = mDbp.getChannelInfoLong(mCid, ColumnChannel.UPDATEMODE);
        if (Feed.Channel.isUpdDn(updateMode)) {
            final long chact = action;
            idop = new DBPolicy.ItemDataOpInterface() {
                @Override
                public File
                getFile(Feed.Item.ParD parD) throws FeederException {
                    String url = FeedPolicy.getDynamicActionTargetUrl(
                            chact, parD.link, parD.enclosureUrl);
                    if (!Util.isValidValue(url))
                        return null;
                    try {
                        File tempOut = Util.getNewTempFile();
                        NetDownloadTask.Builder<NetDownloadTask.Builder> b
                                = new NetDownloadTask.Builder<>(
                                Util.createNetConn(new URL(url)),
                                tempOut);
                        mDnTask.set(b.create());
                        if (isCancel())
                            throw new InterruptedException();
                        mDnTask.get().startSync();
                        return tempOut;
                    } catch (SocketException | InterruptedIOException e) {
                        /* SocketTimeoutException extends InterruptedIOException.
                         */
                        throw new FeederException(Err.IO_NET);
                    } catch (IOException e) {
                        throw new FeederException(Err.IO_FILE);
                    } catch (InterruptedException e) {
                        throw new FeederException(Err.USER_CANCELLED);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
        checkCancel();
        mDbp.updateChannel(mCid, parD.channel, newItems, idop);
        return Err.NO_ERR;
    }

    @Override
    protected Err
    doAsync() throws Exception {
        try {
            return doAsyncTaskInternal();
        } catch (InterruptedException e) {
            mErr = Err.INTERRUPTED;
            throw e;
        } catch (FeederException e) {
            mErr = e.getError();
            throw e;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Privates
    //
    ///////////////////////////////////////////////////////////////////////////
    public Err
    getErr() {
        return mErr;
    }

    public long
    getChannelId() {
        return mCid;
    }
}
