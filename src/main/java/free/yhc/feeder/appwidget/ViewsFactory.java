/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2016
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

package free.yhc.feeder.appwidget;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.TmTask;
import free.yhc.baselib.async.TaskManagerBase;
import free.yhc.baselib.net.NetDownloadTask;
import free.yhc.baselib.net.NetReadTask;
import free.yhc.abaselib.util.UxUtil;
import free.yhc.feeder.R;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.ItemActionHandler;
import free.yhc.feeder.core.ListenerManager;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.task.DownloadTask;

import static free.yhc.abaselib.util.AUtil.isUiThread;

public class ViewsFactory implements
RemoteViewsService.RemoteViewsFactory,
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ViewsFactory.class, Logger.LOGLV_DEFAULT);

    private static final int COLI_ID                 = 0;
    private static final int COLI_CHANNELID          = 1;
    private static final int COLI_TITLE              = 2;
    private static final int COLI_DESCRIPTION        = 3;
    @SuppressWarnings("unused")
    private static final int COLI_ENCLOSURE_LENGTH   = 4;
    @SuppressWarnings("unused")
    private static final int COLI_ENCLOSURE_URL      = 5;
    @SuppressWarnings("unused")
    private static final int COLI_ENCLOSURE_TYPE     = 6;
    @SuppressWarnings("unused")
    private static final int COLI_PUBDATE            = 7;
    @SuppressWarnings("unused")
    private static final int COLI_LINK               = 8;

    private static final ColumnItem[] sQueryProjection = new ColumnItem[] {
            ColumnItem.ID, // Mandatory.
            ColumnItem.CHANNELID,
            ColumnItem.TITLE,
            ColumnItem.DESCRIPTION,
            ColumnItem.ENCLOSURE_LENGTH,
            ColumnItem.ENCLOSURE_URL,
            ColumnItem.ENCLOSURE_TYPE,
            ColumnItem.PUBDATE,
            ColumnItem.LINK };

    private final DBPolicy  mDbp = DBPolicy.get();
    private final ContentsManager mCm = ContentsManager.get();
    private final RTTask mRtt = RTTask.get();
    private final int mAppWidgetId;
    private final DBWatcher mDbWatcher;
    private final ItemActionHandler mItemAction;
    private final DownloadTaskListener mDownloadTaskListener
            = new DownloadTaskListener();

    private long mCategoryId;
    private long[] mCids = null;
    private Cursor mCursor = null;
    private final Object mCursorLock = new Object();

    private class DBWatcher implements ListenerManager.Listener {
        private final HashSet<Long> _mCidSet = new HashSet<>();

        // See comment at 'onNotify'
        private AtomicReference<Boolean> _mClosed = new AtomicReference<>(false);

        private boolean
        isInCategory(Long cid) {
            return null != cid && _mCidSet.contains(cid);
        }

        private boolean
        isInCategoryItem(long id) {
            Long cid = mDbp.getItemInfoLong(id, ColumnItem.CHANNELID);
            return isInCategory(cid);
        }

        void
        register() {
            mDbp.registerUpdatedListener(this, DB.UpdateType.CHANNEL_DATA.flag()
                                               | DB.UpdateType.CHANNEL_TABLE.flag());
            mDbp.registerChannelUpdatedListener(this, this);
            mCm.registerUpdatedListener(this, ContentsManager.UpdateType.ITEM_DATA.flag()
                                              | ContentsManager.UpdateType.CHAN_DATA.flag());

        }

        void
        close() {
            _mClosed.set(true);
        }

        void
        unregister() {
            mDbp.unregisterUpdatedListener(this);
            mDbp.unregisterChannelUpdatedListener(this);
            mCm.unregisterUpdatedListener(this);
        }

        void
        updateCategoryChannels(long[] cids) {
            _mCidSet.clear();
            for (long cid : cids)
                _mCidSet.add(cid);
        }

        @Override
        public void
        onNotify(Object user, ListenerManager.Type type, Object arg0, Object arg1) {
            // NOTE
            // unregister is called at UI context after onDestroy by 'post'.
            // So, following case is possible.
            // 1. onDestroy is called. And ViewsFactory is destroyed.
            //    (member variables of ViewsFactory are unavailable)
            // 2. Before 'unregister' is called at UI context, 'onNotify' is called.
            // 3. Accessing to member variables of ViewsFactory leads to unexpected results.
            //
            // To avoid this, _mClosed flag is used.
            if (_mClosed.get())
                return;

            if (type instanceof DB.UpdateType) {
                switch ((DB.UpdateType)type) {
                case CHANNEL_DATA:
                    long cid = (Long)arg0;
                    // Check that number of channels in the category is changed.
                    long catid = mDbp.getChannelInfoLong(cid, ColumnChannel.CATEGORYID);
                    if (DBG) P.v("Channel update : " + ((DB.UpdateType)type).name() + " : "
                                                 + mAppWidgetId + ", "
                                                 +  mCategoryId + ", " + catid + ", " + cid);
                    if ((!isInCategory(cid) && catid == mCategoryId) // channel is newly inserted to this category.
                        || (isInCategory(cid) && catid != mCategoryId)) { // channel is removed from this category.
                        refreshItemList();
                    }
                    break;

                case CHANNEL_TABLE:
                    // Channel may be deleted or newly inserted.
                    // So, refresh item list forcely...
                    refreshItemList();
                    break;

                default:
                    P.bug(false);
                }
            } else if (type instanceof DBPolicy.UpdateType) {
                switch ((DBPolicy.UpdateType)type) {
                case NEW_ITEMS: {
                    long cid = (Long)arg0;
                    int nrNewItems = (Integer)arg1;
                    if (isInCategory(cid)
                            && nrNewItems > 0)
                            refreshItemList();
                } break;

                case LAST_ITEM_ID:
                    break; // ignore

                default:
                    P.bug(false);
                }
            } else if (type instanceof ContentsManager.UpdateType) {
                switch ((ContentsManager.UpdateType)type) {
                case ITEM_DATA:
                    for (long id : (long[])arg0) {
                        if (isInCategoryItem(id)) {
                            refreshItemList();
                            break;
                        }
                    }
                    break;

                case CHAN_DATA:
                    for (long cid : (long[])arg0) {
                        if (isInCategory(cid)) {
                            refreshItemList();
                            break;
                        }
                    }
                    break;
                }
            }
        }
    }

    private class RTTaskEventHandler
            implements TaskManagerBase.TaskQEventListener {
        @Override
        public void
        onEvent(
                @NonNull TaskManagerBase tm,
                @NonNull TaskManagerBase.TaskQEvent ev,
                int szReady, int szRun,
                @NonNull TmTask task) {
            RTTask rtt = (RTTask)tm;
            RTTask.TaskInfo ti = rtt.getTaskInfo(task);
            long dbid = (Long)ti.ttag;
            switch (ev) {
            case ADDED_TO_READY:
                if (RTTask.Action.DOWNLOAD == ti.ttype
                        && isWidgetItem(dbid))
                    //noinspection RedundantCast
                    ((DownloadTask)task).addEventListener(
                            AppEnv.getUiHandlerAdapter(), mDownloadTaskListener);
                break;
            case MOVED_TO_RUN:
                break; // nothing to do
            case REMOVED_FROM_READY:
            case REMOVED_FROM_RUN:
                //noinspection unchecked
                if (RTTask.Action.DOWNLOAD == ti.ttype)
                    //noinspection RedundantCast
                    ((DownloadTask)task).removeEventListener(mDownloadTaskListener);
                break;
            }

        }
    }

    private class DownloadTaskListener extends NetDownloadTask.EventListener<TmTask, NetReadTask.Result> {
        @Override
        public void
        onCancelled(@NonNull TmTask task, Object param) {
            if (DBG) P.v(task.getUniqueName());
            notifyDataSetChanged();
        }

        @Override
        public void
        onStarted(@NonNull TmTask task) {
            if (DBG) P.v(task.getUniqueName());
            // icon should be changed from 'ready' to 'running'
            notifyDataSetChanged();
        }

        @Override
        public void
        onPostRun(@NonNull TmTask task, NetReadTask.Result result, Exception ex) {
            if (DBG) P.v(task.getUniqueName());
            notifyDataSetChanged();
        }
    }

    private class AdapterBridge implements ItemActionHandler.AdapterBridge {
        @Override
        public void
        updateItemState(int pos, long state) {
            if (DBG) P.v("" + pos + ", " + state);
        }

        @Override
        public void
        itemDataChanged(long id) {
            if (DBG) P.v("" + id);
            // icon should be changed from 'ready' to 'running'
            notifyDataSetChanged();
        }

        @Override
        public void
        dataSetChanged() {
            if (DBG) P.v();
            notifyDataSetChanged();
        }
    }

    private Cursor
    getCursor() {
        mCids = mDbp.getChannelIds(mCategoryId);
        mDbWatcher.updateCategoryChannels(mCids);
        if (DBG) P.v("Channels : " + Util.nrsToNString(mCids));
        return mDbp.queryItem(mCids, sQueryProjection);
    }

    private boolean
    isWidgetChannel(long cid) {
        for (long i : mCids) {
            if (i == cid)
                return true;
        }
        return false;
    }

    private boolean
    isWidgetItem(long id) {
        long cid = mDbp.getItemInfoLong(id, ColumnItem.CHANNELID);
        return isWidgetChannel(cid);
    }

    private void
    notifyDataSetChanged() {
        AppWidgetManager awm = AppWidgetManager.getInstance(AppEnv.getAppContext());
        awm.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.list);
    }

    private void
    refreshItemList() {
        if (DBG) P.v("WidgetDataChanged : " + mAppWidgetId);
        Cursor newCur = getCursor();
        Cursor cur = mCursor;
        synchronized (mCursorLock) {
            mCursor = newCur;
        }
        notifyDataSetChanged();
        if (null != cur)
            cur.close();
    }

    public ViewsFactory(long categoryId, int appWidgetId) {
        mCategoryId = categoryId;
        mAppWidgetId = appWidgetId;

        mDbWatcher = new DBWatcher();
        mDbWatcher.register();

        Cursor cur = getCursor();
        // Doing time-consuming job in advance.
        cur.getCount();
        cur.moveToFirst();
        mCursor = cur;

        mItemAction = new ItemActionHandler(null, new AdapterBridge());
        mRtt.addTaskQEventListener(AppEnv.getUiHandlerAdapter(),
                                   new RTTaskEventHandler());
    }

    boolean
    setCategory(long categoryId) {
        if (mCategoryId == categoryId)
            return false; // nothing to do
        if (DBG) P.d("Category Updated : " + mCategoryId + " -> " + categoryId);
        mCategoryId = categoryId;
        refreshItemList();
        return true;
    }

    long
    getCategory() {
        return mCategoryId;
    }

    void
    onItemClick(int position, long id) {
        P.bug(isUiThread());
        if (DBG) P.v("pos : " + position);
        Long cidLong = mDbp.getItemInfoLong(id, ColumnItem.CHANNELID);
        if (null == cidLong) {
            // CASE (reproducing step)
            //   - Channel is deleted
            //   - App widget is NOT updated yet.
            //   - User select ALREADY-DELETED-ITEM
            UxUtil.showTextToast(R.string.warn_bad_request);
            return;
        }

        long cid = cidLong;
        long act = mDbp.getChannelInfoLong(cid, ColumnChannel.ACTION);
        String[] strs = mDbp.getItemInfoStrings(id, new ColumnItem[] { ColumnItem.LINK,
                                                                       ColumnItem.ENCLOSURE_URL,
                                                                       ColumnItem.ENCLOSURE_TYPE });
        mItemAction.onAction(act,
                             id,
                             position,
                             strs[0], // link
                             strs[1], // enclosure
                             strs[2]);// type.
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .appwidget.ViewsFactory ]";
    }

    @Override
    public int
    getCount() {
        synchronized (mCursorLock) {
            return mCursor.getCount();
        }
    }

    @Override
    public long
    getItemId(int position) {
        synchronized (mCursorLock) {
            // Called at binder thread
            mCursor.moveToPosition(position);
            return mCursor.getLong(COLI_ID);
        }
    }

    @Override
    public RemoteViews
    getLoadingView() {
        // Called at binder thread
        // TODO
        // Loading view here.
        if (DBG) P.v("Enter");
        return null;
    }

    @Override
    public RemoteViews
    getViewAt(int position) {
        // Called at binder thread
        long iid, cid;
        String title, desc;
        synchronized (mCursorLock) {
            mCursor.moveToPosition(position);
            iid = mCursor.getLong(COLI_ID);
            cid = mCursor.getLong(COLI_CHANNELID);
            title = mCursor.getString(COLI_TITLE);
            desc = mCursor.getString(COLI_DESCRIPTION);
        }
        RemoteViews rv = new RemoteViews(AppEnv.getAppContext().getPackageName(),
                                         R.layout.appwidget_row);
        rv.setTextViewText(R.id.channel, mDbp.getChannelInfoString(cid, ColumnChannel.TITLE));
        rv.setTextViewText(R.id.title, title);
        rv.setTextViewText(R.id.description, desc);


        File df = ContentsManager.get().getItemInfoDataFile(iid);
        if (DBG) {
            String msg = "pos: " + position + ", id: " + iid;
            if (null == df)
                msg += "<null>";
            else
                msg += "(" + df.exists() + ")" + df.getAbsolutePath() + "]";
            P.v(msg);
        }
        if (null != df && df.exists()) {
            rv.setViewVisibility(R.id.image, View.VISIBLE);
            rv.setImageViewResource(R.id.image, R.drawable.ic_save);
        } else {
            DownloadTask t = mRtt.getDownloadTask(iid);
            RTTask.RtState rtstate = mRtt.getRtState(t);
            if (DBG) P.v("RtState: " + rtstate.name());
            int icon = 0; // invalid icon number
            switch (rtstate) {
            case IDLE: icon = 0; break;
            case READY: icon = R.drawable.ic_pause; break;
            case RUN: icon = R.drawable.ic_refresh; break;
            case CANCEL: icon = R.drawable.ic_block; break;
            case FAIL: icon = R.drawable.ic_info; break;
            }
            if (0 != icon) {
                rv.setViewVisibility(R.id.image, View.VISIBLE);
                rv.setImageViewResource(R.id.image, icon);
            } else
                rv.setViewVisibility(R.id.image, View.GONE);
        }

        Intent ei = new Intent();
        ei.putExtra(AppWidgetUtils.MAP_KEY_POSITION, position);
        ei.putExtra(AppWidgetUtils.MAP_KEY_ITEMID, iid);
        rv.setOnClickFillInIntent(R.id.item_root, ei);
        return rv;
    }

    @Override
    public int
    getViewTypeCount() {
        // See http://developer.android.com/reference/android/widget/Adapter.html
        return 1;
    }

    @Override
    public boolean
    hasStableIds() {
        return true;
    }

    @Override
    public void
    onCreate() {
        if (DBG) P.v("widgetid : " + mAppWidgetId + " / " + mCategoryId);
    }

    @Override
    public void
    onDataSetChanged() {
        // Called at binder thread
    }

    @Override
    public void
    onDestroy() {
        // Call 'close()' to avoid race condition
        // See comments in 'onNotify' at DBWatcher
        mDbWatcher.close();
        // mDbWatcher.unregister SHOULD be called at UI context
        // See unregisterUpdatedListener at DB.java for details.
        AppEnv.getUiHandler().post(new Runnable() {
            @Override
            public void
            run() {
                mDbWatcher.unregister();
            }
        });
        if (DBG) P.v("Exit");
    }

    @Override
    protected void
    finalize() throws Throwable {
        super.finalize();
        if (null != mCursor)
            mCursor.close();
    }
}
