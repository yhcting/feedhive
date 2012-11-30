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
import static free.yhc.feeder.model.Utils.logW;

import java.util.Calendar;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.MediaColumns;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import free.yhc.feeder.LookAndFeel.ConfirmDialogAction;
import free.yhc.feeder.LookAndFeel.EditTextDialogAction;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.BaseBGTask;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.FeederException;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.UsageReport;
import free.yhc.feeder.model.Utils;

public class ChannelListActivity extends Activity implements
ActionBar.TabListener,
UnexpectedExceptionHandler.TrackedModule {
    // Request codes.
    private static final int REQC_PICK_IMAGE               = 0;
    private static final int REQC_PICK_PREDEFINED_CHANNEL  = 1;

    private static final int DATA_REQ_SZ  = 20;
    private static final int DATA_ARR_MAX = 200;

    private static final int CHANNEL_REFRESH_THRESHOLD = 2;

    private final UIPolicy      mUip = UIPolicy.get();
    private final DBPolicy      mDbp = DBPolicy.get();
    private final RTTask        mRtt = RTTask.get();
    private final UsageReport   mUr  = UsageReport.get();
    private final LookAndFeel   mLnf = LookAndFeel.get();

    private ActionBar   mAb       = null;
    private Flipper     mFlipper  = null;

    // Saved cid for Async execution.
    private long        mCidPickImage = -1;

    private static class FlipperScrollView extends ScrollView {
        private View.OnTouchListener _mTouchInterceptor = null;

        public
        FlipperScrollView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        void
        setTouchInterceptor(View.OnTouchListener interceptor) {
            _mTouchInterceptor = interceptor;
        }

        @Override
        public boolean
        onTouchEvent(MotionEvent ev) {
            boolean ret = super.onTouchEvent(ev);
            if (null != _mTouchInterceptor)
                _mTouchInterceptor.onTouch(this, ev);
            return ret;
        }

        @Override
        public boolean
        onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }

    private class Flipper {
        private Context     _mContext;
        private ViewFlipper _mViewFlipper;
        private Animation   _mSlideLeftIn;
        private Animation   _mSlideLeftOut;
        private Animation   _mSlideRightIn;
        private Animation   _mSlideRightOut;
        private GestureDetector _mGestureDetector;

        private class SwipeGestureDetector extends SimpleOnGestureListener {
            // For swipe animation
            private static final int SWIPE_MIN_DISTANCE = 100;
            private static final int SWIPE_THRESHOLD_VELOCITY = 150;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {
                    // Distance along x-axis SHOULD be larger than two-times of y distance
                    if (2 * Math.abs(e1.getY() - e2.getY()) > Math.abs(e1.getX() - e2.getX()))
                        return false;

                    // right to left swipe
                    if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE
                       && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        // Change ActionBar Tab.
                        int nextIdx = mAb.getSelectedNavigationIndex() + 1;
                        if (nextIdx < mAb.getNavigationItemCount()) {
                            showNext();
                            getTag(mAb.getTabAt(nextIdx)).fromGesture = true;
                            mAb.setSelectedNavigationItem(nextIdx);
                        }
                        return true;
                    } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE
                               && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        // Change ActionBar Tab.
                        int nextIdx = mAb.getSelectedNavigationIndex() - 1;
                        if (nextIdx >= 0) {
                            showPrev();
                            getTag(mAb.getTabAt(nextIdx)).fromGesture = true;
                            mAb.setSelectedNavigationItem(nextIdx);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    // nothing
                }
                return false;
            }
        }

        Flipper(Context context, ViewFlipper viewFlipper) {
            _mContext = context;
            _mViewFlipper = viewFlipper;
            _mSlideLeftIn = AnimationUtils.loadAnimation(context, R.anim.slide_left_in);
            _mSlideLeftOut = AnimationUtils.loadAnimation(context, R.anim.slide_left_out);
            _mSlideRightIn = AnimationUtils.loadAnimation(context, R.anim.slide_right_in);
            _mSlideRightOut = AnimationUtils.loadAnimation(context, R.anim.slide_right_out);
            _mGestureDetector = new GestureDetector(new SwipeGestureDetector());
        }

        /**
         * Add List layout R.layout.list to flipper.
         * @return newly added LinearyLayout inflated with R.layout.list
         */
        LinearLayout
        addListLayout() {
            LinearLayout ll = (LinearLayout)mLnf.inflateLayout(_mContext, R.layout.channel_listview);
            ListView list = ((ListView)ll.findViewById(R.id.list));
            eAssert(null != list);
            list.setAdapter(new ChannelListAdapter(_mContext, null,
                                                   R.layout.channel_row, list,
                                                   DATA_REQ_SZ, DATA_ARR_MAX,
                                                   new AdapterActionListener()));
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void
                onItemClick(AdapterView<?> parent, View view, int position, long itemId) {
                    Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
                    intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_CHANNEL);
                    intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
                    intent.putExtra("cid", ((ChannelListAdapter)parent.getAdapter()).getItemInfo_cid(position));
                    startActivity(intent);
                }
            });

            // Why "event handling for motion detection is here?"
            //   (not in 'ViewFlipper")
            // We can do similar thing by inheriting ViewFlipper and using 'intercepting touch event.'
            // But, in this case, scrolling up/down event is handled by list view and since than
            //   events are dedicated to list view - intercept doesn't work expectedly
            //   (not verified, but experimentally looks like it).
            // So, motion should be handled at list view.
            View.OnTouchListener swipeListener = new View.OnTouchListener() {
                @Override
                public boolean
                onTouch(View v, MotionEvent event) {
                    if (Flipper.this.onTouch(event))
                        // To avoid 'onclick' is executed even if 'gesture' is triggered.
                        event.setAction(MotionEvent.ACTION_CANCEL);
                    return false;
                }
            };
            list.setOnTouchListener(swipeListener);
            FlipperScrollView fll = (FlipperScrollView)ll.findViewById(R.id.empty_list);
            fll.setTouchInterceptor(swipeListener);
            list.setEmptyView(fll);
            addView(ll);
            return ll;
        }

        void
        addView(View child) {
            _mViewFlipper.addView(child);
        }

        void
        showNext() {
            _mViewFlipper.setInAnimation(_mSlideLeftIn);
            _mViewFlipper.setOutAnimation(_mSlideLeftOut);
            _mViewFlipper.showNext();
        }

        void
        showPrev() {
            _mViewFlipper.setInAnimation(_mSlideRightIn);
            _mViewFlipper.setOutAnimation(_mSlideRightOut);
            _mViewFlipper.showPrevious();
        }

        void
        show(Tab tab) {
            _mViewFlipper.setInAnimation(null);
            _mViewFlipper.setOutAnimation(null);
            _mViewFlipper.setDisplayedChild(_mViewFlipper.indexOfChild(getTag(tab).layout));
        }

        void
        remove(Tab tab) {
            _mViewFlipper.removeView(getTag(tab).layout);
        }

        boolean
        onTouch(MotionEvent event) {
            return _mGestureDetector.onTouchEvent(event);
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
            if (isActivityFinishing())
                return;

            // NOTE : refresh??? just 'notifying' is enough?
            // In current DB policy, sometimes DB may be updated even if updating is cancelled!
            refreshListItem(getMyTab(_mCid), _mCid);
        }

        @Override
        public void
        onPreRun(BaseBGTask task) {
            // NOTE : refresh??? just 'notifying' is enough?
            dataSetChanged(getListView(getMyTab(_mCid)), _mCid);
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            eAssert(Err.USER_CANCELLED != result);
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (isActivityFinishing())
                return;

            // In normal case, onPostExecute is not called in case of 'user-cancel'.
            // below code is for safety.
            if (Err.USER_CANCELLED == result)
                return; // onPostExecute SHOULD NOT be called in case of user-cancel

            // NOTE : refresh??? just 'notifying' is enough?
            // It should be 'refresh' due to after successful update,
            //   some channel information in DB may be changed.
            refreshListItem(getMyTab(_mCid), _mCid);
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
            if (isActivityFinishing())
                return;

            if (0 == mRtt.getItemsDownloading(_mCid).length)
                dataSetChanged(getListView(getMyTab(_mCid)), _mCid);
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (isActivityFinishing())
                return;

            if (0 == mRtt.getItemsDownloading(_mCid).length)
                dataSetChanged(getListView(getMyTab(_mCid)), _mCid);
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

            Cursor c = getContentResolver().query(_mImageUri, filePathColumn, null, null, null);
            if (!c.moveToFirst()) {
                c.close();
                return Err.GET_MEDIA;
            }

            int columnIndex = c.getColumnIndex(filePathColumn[0]);
            String filePath = c.getString(columnIndex);
            c.close();

            //logI("Pick Icon : file [" + filePath + "]");

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
                getCurrentListAdapter().setChannelIcon(_mCid, _mBm);
            else
                mLnf.showTextToast(ChannelListActivity.this, result.getMsgId());
        }
    }

    private class DeleteAllDnfilesWorker extends DiagAsyncTask.Worker {
        @Override
        public Err
        doBackgroundWork(DiagAsyncTask task) {
            Cursor c = mDbp.queryChannel(DB.ColumnChannel.ID);
            if (!c.moveToFirst()) {
                c.close();
                return Err.NO_ERR;
            }

            boolean bOk = true;
            do {
                if (!mUip.cleanChannelDir(c.getLong(0)))
                    bOk = false;
            } while (c.moveToNext());
            return bOk? Err.NO_ERR: Err.IO_FILE;
        }

        @Override
        public void
        onPostExecute(DiagAsyncTask task, Err result) {
            if (Err.NO_ERR != result)
                mLnf.showTextToast(ChannelListActivity.this, R.string.delete_all_downloaded_file_errmsg);
        }
    }

    private class DeleteChannelWorker extends DiagAsyncTask.Worker {
        private final long[]  _mCids;
        private long    _mNrDelItems    = -1;

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
            mLnf.showTextToast(ChannelListActivity.this,
                               _mNrDelItems + getResources().getString(R.string.channel_deleted_msg));
            refreshListAsync(mAb.getSelectedTab());
            ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
        }
    }

    private class RTTaskRegisterListener implements RTTask.OnRegisterListener {
        @Override
        public void
        onRegister(BGTask task, long cid, RTTask.Action act) {
            if (RTTask.Action.UPDATE == act)
                mRtt.bind(cid, RTTask.Action.UPDATE, ChannelListActivity.this, new UpdateBGTaskListener(cid));
        }
        @Override
        public void onUnregister(BGTask task, long cid, RTTask.Action act) { }
    }

    private class AdapterActionListener implements ChannelListAdapter.OnActionListener {
        @Override
        public void
        onUpdateClick(ImageView ibtn, long cid) {
            //logI("ChannelList : update cid : " + cid);
            onContextBtn_channelUpdate(ibtn, cid);
        }

        @Override
        public void
        onMoveUpClick(ImageView ibtn, long cid) {
            ChannelListAdapter adapter = getCurrentListAdapter();
            int pos = getPosition(adapter, cid);
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
            ChannelListAdapter adapter = getCurrentListAdapter();
            int pos = getPosition(adapter, cid);
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

    class TabTag {
        long         categoryid;
        boolean      fromGesture = false;
        ListView     listView;
        LinearLayout layout;
    }

    private boolean
    isActivityFinishing() {
        return isFinishing();
    }

    private TabTag
    getTag(Tab tab) {
        return (TabTag)tab.getTag();
    }

    private ListView
    getListView(Tab tab) {
        return getTag(tab).listView;
    }

    private void
    selectDefaultAsSelected() {
        // 0 is index of default tab
        mAb.setSelectedNavigationItem(0);
    }

    private Tab
    getDefaultTab() {
        return mAb.getTabAt(0);
    }

    private ListView
    getCurrentListView() {
        return getListView(mAb.getSelectedTab());
    }

    private ChannelListAdapter
    getListAdapter(Tab tab) {
        return (ChannelListAdapter)getTag(tab).listView.getAdapter();
    }

    private ChannelListAdapter
    getCurrentListAdapter() {
        return getListAdapter(mAb.getSelectedTab());
    }

    private int
    getPosition(ChannelListAdapter adapter, long cid) {
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItemInfo_cid(i) == cid)
                return i;
        }
        return -1;
    }

    private long
    getCategoryId(Tab tab) {
        return getTag(tab).categoryid;
    }

    private long
    getCurrentCategoryId() {
        return getCategoryId(mAb.getSelectedTab());
    }

    /**
     * get Tab that has give cid.
     * @param cid
     * @return Tab
     */
    private Tab
    getMyTab(long cid) {
        long catid = mDbp.getChannelInfoLong(cid, DB.ColumnChannel.CATEGORYID);
        for (int i = 0; i < mAb.getTabCount(); i++)
            if (getTag(mAb.getTabAt(i)).categoryid == catid)
                return mAb.getTabAt(i);

        logW("getMyTab : Wrong cid(" + cid + ")!!");
        return mAb.getSelectedTab(); // return selected tab by default;
    }

    private Cursor
    adapterCursorQuery(long categoryid) {
        return mDbp.queryChannel(categoryid, new DB.ColumnChannel[] {
                    DB.ColumnChannel.ID, // Mandatory.
                    DB.ColumnChannel.TITLE,
                    DB.ColumnChannel.DESCRIPTION,
                    DB.ColumnChannel.LASTUPDATE,
                    DB.ColumnChannel.IMAGEBLOB,
                    DB.ColumnChannel.URL });
    }

    private boolean
    changeCategory(long cid, Tab from, Tab to) {
        if (from.getPosition() == to.getPosition()) // nothing to do
            return true;
        mDbp.updateChannel(cid, DB.ColumnChannel.CATEGORYID, getTag(to).categoryid);
        getListAdapter(from).removeItem(getListAdapter(from).findPosition(cid));
        dataSetChanged(this.getListView(from));
        refreshListAsync(to);
        return true;
    }

    /**
     * Notify that dataset for adapter is changed.
     * All list item will try to rebind their own view.
     * @param lv
     */
    private void
    dataSetChanged(ListView lv) {
        // ((ChannelListAdapter)lv.getAdapter()).clearChangeState();
        ((ChannelListAdapter)lv.getAdapter()).notifyDataSetChanged();
    }

    /**
     * Notify that dataset of given 'cid' in adapter is changed.
     * List item of only given 'cid' - one list item - will try to rebind it's view.
     * @param lv ListView
     * @param cid
     */
    private void
    dataSetChanged(ListView lv, long cid) {
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
        ((ChannelListAdapter)lv.getAdapter()).notifyDataSetChanged();
    }

    private void
    refreshListItem(Tab tab, long cid) {
        refreshListItem(tab, new long[] { cid });
    }

    private void
    refreshListItem(Tab tab, long[] cids) {
        Cursor newCursor = adapterCursorQuery(getTag(tab).categoryid);
        ChannelListAdapter adapter = getListAdapter(tab);
        adapter.changeCursor(newCursor);
        int[] ids = new int[cids.length];
        for (int i = 0; i < ids.length; i++)
            ids[i] = adapter.findItemId(cids[i]);
        adapter.reloadItem(ids);
        adapter.notifyDataSetChanged();
    }

    /**
     * Cursor is changed and all view should be rebinded.
     * @param tab
     */
    private void
    refreshListAsync(Tab tab) {
        Cursor newCursor = adapterCursorQuery(getTag(tab).categoryid);
        getListAdapter(tab).changeCursor(newCursor);
        getListAdapter(tab).reloadDataSetAsync(null);
    }

    /**
     * Refresh full list.
     */
    private void
    refreshListAsync() {
        for (int i = 0; i < mAb.getTabCount(); i++)
            refreshListAsync(mAb.getTabAt(i));
    }

    private Tab
    addCategory(Feed.Category cat) {
        String text;
        if (mDbp.isDefaultCategoryId(cat.id)
           && !Utils.isValidValue(mDbp.getCategoryName(cat.id)))
            text = getResources().getText(R.string.default_category_name).toString();
        else
            text = cat.name;

        // Add new tab to action bar
        Tab tab = mAb.newTab()
                    .setText(text)
                    .setTag(cat.id)
                    .setTabListener(this);

        LinearLayout layout = mFlipper.addListLayout();

        TabTag tag = new TabTag();
        tag.categoryid = cat.id;
        tag.layout = layout;
        tag.listView = (ListView)layout.findViewById(R.id.list);

        tab.setTag(tag);
        mAb.addTab(tab, false);
        refreshListAsync(tab); // create cursor adapters
        return tab;
    }

    /**
     * All channels belonging to this category will be moved to default category.
     * @param categoryid
     */
    private void
    deleteCategory(long categoryid) {
        mDbp.deleteCategory(categoryid);
        // channel list of default category is changed.
        refreshListAsync(getDefaultTab());

        Tab curTab = mAb.getSelectedTab();
        mAb.removeTab(curTab);
        mFlipper.remove(curTab);
        selectDefaultAsSelected();
    }

    private String
    getTabText(Tab tab) {
        return tab.getText().toString();
    }

    /**
     * Add channel to current selected category.
     * List will be scrolled to newly added channel.
     * @param url
     */
    private void
    addChannel(String url, String iconurl) {
        eAssert(url != null);
        url = Utils.removeTrailingSlash(url);

        long cid = -1;
        try {
            cid = mDbp.insertNewChannel(getCurrentCategoryId(), url);
        } catch (FeederException e) {
            mLnf.showTextToast(this, e.getError().getMsgId());
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
        ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());

        // refresh current category.
        refreshListAsync(mAb.getSelectedTab());
    }

    /**
     * Delete channel and it's items from DB.
     * This completely deletes all channel and items.
     * @param tab
     * @param cid
     */
    private void
    deleteChannel(Tab tab, long cid) {
        eAssert(null != tab);
        DiagAsyncTask task = new DiagAsyncTask(this,
                                               new DeleteChannelWorker(new long[] { cid }),
                                               DiagAsyncTask.Style.SPIN,
                                               R.string.deleting_channel_msg);
        task.run();
    }

    private void
    onOpt_addChannel_youtubeEditDiag(final MenuItem item) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void prepare(Dialog dialog, EditText edit) {}
            @Override
            public void onOk(Dialog dialog, EditText edit) {
                switch (item.getItemId()) {
                case R.id.uploader:
                    addChannel(Utils.buildYoutubeFeedUrl_uploader(edit.getText().toString()), null);
                    break;
                case R.id.search:
                    addChannel(Utils.buildYoutubeFeedUrl_search(edit.getText().toString()), null);
                    break;
                default:
                    eAssert(false);
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, item.getTitle(), action).show();
    }

    private void
    onOpt_addChannel_youtube(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_addchannel_youtube, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onOpt_addChannel_youtubeEditDiag(item);
                return true;
            }
        });
        popup.show();
    }


    private void
    onOpt_addChannel_url(final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void prepare(Dialog dialog, EditText edit) {
                // start edit box with 'http://'
                final String prefix = "http://";
                edit.setText(prefix);
                edit.setSelection(prefix.length());
            }

            @Override
            public void onOk(Dialog dialog, EditText edit) {
                String url = edit.getText().toString();
                if (!url.matches("http\\:\\/\\/\\s*")) {
                    addChannel(url, null);
                    mUr.storeUsageReport("URL : " + url + "\n");
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, R.string.channel_url, action).show();
    }

    private void
    onOpt_addChannel_predefined(View anchor) {
        Intent intent = new Intent(this, PredefinedChannelActivity.class);
        intent.putExtra("category", getCurrentCategoryId());
        startActivityForResult(intent, REQC_PICK_PREDEFINED_CHANNEL);
    }

    private void
    onOpt_addChannel(final View anchor) {
        if (0 == mAb.getNavigationItemCount()) {
            eAssert(false);
            return;
        }

        if (0 > mAb.getSelectedNavigationIndex()) {
            mLnf.showTextToast(ChannelListActivity.this, R.string.warn_select_category_to_add);
            return;
        }

        if (!Utils.isNetworkAvailable()) {
            // TODO Handling error
            mLnf.showTextToast(ChannelListActivity.this, R.string.warn_network_unavailable);
            return;
        }

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_addchannel, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.predefined:
                    onOpt_addChannel_predefined(anchor);
                    break;
                case R.id.url:
                    onOpt_addChannel_url(anchor);
                    break;
                case R.id.youtube:
                    onOpt_addChannel_youtube(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_category_add(final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (mDbp.isDuplicatedCategoryName(name)) {
                    mLnf.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    Feed.Category cat = new Feed.Category(name);
                    if (0 > mDbp.insertCategory(cat))
                        mLnf.showTextToast(ChannelListActivity.this, R.string.warn_add_category);
                    else {
                        eAssert(cat.id >= 0);
                        refreshListAsync(addCategory(cat));
                    }
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, R.string.add_category, action).show();
    }

    private void
    onOpt_category_rename(final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (mDbp.isDuplicatedCategoryName(name)) {
                    mLnf.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    mAb.getSelectedTab().setText(name);
                    mDbp.updateCategory(getCurrentCategoryId(), name);
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, R.string.rename_category, action).show();
    }

    private void
    onOpt_category_delete(final View anchor) {
        final long categoryid = getCategoryId(mAb.getSelectedTab());
        if (mDbp.isDefaultCategoryId(categoryid)) {
            mLnf.showTextToast(this, R.string.warn_delete_default_category);
            return;
        }

        // 0 should be default category index!
        eAssert(mAb.getSelectedNavigationIndex() > 0);

        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                deleteCategory(categoryid);
            }
        };
        mLnf.buildConfirmDialog(this, R.string.delete_category, R.string.delete_category_msg, action).show();
    }

    private void
    onOpt_category(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_category, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.add:
                    onOpt_category_add(anchor);
                    break;
                case R.id.rename:
                    onOpt_category_rename(anchor);
                    break;
                case R.id.delete:
                    onOpt_category_delete(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_itemsAll(final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_ALL);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        startActivity(intent);
    }

    private void
    onOpt_itemsCategory(final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_CATEGORY);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        intent.putExtra("categoryid", getCurrentCategoryId());
        startActivity(intent);
    }

    private void
    onOpt_itemsFavorite(final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_FAVORITE);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        startActivity(intent);
    }


    private void
    onOpt_management_deleteAllDnFiles(final View anchor) {
        // check constraints
        if (mRtt.getItemsDownloading().length > 0) {
            mLnf.showTextToast(ChannelListActivity.this, R.string.del_dnfiles_not_allowed_msg);
            return;
        }

        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                DiagAsyncTask task = new DiagAsyncTask(ChannelListActivity.this,
                                                       new DeleteAllDnfilesWorker(),
                                                       DiagAsyncTask.Style.SPIN,
                                                       R.string.delete_all_downloaded_file);
                task.run();
            }
        };

        mLnf.buildConfirmDialog(this,
                                       R.string.delete_all_downloaded_file,
                                       R.string.delete_all_downloaded_file_msg,
                                       action)
            .show();
    }

    private void
    onOpt_management_feedbackOpinion(final View anchor) {
        if (!Utils.isNetworkAvailable()) {
            mLnf.showTextToast(this, R.string.warn_network_unavailable);
            return;
        }

        if (!mUr.sendFeedbackReportMain(this))
            mLnf.showTextToast(this, R.string.warn_find_email_app);
    }

    private void
    onOpt_management_dbManage(final View anchor) {
        Intent intent = new Intent(this, DBManagerActivity.class);
        startActivity(intent);
    }

    private void
    onOpt_management(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_management, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.media_delete_all:
                    onOpt_management_deleteAllDnFiles(anchor);
                    break;
                case R.id.feedback_opinion:
                    onOpt_management_feedbackOpinion(anchor);
                    break;
                case R.id.db_manage:
                    onOpt_management_dbManage(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_setting(final View anchor) {
        Intent intent = new Intent(this, FeederPreferenceActivity.class);
        startActivity(intent);
    }

    private void
    onOpt_information(final View anchor) {
        PackageManager pm = getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(getPackageName(), 0);
        }catch (NameNotFoundException e) { ; }

        if (null == pi)
            return; // never happen

        CharSequence title = getResources().getText(R.string.about_app);
        StringBuilder strbldr = new StringBuilder();
        strbldr.append(getResources().getText(R.string.version)).append(" : ").append(pi.versionName).append("\n")
               .append(getResources().getText(R.string.about_app_email)).append("\n")
               .append(getResources().getText(R.string.about_app_blog)).append("\n")
               .append(getResources().getText(R.string.about_app_page)).append("\n");
        AlertDialog diag = mLnf.createAlertDialog(this, 0, title, strbldr.toString());
        diag.show();
    }

    private void
    onContext_deleteChannel(final long cid) {
        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                deleteChannel(mAb.getSelectedTab(), cid);
            }
        };

        mLnf.buildConfirmDialog(this,
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
        final LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        LinearLayout layout = (LinearLayout)inflater.inflate(R.layout.select_list_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        final AlertDialog dialog = builder.create();

        // Create Adapter for list and set it.
        final ListView list = (ListView)layout.findViewById(R.id.list);
        Tab[] tabs = new Tab[mAb.getTabCount()];
        for (int i = 0; i < mAb.getTabCount(); i++)
            tabs[i] = mAb.getTabAt(i);
        list.setAdapter(new ArrayAdapter<Tab>(this, R.id.text, tabs) {
            @Override
            public View
            getView(int position, View convertView, ViewGroup parent) {
                View row;

                if (null == convertView)
                    row = inflater.inflate(R.layout.change_category_row, null);
                else
                    row = convertView;

                TextView tv = (TextView)row.findViewById(R.id.text);
                tv.setText(getTabText(getItem(position)));

                return row;
            }
        });

        // Set action for dialog.
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long itemId) {
                changeCategory(cid,
                               mAb.getSelectedTab(),
                               (Tab)list.getAdapter().getItem(position));
                dialog.dismiss();
            }
        });


        dialog.setTitle(R.string.select_category);
        dialog.show();
    }

    private void
    onContext_setting(final long cid) {
        Intent intent = new Intent(this, ChannelSettingActivity.class);
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
            mLnf.showTextToast(this, R.string.warn_find_gallery_app);
            return;
        }
    }

    private void
    onContextBtn_channelUpdate(ImageView ibtn, long cid) {
        RTTask.TaskState state = mRtt.getState(cid, RTTask.Action.UPDATE);
        switch (state) {
        case IDLE: {
            //logI("ChannelList : update : " + cid);
            BGTaskUpdateChannel task = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid));
            mRtt.register(cid, RTTask.Action.UPDATE, task);
            mRtt.start(cid, RTTask.Action.UPDATE);
            dataSetChanged(getCurrentListView(), cid);
        } break;

        case RUNNING:
        case READY:
            //logI("ChannelList : cancel : " + cid);
            mRtt.cancel(cid, RTTask.Action.UPDATE, null);
            // to change icon into "canceling"
            dataSetChanged(getCurrentListView(), cid);
            break;

        case FAILED: {
            Err result = mRtt.getErr(cid, RTTask.Action.UPDATE);
            mLnf.showTextToast(this, result.getMsgId());
            mRtt.consumeResult(cid, RTTask.Action.UPDATE);
            dataSetChanged(getCurrentListView(), cid);
        } break;

        case CANCELING:
            mLnf.showTextToast(this, R.string.wait_cancel);
            break;

        default:
            eAssert(false);
        }
    }

    private void
    onResult_pickImage(int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;
        // this may takes quite long time (if image size is big!).
        // So, let's do it in background.
        new DiagAsyncTask(this,
                          new PickIconWorker(data.getData()),
                          DiagAsyncTask.Style.SPIN,
                          R.string.pick_icon_progress)
            .run();
    }

    private void
    onResult_pickPredefinedChannel(int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;

        final String url = data.getStringExtra("url");
        eAssert(Utils.isValidValue(url));
        final String iconurl = data.getStringExtra("iconurl");
        // NOTE
        // Without using 'post', user may feel bad ui response.
        Utils.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                addChannel(url, iconurl);
            }
        });
    }

    private void
    setupToolButtons() {
        findViewById(R.id.btn_items_favorite).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_itemsFavorite(v);
            }
        });

        findViewById(R.id.btn_items_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_itemsCategory(v);
            }
        });

        findViewById(R.id.btn_items_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_itemsAll(v);
            }
        });

        findViewById(R.id.btn_add_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_addChannel(v);
            }
        });

        findViewById(R.id.btn_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_category(v);
            }
        });

        findViewById(R.id.btn_management).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_management(v);
            }
        });

        findViewById(R.id.btn_setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_setting(v);
            }
        });

        findViewById(R.id.btn_information).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_information(v);
                /* For scheduled update test.
                Cursor c = mDbp.queryChannel(DB.ColumnChannel.ID);
                c.moveToFirst();
                Calendar calNow = Calendar.getInstance();
                long dayms = calNow.getTimeInMillis() - Utils.dayBaseMs(calNow);
                dayms += 5000; // after 5 sec
                mDbp.updateChannel_schedUpdate(c.getLong(0), new long[] { dayms/1000 });
                c.close();
                ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
                */
            }
        });
    }

    @Override
    public void
    onTabSelected(Tab tab, FragmentTransaction ft) {
        onTabReselected(tab, ft);
    }

    @Override
    public void
    onTabUnselected(Tab tab, FragmentTransaction ft) {
        // to make sure
        getTag(tab).fromGesture = false;
    }

    @Override
    public void
    onTabReselected(Tab tab, FragmentTransaction ft) {
        if (!getTag(tab).fromGesture)
            mFlipper.show(tab);
        getTag(tab).fromGesture = false;
        registerForContextMenu(getTag(tab).listView);
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.channel_context, menu);
        AdapterContextMenuInfo mInfo = (AdapterContextMenuInfo)menuInfo;
        long dbId = getCurrentListAdapter().getItemInfo_cid(mInfo.position);
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
    public boolean
    onContextItemSelected(MenuItem mItem) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)mItem.getMenuInfo();

        long dbId = getCurrentListAdapter().getItemInfo_cid(info.position);
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

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case REQC_PICK_IMAGE:
            onResult_pickImage(resultCode, data);
            break;
        case REQC_PICK_PREDEFINED_CHANNEL:
            onResult_pickPredefinedChannel(resultCode, data);
            break;
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ChannelListActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);

        //logI("==> ChannelListActivity : onCreate");

        // TODO
        // Is this best place to put this line of code (sendReportMail())???
        // More consideration is required.

        // Send error report if exists.
        mUr.sendErrReportMail(this);
        // Send usage report if exists and time is passed enough.
        mUr.sendUsageReportMail(this);


        setContentView(R.layout.channel_list);

        Feed.Category[] cats;
        cats = mDbp.getCategories();

        eAssert(cats.length > 0);

        // Setup for swipe.
        mFlipper = new Flipper(this, (ViewFlipper)findViewById(R.id.flipper));
        setupToolButtons();

        // Setup Tabs
        mAb = getActionBar();
        mAb.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mAb.setDisplayShowTitleEnabled(false);
        mAb.setDisplayShowHomeEnabled(false);

        for (Feed.Category cat : cats)
            addCategory(cat);

        // Select default category as current category.
        selectDefaultAsSelected();

        // To avoid duplicated refreshing list at onResume().
        mDbp.registerChannelWatcher(this);
        mDbp.registerChannelTableWatcher(this);
    }


    @Override
    protected void
    onStart() {
        super.onStart();
        //logI("==> ChannelListActivity : onStart");
    }

    @Override
    protected void
    onResume() {
        super.onResume();

        if (mDbp.isCategoryTableWatcherRegistered(this)
            && mDbp.isCategoryTableWatcherUpdated(this)) {
            // category table is changed outside of this activity.
            // restarting is required!!
            Intent intent = new Intent(this, ChannelListActivity.class);
            startActivity(intent);
            finish();
            return;
        }
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

        // Check channel state and bind it.
        // Why here? Not 'onStart'.
        // See comments in 'onPause()'
        try {
            mDbp.getDelayedChannelUpdate();
            Cursor c = mDbp.queryChannel(DB.ColumnChannel.ID);
            if (c.moveToFirst()) {
                do {
                    long cid = c.getLong(0);
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

        if (null == mAb) {
            logW("ChannelListActivity : mAb(action bar) is NULL");
            mAb = getActionBar();
        }

        // default is "full refresh"
        long cids[] = new long[0];
        boolean fullRefresh = true;
        boolean fullRefreshCurrent = false;
        if (mDbp.isChannelWatcherRegistered(this))
            cids = mDbp.getChannelWatcherUpdated(this);
        fullRefresh = cids.length > CHANNEL_REFRESH_THRESHOLD? true: false;

        // NOTE
        // Channel may be added or deleted.
        // And channel operation is only allowed on current selected list
        //   according to use case.
        if (mDbp.isChannelTableWatcherRegistered(this)
            && mDbp.isChannelTableWatcherUpdated(this))
            fullRefreshCurrent = true;

        // We don't need to worry about item table change.
        // Because, if item is newly inserted, that means some of channel is updated.
        // And that channel will be updated according to DB changes.

        mDbp.unregisterChannelWatcher(this);
        mDbp.unregisterChannelTableWatcher(this);

        if (fullRefresh)
            refreshListAsync();
        else {
            // only small amount of channel is updated. do synchronous update.
            for (int i = 0; i < mAb.getTabCount(); i++)
                if (fullRefreshCurrent && mAb.getTabAt(i) == mAb.getSelectedTab())
                    refreshListAsync(mAb.getTabAt(i));
                else
                    refreshListItem(mAb.getTabAt(i), cids);
        }
    }

   @Override
    public void
    onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

    }

    @Override
    protected void
    onPause() {
        //logI("==> ChannelListActivity : onPause");
        mDbp.registerChannelWatcher(this);
        mDbp.registerChannelTableWatcher(this);
        mDbp.registerCategoryTableWatcher(this);
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
    protected void
    onStop() {
        //logI("==> ChannelListActivity : onStop");
        //uilc.onStop();
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        //logI("==> ChannelListActivity : onDestroy");
        super.onDestroy();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }
}
