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

package free.yhc.feeder.core;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.abaselib.util.UxUtil;
import free.yhc.feeder.ItemViewActivity;
import free.yhc.feeder.R;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.feed.Feed;
import free.yhc.feeder.feed.FeedPolicy;
import free.yhc.feeder.task.DownloadTask;

public class ItemActionHandler {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ItemActionHandler.class, Logger.LOGLV_DEFAULT);

    public static final int REQC_ITEM_VIEW = 0;

    private final DBPolicy mDbp = DBPolicy.get();
    private final RTTask mRtt = RTTask.get();
    private final Activity mActivity;
    private final Context mContext;
    private final AdapterBridge mABridge;

    public interface AdapterBridge {
        void updateItemState(int pos, long state);
        void itemDataChanged(long id);
        void dataSetChanged();
    }

    private void
    finalizeIntent(Intent intent) {
        if (null != mActivity)
            return;

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private boolean
    changeItemState_opened(long id, int position) {
        // change state as 'opened' at this moment.
        long state = mDbp.getItemInfoLong(id, ColumnItem.STATE);
        if (Feed.Item.isStateOpenNew(state)) {
            state = Util.bitSet(state, Feed.Item.FSTAT_OPEN_OPENED, Feed.Item.MSTAT_OPEN);
            mDbp.updateItemAsync_state(id, state);
            mABridge.updateItemState(position, state);
            mABridge.itemDataChanged(id);
            return true;
        }
        return false;
    }

    private void
    onActionOpen_http(long action, long id, int position, String url, String protocol) {
        DownloadTask t = mRtt.getDownloadTask(id);
        if (null != t && mRtt.isFailed(t)) {
            UxUtil.showTextToast(t.getErr().getMsgId());
            mRtt.removeWatchedTask(t);
            mABridge.itemDataChanged(id);
            return;
        }

        if (Feed.Channel.isActProgIn(action)) {
            Intent intent = new Intent(mContext, ItemViewActivity.class);
            intent.putExtra("id", id);
            finalizeIntent(intent);
            if (null != mActivity)
                mActivity.startActivityForResult(intent, REQC_ITEM_VIEW);
            else
                mContext.startActivity(intent);
        } else if (Feed.Channel.isActProgEx(action)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            try {
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                UxUtil.showTextToast(
                        mContext.getResources().getText(R.string.warn_find_app_to_open).toString() + protocol);
                return;
            }
        } else
            P.bug(false);

        changeItemState_opened(id, position);
    }

    private void
    onActionOpen_rtsp(@SuppressWarnings("unused") long action,
                      long id, int position, String url, String protocol) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        finalizeIntent(intent);
        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            UxUtil.showTextToast(
                    mContext.getResources().getText(R.string.warn_find_app_to_open).toString() + protocol);
            return;
        }

        changeItemState_opened(id, position);
    }

    private void
    onActionOpen(long action, long id, int position, String url) {
        String protocol = url.substring(0, url.indexOf("://"));
        if (protocol.equalsIgnoreCase("rtsp"))
            onActionOpen_rtsp(action, id, position, url, protocol);
        else // default : handle as http
            onActionOpen_http(action, id, position, url, protocol);
    }

    private void
    onActionDn(@SuppressWarnings("unused") long action,
               long id, int position, String url, String encType) {
        // 'enclosure' is used.
        File f = ContentsManager.get().getItemInfoDataFile(id);
        P.bug(null != f);
        assert null != f;
        if (f.exists()) {
            // "RSS described media type" vs "mime type by guessing from file extention".
            // Experimentally, later is more accurate! (lots of RSS doesn't care about describing exact media type.)
            String type = Util.guessMimeTypeFromUrl(url);
            if (null == type)
                type = encType;

            if (!Util.isMimeType(type))
                type = "text/plain"; // this is default.


            // File is already exists. Do action with it!
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (null == type)
                intent.setData(Uri.fromFile(f));
            else
                intent.setDataAndType(Uri.fromFile(f), type);
            finalizeIntent(intent);

            try {
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                UxUtil.showTextToast(
                        mContext.getResources().getText(R.string.warn_find_app_to_open).toString() + " [" + type + "]");
                return;
            }
            // change state as 'opened' at this moment.
            changeItemState_opened(id, position);
        } else {
            DownloadTask t = mRtt.getDownloadTask(id);
            switch(mRtt.getRtState(t)) {
            case IDLE: {
                DownloadTask dnTask;
                try {
                    dnTask = DownloadTask.create(url, id);
                    if (!mRtt.addTask(dnTask, id, RTTask.Action.DOWNLOAD))
                        P.bug(false);
                    mABridge.itemDataChanged(id);
                } catch (MalformedURLException e) {
                    UxUtil.showTextToast(R.string.err_url);
                } catch (ConnectException e) {
                    UxUtil.showTextToast(R.string.err_ionet);
                } catch (IOException e) {
                    UxUtil.showTextToast(R.string.err_iofile);
                }
            } break;

            case RUN:
            case READY:
                assert t != null;
                mRtt.cancelTask(t, null);
                mABridge.itemDataChanged(id);
                break;

            case CANCEL:
                UxUtil.showTextToast(R.string.wait_cancel);
                break;

            case FAIL: {
                assert null != t;
                UxUtil.showTextToast(t.getErr().getMsgId());
                mRtt.removeWatchedTask(t);
                mABridge.itemDataChanged(id);
            } break;

            default:
                P.bug(false);
            }
        }
    }

    public void
    onAction(long action, long id, final int position,
             String link, String enclosure, String encType) {
        // NOTE
        // This is very simple policy!
        long actionType = Feed.Channel.getActType(action);
        //String link = getListAdapter().getItemInfo_link(position);
        //String enclosure = getListAdapter().getItemInfo_encUrl(position);

        if (Feed.Channel.FACT_TYPE_DYNAMIC == actionType) {
            String url = FeedPolicy.getDynamicActionTargetUrl(action, link, enclosure);
            // NOTE
            // Items that have both invalid link and enclosure url, SHOULD NOT be added to DB.
            // Parser give those away in parsing phase.
            // See RSSParser/AtomParser.
            P.bug(null != url);
            if (enclosure.equals(url))
                onActionDn(action, id, position, url, encType);
            else
                onActionOpen(action, id, position, url);
        } else if (Feed.Channel.FACT_TYPE_EMBEDDED_MEDIA == actionType) {
            // In case of embedded media, external program should be used in force.
            action = Util.bitSet(action, Feed.Channel.FACT_PROG_EX, Feed.Channel.MACT_PROG);
            onActionOpen(action, id, position, enclosure);
        } else
            P.bug(false);
    }

    public ItemActionHandler(Activity activity,
                             AdapterBridge bridge) {
        mABridge = bridge;
        mActivity = activity;
        mContext = null == mActivity? AppEnv.getAppContext(): mActivity;
    }
}
