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

package free.yhc.feeder;

import java.io.File;
import java.util.Calendar;
import java.util.HashSet;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.TmTask;
import free.yhc.baselib.async.TaskBase;
import free.yhc.baselib.async.TaskManagerBase;
import free.yhc.baselib.net.NetReadTask;
import free.yhc.abaselib.util.AUtil;
import free.yhc.abaselib.util.UxUtil;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.task.DownloadTask;
import free.yhc.feeder.task.UpdateTask;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.feed.Feed;
import free.yhc.feeder.core.ItemActionHandler;
import free.yhc.feeder.core.ListenerManager;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

import static free.yhc.baselib.util.Util.convertArrayLongTolong;

public class ItemListActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ItemListActivity.class, Logger.LOGLV_DEFAULT);

    private static final int DATA_REQ_SZ = 20;
    private static final int DATA_ARR_MAX = 500;

    // Keys for extra value of intent : IKey (Intent Key)
    public static final String IKEY_MODE = "mode";  // mode
    public static final String IKEY_FILTER = "filter";// filter

    // Option flags

    // bit[0:2] mode : scope of items to list
    // others are reserved (ex. ALL or ALL_LINK or ALL_ENCLOSURE)
    // Base query outline to create cursor, depends on this Mode value.
    public static final int MODE_CHANNEL  = 0; // items of channel
    public static final int MODE_CATEGORY = 1; // items of category
    public static final int MODE_FAVORITE = 2; // favorite items
    public static final int MODE_ALL      = 3; // all items

    // bit[3:7] filter : select items
    // others are reserved (ex. filtering items with time (specific period of time) etc)
    // Filtering from cursor depends on this value.
    public static final int FILTER_NONE = 0; // no filter
    @SuppressWarnings("unused")
    public static final int FILTER_NEW  = 1; // new items of each channel.

    private final DBPolicy mDbp = DBPolicy.get();
    private final RTTask mRtt = RTTask.get();

    private final HashSet<UpdateTask> mUpTasks = new HashSet<>();
    private final HashSet<DownloadTask> mDnTasks = new HashSet<>();
    private final UpdateTaskListener mUpdateTaskListener = new UpdateTaskListener();
    private final DownloadTaskListener mDownloadTaskListener = new DownloadTaskListener();
    private final RtTaskQEventListener mRtTaskQEventListener = new RtTaskQEventListener();
    private final RtTaskQEventListenerPaused mRtTaskQEventListenerPaused
            = new RtTaskQEventListenerPaused();

    private DBWatcher mDbWatcher = null;
    private OpMode mOpMode = null;
    private ListView mList = null;
    private ItemActionHandler mItemAction;

    private static class DBWatcher implements ListenerManager.Listener {
        private final HashSet<Long> _mUpdatedChannelSet = new HashSet<>();
        // NOTE
        // initial value should be 'true' because we don't know what happened to DB
        //   while this activity instance DOESN'T exist!
        private boolean _mItemTableUpdated = true;

        void
        register() {
            DBPolicy.get().registerUpdatedListener(this,
                                                  DB.UpdateType.CHANNEL_DATA.flag()
                                                  | DB.UpdateType.ITEM_TABLE.flag());
        }

        void
        unregister() {
            DBPolicy.get().unregisterUpdatedListener(this);
        }

        void
        reset() {
            _mUpdatedChannelSet.clear();
            _mItemTableUpdated = false;
        }

        boolean
        isItemTableUpdated() {
            return _mItemTableUpdated;
        }

        long[]
        getUpdatedChannels() {
            return convertArrayLongTolong(_mUpdatedChannelSet.toArray(new Long[_mUpdatedChannelSet.size()]));
        }

        @Override
        public void
        onNotify(Object user, ListenerManager.Type type, Object arg0, Object arg1) {
            switch ((DB.UpdateType)type) {
            case CHANNEL_DATA:
                _mUpdatedChannelSet.add((Long)arg0);
                break;

            case ITEM_TABLE:
                _mItemTableUpdated = true;
                break;

            default:
                P.bug(false);
            }
        }
    }

    private class OpMode {
        // 'State' of item may be changed often dynamically (ex. when open item)
        // So, to synchronized cursor information with item state, we need to refresh cursor
        //   whenever item state is changed.
        // But it's big overhead.
        // So, in case STATE, it didn't included in list cursor, but read from DB if needed.
        protected final ColumnItem[] _mQueryProjection = new ColumnItem[] {
                    ColumnItem.ID, // Mandatory.
                    ColumnItem.CHANNELID,
                    ColumnItem.TITLE,
                    ColumnItem.DESCRIPTION,
                    ColumnItem.ENCLOSURE_LENGTH,
                    ColumnItem.ENCLOSURE_URL,
                    ColumnItem.ENCLOSURE_TYPE,
                    ColumnItem.PUBDATE,
                    ColumnItem.LINK };

        protected String _mSearch = "";
        protected long _mFromPubtime = -1;
        protected long _mToPubtime = -1;
        protected volatile boolean _mBgtaskRunning = false;

        void
        onCreate() {
            //noinspection ConstantConditions
            getActionBar().setDisplayShowHomeEnabled(false);
        }

        void onResume() {}
        long[] getCids() { return new long[0]; }
        Cursor query() { return null; }

        boolean
        doesRunningBGTaskExists() {
            return _mBgtaskRunning;
        }

        long minPubtime() {
            return -1;
        }

        void
        setFilter(String search, long fromPubtime, long toPubtime) {
            _mSearch = search;
            _mFromPubtime = fromPubtime;
            _mToPubtime = toPubtime;
        }

        @SuppressWarnings("unused")
        boolean
        isFilterEnabled() {
            return !_mSearch.isEmpty()
                   || ((_mFromPubtime <= _mToPubtime) && (_mFromPubtime > 0));
        }

        ItemListAdapter.OnActionListener
        getAdapterActionHandler() {
            return new ItemListAdapter.OnActionListener() {
                @Override
                public void
                onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state) {
                    // Toggle Favorite bit.
                    state = state ^ Feed.Item.MSTAT_FAV;
                    mDbp.updateItemAsync_state(id, state);
                    adapter.updateItemState(position, state);
                    dataSetChanged();
                }
            };
        }
    }

    private class OpModeChannel extends OpMode {
        private long _mCid; // channel id

        OpModeChannel(Intent i) {
            _mCid = i.getLongExtra("cid", -1);
            P.bug(-1 != _mCid);
        }

        @Override
        long[]
        getCids() {
            return new long[] { _mCid };
        }

        @Override
        void
        onCreate() {
            super.onCreate();
            setTitle(mDbp.getChannelInfoString(_mCid, ColumnChannel.TITLE));
            // TODO
            //   How to use custom view + default option menu ???
            //
            // Set custom action bar
            ActionBar bar = getActionBar();
            P.bug(null != bar);
            assert null != bar;
            @SuppressLint("InflateParams")
            LinearLayout abView = (LinearLayout)getLayoutInflater().inflate(R.layout.item_list_actionbar,null);
            bar.setCustomView(abView, new ActionBar.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    Gravity.RIGHT));

            int change = bar.getDisplayOptions() ^ ActionBar.DISPLAY_SHOW_CUSTOM;
            //noinspection ResourceType
            bar.setDisplayOptions(change, ActionBar.DISPLAY_SHOW_CUSTOM);
            // Update "OLDLAST_ITEMID" when user opens item views.
            mDbp.updateChannel_lastItemId(_mCid);
        }

        @Override
        void
        onResume() {
            super.onResume();
            // See comments in 'ChannelListActivity.onPause()'
            // Bind update task if needed
            addChannelTaskEventListeners(new long[] {_mCid});
            setUpdateButton();
        }

        @Override
        Cursor
        query() {
            return mDbp.queryItem(_mCid, _mQueryProjection, _mSearch, _mFromPubtime, _mToPubtime);
        }

        @Override
        long
        minPubtime() {
            return mDbp.getItemMinPubtime(_mCid);
        }
    }

    private class OpModeCategory extends OpMode {
        private long _mCategoryid; // category id
        private long[] _mCids = null;

        OpModeCategory(Intent intent) {
            _mCategoryid = intent.getLongExtra("categoryid", -1);
            P.bug(-1 != _mCategoryid);
            _mCids = mDbp.getChannelIds(_mCategoryid);
        }

        @Override
        long[]
        getCids() {
            return _mCids;
        }

        @Override
        void
        onCreate() {
            super.onCreate();
            setTitle(getResources().getString(R.string.category) + ":" + mDbp.getCategoryName(_mCategoryid));
            _mBgtaskRunning = true;
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        mDbp.getDelayedChannelUpdate();
                        mDbp.updateChannel_lastItemIds(_mCids);
                    } finally {
                        mDbp.putDelayedChannelUpdate();
                        _mBgtaskRunning = false;
                    }
                }
            });
        }

        @Override
        void
        onResume() {
            super.onResume();
            // Bind update task if needed
            addChannelTaskEventListeners(_mCids);
        }

        @Override
        Cursor
        query() {
            return mDbp.queryItem(_mCids, _mQueryProjection, _mSearch, _mFromPubtime, _mToPubtime);
        }

        @Override
        long
        minPubtime() {
            return mDbp.getItemMinPubtime(_mCids);
        }
    }

    private class OpModeFavorite extends OpMode {
        OpModeFavorite(@SuppressWarnings("unused")  Intent intent) {
        }

        @Override
        void
        onCreate() {
            super.onCreate();
            setTitle(getResources().getString(R.string.favorite_items));
        }

        @Override
        void
        onResume() {
            super.onResume();
            long[] ids = mRtt.getItemsDownloading();
            try {
                mDbp.getDelayedChannelUpdate();
                for (long id : ids)
                    if (Feed.Item.isStatFavOn(mDbp.getItemInfoLong(id, ColumnItem.STATE)))
                        addItemTaskEventListeners(new long[] {id});
            } finally {
                mDbp.putDelayedChannelUpdate();
            }
        }

        @Override
        ItemListAdapter.OnActionListener
        getAdapterActionHandler() {
            return new ItemListAdapter.OnActionListener() {
                @Override
                public void
                onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state) {
                    // Toggle Favorite bit.
                    state = state ^ Feed.Item.MSTAT_FAV;
                    mDbp.updateItemAsync_state(id, state);
                    P.bug(!Feed.Item.isStatFavOn(state));
                    adapter.removeItem(position);
                    dataSetChanged();
                }
            };
        }

        @Override
        Cursor
        query() {
            return mDbp.queryItemMask(_mQueryProjection, ColumnItem.STATE,
                                      Feed.Item.MSTAT_FAV, Feed.Item.FSTAT_FAV_ON,
                                      _mSearch, _mFromPubtime, _mToPubtime);
        }

        @Override
        long
        minPubtime() {
            return mDbp.getItemMinPubtime(ColumnItem.STATE,
                                          Feed.Item.MSTAT_FAV,
                                          Feed.Item.FSTAT_FAV_ON);
        }
    }

    private class OpModeAll extends OpMode {
        private long[] mCids = null;

        OpModeAll(@SuppressWarnings("unused")  Intent intent) {
            mCids = mDbp.getChannelIds();
        }

        @Override
        long[]
        getCids() {
            return mCids;
        }

        @Override
        void
        onCreate() {
            super.onCreate();
            setTitle(getResources().getString(R.string.all_items));

            _mBgtaskRunning = true;
            AsyncTask.execute(new Runnable() {
                @Override
                public void
                run() {
                    try {
                        mDbp.getDelayedChannelUpdate();
                        mDbp.updateChannel_lastItemIds(mCids);
                    } finally {
                        mDbp.putDelayedChannelUpdate();
                        _mBgtaskRunning = false;
                    }
                }
            });

        }

        @Override
        void
        onResume() {
            super.onResume();
            addItemTaskEventListeners(mRtt.getItemsDownloading());
        }

        @Override
        Cursor
        query() {
            return mDbp.queryItem(_mQueryProjection, _mSearch, _mFromPubtime, _mToPubtime);
        }

        @Override
        long
        minPubtime() {
            return mDbp.getItemMinPubtime();
        }
    }

    public class UpdateTaskListener extends TaskBase.EventListener<UpdateTask, Err> {
        UpdateTaskListener() {}

        @Override
        public void
        onCancelled(@NonNull UpdateTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (isActivityFinishing())
                return;

            requestSetUpdateButton();
        }

        @Override
        public void
        onStarted(@NonNull UpdateTask task) {
            requestSetUpdateButton();
        }

        @Override
        public void
        onPostRun(@NonNull UpdateTask task, Err result, Exception ex) {
            // WHY below code is required?
            // Even if activity is already in 'pause', 'stop' or 'destroy' state,
            //   corresponding callback is run on UI Thread.
            // So, it is posted to message loop.
            // But, in case that there are messages already in message - ex refreshing list -
            //   in message queue before each callback is posted, those messages will be handled
            //   even if activity is already in finishing state.
            // Therefore, in this exceptional case, some operations that require alive-activity,
            //   are failed or do something unexpected.
            // To avoid this, let's check activity state before moving forward
            if (isActivityFinishing())
                return;

            requestSetUpdateButton();

            if (Err.NO_ERR == result) {
                // Update "OLDLAST_ITEMID"
                // User already know that there is new feed because
                //   user starts to update in feed screen!.
                // So, we can assume that user knows latest updates here.
                mDbp.updateChannel_lastItemId(task.getChannelId());
                refreshListAsync();
            }
        }
    }

    private class DownloadTaskListener extends TaskBase.EventListener<DownloadTask, NetReadTask.Result> {
        DownloadTaskListener() {}

        @Override
        public void
        onCancelled(@NonNull DownloadTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (isActivityFinishing())
                return;
            itemDataChanged(task.getItemId());
        }

        @Override
        public void
        onStarted(@NonNull DownloadTask task) {
            // icon should be changed from 'ready' to 'running'
            itemDataChanged(task.getItemId());
        }

        @Override
        public void
        onPostRun(@NonNull DownloadTask task,
                  NetReadTask.Result result,
                  Exception ex) {
            //logI("+++ Item Activity DownloadData PostRun");
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (isActivityFinishing())
                return;

            if (Err.NO_ERR == task.getErr())
                getListAdapter().updateItemHasDnFile(getListAdapter().findPosition(task.getItemId()), true);

            itemDataChanged(task.getItemId());
        }
    }

    private class RtTaskQEventListener implements TaskManagerBase.TaskQEventListener {
        @Override
        public void
        onEvent(@NonNull TaskManagerBase tm,
                @NonNull TaskManagerBase.TaskQEvent ev,
                int szReady, int szRun,
                @NonNull TmTask task) {
            RTTask rtt = (RTTask)tm;
            RTTask.TaskInfo ti = rtt.getTaskInfo(task);
            // This should be run on ui handler thread.
            switch (ev) {
            case ADDED_TO_READY:
                if (RTTask.Action.UPDATE == ti.ttype)
                    addUpdateTaskListener(task);
                else if (RTTask.Action.DOWNLOAD == ti.ttype)
                    addDownloadTaskListener(task);
                else
                    P.bug(false);
                break;
            case REMOVED_FROM_READY:
            case REMOVED_FROM_RUN:
            case MOVED_TO_RUN:
            }
        }
    }

    private class RtTaskQEventListenerPaused implements RTTask.TaskQEventListener {
        @Override
        public void
        onEvent(@NonNull TaskManagerBase tm,
                @NonNull TaskManagerBase.TaskQEvent ev,
                int szReady, int szRun,
                @NonNull TmTask task) {
            RTTask rtt = (RTTask)tm;
            RTTask.TaskInfo ti = rtt.getTaskInfo(task);
            // This should be run on ui handler thread.
            switch (ev) {
            case REMOVED_FROM_READY:
            case REMOVED_FROM_RUN:
                if (RTTask.Action.DOWNLOAD == ti.ttype) {
                    DownloadTask t = (DownloadTask)task;
                    // This will be run after activity is paused.
                    File df = ContentsManager.get().getItemInfoDataFile(t.getItemId());
                    getListAdapter().updateItemHasDnFile(
                            getListAdapter().findPosition(t.getItemId()),
                            null != df && df.exists());
                }
                break;
            case ADDED_TO_READY:
            case MOVED_TO_RUN:
            }
        }
    }

    private class ListAdapterBridge implements ItemActionHandler.AdapterBridge {
        @Override
        public void
        updateItemState(int pos, long state) {
            getListAdapter().updateItemState(pos, state);
        }

        @Override
        public void
        itemDataChanged(long id) {
            ItemListActivity.this.itemDataChanged(id);
        }

        @Override
        public void
        dataSetChanged() {
            ItemListActivity.this.dataSetChanged();
        }
    }

    private boolean
    isActivityFinishing() {
        return isFinishing();
    }

    private ItemListAdapter
    getListAdapter() {
        if (DBG)P.bug(null != mList);
        return (ItemListAdapter)mList.getAdapter();
    }

    //=========================================================================
    //
    //=========================================================================
    private void
    addUpdateTaskListener(@NonNull TmTask t) {
        UpdateTask upt = (UpdateTask)t;
        if (upt.addEventListener(AppEnv.getUiHandlerAdapter(), mUpdateTaskListener))
            mUpTasks.add(upt);
    }

    private void
    addDownloadTaskListener(@NonNull TmTask t) {
        DownloadTask dnt = (DownloadTask)t;
        if (dnt.addEventListener(AppEnv.getUiHandlerAdapter(), mDownloadTaskListener))
            mDnTasks.add(dnt);
    }

    private void
    addItemTaskEventListeners(@NonNull long[] ids) {
        for (long id : ids) {
            DownloadTask t = mRtt.getDownloadTask(id);
            if (RTTask.RtState.IDLE != mRtt.getRtState(t)) {
                P.bug(null != t);
                assert null != t;
                addDownloadTaskListener(t);
            }
        }
    }

    private void
    addChannelTaskEventListeners(@NonNull long[] cids) {
        // See comments in 'ChannelListActivity.onPause()'
        // Bind update task if needed
        for (long cid : cids) {
            UpdateTask t = mRtt.getUpdateTask(cid);
            if (RTTask.RtState.IDLE != mRtt.getRtState(t)) {
                P.bug(null != t);
                assert null != t;
                addUpdateTaskListener(t);
            }
        }
        // Bind downloading tasks
        addItemTaskEventListeners(mRtt.getItemsDownloading(cids));
    }

    private void
    removeTaskEventListeners() {
        for (UpdateTask t : mUpTasks)
            t.removeEventListener(mUpdateTaskListener);
        for (DownloadTask t : mDnTasks)
            t.removeEventListener(mDownloadTaskListener);
    }

    //=========================================================================
    //
    //=========================================================================
    private void
    dataSetChanged() {
        if (null == mList || null == getListAdapter())
            return;
        //getListAdapter().clearChangeState();
        getListAdapter().notifyDataSetChanged();
    }

    @SuppressWarnings("unused")
    private void
    dataSetChanged(long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void
    itemDataChanged(long id) {
        if (null == mList || null == getListAdapter())
            return;
        getListAdapter().notifyItemDataChanged(id);
    }

    @SuppressWarnings("unused")
    private void
    refreshList(long id) {
        if (null == mList || null == getListAdapter())
            return;

        Cursor newCursor = mOpMode.query();
        getListAdapter().changeCursor(newCursor);
        getListAdapter().reloadItem(getListAdapter().findItemId(id));
    }

    private void
    refreshListAsync() {
        if (null == mList || null == getListAdapter())
            return;

        // [ NOTE ]
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = mOpMode.query();
        getListAdapter().changeCursor(newCursor);
        getListAdapter().reloadDataSetAsync();
    }

    private void
    setOnClick_startUpdate(final ImageView btn) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                ItemListActivity.this.updateItems();
                requestSetUpdateButton();
            }
        });
    }

    private void
    setOnClick_cancelUpdate(final ImageView btn) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                // Update is supported only at channel item list.
                P.bug(mOpMode instanceof OpModeChannel);
                UpdateTask t = mRtt.getUpdateTask(mOpMode.getCids()[0]);
                if (null != t)
                    mRtt.cancelTask(t, null);
                requestSetUpdateButton();
            }
        });
    }

    private void
    setOnClick_notification(final ImageView btn, final int msg) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                UxUtil.showTextToast(msg);
            }
        });
    }

    private void
    setOnClick_errResult(final ImageView btn, final Err result) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                // Update is supported only at channel item list.
                P.bug(mOpMode instanceof OpModeChannel);
                UxUtil.showTextToast(result.getMsgId());
                UpdateTask t = mRtt.getUpdateTask(mOpMode.getCids()[0]);
                if (null != t)
                    mRtt.removeWatchedTask(t);
                requestSetUpdateButton();
            }
        });
    }

    private void
    updateItems() {
        // Update is supported only at channel item list.
        P.bug(mOpMode instanceof OpModeChannel);
        long cid = mOpMode.getCids()[0];
        UpdateTask t = new UpdateTask(cid, null);
        addUpdateTaskListener(t);
        t.start();
    }

    private void
    setUpdateButton() {
        // Update is supported only at channel item list.
        if (!(mOpMode instanceof OpModeChannel))
            return;

        ActionBar bar = getActionBar();
        P.bug(null != bar);
        assert null != bar;
        if (null == bar.getCustomView())
            return; // action bar is not initialized yet.

        long cid = mOpMode.getCids()[0];
        ImageView iv = (ImageView)bar.getCustomView().findViewById(R.id.update_button);
        Animation anim = iv.getAnimation();

        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        iv.setAlpha(1.0f);
        iv.setClickable(true);
        UpdateTask t = mRtt.getUpdateTask(cid);
        switch (mRtt.getRtState(t)) {
        case IDLE:
            if (DBG) P.v("updateButton : IDLE");
            iv.setImageResource(R.drawable.ic_refresh);
            setOnClick_startUpdate(iv);
            break;

        case READY:
            if (DBG) P.v("updateButton : READY");
            iv.setImageResource(R.drawable.ic_pause);
            setOnClick_cancelUpdate(iv);
            break;

        case RUN:
            if (DBG) P.v("updateButton : RUN");
            //noinspection ResourceType
            iv.setImageResource(R.drawable.download);
            ((AnimationDrawable)iv.getDrawable()).start();
            setOnClick_cancelUpdate(iv);
            break;

        case CANCEL:
            if (DBG) P.v("updateButton : CANCEL");
            iv.setImageResource(R.drawable.ic_block);
            iv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_inout));
            iv.setClickable(false);
            setOnClick_notification(iv, R.string.wait_cancel);
            break;

        case FAIL: {
            if (DBG) P.v("updateButton : FAIL");
            iv.setImageResource(R.drawable.ic_info);
            Err result = t.getErr();
            setOnClick_errResult(iv, result);
        } break;

        default:
            P.bug(false);
        }
    }

    private void
    requestSetUpdateButton() {
        AppEnv.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                setUpdateButton();
            }
        });
    }

    private void
    onContext_deleteDnFile(final long id,
                           @SuppressWarnings("unused") final int position) {
        // Create "Enter Url" dialog
        AlertDialog dialog =
                UiHelper.createWarningDialog(this,
                                             R.string.delete_downloadded_file,
                                             R.string.delete_item_downloadded_file_msg);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                // NOTE
                // There is no use case that "null == f" here.
                if (!ContentsManager.get().deleteItemContent(id))
                    UxUtil.showTextToast(Err.IO_FILE.getMsgId());
                else {
                    getListAdapter().updateItemHasDnFile(getListAdapter().findPosition(id), false);
                    itemDataChanged(id);
                }
                dialog.dismiss();
            }
        });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getText(R.string.no),
                          new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                // Do nothing
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void
    onContext_delete(final long id,
                     @SuppressWarnings("unused") final int position) {
        AlertDialog dialog =
                UiHelper.createWarningDialog(this,
                                             R.string.delete_item,
                                             R.string.delete_item_msg);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                ContentsManager.get().deleteItemContent(id);
                mDbp.deleteItem(ColumnItem.ID, id);
                // Database is changed. So, cursor should be reloaded.
                refreshListAsync();
            }
        });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getText(R.string.no),
                          new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
            }
        });
        dialog.show();
    }

    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        if (getListAdapter().isLoadingItem(info.position))
            return;

        long dbId = getListAdapter().getItemInfo_id(info.position);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_context, menu);

        // check for "Delete downloadded File" option
        File f = ContentsManager.get().getItemInfoDataFile(dbId);
        if (null != f && f.exists())
            menu.findItem(R.id.delete_dnfile).setVisible(true);
        else
            menu.findItem(R.id.delete_dnfile).setVisible(false);
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem mItem) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)mItem.getMenuInfo();
        long dbId = getListAdapter().getItemInfo_id(info.position);
        switch (mItem.getItemId()) {
        case R.id.delete_dnfile:
            //logI(" Delete downloadded File : ID : " + dbId + " / " + info.position);
            onContext_deleteDnFile(dbId, info.position);
            return true;
        case R.id.mark_unopened:
            //logI(" Mark As Unactioned : ID : " + dbId + " / " + info.position);
            return true;

        case R.id.delete:
            onContext_delete(dbId, info.position);
            return true;
        }
        return false;
    }

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        //noinspection StatementWithEmptyBody
        switch (item.getItemId()) {
        }
        return true;
    }

    /**
     * build string array of numbers. (from <= x < to)
     * @param to exclusive
     */
    private String[]
    buildStringArray(int from, int to) {
        // get years array
        String[] ss = new String[to - from];
        for (int i = 0; i < ss.length; i++)
            ss[i] = "" + from++;
        return ss;
    }

    private void
    setSpinner(View v, int id, String[] entries, int selectpos,
               AdapterView.OnItemSelectedListener selectedListener) {
        Spinner sp = (Spinner)v.findViewById(id);
        ArrayAdapter<String> spinnerArrayAdapter
            = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entries);
        sp.setAdapter(spinnerArrayAdapter);
        sp.setSelection(selectpos);
        if (null != selectedListener)
            sp.setOnItemSelectedListener(selectedListener);
    }

    private void
    onSearchBtnClick() {
        // Empty list SHOULD not have visible-search button.
        // I can skip testing return value of mOpMode.minPubtime()
        final Calendar since = Calendar.getInstance();
        since.setTimeInMillis(mOpMode.minPubtime());
        final Calendar now = Calendar.getInstance();
        if (now.getTimeInMillis() < since.getTimeInMillis()) {
            UxUtil.showTextToast(R.string.warn_no_item_before_now);
            return;
        }

        // Setup search dialog controls
        final View diagV  =  AUtil.inflateLayout(R.layout.item_list_search);
        final AlertDialog diag =
                UiHelper.createEditTextDialog(this, diagV, R.string.feed_search);

        String[] years = buildStringArray(since.get(Calendar.YEAR), now.get(Calendar.YEAR) + 1);
        setSpinner(diagV, R.id.sp_year0, years, 0, new AdapterView.OnItemSelectedListener() {
            @Override
            public void
            onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] mons = Util.getMonths(since, now, since.get(Calendar.YEAR) + position);
                P.bug(null != mons);
                assert null != mons;
                String[] months = buildStringArray(mons[0], mons[1] + 1);
                setSpinner(diagV, R.id.sp_month0, months, 0, null); // select first (eariest) month
            }

            @Override
            public void
            onNothingSelected(AdapterView<?> parent) {
            }
        }); // select first (eariest) year

        int[] mons = Util.getMonths(since, now, since.get(Calendar.YEAR));
        P.bug(null != mons);
        assert null != mons;
        String[] months = buildStringArray(mons[0], mons[1] + 1);
        setSpinner(diagV, R.id.sp_month0, months, 0, null); // select first (eariest) month

        setSpinner(diagV, R.id.sp_year1, years, years.length - 1, new AdapterView.OnItemSelectedListener() {
            @Override
            public void
            onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] mons = Util.getMonths(since, now, since.get(Calendar.YEAR) + position);
                P.bug(null != mons);
                assert null != mons;
                String[] months = buildStringArray(mons[0], mons[1] + 1);
                setSpinner(diagV, R.id.sp_month1, months, months.length - 1, null); // select first (eariest) month
            }
            @Override
            public void
            onNothingSelected(AdapterView<?> parent) {}
        }); // select last (latest) year
        mons = Util.getMonths(since, now, now.get(Calendar.YEAR));
        P.bug(null != mons);
        assert null != mons;
        months = buildStringArray(mons[0], mons[1] + 1);
        setSpinner(diagV, R.id.sp_month1, months, months.length - 1, null); // select last (latest) month

        diag.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dia, int which) {
                int y0, y1, m0, m1;
                Spinner sp = (Spinner)diagV.findViewById(R.id.sp_year0);
                y0 = Integer.parseInt((String)sp.getSelectedItem());
                sp = (Spinner)diagV.findViewById(R.id.sp_year1);
                y1 = Integer.parseInt((String)sp.getSelectedItem());
                sp = (Spinner)diagV.findViewById(R.id.sp_month0);
                m0 = Integer.parseInt((String)sp.getSelectedItem());
                sp = (Spinner)diagV.findViewById(R.id.sp_month1);
                m1 = Integer.parseInt((String)sp.getSelectedItem());
                String search = ((EditText)diagV.findViewById(R.id.search)).getText().toString();

                Calendar from = Calendar.getInstance();
                Calendar to = Calendar.getInstance();

                // Set as min value.
                from.set(Calendar.YEAR, y0);
                //noinspection ResourceType
                from.set(Calendar.MONDAY, Util.monthToCalendarMonth(m0));
                from.set(Calendar.DAY_OF_MONTH, from.getMinimum(Calendar.DAY_OF_MONTH));
                from.set(Calendar.HOUR_OF_DAY, from.getMinimum(Calendar.HOUR_OF_DAY));
                from.set(Calendar.MINUTE, 0);
                from.set(Calendar.SECOND, 0);

                // Set as max value.
                to.set(Calendar.YEAR, y1);
                //noinspection ResourceType
                to.set(Calendar.MONDAY, Util.monthToCalendarMonth(m1));
                to.set(Calendar.DAY_OF_MONTH, to.getMaximum(Calendar.DAY_OF_MONTH));
                to.set(Calendar.HOUR_OF_DAY, to.getMaximum(Calendar.HOUR_OF_DAY));
                to.set(Calendar.MINUTE, 59);
                to.set(Calendar.SECOND, 59);

                diag.setTitle(R.string.plz_wait);
                mOpMode.setFilter(search, from.getTimeInMillis(), to.getTimeInMillis());
                mList.setSelection(0);
                getListAdapter().moveToFirstDataSet();
                AppEnv.getUiHandler().post(new Runnable() {
                   @Override
                   public void run() {
                       refreshListAsync();
                       diag.dismiss();
                   }
                });
            }
        });

        diag.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getText(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialog, int which) {
                diag.dismiss();
            }
        });

        diag.show();
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case ItemActionHandler.REQC_ITEM_VIEW:
            if (resultCode == ItemViewActivity.RESULT_DOWNLOAD) {
                long id = data.getLongExtra("id", -1);
                if (id > 0) {
                    getListAdapter().updateItemHasDnFile(getListAdapter().findPosition(id), true);
                    itemDataChanged(id);
                }
            }
            break;

        default:
            P.bug(false);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ItemListActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);
        //logI("==> ItemListActivity : onCreate");
        //noinspection ConstantConditions
        getActionBar().show();

        mItemAction = new ItemActionHandler(this, new ListAdapterBridge());

        int mode = getIntent().getIntExtra(IKEY_MODE, -1);
        P.bug(-1 != mode);
        switch (mode) {
        case MODE_CHANNEL:
            mOpMode = new OpModeChannel(getIntent());
            break;
        case MODE_CATEGORY:
            mOpMode = new OpModeCategory(getIntent());
            break;
        case MODE_FAVORITE:
            mOpMode = new OpModeFavorite(getIntent());
            break;
        case MODE_ALL:
            mOpMode = new OpModeAll(getIntent());
            break;
        default:
            P.bug(false);
        }
        setContentView(R.layout.item_list);
        mList = ((ListView)findViewById(R.id.list));
        P.bug(null != mList);
        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick (AdapterView<?> parent, View view, int position, long itemId) {
                if (getListAdapter().isLoadingItem(position))
                    return;
                long dbId = getListAdapter().getItemInfo_id(position);
                long cid = getListAdapter().getItemInfo_cid(position);
                long act = mDbp.getChannelInfoLong(cid, ColumnChannel.ACTION);
                mItemAction.onAction(act,
                                     dbId,
                                     position,
                                     getListAdapter().getItemInfo_link(position),
                                     getListAdapter().getItemInfo_encUrl(position),
                                     getListAdapter().getItemInfo_encType(position));
            }
        });
        registerForContextMenu(mList);

        (findViewById(R.id.searchbtn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                // TODO Auto-generated method stub
                onSearchBtnClick();
            }
        });

        mOpMode.onCreate();

        mList.setAdapter(new ItemListAdapter(ItemListActivity.this,
                                            mOpMode.query(),
                                            mList,
                                            DATA_REQ_SZ,
                                            DATA_ARR_MAX,
                                            mOpMode.getAdapterActionHandler()));
        mDbWatcher = new DBWatcher();
    }

    @Override
    protected void
    onStart() {
        super.onStart();
        //logI("==> ItemListActivity : onStart");
    }

    @Override
    protected void
    onResume() {
        super.onResume();
        //logI("==> ItemListActivity : onResume");
        // Register to get notification regarding RTTask.
        // See comments in 'ChannelListActivity.onResume' around 'registerManagerEventListener'
        mRtt.removeTaskQEventListener(mRtTaskQEventListenerPaused);
        mRtt.addTaskQEventListener(AppEnv.getUiHandlerAdapter(), mRtTaskQEventListener);
        View searchBtn = findViewById(R.id.searchbtn);
        if (getListAdapter().isEmpty())
            searchBtn.setVisibility(View.GONE);
        else
            searchBtn.setVisibility(View.VISIBLE);

        mOpMode.onResume();

        boolean fullRefresh = false;

        // NOTE
        // Check that whether list is needed to be fully refreshed or not.
        // How to check it!
        // If one of channel that are belongs to current item list, is changed
        //   and item table is changed, than I can decide that current viewing items are updated.
        long[] watchCids = mDbWatcher.getUpdatedChannels();
        // default is 'full-refreshing'
        boolean itemTableWatcherUpdated = mDbWatcher.isItemTableUpdated();

        mDbWatcher.unregister();
        mDbWatcher.reset();

        long[] mCids = mOpMode.getCids();

        // Simple algorithm : nested loop because # of channel is small enough in most cases.
        boolean channelChanged = false;
        for (long wcid : watchCids) {
            for (long cid : mCids)
                if (cid == wcid) {
                    channelChanged = true;
                    break;
                }
        }

        if (channelChanged && itemTableWatcherUpdated)
            fullRefresh = true;

        if (fullRefresh)
            refreshListAsync();
        else
            dataSetChanged();
    }

    @Override
    protected void
    onPause() {
        //logI("==> ItemListActivity : onPause");
        mDbWatcher.register();
        removeTaskEventListeners();
        mRtt.removeTaskQEventListener(mRtTaskQEventListener);
        mRtt.addTaskQEventListener(AppEnv.getUiHandlerAdapter(), mRtTaskQEventListenerPaused);
        super.onPause();
    }

    @Override
    protected void
    onStop() {
        //logI("==> ItemListActivity : onStop");
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        //logI("==> ItemListActivity : onDestroy");
        super.onDestroy();
        mDbWatcher.unregister();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    public void
    onBackPressed() {
        try {
            while (mOpMode.doesRunningBGTaskExists())
                Thread.sleep(50);
        } catch (InterruptedException ignored) {}
        super.onBackPressed();
    }
}
