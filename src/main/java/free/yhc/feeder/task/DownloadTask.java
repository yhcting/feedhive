/******************************************************************************
 * Copyright (C) 2016
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

import android.os.Handler;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.URL;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.adapter.android.AHandlerAdapter;
import free.yhc.baselib.async.ThreadEx;
import free.yhc.baselib.net.NetConn;
import free.yhc.baselib.net.NetDownloadTask;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;

public class DownloadTask extends NetDownloadTask {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(DownloadTask.class, Logger.LOGLV_DEFAULT);

    private static final int DEFAULT_NET_READ_BUFFER_SIZE = 16 * 1024;

    private long mId;
    private long mCid;
    private Err mErr = Err.UNKNOWN;

    public DownloadTask(
            @NonNull String name,
            @NonNull HandlerAdapter owner,
            @NonNull NetConn netConn,
            @NonNull File tmpFile,
            @NonNull File outFile,
            int netReadBufferSize,
            int priority,
            boolean interruptOnCancel,
            long id)
            throws IOException {
        super(name,
              owner,
              netConn,
              tmpFile,
              outFile,
              netReadBufferSize,
              priority,
              interruptOnCancel);
        mId = id;
        mCid = DBPolicy.get().getItemInfoLong(mId, ColumnItem.CHANNELID);
    }

    public static class Builder<B extends Builder>
            extends NetDownloadTask.Builder<B> {
        protected final long mId;

        public Builder(
                @NonNull NetConn netConn,
                @NonNull File outfile,
                long id) throws IOException {
            super(netConn, outfile);
            mName = "DownloadTask(" + id + ")";
            mPriority = ThreadEx.TASK_PRIORITY_MIN;
            mInterruptOnCancel = true;
            mId = id;
        }

        @Override
        @NonNull
        public DownloadTask
        create() {
            try {
                return new DownloadTask(mName,
                                        mOwner,
                                        mNetConn,
                                        mTmpFile,
                                        mOutfile,
                                        mBufferSize,
                                        mPriority,
                                        mInterruptOnCancel,
                                        mId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    @Override
    protected NetDownloadTask.Result
    doAsync() throws IOException, InterruptedException {
        try {
            NetDownloadTask.Result r = super.doAsync();
            ContentsManager cm = ContentsManager.get();
            cm.addItemContent(r.outFile, mId);
            mErr = Err.NO_ERR;
            return r;
        } catch (SocketException | InterruptedIOException e) {
            /* SocketTimeoutException extends InterruptedIOException.
             */
            mErr = Err.IO_NET;
            throw e;
        } catch (InterruptedException e) {
            mErr = Err.USER_CANCELLED;
            throw e;
        } catch (IOException e) {
            mErr = Err.IO_FILE;
            throw e;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * @throws IOException (MalformedURLException, IOException)
     */
    @NonNull
    public static DownloadTask
    create(String url, long id) throws IOException {
        NetConn netConn = Util.createNetConn(new URL(url));
        File tf = Util.getNewTempFile();
        File of = ContentsManager.get().getItemInfoDataFile(id);
        if (null == of)
            throw new IOException();
        // create directory for downloaded item data.
        //noinspection ResultOfMethodCallIgnored
        of.getParentFile().mkdirs();
        DownloadTask.Builder<DownloadTask.Builder> b
                = new DownloadTask.Builder<>(netConn, of, id);
        b.setTmpFile(tf);
        return b.create();
    }

    public Err
    getErr() {
        return mErr;
    }

    public long
    getItemId() {
        return mId;
    }

    public long
    getChannelId() {
        return mCid;
    }
}
