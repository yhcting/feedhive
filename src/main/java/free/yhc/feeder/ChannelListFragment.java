/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

import static free.yhc.feeder.core.Utils.eAssert;

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
import free.yhc.feeder.UiHelper.OnConfirmDialogAction;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.BGTask;
import free.yhc.feeder.core.BGTaskUpdateChannel;
import free.yhc.feeder.core.BaseBGTask;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.Feed;
import free.yhc.feeder.core.FeederException;
import free.yhc.feeder.core.ListenerManager;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Utils;

public class ChannelListFragment extends Fragment implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ChannelListFragment.class);

    private static final int DATA_ARR_MAX = 100;
    private static final int DATA_REQ_SZ = 20;
    private static final int REQC_PICK_IMAGE = 0;

    private static final String KEY_CATID = "categoryid";
    private static final String KEY_PRIMARY = "primary";


    private final DBPolicy mDbp = DBPolicy.get();
    private final RTTask mRtt = RTTask.get();

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
            return Utils.convertArrayLongTolong(_mUpdatedChannelSet.toArray(new Long[_mUpdatedChannelSet.size()]));
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
                eAssert(false);
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
                eAssert(false);
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
                eAssert(false);
                return;
            }
            if (cnt - 1 == pos)
                return; // nothing to do


            mDbp.updatechannel_switchPosition(adapter.getItemInfo_cid(pos),
                                              adapter.getItemInfo_cid(pos + 1));
            adapter.switchPos(pos, pos + 1);
        }
    }

    private class UpdateBGTaskListener extends BaseBGTask.OnEventListener {
        private long    _mCid = -1;

        UpdateBGTaskListener(long cid) {
            _mCid = cid;
        }

        @Override
        public void
        onCancelled(BaseBGTask task, Object param) {
            eAssert(_mCid >= 0);
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            // NOTE : refresh??? just 'notifying' is enough?
            // In current DB policy, sometimes DB may be updated even if updating is cancelled!
            refreshListItem(_mCid);
        }

        @Override
        public void
        onPreRun(BaseBGTask task) {
            // NOTE : refresh??? just 'notifying' is enough?
            channelRttStateChanged(_mCid);
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            // NOTE : refresh??? just 'notifying' is enough?
            // It should be 'refresh' due to after successful update,
            //   some channel information in DB may be changed.
            refreshListItem(_mCid);
        }
    }

    private class DownloadBGTaskListener extends BaseBGTask.OnEventListener {
        private long    _mCid = -1;
        DownloadBGTaskListener(long cid) {
            _mCid = cid;
        }

        @Override
        public void
        onCancelled(BaseBGTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            if (0 == mRtt.getItemsDownloading(_mCid).length)
                channelRttStateChanged(_mCid);
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            if (0 == mRtt.getItemsDownloading(_mCid).length)
                channelRttStateChanged(_mCid);
        }
    }


    private class DeleteChannelWorker extends DiagAsyncTask.Worker {
        private final long[]    _mCids;
        private long            _mNrDelItems    = 0;

        DeleteChannelWorker(long[] cids) {
            _mCids = cids;
        }

        @Override
        public Err
        doBackgroundWork(DiagAsyncTask task) {
            _mNrDelItems = mDbp.deleteChannel(_mCids);
            return Err.NO_ERR;
        }

        @Override
        public void
        onPostExecute(DiagAsyncTask task, Err result) {
            UiHelper.showTextToast(getActivity(),
                                   _mNrDelItems + getResources().getString(R.string.channel_deleted_msg));
            for (long cid : _mCids)
                ChannelListFragment.this.getAdapter().removeChannel(cid);
            dataSetChanged();
            ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
        }
    }

    private class PickIconWorker extends DiagAsyncTask.Worker {
        private long        _mCid = -1;
        private final Uri   _mImageUri;

        PickIconWorker(Uri uri) {
            _mImageUri = uri;
        }

        @Override
        public Err
        doBackgroundWork(DiagAsyncTask task) {
            String[] filePathColumn = {MediaColumns.DATA};

            _mCid = mCidPickImage;

            Cursor c = getActivity().getContentResolver().query(_mImageUri, filePathColumn, null, null, null);
            if (!c.moveToFirst()) {
                c.close();
                return Err.GET_MEDIA;
            }

            int columnIndex = c.getColumnIndex(filePathColumn[0]);
            String filePath = c.getString(columnIndex);
            c.close();

            // Make url string from file path
            Bitmap bm = Utils.decodeImage(filePath, Feed.Channel.ICON_MAX_WIDTH, Feed.Channel.ICON_MAX_HEIGHT);
            byte[] imageData = Utils.compressBitmap(bm);

            if (null == imageData)
                return Err.CODEC_DECODE;

            if (mCidPickImage < 0) {
                eAssert(false);
                return Err.UNKNOWN; // something evil!!!
            } else {
                mDbp.updateChannel(mCidPickImage, ColumnChannel.IMAGEBLOB, imageData);
                mCidPickImage = -1;
            }
            return Err.NO_ERR;
        }

        @Override
        public void
        onPostExecute(DiagAsyncTask task, Err result) {
            if (Err.NO_ERR == result)
                getAdapter().notifyChannelIconChanged(_mCid);
            else
                UiHelper.showTextToast(getActivity(), result.getMsgId());
        }
    }

    private class RTTaskRegisterListener implements RTTask.OnRegisterListener {
        @Override
        public void
        onRegister(BGTask task, long cid, RTTask.Action act) {
            if (RTTask.Action.UPDATE == act)
                mRtt.bind(cid, RTTask.Action.UPDATE, ChannelListFragment.this, new UpdateBGTaskListener(cid));
        }
        @Override
        public void
        onUnregister(BGTask task, long cid, RTTask.Action act) { }
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
        /*
        ChannelListAdapter cla = (ChannelListAdapter)lv.getAdapter();
        ((ChannelListAdapter)lv.getAdapter()).clearChangeState();
        for (int i = lv.getFirstVisiblePosition();
             i <= lv.getLastVisiblePosition();
             i++) {
            long itemCid = cla.getChannelId(i);
            if (itemCid == cid)
                cla.addChanged(cla.getItemId(i));
            else
                cla.addUnchanged(cla.getItemId(i));
        }
        */
        dataSetChanged();
    }

    private void
    channelRttStateChanged(long cid) {
        getAdapter().notifyChannelRttStateChanged(cid);
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
    deleteChannel(long cid) {
        DiagAsyncTask task = new DiagAsyncTask(getActivity(),
                                               new DeleteChannelWorker(new long[] { cid }),
                                               DiagAsyncTask.Style.SPIN,
                                               R.string.deleting_channel_msg);
        task.run();
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
        RTTask.TaskState state = mRtt.getState(cid, RTTask.Action.UPDATE);
        switch (state) {
        case IDLE: {
            BGTaskUpdateChannel task = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid));
            mRtt.register(cid, RTTask.Action.UPDATE, task);
            mRtt.start(cid, RTTask.Action.UPDATE);
            channelRttStateChanged(cid);
        } break;

        case RUNNING:
        case READY:
            mRtt.cancel(cid, RTTask.Action.UPDATE, null);
            // to change icon into "canceling"
            channelRttStateChanged(cid);
            break;

        case FAILED: {
            Err result = mRtt.getErr(cid, RTTask.Action.UPDATE);
            UiHelper.showTextToast(getActivity(), result.getMsgId());
            mRtt.consumeResult(cid, RTTask.Action.UPDATE);
            channelRttStateChanged(cid);
        } break;

        case CANCELING:
            UiHelper.showTextToast(getActivity(), R.string.wait_cancel);
            break;

        default:
            eAssert(false);
        }
    }


    private void
    onContext_deleteChannel(final long cid) {
        OnConfirmDialogAction action = new OnConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                deleteChannel(cid);
            }
            @Override
            public void onCancel(Dialog dialog) { }
        };

        UiHelper.buildConfirmDialog(getActivity(),
                                    R.string.delete_channel,
                                    R.string.delete_channel_msg,
                                    action)
                .show();
    }

    private void
    onContext_deleteDownloaded(final long cid) {
        OnConfirmDialogAction action = new OnConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                // delete entire channel directory and re-make it.
                // Why?
                // All and only downloadded files are located in channel directory.
                ContentsManager.get().cleanChannelDir(cid);
            }
            @Override
            public void onCancel(Dialog dialog) { }
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
            UiHelper.showTextToast(this.getActivity(), R.string.del_dnfiles_not_allowed_msg);
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
            UiHelper.showTextToast(getActivity(), R.string.warn_find_gallery_app);
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
        eAssert(url != null);
        url = Utils.removeTrailingSlash(url);

        long cid;
        try {
            cid = mDbp.insertNewChannel(getCategoryId(), url);
        } catch (FeederException e) {
            UiHelper.showTextToast(getActivity(), e.getError().getMsgId());
            return;
        }

        // full update for this newly inserted channel
        BGTaskUpdateChannel task;
        if (Utils.isValidValue(iconurl))
            task = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid, iconurl));
        else
            task = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid));

        mRtt.register(cid, RTTask.Action.UPDATE, task);
        mRtt.start(cid, RTTask.Action.UPDATE);

        getAdapter().appendChannel(cid);
        dataSetChanged();
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
        new DiagAsyncTask(getActivity(),
                          new PickIconWorker(data.getData()),
                          DiagAsyncTask.Style.SPIN,
                          R.string.pick_icon_progress)
            .run();
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
        eAssert(mPrimary);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.channel_context, menu);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        if (getAdapter().isLoadingItem(info.position))
            return;

        long dbId = getAdapter().getItemInfo_cid(info.position);
        RTTask.TaskState updateState = mRtt.getState(dbId, RTTask.Action.UPDATE);

        if (RTTask.TaskState.RUNNING == updateState
            || RTTask.TaskState.READY == updateState
            || RTTask.TaskState.CANCELING == updateState) {
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
        eAssert(null != mListView);
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
        //logI("==> ChannelListActivity : onResume");
        // NOTE
        // Case to think about
        // - new update task is registered between 'registerManagerEventListener' and 'getUpdateState'
        // - then, this task will be binded twice.
        // => This leads to over head operation (ex. refreshing list two times continuously etc.)
        //    But, this doesn't issue logical error. So, I can get along with this case.
        //
        // If 'registerManagerEventListener' is below 'getUpdateState',
        //   we may miss binding some updating task!
        mRtt.registerRegisterEventListener(this, new RTTaskRegisterListener());

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

                    if (RTTask.TaskState.IDLE != mRtt.getState(cid, RTTask.Action.UPDATE))
                        mRtt.bind(cid, RTTask.Action.UPDATE, this, new UpdateBGTaskListener(cid));
                    long[] ids = mRtt.getItemsDownloading(cid);
                    for (long id : ids)
                        mRtt.bind(id, RTTask.Action.DOWNLOAD, this, new DownloadBGTaskListener(cid));
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
            refreshListItem(Utils.convertArrayLongTolong(myUpdatedCids.toArray(new Long[myUpdatedCids.size()])));

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
        mRtt.unregisterRegisterEventListener(this);
        // Why This should be here (NOT 'onStop'!)
        // In normal case, starting 'ItemListAcvitiy' issues 'onStop'.
        // And when exiting from 'ItemListActivity' by back-key event, 'onStart' is called.
        // But, during updating - there is background thread  - 'onResume' and 'onCancel' are called
        //   instead of 'onStart' and 'onStop'.
        // That is, if there is background running background thread, activity is NOT stopped but just paused.
        // (This is experimental conclusion - NOT by analyzing framework source code.)
        // I think this is Android's bug or implicit policy.
        // Because of above issue, 'binding' and 'unbinding' are done at 'onResume' and 'onPause'.
        mRtt.unbind(this);


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
