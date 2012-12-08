/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.Calendar;
import java.util.HashSet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import free.yhc.feeder.LookAndFeel.ConfirmDialogAction;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.BaseBGTask;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ChannelListFragment extends Fragment implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ChannelListFragment.class);

    private static final int DATA_ARR_MAX       = 100;
    private static final int DATA_REQ_SZ        = 20;
    private static final int REQC_PICK_IMAGE    = 0;

    private static final String KEY_CATID       = "categoryid";
    private static final String KEY_PRIMARY     = "primary";


    private final LookAndFeel   mLnf = LookAndFeel.get();
    private final DBPolicy      mDbp = DBPolicy.get();
    private final RTTask        mRtt = RTTask.get();
    private final UIPolicy      mUip = UIPolicy.get();

    private boolean mPrimary = false;
    private long    mCatId  =   -1;

    private ListView    mListView = null;
    // Saved cid for Async execution.
    private long        mCidPickImage = -1;

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
            dataSetChanged(_mCid);
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            eAssert(Err.USER_CANCELLED != result);
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            // In normal case, onPostExecute is not called in case of 'user-cancel'.
            // below code is for safety.
            if (Err.USER_CANCELLED == result)
                return; // onPostExecute SHOULD NOT be called in case of user-cancel

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
                dataSetChanged(_mCid);
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (getActivity().isFinishing())
                return;

            if (0 == mRtt.getItemsDownloading(_mCid).length)
                dataSetChanged(_mCid);
        }
    }


    private class DeleteChannelWorker extends DiagAsyncTask.Worker {
        private final long[]    _mCids;
        private long            _mNrDelItems    = -1;

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
            mLnf.showTextToast(getActivity(),
                               _mNrDelItems + getResources().getString(R.string.channel_deleted_msg));
            refreshListAsync();
            ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
        }
    }

    private class PickIconWorker extends DiagAsyncTask.Worker {
        private long        _mCid = -1;
        private Bitmap      _mBm = null;
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
            _mBm = Utils.decodeImage(filePath, Feed.Channel.ICON_MAX_WIDTH, Feed.Channel.ICON_MAX_HEIGHT);
            byte[] imageData = Utils.compressBitmap(_mBm);

            if (null == imageData)
                return Err.CODEC_DECODE;

            if (mCidPickImage < 0) {
                eAssert(false);
                return Err.UNKNOWN; // something evil!!!
            } else {
                mDbp.updateChannel(mCidPickImage, DB.ColumnChannel.IMAGEBLOB, imageData);
                mCidPickImage = -1;
            }
            return Err.NO_ERR;
        }

        @Override
        public void
        onPostExecute(DiagAsyncTask task, Err result) {
            if (Err.NO_ERR == result)
                getAdapter().setChannelIcon(_mCid, _mBm);
            else
                mLnf.showTextToast(getActivity(), result.getMsgId());
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
        public void onUnregister(BGTask task, long cid, RTTask.Action act) { }
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
     * @param lv
     */
    private void
    dataSetChanged() {
        // ((ChannelListAdapter)lv.getAdapter()).clearChangeState();
        getAdapter().notifyDataSetChanged();
    }

    /**
     * Notify that dataset of given 'cid' in adapter is changed.
     * List item of only given 'cid' - one list item - will try to rebind it's view.
     * @param lv ListView
     * @param cid
     */
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
        getAdapter().notifyDataSetChanged();
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
     * @param tab
     * @param cid
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
        mDbp.updateChannel(cid, DB.ColumnChannel.CATEGORYID, catTo);
        getAdapter().removeItem(getAdapter().findPosition(cid));
        getMyActivity().categoryDataSetChanged(catTo);
        dataSetChanged();
        return true;
    }

    private void
    onContextBtn_channelUpdate(ImageView ibtn, long cid) {
        RTTask.TaskState state = mRtt.getState(cid, RTTask.Action.UPDATE);
        switch (state) {
        case IDLE: {
            //if (DBG) P.v("update : " + cid);
            BGTaskUpdateChannel task = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid));
            mRtt.register(cid, RTTask.Action.UPDATE, task);
            mRtt.start(cid, RTTask.Action.UPDATE);
            dataSetChanged(cid);
        } break;

        case RUNNING:
        case READY:
            //if (DBG) P.v("cancel : " + cid);
            mRtt.cancel(cid, RTTask.Action.UPDATE, null);
            // to change icon into "canceling"
            dataSetChanged(cid);
            break;

        case FAILED: {
            Err result = mRtt.getErr(cid, RTTask.Action.UPDATE);
            mLnf.showTextToast(getActivity(), result.getMsgId());
            mRtt.consumeResult(cid, RTTask.Action.UPDATE);
            dataSetChanged(cid);
        } break;

        case CANCELING:
            mLnf.showTextToast(getActivity(), R.string.wait_cancel);
            break;

        default:
            eAssert(false);
        }
    }


    private void
    onContext_deleteChannel(final long cid) {
        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                deleteChannel(cid);
            }
        };

        mLnf.buildConfirmDialog(getActivity(),
                                R.string.delete_channel,
                                R.string.delete_channel_msg,
                                action)
            .show();
    }

    private void
    onContext_deleteDownloaded(final long cid) {
        // delete entire channel directory and re-make it.
        // Why?
        // All and only downloaded files are located in channel directory.
        mUip.cleanChannelDir(cid);
    }

    private void
    onContext_changeCategory(final long cid) {
        // Create Adapter for list and set it.
        Feed.Category[] cats = mDbp.getCategories();
        final String[] menus = new String[cats.length - 1];
        final long[] catIds = new long[cats.length - 1];
        int i = 0;
        for (Feed.Category cat : cats) {
            if (cat.id != mCatId) {
                if (!Utils.isValidValue(cat.name)
                    && mDbp.isDefaultCategoryId(cat.id))
                    menus[i] = getActivity().getResources().getText(R.string.default_category_name).toString();
                else
                    menus[i] = cat.name;

                catIds[i++] = cat.id;
            }
        }

        AlertDialog.Builder bldr = new AlertDialog.Builder(getActivity());
        ArrayAdapter<String> adapter
            = new ArrayAdapter<String>(getActivity(), android.R.layout.select_dialog_item, menus);
        bldr.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialog, int which) {
                changeCategory(cid, catIds[which]);
            }
        });

        bldr.setTitle(R.string.select_category);
        bldr.create().show();
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
            mLnf.showTextToast(getActivity(), R.string.warn_find_gallery_app);
            return;
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
        adapter.notifyDataSetChanged();
    }

    private void
    refreshListItem(long cid) {
        refreshListItem(new long[] { cid });
    }

    /**
     * Cursor is changed and all view should be rebinded.
     * @param tab
     */
    public void
    refreshListAsync() {
        Cursor newCursor = ChannelListAdapter.getQueryCursor(mCatId);
        getAdapter().changeCursor(newCursor);
        getAdapter().reloadDataSetAsync();
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
    onCreateContextMenu2(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
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
    onAttach(Activity activity) {
        super.onAttach(activity);
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
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreInstanceState(savedInstanceState);
    }

    @Override
    public View
    onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
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
        if (DBG) P.v("Fragment : onStart");
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

        HashSet<Long> updatedCids = new HashSet<Long>();
        if (mDbp.isChannelWatcherRegistered(this)) {
            for (long cid : mDbp.getChannelWatcherUpdated(this))
                updatedCids.add(cid);
        }

        HashSet<Long> myUpdatedCids = new HashSet<Long>();
        // Check channel state and bind it.
        // Why here? Not 'onStart'.
        // See comments in 'onPause()'
        try {
            mDbp.getDelayedChannelUpdate();
            Cursor c = mDbp.queryChannel(mCatId, DB.ColumnChannel.ID);
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
        if (mDbp.isChannelTableWatcherRegistered(this)
            && mDbp.isChannelTableWatcherUpdated(this)
            && myUpdatedCids.size() > 0) {
            refreshListAsync();
        } else
            refreshListItem(Utils.convertArrayLongTolong(myUpdatedCids.toArray(new Long[0])));

        // We don't need to worry about item table change.
        // Because, if item is newly inserted, that means some of channel is updated.
        // And that channel will be updated according to DB changes.

        mDbp.unregisterChannelWatcher(this);
        mDbp.unregisterChannelTableWatcher(this);
    }

    @Override
    public void
    onPause() {
        //logI("==> ChannelListActivity : onPause");
        mDbp.registerChannelWatcher(this);
        mDbp.registerChannelTableWatcher(this);
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
        if (DBG) P.v("Fragment : onStop");
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
    }

    @Override
    public void
    onDetach() {
        super.onDetach();
    }
}
