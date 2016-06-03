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

import java.util.Calendar;
import java.util.HashSet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.MediaColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.Task;
import free.yhc.baselib.async.TmTask;
import free.yhc.baselib.async.TaskBase;
import free.yhc.baselib.async.TaskManagerBase;
import free.yhc.baselib.net.NetReadTask;
import free.yhc.abaselib.util.UxUtil;
import free.yhc.abaselib.ux.DialogTask;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.task.DownloadTask;
import free.yhc.feeder.task.UpdateTask;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.feed.Feed;
import free.yhc.feeder.core.FeederException;
import free.yhc.feeder.core.ListenerManager;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

import static free.yhc.baselib.util.Util.convertArrayLongTolong;

public class ChannelListFragment extends Fragment implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ChannelListFragment.class, Logger.LOGLV_DEFAULT);

    private static final int DATA_ARR_MAX = 100;
    private static final int DATA_REQ_SZ = 20;
    private static final int REQC_PICK_IMAGE = 0;

    private static final String KEY_CATID = "categoryid";
    private static final String KEY_PRIMARY = "primary";


    private final DBPolicy mDbp = DBPolicy.get();
    private final RTTask mRtt = RTTask.get();
    private final RtTaskQEventListener mRtTaskQEventListener = new RtTaskQEventListener();
    private final UpdateTaskListener mUpdateTaskListener = new UpdateTaskListener();
    private final DownloadTaskListener mDownloadTaskListener = new DownloadTaskListener();

    // Tasks that this fragment is registered as listeners.
    private final HashSet<UpdateTask> mUpTasks = new HashSet<>();
    private final HashSet<DownloadTask> mDnTasks = new HashSet<>();

    private DBWatcher mDbWatcher = null;
    private boolean mPrimary = false;
    private long mCatId = -1;

    private ListView mListView = null;
    // Saved cid for Async execution.
    private long mCidPickImage = -1;

    private static class DBWatcher implements ListenerManager.Listener {
        // NOTE
        // initial value should be 'true' because we don't know what happened to DB
        //   while this fragment instance DOESN'T exist!
        private boolean _mChannelTableUpdated = true;
        private final HashSet<Long> _mUpdatedChannelSet = new HashSet<>();

        void
        register() {
            DBPolicy.get().registerUpdatedListener(this,
                                                   DB.UpdateType.CHANNEL_TABLE.flag()
                                                   | DB.UpdateType.CHANNEL_DATA.flag());
        }

        void
        unregister() {
            DBPolicy.get().unregisterUpdatedListener(this);
        }

        void
        reset() {
            _mChannelTableUpdated = false;
            _mUpdatedChannelSet.clear();
        }

        long[]
        getUpdatedChannels() {
            return convertArrayLongTolong(_mUpdatedChannelSet.toArray(new Long[_mUpdatedChannelSet.size()]));
        }

        boolean
        isChannelTableUpdated() {
            return _mChannelTableUpdated;
        }

        @Override
        public void
        onNotify(Object user, ListenerManager.Type type, Object arg0, Object arg1) {
            switch ((DB.UpdateType)type) {
            case CHANNEL_TABLE:
                _mChannelTableUpdated = true;
                break;

            case CHANNEL_DATA:
                _mUpdatedChannelSet.add((Long)arg0);
                break;

            default:
                P.bug(false);
            }
        }
    }

    private class AdapterActionListener implements ChannelListAdapter.OnActionListener {
        @Override
        public void
        onUpdateClick(ImageView ibtn, long cid) {
            onContextBtn_channelUpdate(ibtn, cid);
        }

        @Override
        public void
        onMoveUpClick(ImageView ibtn, long cid) {
            ChannelListAdapter adapter = getAdapter();
            int pos = getPosition(cid);
            if (pos < 0) {
                P.bug(false);
                return;
            }
            if (0 == pos)
                return; // nothing to do


            mDbp.updatechannel_switchPosition(adapter.getItemInfo_cid(pos - 1),
                                              adapter.getItemInfo_cid(pos));
            adapter.switchPos(pos - 1, pos);
        }

        @Override
        public void
        onMoveDownClick(ImageView ibtn, long cid) {
            ChannelListAdapter adapter = getAdapter();
            int pos = getPosition(cid);
            int cnt = adapter.getCount();
            if (pos >= cnt) {
                P.bug(false);
                return;
            }
            if (cnt - 1 == pos)
                return; // nothing to do


            mDbp.updatechannel_switchPosition(adapter.getItemInfo_cid(pos),
                                              adapter.getItemInfo_cid(pos + 1));
            adapter.switchPos(pos, pos + 1);
        }
    }

    private class UpdateTaskListener extends TaskBase.EventListener<UpdateTask, Err> {
        @Override
        public void
        onCancelled(@NonNull UpdateTask task, Object param) {
            P.bug(task.getChannelId() >= 0);
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            // NOTE : refresh??? just 'notifying' is enough?
            // In current DB policy, sometimes DB may be updated even if updating is cancelled!
            refreshListItem(task.getChannelId());
        }

        @Override
        public void
        onStarted(@NonNull UpdateTask task) {
            // NOTE : refresh??? just 'notifying' is enough?
            itemDataChanged(task.getChannelId());
        }

        @Override
        public void
        onPostRun(@NonNull UpdateTask task,
                  Err result,
                  Exception ex) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            // NOTE : refresh??? just 'notifying' is enough?
            // It should be 'refresh' due to after successful update,
            //   some channel information in DB may be changed.
            refreshListItem(task.getChannelId());
        }
    }

    private class DownloadTaskListener extends TaskBase.EventListener<DownloadTask, NetReadTask.Result> {
        DownloadTaskListener() {}

        @Override
        public void
        onCancelled(@NonNull DownloadTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            if (0 == mRtt.getItemsDownloading(task.getChannelId()).length)
                itemDataChanged(task.getChannelId());
        }

        @Override
        public void
        onPostRun(@NonNull DownloadTask task,
                  NetReadTask.Result result,
                  Exception ex) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            if (0 == mRtt.getItemsDownloading(task.getChannelId()).length)
                itemDataChanged(task.getChannelId());
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
                if (RTTask.Action.UPDATE == ti.ttype) {
                    UpdateTask t = (UpdateTask)task;
                    if (t.addEventListener(AppEnv.getUiHandlerAdapter(), mUpdateTaskListener))
                        mUpTasks.add(t);
                }
                break;
            case REMOVED_FROM_READY:
            case MOVED_TO_RUN:
            case REMOVED_FROM_RUN:
            }
        }
    }

    private ChannelListActivity
    getMyActivity() {
        return (ChannelListActivity)getActivity();
    }


    private ChannelListAdapter
    getAdapter() {
        return (ChannelListAdapter)mListView.getAdapter();
    }

    /**
     * Notify that dataset for adapter is changed.
     * All list item will try to rebind their own view.
     */
    private void
    dataSetChanged() {
        if (null == mListView || null == getAdapter())
            return;
        // ((ChannelListAdapter)lv.getAdapter()).clearChangeState();
        // Delayed action(notify) may lead to unexpected exception. Why?
        // Delayed action means, action can be triggered even after this fragment is destroyed
        // In this case adapter and fragment has invalid reference values and this
        //   may lead to unexpected (ex. "try to use recycled bitmap at ImageView") excpetion.
        // So, DO NOT USE delayed action here.
        getAdapter().notifyDataSetChanged();
    }

    /**
     * Notify that dataset of given 'cid' in adapter is changed.
     * List item of only given 'cid' - one list item - will try to rebind it's view.
     */
    @SuppressWarnings("unused")
    private void
    dataSetChanged(long cid) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void
    itemDataChanged(long cid) {
        if (null == mListView || null == getAdapter())
            return;
        getAdapter().notifyItemDataChanged(cid);
    }

    private int
    getPosition(long cid) {
        ChannelListAdapter adapter = getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItemInfo_cid(i) == cid)
                return i;
        }
        return -1;
    }

    /**
     * Delete channel and it's items from DB.
     * This completely deletes all channel and items.
     */
    private void
    deleteChannel(final long cid) {
        DialogTask.Builder<DialogTask.Builder> bldr
                = new DialogTask.Builder<>(getActivity(), new Task<Void>() {
            @Override
            protected Void
            doAsync() {
                final long nr = mDbp.deleteChannel(cid);
                AppEnv.getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        UxUtil.showTextToast(nr + getResources().getString(R.string.channel_deleted_msg));
                        ChannelListFragment.this.getAdapter().removeChannel(cid);
                        dataSetChanged();
                        ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
                    }
                });
                return null;
            }
        });
        bldr.setMessage(R.string.deleting_channel_msg);
        if (!bldr.create().start())
            P.bug();
    }

    private boolean
    changeCategory(long cid, long catTo) {
        mDbp.updateChannel(cid, ColumnChannel.CATEGORYID, catTo);
        getAdapter().removeItem(getAdapter().findPosition(cid));
        getMyActivity().categoryDataSetChanged(catTo);
        dataSetChanged();
        return true;
    }

    private void
    onContextBtn_channelUpdate(@SuppressWarnings("unused") ImageView ibtn,
                               long cid) {
        UpdateTask t = mRtt.getUpdateTask(cid);
        switch (mRtt.getRtState(t)) {
        case IDLE: {
            if (DBG) P.v("ChannelUpdate: IDLE => start Update");
            UpdateTask task = new UpdateTask(cid, null);
            if (task.addEventListener(AppEnv.getUiHandlerAdapter(), mUpdateTaskListener))
                mUpTasks.add(task);
            mRtt.addTask(task, cid, RTTask.Action.UPDATE);
            itemDataChanged(cid);
        } break;

        case RUN:
        case READY:
            if (DBG) P.v("ChannelUpdate: RUN/READY => cancel");
            assert null != t;
            mRtt.cancelTask(t);
            // to change icon into "canceling"
            itemDataChanged(cid);
            break;

        case FAIL: {
            if (DBG) P.v("ChannelUpdate: FAIL => NOTI");
            assert null != t;
            Err result = t.getErr();
            UxUtil.showTextToast(result.getMsgId());
            // Ignore return value intentionally.
            mRtt.removeWatchedTask(t);
            itemDataChanged(cid);
        } break;

        case CANCEL:
            if (DBG) P.v("ChannelUpdate: CANCELLING => CANCELLING");
            UxUtil.showTextToast(R.string.wait_cancel);
            break;

        default:
            P.bug(false);
        }
    }


    private void
    onContext_deleteChannel(final long cid) {
        UxUtil.ConfirmAction action = new UxUtil.ConfirmAction() {
            @Override
            public void onPositive(@NonNull Dialog dialog) {
                deleteChannel(cid);
            }
            @Override
            public void onNegative(@NonNull Dialog dialog) { }
        };

        UiHelper.buildConfirmDialog(getActivity(),
                                    R.string.delete_channel,
                                    R.string.delete_channel_msg,
                                    action)
                .show();
    }

    private void
    onContext_deleteDownloaded(final long cid) {
        UxUtil.ConfirmAction action = new UxUtil.ConfirmAction() {
            @Override
            public void onPositive(@NonNull Dialog dialog) {
                // delete entire channel directory and re-make it.
                // Why?
                // All and only downloadded files are located in channel directory.
                ContentsManager.get().cleanChannelDir(cid);
            }
            @Override
            public void onNegative(@NonNull Dialog dialog) { }
        };
        UiHelper.buildConfirmDialog(getActivity(),
                                    R.string.delete_downloadded_file,
                                    R.string.delete_channel_downloadded_file_msg,
                                    action)
                .show();
    }

    private void
    onContext_deleteUsedDownloaded(final long cid) {
        AlertDialog diag = UiHelper.buildDeleteUsedDnFilesConfirmDialog(cid, this.getActivity(), null, null);
        if (null == diag)
            UxUtil.showTextToast(R.string.del_dnfiles_not_allowed_msg);
        else
            diag.show();
    }

    private void
    onContext_changeCategory(final long cid) {
        UiHelper.OnCategorySelectedListener action = new UiHelper.OnCategorySelectedListener() {
            @Override
            public void
            onSelected(long category, Object user) {
                changeCategory(cid, category);
            }
        };

        UiHelper.selectCategoryDialog(getActivity(),
                                      R.string.select_category,
                                      action,
                                      mCatId,
                                      null)
                .show();
    }

    private void
    onContext_setting(final long cid) {
        Intent intent = new Intent(getActivity(), ChannelSettingActivity.class);
        intent.putExtra("cid", cid);
        startActivity(intent);
    }

    private void
    onContext_pickIcon(final long cid) {
        Intent i = new Intent(Intent.ACTION_PICK,
                              android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        mCidPickImage = cid;
        try {
            startActivityForResult(Intent.createChooser(i,
                                                        getResources().getText(R.string.pick_icon)),
                                   REQC_PICK_IMAGE);
        } catch (ActivityNotFoundException e) {
            UxUtil.showTextToast(R.string.warn_find_gallery_app);
        }
    }

    private void
    refreshListItem(long[] cids) {
        ChannelListAdapter adapter = getAdapter();
        Cursor newCursor = ChannelListAdapter.getQueryCursor(mCatId);
        adapter.changeCursor(newCursor);
        int[] ids = new int[cids.length];
        for (int i = 0; i < ids.length; i++)
            ids[i] = adapter.findItemId(cids[i]);
        adapter.reloadItem(ids);
        dataSetChanged();
    }

    private void
    refreshListItem(long cid) {
        refreshListItem(new long[] { cid });
    }

    /**
     * Cursor is changed and all view should be rebinded.
     */
    public void
    refreshListAsync() {
        Cursor newCursor = ChannelListAdapter.getQueryCursor(mCatId);
        getAdapter().changeCursor(newCursor);
        getAdapter().reloadDataSetAsync();
    }

    public void
    addChannel(String url, String iconurl) {
        P.bug(url != null);
        url = Util.removeTrailingSlash(url);

        long cid;
        try {
            cid = mDbp.insertNewChannel(getCategoryId(), url);
        } catch (FeederException e) {
            UxUtil.showTextToast(e.getError().getMsgId());
            return;
        }
        /* Cursor of adapter is changed.
         * Not to update whole cursor, append channel data manually to the adapter.
         */
        getAdapter().appendChannel(cid);
        /* channel information should be updated to adapter before starting task
         * because, update task request to adapter to update task 'state'.
         */
        dataSetChanged();

        // full update for this newly inserted channel
        UpdateTask task;
        if (!Util.isValidValue(iconurl))
            iconurl = null;
        task = new UpdateTask(cid, iconurl);
        if (task.addEventListener(AppEnv.getUiHandlerAdapter(), mUpdateTaskListener))
            mUpTasks.add(task);
        mRtt.addTask(task, cid, RTTask.Action.UPDATE);

        ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
    }

    public void
    setToPrimary(boolean primary) {
        mPrimary = primary;
        if (null != getActivity()
            && null != mListView) {
            if (primary)
                getActivity().registerForContextMenu(mListView);
            else
                getActivity().unregisterForContextMenu(mListView);
        }
    }

    public void
    onResult_pickImage(int resultCode, Intent data) {
        if (Activity.RESULT_OK != resultCode)
            return;

        // this may takes quite long time (if image size is big!).
        // So, let's do it in background.
        final Uri uri = data.getData();
        final long cid = mCidPickImage;
        DialogTask.Builder<DialogTask.Builder> bldr
                = new DialogTask.Builder<>(getActivity(), new Task<Void>() {
            private Err
            doAsync_() {
                String[] filePathColumn = {MediaColumns.DATA};

                Cursor c = getActivity().getContentResolver().query(
                        uri, filePathColumn, null, null, null);
                if (null == c)
                    return Err.GET_MEDIA;

                if (!c.moveToFirst()) {
                    c.close();
                    return Err.GET_MEDIA;
                }

                int columnIndex = c.getColumnIndex(filePathColumn[0]);
                String filePath = c.getString(columnIndex);
                c.close();

                // Make url string from file path
                Bitmap bm = Util.decodeImage(filePath, Feed.Channel.ICON_MAX_WIDTH, Feed.Channel.ICON_MAX_HEIGHT);
                if (null == bm)
                    return Err.CODEC_DECODE;
                byte[] imageData = Util.compressBitmap(bm);
                if (mCidPickImage < 0) {
                    P.bug(false);
                    return Err.UNKNOWN; // something evil!!!
                } else {
                    mDbp.updateChannel(mCidPickImage, ColumnChannel.IMAGEBLOB, imageData);
                    mCidPickImage = -1;
                }
                return Err.NO_ERR;
            }

            @Override
            protected Void
            doAsync() {
                final Err err = doAsync_();
                AppEnv.getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (Err.NO_ERR == err)
                            getAdapter().notifyChannelIconChanged(cid);
                        else
                            UxUtil.showTextToast(err.getMsgId());
                    }
                });
                return null;
            }
        });

        bldr.setMessage(R.string.pick_icon_progress);
        if (!bldr.create().start())
            P.bug();
    }

    public long
    getCategoryId() {
        return mCatId;
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem mItem) {
        if (!mPrimary
            || !getMyActivity().isContextMenuOwner(this))
            return false;

        AdapterContextMenuInfo info = (AdapterContextMenuInfo)mItem.getMenuInfo();
        long dbId = getAdapter().getItemInfo_cid(info.position);
        switch (mItem.getItemId()) {
        case R.id.delete:
            onContext_deleteChannel(dbId);
            return true;

        case R.id.delete_dnfile:
            onContext_deleteDownloaded(dbId);
            return true;

        case R.id.delete_used_dnfile:
            onContext_deleteUsedDownloaded(dbId);
            return true;

        case R.id.change_category:
            onContext_changeCategory(dbId);
            return true;

        case R.id.setting:
            onContext_setting(dbId);
            return true;

        case R.id.pick_icon:
            onContext_pickIcon(dbId);
            return true;
        }
        return false;
    }

    public void
    onCreateContextMenu2(ContextMenu menu,
                         @SuppressWarnings("unused") View v,
                         ContextMenuInfo menuInfo) {
        P.bug(mPrimary);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.channel_context, menu);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        if (getAdapter().isLoadingItem(info.position))
            return;

        long dbId = getAdapter().getItemInfo_cid(info.position);
        UpdateTask t = mRtt.getUpdateTask(dbId);
        switch (mRtt.getRtState(t)) {
        case READY:
        case RUN:
        case CANCEL:
            menu.findItem(R.id.delete).setEnabled(false);
            menu.findItem(R.id.pick_icon).setEnabled(false);
            /* full update is useless at this moment. Codes are left for history tracking
            menu.findItem(R.id.full_update).setEnabled(false);
            */
        }

        if (mRtt.getItemsDownloading(dbId).length > 0) {
            menu.findItem(R.id.delete).setEnabled(false);
            menu.findItem(R.id.delete_dnfile).setEnabled(false);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ChannelListFragment ]";
    }

    /*
    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
    }
    */

    @Override
    public void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case REQC_PICK_IMAGE:
            onResult_pickImage(resultCode, data);
            break;
        }
    }

    public static ChannelListFragment
    newInstance(long catId) {
        ChannelListFragment f = new ChannelListFragment();
        f.initialize(catId);
        return f;
    }

    public void
    initialize(long catId) {
        mCatId = catId;
    }

    @Override
    public void
    onSaveInstanceState(Bundle outState) {
        outState.putLong(KEY_CATID, mCatId);
        outState.putBoolean(KEY_PRIMARY, mPrimary);
    }

    private void
    restoreInstanceState(Bundle data) {
        if (null == data)
            return;

        mCatId = data.getLong(KEY_CATID, mCatId);
        mPrimary = data.getBoolean(KEY_PRIMARY, mPrimary);
    }

    @Override
    public void
    onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void
    onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);
        restoreInstanceState(savedInstanceState);
        mDbWatcher = new DBWatcher();
    }

    @Override
    public View
    onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        @SuppressLint("InflateParams")
        LinearLayout ll = (LinearLayout)inflater.inflate(R.layout.channel_listview, null);
        mListView = (ListView)ll.findViewById(R.id.list);
        P.bug(null != mListView);
        mListView.setAdapter(new ChannelListAdapter(getActivity(),
                                                    ChannelListAdapter.getQueryCursor(mCatId),
                                                    mListView,
                                                    DATA_REQ_SZ,
                                                    DATA_ARR_MAX,
                                                    new AdapterActionListener()));
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long itemId) {
                if (getAdapter().isLoadingItem(position))
                    return;

                Intent intent = new Intent(getActivity(), ItemListActivity.class);
                intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_CHANNEL);
                intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
                intent.putExtra("cid", ((ChannelListAdapter)parent.getAdapter()).getItemInfo_cid(position));
                startActivity(intent);
            }
        });
        ScrollView sv = (ScrollView)ll.findViewById(R.id.empty_list);
        mListView.setEmptyView(sv);
        setToPrimary(mPrimary);
        return ll;
    }

    @Override
    public void
    onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void
    onStart() {
        super.onStart();
        if (DBG) P.v("Enter");
    }

    @Override
    public void
    onResume() {
        super.onResume();
        mRtt.addTaskQEventListener(AppEnv.getUiHandlerAdapter(), mRtTaskQEventListener);
        HashSet<Long> updatedCids = new HashSet<>();
        for (long cid : mDbWatcher.getUpdatedChannels())
            updatedCids.add(cid);

        HashSet<Long> myUpdatedCids = new HashSet<>();
        // Check channel state and bind it.
        // Why here? Not 'onStart'.
        // See comments in 'onPause()'
        try {
            mDbp.getDelayedChannelUpdate();
            Cursor c = mDbp.queryChannel(mCatId, ColumnChannel.ID);
            if (c.moveToFirst()) {
                do {
                    long cid = c.getLong(0);
                    if (updatedCids.contains(cid))
                        myUpdatedCids.add(cid);

                    UpdateTask upt = mRtt.getUpdateTask(cid);
                    if (RTTask.RtState.IDLE != mRtt.getRtState(upt)) {
                        assert null != upt;
                        //noinspection unchecked
                        if (upt.addEventListener(AppEnv.getUiHandlerAdapter(), mUpdateTaskListener))
                            mUpTasks.add(upt);
                    }
                    long[] ids = mRtt.getItemsDownloading(cid);
                    for (long id : ids) {
                        DownloadTask dnt = mRtt.getDownloadTask(id);
                        if (RTTask.RtState.IDLE != mRtt.getRtState(dnt)) {
                            assert null != dnt;
                            //noinspection unchecked
                            if (dnt.addEventListener(AppEnv.getUiHandlerAdapter(), mDownloadTaskListener))
                                mDnTasks.add(dnt);
                        }
                    }
                } while (c.moveToNext());
            }
            c.close();
        } finally {
            mDbp.putDelayedChannelUpdate();
        }

        // NOTE
        // Channel may be added or deleted.
        // And channel operation is only allowed on current selected list
        //   according to use case.
        if (mDbWatcher.isChannelTableUpdated()
            && myUpdatedCids.size() > 0) {
            refreshListAsync();
        } else
            refreshListItem(convertArrayLongTolong(myUpdatedCids.toArray(new Long[myUpdatedCids.size()])));

        // We don't need to worry about item table change.
        // Because, if item is newly inserted, that means some of channel is updated.
        // And that channel will be updated according to DB changes.

        mDbWatcher.unregister();
        mDbWatcher.reset();
    }

    @Override
    public void
    onPause() {
        //logI("==> ChannelListActivity : onPause");
        mDbWatcher.register();
        mRtt.removeTaskQEventListener(mRtTaskQEventListener);
        // Why This should be here (NOT 'onStop'!)
        // In normal case, starting 'ItemListAcvitiy' issues 'onStop'.
        // And when exiting from 'ItemListActivity' by back-key event, 'onStart' is called.
        // But, during updating - there is background thread  - 'onResume' and 'onCancel' are called
        //   instead of 'onStart' and 'onStop'.
        // That is, if there is background running background thread, activity is NOT stopped but just paused.
        // (This is experimental conclusion - NOT by analyzing framework source code.)
        // I think this is Android's bug or implicit policy.
        // Because of above issue, 'adding' and 'removing' are done at 'onResume' and 'onPause'.
        for (UpdateTask t : mUpTasks)
            t.removeEventListener(mUpdateTaskListener);
        for (DownloadTask t : mDnTasks)
            t.removeEventListener(mDownloadTaskListener);

        super.onPause();
    }

    @Override
    public void
    onStop() {
        if (DBG) P.v("Enter");
        super.onStop();
    }
    @Override
    public void
    onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void
    onDestroy() {
        super.onDestroy();
        mDbWatcher.unregister();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }

    @Override
    public void
    onDetach() {
        super.onDetach();
    }
}
