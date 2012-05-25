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
import static free.yhc.feeder.model.Utils.logI;
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
import android.content.DialogInterface;
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
import android.view.KeyEvent;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ChannelListActivity extends Activity implements
ActionBar.TabListener,
UILifecycle.OnEvent,
UnexpectedExceptionHandler.TrackedModule {
    // Request codes.
    private static final int ReqCPickImage = 0;

    private UILifecycle uilc ;
    private View        contentv;
    private View        waitv;
    private ActionBar   ab      = null;
    private Flipper     flipper = null;

    // Saved cid for Async execution.
    private long      cid_pickImage = -1;

    private interface EditTextDialogAction {
        void prepare(Dialog dialog, EditText edit);
        void onOk(Dialog dialog, EditText edit);
    }

    private interface ConfirmDialogAction {
        void onOk(Dialog dialog);
    }

    private static class FlipperScrollView extends ScrollView {
        private View.OnTouchListener touchInterceptor = null;

        public
        FlipperScrollView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        void
        setTouchInterceptor(View.OnTouchListener interceptor) {
            touchInterceptor = interceptor;
        }

        @Override
        public boolean
        onTouchEvent(MotionEvent ev) {
            boolean ret = super.onTouchEvent(ev);
            if (null != touchInterceptor)
                touchInterceptor.onTouch(this, ev);
            return ret;
        }

        @Override
        public boolean
        onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }

    private class Flipper {
        private Context     context;
        private ViewFlipper viewFlipper;
        private Animation   slideLeftIn;
        private Animation   slideLeftOut;
        private Animation   slideRightIn;
        private Animation   slideRightOut;
        private GestureDetector gestureDetector;

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
                        int nextIdx = ab.getSelectedNavigationIndex() + 1;
                        if (nextIdx < ab.getNavigationItemCount()) {
                            showNext();
                            getTag(ab.getTabAt(nextIdx)).fromGesture = true;
                            ab.setSelectedNavigationItem(nextIdx);
                        }
                        return true;
                    } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE
                               && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        // Change ActionBar Tab.
                        int nextIdx = ab.getSelectedNavigationIndex() - 1;
                        if (nextIdx >= 0) {
                            showPrev();
                            getTag(ab.getTabAt(nextIdx)).fromGesture = true;
                            ab.setSelectedNavigationItem(nextIdx);
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
            this.context = context;
            this.viewFlipper = viewFlipper;
            slideLeftIn = AnimationUtils.loadAnimation(context, R.anim.slide_left_in);
            slideLeftOut = AnimationUtils.loadAnimation(context, R.anim.slide_left_out);
            slideRightIn = AnimationUtils.loadAnimation(context, R.anim.slide_right_in);
            slideRightOut = AnimationUtils.loadAnimation(context, R.anim.slide_right_out);
            gestureDetector = new GestureDetector(new SwipeGestureDetector());
        }

        /**
         * Add List layout R.layout.list to flipper.
         * @return newly added LinearyLayout inflated with R.layout.list
         */
        LinearLayout
        addListLayout() {
            LinearLayout ll = (LinearLayout)LookAndFeel.inflateLayout(context, R.layout.list);
            ListView list = ((ListView)ll.findViewById(R.id.list));
            eAssert(null != list);
            list.setAdapter(new ChannelListAdapter(context, R.layout.channel_row, null,
                                                   new OnAdapterActionHandler()));
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void
                onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
                    intent.putExtra(ItemListActivity.IKeyMode, ItemListActivity.ModeChannel);
                    intent.putExtra(ItemListActivity.IKeyFilter, ItemListActivity.FilterNone);
                    intent.putExtra("cid", id);
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
            viewFlipper.addView(child);
        }

        void
        showNext() {
            viewFlipper.setInAnimation(slideLeftIn);
            viewFlipper.setOutAnimation(slideLeftOut);
            viewFlipper.showNext();
        }

        void
        showPrev() {
            viewFlipper.setInAnimation(slideRightIn);
            viewFlipper.setOutAnimation(slideRightOut);
            viewFlipper.showPrevious();
        }

        void
        show(Tab tab) {
            viewFlipper.setInAnimation(null);
            viewFlipper.setOutAnimation(null);
            viewFlipper.setDisplayedChild(viewFlipper.indexOfChild(getTag(tab).layout));
        }

        void
        remove(Tab tab) {
            viewFlipper.removeView(getTag(tab).layout);
        }

        boolean
        onTouch(MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }
    }

    private class UpdateBGTaskOnEvent implements BGTask.OnEvent {
        private long    cid = -1;

        UpdateBGTaskOnEvent(long cid) {
            this.cid = cid;
        }

        @Override
        public void
        onProgress(BGTask task, long progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            eAssert(cid >= 0);
            // NOTE : refresh??? just 'notifying' is enough?
            // In current DB policy, sometimes DB may be updated even if updating is cancelled!
            refreshList(getMyTab(cid), cid);
        }

        @Override
        public void
        onPreRun(BGTask task) {
            // NOTE : refresh??? just 'notifying' is enough?
            dataSetChanged(getListView(getMyTab(cid)), cid);
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            eAssert(Err.UserCancelled != result);
            // In normal case, onPostExecute is not called in case of 'user-cancel'.
            // below code is for safety.
            if (Err.UserCancelled == result)
                return; // onPostExecute SHOULD NOT be called in case of user-cancel

            // NOTE : refresh??? just 'notifying' is enough?
            // It should be 'refresh' due to after successful update,
            //   some channel information in DB may be changed.
            refreshList(getMyTab(cid), cid);
        }
    }


    private class PickIconEventHandler implements SpinAsyncTask.OnEvent {
        private long cid = -1;
        @Override
        public Err
        onDoWork(SpinAsyncTask task, Object... objs) {
            Intent data = (Intent)objs[0];
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaColumns.DATA};

            cid = cid_pickImage;

            Cursor c = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            if (!c.moveToFirst()) {
                c.close();
                return Err.MediaGet;
            }

            int columnIndex = c.getColumnIndex(filePathColumn[0]);
            String filePath = c.getString(columnIndex);
            c.close();

            logI("Pick Icon : file [" + filePath + "]");

            // Make url string from file path
            Bitmap bm = Utils.decodeImage(filePath, Feed.Channel.ICON_MAX_WIDTH, Feed.Channel.ICON_MAX_HEIGHT);
            byte[] imageData = Utils.compressBitmap(bm);
            bm.recycle();

            if (null == imageData)
                return Err.CodecDecode;

            if (cid_pickImage < 0) {
                eAssert(false);
                return Err.Unknown; // something evil!!!
            } else {
                DBPolicy.S().updateChannel(cid_pickImage, DB.ColumnChannel.IMAGEBLOB, imageData);
                cid_pickImage = -1;
            }
            return Err.NoErr;
        }

        @Override
        public void
        onPostExecute(SpinAsyncTask task, Err result) {
            if (Err.NoErr == result)
                //NOTE
                //  "getListAdapter().notifyDataSetChanged();" doesn't works here... why??
                //  DB data may be changed! So, we need to re-create cursor again.
                //  'notifyDataSetChanged' is just for recreating list item view.
                //  (DB item doens't reloaded!)
                refreshList(ab.getSelectedTab(), cid);
            else
                LookAndFeel.showTextToast(ChannelListActivity.this, result.getMsgId());
        }

        @Override
        public void
        onCancel(SpinAsyncTask task) {}
    }

    private class DeleteAllDnfilesEventHandler implements SpinAsyncTask.OnEvent {
        @Override
        public Err
        onDoWork(SpinAsyncTask task, Object... objs) {
            Cursor c = DBPolicy.S().queryChannel(DB.ColumnChannel.ID);
            if (!c.moveToFirst()) {
                c.close();
                return Err.NoErr;
            }

            boolean bOk = true;
            do {
                if (!UIPolicy.cleanChannelDir(c.getLong(0)))
                    bOk = false;
            } while (c.moveToNext());
            return bOk? Err.NoErr: Err.IOFile;
        }

        @Override
        public void
        onPostExecute(SpinAsyncTask task, Err result) {
            if (Err.NoErr != result)
                LookAndFeel.showTextToast(ChannelListActivity.this, R.string.delete_all_downloaded_file_errmsg);
        }

        @Override
        public void
        onCancel(SpinAsyncTask task) {}
    }

    private class DeleteChannelEventHandler implements SpinAsyncTask.OnEvent {
        private long nrDelItems    = -1;
        @Override
        public Err
        onDoWork(SpinAsyncTask task, Object... objs) {
            long[] cids = new long[objs.length];
            for (int i = 0; i < objs.length; i++)
                cids[i] = (Long)objs[i];

            nrDelItems = DBPolicy.S().deleteChannel(cids);
            return Err.NoErr;
        }

        @Override
        public void
        onCancel(SpinAsyncTask task) {
        }

        @Override
        public void
        onPostExecute(SpinAsyncTask task, Err result) {
            LookAndFeel.showTextToast(ChannelListActivity.this,
                                      nrDelItems + getResources().getString(R.string.channel_deleted_msg));
            refreshList(ab.getSelectedTab());
            ScheduledUpdater.scheduleNextUpdate(ChannelListActivity.this, Calendar.getInstance());
        }
    }

    private class RTTaskManagerEventHandler implements RTTask.OnRTTaskManagerEvent {
        @Override
        public void
        onBGTaskRegister(long cid, BGTask task, RTTask.Action act) {
            if (RTTask.Action.Update == act)
                RTTask.S().bind(cid, RTTask.Action.Update, ChannelListActivity.this, new UpdateBGTaskOnEvent(cid));
        }
        @Override
        public void onBGTaskUnregister(long cid, BGTask task, RTTask.Action act) { }
    }

    private class OnAdapterActionHandler implements ChannelListAdapter.OnAction {
        @Override
        public void
        onUpdateClick(ImageView ibtn, long cid) {
            logI("ChannelList : update cid : " + cid);
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


            DBPolicy.S().updatechannel_switchPosition(adapter.getItemId(pos - 1),
                                                      adapter.getItemId(pos));
            refreshList(ab.getSelectedTab());
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


            DBPolicy.S().updatechannel_switchPosition(adapter.getItemId(pos),
                                                      adapter.getItemId(pos + 1));
            refreshList(ab.getSelectedTab());
        }
    }

    class TabTag {
        long         categoryid;
        boolean      fromGesture = false;
        ListView     listView;
        LinearLayout layout;
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
        ab.setSelectedNavigationItem(0);
    }

    private Tab
    getDefaultTab() {
        return ab.getTabAt(0);
    }

    private ListView
    getCurrentListView() {
        return getListView(ab.getSelectedTab());
    }

    private ChannelListAdapter
    getListAdapter(Tab tab) {
        return (ChannelListAdapter)getTag(tab).listView.getAdapter();
    }

    private ChannelListAdapter
    getCurrentListAdapter() {
        return getListAdapter(ab.getSelectedTab());
    }

    private int
    getPosition(ChannelListAdapter adapter, long cid) {
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItemId(i) == cid)
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
        return getCategoryId(ab.getSelectedTab());
    }

    /**
     * get Tab that has give cid.
     * @param cid
     * @return Tab
     */
    private Tab
    getMyTab(long cid) {
        long catid = DBPolicy.S().getChannelInfoLong(cid, DB.ColumnChannel.CATEGORYID);
        for (int i = 0; i < ab.getTabCount(); i++)
            if (getTag(ab.getTabAt(i)).categoryid == catid)
                return ab.getTabAt(i);

        logW("getMyTab : Wrong cid(" + cid + ")!!");
        return ab.getSelectedTab(); // return selected tab by default;
    }

    private Cursor
    adapterCursorQuery(long categoryid) {
        return DBPolicy.S().queryChannel(categoryid, new DB.ColumnChannel[] {
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
        DBPolicy.S().updateChannel(cid, DB.ColumnChannel.CATEGORYID, getTag(to).categoryid);
        refreshList(from);
        refreshList(to);
        return true;
    }

    /**
     * Notify that dataset for adapter is changed.
     * All list item will try to rebind their own view.
     * @param lv
     */
    private void
    dataSetChanged(ListView lv) {
        ((ChannelListAdapter)lv.getAdapter()).clearChangeState();
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
        ChannelListAdapter cla = (ChannelListAdapter)lv.getAdapter();
        ((ChannelListAdapter)lv.getAdapter()).clearChangeState();
        for (int i = lv.getFirstVisiblePosition();
             i <= lv.getLastVisiblePosition();
             i++) {
            long itemId = cla.getItemId(i);
            if (itemId == cid)
                cla.addChanged(itemId);
            else
                cla.addUnchanged(itemId);
        }
        ((ChannelListAdapter)lv.getAdapter()).notifyDataSetChanged();
    }

    /**
     * Cursor is changed and rebind view whose id is 'cid'
     * @param tab
     * @param cid
     */
    private void
    refreshList(Tab tab, long cid) {
        // NOTE
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery(getTag(tab).categoryid);
        getListAdapter(tab).changeCursor(newCursor);
        dataSetChanged(getTag(tab).listView, cid);
    }

    /**
     * Cursor is changed and all view should be rebinded.
     * @param tab
     */
    private void
    refreshList(Tab tab) {
        Cursor newCursor = adapterCursorQuery(getTag(tab).categoryid);
        getListAdapter(tab).changeCursor(newCursor);
        dataSetChanged(getTag(tab).listView);
    }

    private Tab
    addCategory(Feed.Category cat) {
        String text;
        if (DBPolicy.S().isDefaultCategoryId(cat.id)
           && !Utils.isValidValue(DBPolicy.S().getCategoryName(cat.id)))
            text = getResources().getText(R.string.default_category_name).toString();
        else
            text = cat.name;

        // Add new tab to action bar
        Tab tab = ab.newTab()
                    .setText(text)
                    .setTag(cat.id)
                    .setTabListener(this);

        LinearLayout layout = flipper.addListLayout();

        TabTag tag = new TabTag();
        tag.categoryid = cat.id;
        tag.layout = layout;
        tag.listView = (ListView)layout.findViewById(R.id.list);

        tab.setTag(tag);
        ab.addTab(tab, false);
        refreshList(tab); // create cursor adapters
        return tab;
    }

    /**
     * All channels belonging to this category will be moved to default category.
     * @param categoryid
     */
    private void
    deleteCategory(long categoryid) {
        DBPolicy.S().deleteCategory(categoryid);
        // channel list of default category is changed.
        refreshList(getDefaultTab());

        Tab curTab = ab.getSelectedTab();
        ab.removeTab(curTab);
        flipper.remove(curTab);
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
    addChannel(String url) {
        eAssert(url != null);
        url = Utils.removeTrailingSlash(url);
        long cid = DBPolicy.S().insertNewChannel(getCurrentCategoryId(), url);
        if (cid < 0) {
            LookAndFeel.showTextToast(this, R.string.warn_add_channel);
            return;
        }
        // full update for this newly inserted channel
        BGTaskUpdateChannel task = new BGTaskUpdateChannel(this, new BGTaskUpdateChannel.Arg(cid));
        RTTask.S().register(cid, RTTask.Action.Update, task);
        RTTask.S().start(cid, RTTask.Action.Update);
        ScheduledUpdater.scheduleNextUpdate(this, Calendar.getInstance());

        // refresh current category.
        refreshList(ab.getSelectedTab(), cid);

        // Move to bottom of the list where newly inserted channel is located on.
        // (This is for feedback to user saying "new channel is now adding").
        final ListView lv = getTag(ab.getSelectedTab()).listView;
        lv.post(new Runnable() {
            @Override
            public void run() {
                // Select the last row so it will scroll into view...
                lv.setSelection(lv.getCount() - 1);
            }
        });
    }

    /**
     * Set channel's state to 'unused'.
     * This doesn't delete items belonging to this channel.
     * @param tab
     * @param cid
     */
    private void
    unlistChannel(Tab tab, long cid) {
        eAssert(null != tab);
        DBPolicy.S().unlistChannel(cid);
        refreshList(tab);
        ScheduledUpdater.scheduleNextUpdate(this, Calendar.getInstance());
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
        SpinAsyncTask task = new SpinAsyncTask(this,
                                               new DeleteChannelEventHandler(),
                                               R.string.deleting_channel_msg);
        task.execute(new Long(cid));
    }

    private AlertDialog
    buildOneLineEditTextDialog(int title, final EditTextDialogAction action) {
        // Create "Enter Url" dialog
        View layout = LookAndFeel.inflateLayout(this, R.layout.oneline_editbox_dialog);
        final AlertDialog dialog = LookAndFeel.createEditTextDialog(this,
                                                                    layout,
                                                                    title);
        // Set action for dialog.
        final EditText edit = (EditText)layout.findViewById(R.id.editbox);
        edit.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((KeyEvent.ACTION_DOWN == event.getAction()) && (KeyEvent.KEYCODE_ENTER == keyCode)) {
                    if (!edit.getText().toString().isEmpty()) {
                        action.onOk(dialog, ((EditText)v));
                        return true;
                    }
                }
                return false;
            }
        });
        action.prepare(dialog, edit);

        dialog.setButton(getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dia, int which) {
                dialog.dismiss();
                if (!edit.getText().toString().isEmpty())
                    action.onOk(dialog, edit);
            }
        });

        dialog.setButton2(getResources().getText(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return dialog;
    }

    private AlertDialog
    buildConfirmDialog(int title, int description,
                       final ConfirmDialogAction action) {
        final AlertDialog dialog = LookAndFeel.createWarningDialog(this, title, description);
        dialog.setButton(getResources().getText(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface diag, int which) {
                dialog.dismiss();
                action.onOk(dialog);
            }
        });

        dialog.setButton2(getResources().getText(R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return dialog;
    }

    private void
    onOpt_addChannel() {
        if (0 == ab.getNavigationItemCount()) {
            eAssert(false);
            return;
        }

        if (0 > ab.getSelectedNavigationIndex()) {
            LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_select_category_to_add);
            return;
        }

        if (!Utils.isNetworkAvailable(this)) {
            // TODO Handling error
            LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_network_unavailable);
            return;
        }

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
                if (!url.matches("http\\:\\/\\/\\s*"))
                    addChannel(url);
            }
        };
        buildOneLineEditTextDialog(R.string.channel_url, action).show();
    }

    private void
    onOpt_addYoutubeChannel_editDiag(final int optStringId) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void prepare(Dialog dialog, EditText edit) {}
            @Override
            public void onOk(Dialog dialog, EditText edit) {
                switch (optStringId) {
                case R.string.tag:
                    addChannel(Utils.buildYoutubeFeedUrl_tag(edit.getText().toString()));
                    break;
                case R.string.uploader:
                    addChannel(Utils.buildYoutubeFeedUrl_uploader(edit.getText().toString()));
                    break;
                case R.string.word_search:
                    addChannel(Utils.buildYoutubeFeedUrl_search(edit.getText().toString()));
                    break;
                default:
                    eAssert(false);
                }
            }
        };
        buildOneLineEditTextDialog(optStringId, action).show();
    }

    private void
    onOpt_addYoutubeChannel() {
        final int[] optStringIds = { R.string.tag, R.string.uploader, R.string.word_search };
        final CharSequence[] items = new CharSequence[optStringIds.length];
        for (int i = 0; i < optStringIds.length; i++)
            items[i] = getResources().getText(optStringIds[i]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getText(R.string.way_youtube_subscribe));
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                onOpt_addYoutubeChannel_editDiag(optStringIds[item]);
            }
        });
        builder.create().show();
    }

    private void
    onOpt_itemsCategory() {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKeyMode, ItemListActivity.ModeCategory);
        intent.putExtra(ItemListActivity.IKeyFilter, ItemListActivity.FilterNone);
        intent.putExtra("categoryid", getCurrentCategoryId());
        startActivity(intent);
    }

    private void
    onOpt_itemsFavorite() {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKeyMode, ItemListActivity.ModeFavorite);
        intent.putExtra(ItemListActivity.IKeyFilter, ItemListActivity.FilterNone);
        startActivity(intent);
    }

    private void
    onOpt_addCategory() {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (DBPolicy.S().isDuplicatedCategoryName(name)) {
                    LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    Feed.Category cat = new Feed.Category(name);
                    if (0 > DBPolicy.S().insertCategory(cat))
                        LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_add_category);
                    else {
                        eAssert(cat.id >= 0);
                        refreshList(addCategory(cat));
                    }
                }
            }
        };
        buildOneLineEditTextDialog(R.string.add_category, action).show();
    }

    private void
    onOpt_deleteCategory() {
        final long categoryid = getCategoryId(ab.getSelectedTab());
        if (DBPolicy.S().isDefaultCategoryId(categoryid)) {
            LookAndFeel.showTextToast(this, R.string.warn_delete_default_category);
            return;
        }

        // 0 should be default category index!
        eAssert(ab.getSelectedNavigationIndex() > 0);

        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                deleteCategory(categoryid);
            }
        };
        buildConfirmDialog(R.string.delete_category, R.string.delete_category_msg, action)
            .show();
    }

    private void
    onOpt_renameCategory() {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (DBPolicy.S().isDuplicatedCategoryName(name)) {
                    LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    ab.getSelectedTab().setText(name);
                    DBPolicy.S().updateCategory(getCurrentCategoryId(), name);
                }
            }
        };
        buildOneLineEditTextDialog(R.string.rename_category, action).show();
    }

    private void
    onOpt_deleteAllDnfiles() {
        // check constraints
        if (RTTask.S().getItemsDownloading().length > 0) {
            LookAndFeel.showTextToast(ChannelListActivity.this, R.string.del_dnfiles_not_allowed_msg);
            return;
        }

        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                SpinAsyncTask task = new SpinAsyncTask(ChannelListActivity.this,
                                                       new DeleteAllDnfilesEventHandler(),
                                                       R.string.delete_all_downloaded_file);
                task.execute(new Object()); // just pass dummy object;
            }
        };

        buildConfirmDialog(R.string.delete_all_downloaded_file, R.string.delete_all_downloaded_file_msg, action)
            .show();
    }

    private void
    onOpt_setting() {
        Intent intent = new Intent(this, FeederPreferenceActivity.class);
        startActivity(intent);
    }

    private void
    onOpt_selectPredefinedChannel() {
        Intent intent = new Intent(this, PredefinedChannelActivity.class);
        intent.putExtra("category", getCurrentCategoryId());
        startActivity(intent);
    }

    private void
    onOpt_information() {
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
        AlertDialog diag = LookAndFeel.createAlertDialog(this, 0, title, strbldr.toString());
        diag.show();
    }

    private void
    onContext_unlistChannel(final long cid) {
        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                unlistChannel(ab.getSelectedTab(), cid);
            }
        };

        buildConfirmDialog(R.string.unlist_channel, R.string.unlist_channel_msg, action)
            .show();
    }

    private void
    onContext_deleteChannel(final long cid) {
        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                deleteChannel(ab.getSelectedTab(), cid);
            }
        };

        buildConfirmDialog(R.string.delete_channel, R.string.delete_channel_msg, action)
            .show();
    }

    private void
    onContext_deleteDownloaded(final long cid) {
        // delete entire channel directory and re-make it.
        // Why?
        // All and only downloaded files are located in channel directory.
        UIPolicy.cleanChannelDir(cid);
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
        Tab[] tabs = new Tab[ab.getTabCount()];
        for (int i = 0; i < ab.getTabCount(); i++)
            tabs[i] = ab.getTabAt(i);
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
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                changeCategory(cid,
                               ab.getSelectedTab(),
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
        cid_pickImage = cid;
        try {
            startActivityForResult(Intent.createChooser(i,
                                                        getResources().getText(R.string.pick_icon)),
                                   ReqCPickImage);
        } catch (ActivityNotFoundException e) {
            LookAndFeel.showTextToast(this, R.string.warn_find_gallery_app);
            return;
        }
    }

    private void
    onContextBtn_channelUpdate(ImageView ibtn, long cid) {
        /* code for test...
        ScheduledUpdater.setNextScheduledUpdate(this, cid);
        return;
        */
        RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
        if (RTTask.TaskState.Idle == state) {
            logI("ChannelList : update : " + cid);
            BGTaskUpdateChannel task = new BGTaskUpdateChannel(this, new BGTaskUpdateChannel.Arg(cid));
            RTTask.S().register(cid, RTTask.Action.Update, task);
            RTTask.S().start(cid, RTTask.Action.Update);
            dataSetChanged(getCurrentListView(), cid);
        } else if (RTTask.TaskState.Running == state
                   || RTTask.TaskState.Ready == state) {
            logI("ChannelList : cancel : " + cid);
            RTTask.S().cancel(cid, RTTask.Action.Update, null);
            // to change icon into "canceling"
            dataSetChanged(getCurrentListView(), cid);
        } else if (RTTask.TaskState.Failed == state) {
            Err result = RTTask.S().getErr(cid, RTTask.Action.Update);
            LookAndFeel.showTextToast(this, result.getMsgId());
            RTTask.S().consumeResult(cid, RTTask.Action.Update);
            dataSetChanged(getCurrentListView(), cid);
        } else if (RTTask.TaskState.Canceling == state) {
            LookAndFeel.showTextToast(this, R.string.wait_cancel);
        } else
            eAssert(false);
    }

    private void
    onResult_pickImage(int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;
        // this may takes quite long time (if image size is big!).
        // So, let's do it in background.
        new SpinAsyncTask(this, new PickIconEventHandler(), R.string.pick_icon_progress).execute(data);
    }

    private void
    setupToolButtons(View contentv) {
        contentv.findViewById(R.id.btn_add_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_addChannel();
            }
        });

        contentv.findViewById(R.id.btn_add_youtube_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_addYoutubeChannel();
            }
        });

        contentv.findViewById(R.id.btn_items_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_itemsCategory();
            }
        });

        contentv.findViewById(R.id.btn_items_favorite).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_itemsFavorite();
            }
        });

        contentv.findViewById(R.id.btn_add_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_addCategory();
            }
        });

        contentv.findViewById(R.id.btn_del_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_deleteCategory();
            }
        });

        contentv.findViewById(R.id.btn_rename_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_renameCategory();
            }
        });

        contentv.findViewById(R.id.btn_del_dnfiles).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_deleteAllDnfiles();
            }
        });

        contentv.findViewById(R.id.btn_setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_setting();
            }
        });

        contentv.findViewById(R.id.btn_predefined).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_selectPredefinedChannel();
            }
        });

        contentv.findViewById(R.id.btn_information).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOpt_information();
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
            flipper.show(tab);
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
        RTTask.TaskState updateState = RTTask.S().getState(mInfo.id, RTTask.Action.Update);

        if (RTTask.TaskState.Running == updateState
            || RTTask.TaskState.Ready == updateState
            || RTTask.TaskState.Canceling == updateState) {
            menu.findItem(R.id.unlist).setEnabled(false);
            menu.findItem(R.id.delete).setEnabled(false);
            menu.findItem(R.id.pick_icon).setEnabled(false);
            /* full update is useless at this moment. Codes are left for history tracking
            menu.findItem(R.id.full_update).setEnabled(false);
            */
        }

        if (RTTask.S().getItemsDownloading(mInfo.id).length > 0) {
            menu.findItem(R.id.unlist).setEnabled(false);
            menu.findItem(R.id.delete).setEnabled(false);
            menu.findItem(R.id.delete_dnfile).setEnabled(false);
        }
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem mItem) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)mItem.getMenuInfo();

        switch (mItem.getItemId()) {
        case R.id.unlist:
            onContext_unlistChannel(info.id);
            return true;

        case R.id.delete:
            onContext_deleteChannel(info.id);
            return true;

        case R.id.delete_dnfile:
            onContext_deleteDownloaded(info.id);
            return true;

        case R.id.change_category:
            onContext_changeCategory(info.id);
            return true;

        case R.id.setting:
            onContext_setting(info.id);
            return true;

        case R.id.pick_icon:
            onContext_pickIcon(info.id);
            return true;
        }
        return false;
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case ReqCPickImage:
            onResult_pickImage(resultCode, data);
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
    onUICreate(View contentv) {
        logI("==> ChannelListActivity : onUICreate");
        Feed.Category[] cats;
        cats = DBPolicy.S().getCategories();

        eAssert(cats.length > 0);

        // Setup for swipe.
        flipper = new Flipper(this, (ViewFlipper)contentv.findViewById(R.id.flipper));
        setupToolButtons(contentv);

        // Setup Tabs
        ab = getActionBar();
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowHomeEnabled(false);

        for (Feed.Category cat : cats)
            addCategory(cat);

        // Select default category as current category.
        selectDefaultAsSelected();
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.S().registerModule(this);
        super.onCreate(savedInstanceState);

        logI("==> ChannelListActivity : onCreate");

        // TODO
        // Is this best place to put this line of code (sendReportMail())???
        // More consideration is required.
        // Send error report if exists.
        UnexpectedExceptionHandler.S().sendReportMail(this);

        uilc = new UILifecycle("Channel", this, this,
                               LookAndFeel.inflateLayout(this, R.layout.channel_list),
                               LookAndFeel.inflateLayout(this, R.layout.plz_wait));
        uilc.onCreate();
    }

    @Override
    public void
    onUIStart(View contentv) {
        logI("==> ChannelListActivity : onUIStart");
        // nothing to do
    }

    @Override
    protected void
    onStart() {
        super.onStart();
        logI("==> ChannelListActivity : onStart");
        uilc.onStart();
    }

    @Override
    public void
    onUIResume(View contentv) {
        logI("==> ChannelListActivity : onUIResume");
        // NOTE
        // Case to think about
        // - new update task is registered between 'registerManagerEventListener' and 'getUpdateState'
        // - then, this task will be binded twice.
        // => This leads to over head operation (ex. refreshing list two times continuously etc.)
        //    But, this doesn't issue logical error. So, I can get along with this case.
        //
        // If 'registerManagerEventListener' is below 'getUpdateState',
        //   we may miss binding some updating task!

        RTTask.S().registerManagerEventListener(this, new RTTaskManagerEventHandler());

        // Check channel state and bind it.
        // Why here? Not 'onStart'.
        // See comments in 'onPause()'
        Cursor c = DBPolicy.S().queryChannel(DB.ColumnChannel.ID);
        if (c.moveToFirst()) {
            do {
                long cid = c.getLong(0);
                if (RTTask.TaskState.Idle != RTTask.S().getState(cid, RTTask.Action.Update))
                    RTTask.S().bind(cid, RTTask.Action.Update, this, new UpdateBGTaskOnEvent(cid));
            } while (c.moveToNext());
        }
        c.close();

        if (null == ab) {
            logW("ChannelListActivity : ab(action bar) is NULL");
            ab = getActionBar();
        }
        // Database data may be changed.
        // So refresh all list
        for (int i = 0; i < ab.getTabCount(); i++)
            // 'notifyDataSetChanged' doesn't lead to refreshing channel row info
            //   in case of database is changed!
            refreshList(ab.getTabAt(i));
    }

    @Override
    protected void
    onResume() {
        super.onResume();
        logI("==> ChannelListActivity : onResume");
        uilc.onResume();
    }

    // NOTE
    // Why 'onPostResume + delay(100ms)' is used to start real-UI-jobs?
    // Before starting real-UI-job (time-consuming-job), first screen - sign of life - should be shown to user.
    // But, in Android, there is no callback that is called just after first draw.
    // (Sing-of-life-screen doesn't shown if below 'handler.post' is triggered in onResume.)
    // 'onWindowFocusChanged' is called at very early stage of Activity Lifecycle and it is called after first draw
    // (There is no documented manual about this. But, it is just experimental result.)
    // Because of delay, sometimes this may not work as expected if system is very heavy loaded.
    // Not best place, but fair enough - 100 ms is enough large in most case!
    @Override
    protected void
    onPostResume() {
        super.onPostResume();
        logI("==> ChannelListActivity : onPostResume()");
        uilc.getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                uilc.triggerDelayedNextState();
            }
        }, 100);
    }

    @Override
    public void
    onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

    }

    @Override
    public void
    onUIPause(View contentv) {
        logI("==> ChannelListActivity : onUIPause");

        RTTask.S().unregisterManagerEventListener(this);
        // Why This should be here (NOT 'onStop'!)
        // In normal case, starting 'ItemListAcvitiy' issues 'onStop'.
        // And when exiting from 'ItemListActivity' by back-key event, 'onStart' is called.
        // But, during updating - there is background thread  - 'onResume' and 'onCancel' are called
        //   instead of 'onStart' and 'onStop'.
        // That is, if there is background running background thread, activity is NOT stopped but just paused.
        // (This is experimental conclusion - NOT by analyzing framework source code.)
        // I think this is Android's bug or implicit policy.
        // Because of above issue, 'binding' and 'unbinding' are done at 'onResume' and 'onPause'.
        RTTask.S().unbind(this);
    }

    @Override
    protected void
    onPause() {
        logI("==> ChannelListActivity : onPause");
        uilc.onPause();
        super.onPause();
    }

    @Override
    public void
    onUIStop(View contentv) {
        logI("==> ChannelListActivity : onUIStop");
    }

    @Override
    protected void
    onStop() {
        logI("==> ChannelListActivity : onStop");
        uilc.onStop();
        super.onStop();
    }

    @Override
    public void
    onUIDestroy(View contentv) {
        logI("==> ChannelListActivity : onUIDestroy");
        for (int i = 0; i < ab.getTabCount(); i++)
            getListAdapter(ab.getTabAt(i)).getCursor().close();
    }

    @Override
    protected void
    onDestroy() {
        logI("==> ChannelListActivity : onDestroy");
        uilc.onDestroy();
        super.onDestroy();
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }
}
