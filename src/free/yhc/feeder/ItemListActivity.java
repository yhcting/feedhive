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

import java.io.File;
import java.util.Calendar;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
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
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskDownloadToFile;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;
public class ItemListActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    private static final int DataReqSz  = 20;
    private static final int DataArrMax = 500;

    // Keys for extra value of intent : IKey (Intent Key)
    public static final String IKeyMode    = "mode";  // mode
    public static final String IKeyFilter  = "filter";// filter

    // Option flags

    // bit[0:2] mode : scope of items to list
    // others are reserved (ex. ALL or ALL_LINK or ALL_ENCLOSURE)
    // Base query outline to create cursor, depends on this Mode value.
    public static final int ModeChannel       = 0; // items of channel
    public static final int ModeCategory      = 1; // items of category
    public static final int ModeFavorite      = 2; // favorite items
    public static final int ModeAll           = 3; // all items

    // bit[3:7] filter : select items
    // others are reserved (ex. filtering items with time (specific period of time) etc)
    // Filtering from cursor depends on this value.
    public static final int FilterNone        = 0; // no filter
    public static final int FilterNew         = 1; // new items of each channel.

    private final DBPolicy  db      = DBPolicy.S();
    private OpMode          opMode  = null;
    private ListView        list    = null;

    private class OpMode {
        // 'State' of item may be changed often dynamically (ex. when open item)
        // So, to synchronized cursor information with item state, we need to refresh cursor
        //   whenever item state is changed.
        // But it's big overhead.
        // So, in case STATE, it didn't included in list cursor, but read from DB if needed.
        protected final DB.ColumnItem[] queryProjection = new DB.ColumnItem[] {
                    DB.ColumnItem.ID, // Mandatory.
                    DB.ColumnItem.CHANNELID,
                    DB.ColumnItem.TITLE,
                    DB.ColumnItem.DESCRIPTION,
                    DB.ColumnItem.ENCLOSURE_LENGTH,
                    DB.ColumnItem.ENCLOSURE_URL,
                    DB.ColumnItem.ENCLOSURE_TYPE,
                    DB.ColumnItem.PUBDATE,
                    DB.ColumnItem.LINK };

        protected String search = "";
        protected long   fromPubtime = -1;
        protected long   toPubtime = -1;
        protected volatile boolean bgtaskRunning = false;

        void    onCreate() {
            getActionBar().setDisplayShowHomeEnabled(false);
        }

        void    onResume() {}
        Cursor  query()    { return null; }

        boolean doesRunningBGTaskExists() {
            return bgtaskRunning;
        }

        long    minPubtime() { return -1; }
        void    setFilter(String search, long fromPubtime, long toPubtime) {
            this.search = search;
            this.fromPubtime = fromPubtime;
            this.toPubtime = toPubtime;
        }

        boolean isFilterEnabled() {
            return !search.isEmpty()
                   || ((fromPubtime <= toPubtime) && (fromPubtime > 0));
        }

        ItemListAdapter.OnAction
        getAdapterActionHandler() {
            return new ItemListAdapter.OnAction() {
                @Override
                public void onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state) {
                    // Toggle Favorite bit.
                    state = state ^ Feed.Item.MStatFav;
                    DBPolicy.S().updateItemAsync_state(id, state);
                    adapter.updateItemState(position, state);
                    dataSetChanged();
                }
            };
        }
    }

    private class OpModeChannel extends OpMode {
        private long cid; // channel id

        OpModeChannel(Intent i) {
            cid = i.getLongExtra("cid", -1);
            eAssert(-1 != cid);
        }

        long getChannelId() {
            return cid;
        }

        @Override
        void onCreate() {
            super.onCreate();
            setTitle(db.getChannelInfoString(cid, DB.ColumnChannel.TITLE));
            // TODO
            //   How to use custom view + default option menu ???
            //
            // Set custom action bar
            ActionBar bar = getActionBar();
            LinearLayout abView = (LinearLayout)getLayoutInflater().inflate(R.layout.item_list_actionbar,null);
            bar.setCustomView(abView, new ActionBar.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    Gravity.RIGHT));

            int change = bar.getDisplayOptions() ^ ActionBar.DISPLAY_SHOW_CUSTOM;
            bar.setDisplayOptions(change, ActionBar.DISPLAY_SHOW_CUSTOM);
            // Update "OLDLAST_ITEMID" when user opens item views.
            DBPolicy.S().updateChannel_lastItemId(cid);
        }

        @Override
        void onResume() {
            super.onResume();
            // See comments in 'ChannelListActivity.onPause()'
            // Bind update task if needed
            RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
            if (RTTask.TaskState.Idle != state)
                RTTask.S().bind(cid, RTTask.Action.Update, this, new UpdateBGTaskOnEvent(cid));

            // Bind downloading tasks
            long[] ids = RTTask.S().getItemsDownloading(cid);
            for (long id : ids)
                RTTask.S().bind(id, RTTask.Action.Download, this, new DownloadDataBGTaskOnEvent(id));

            setUpdateButton();
        }

        @Override
        Cursor query() {
            return db.queryItem(cid, queryProjection, search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return db.getItemMinPubtime(cid);
        }
    }

    private class OpModeCategory extends OpMode {
        private long categoryid; // category id
        private long[] cids = null;

        OpModeCategory(Intent intent) {
            categoryid = intent.getLongExtra("categoryid", -1);
            eAssert(-1 != categoryid);
            cids = DBPolicy.S().getChannelIds(categoryid);
        }

        long getCategoryId() {
            return categoryid;
        }

        long[] getCids() {
            return cids;
        }

        @Override
        void onCreate() {
            super.onCreate();
            setTitle(getResources().getString(R.string.category) + ":" + db.getCategoryName(categoryid));
            bgtaskRunning = true;
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    Long[] whereValues = Utils.convertArraylongToLong(cids);
                    Long[] targetValues = new Long[whereValues.length];
                    DBPolicy.S().getDelayedChannelUpdate();
                    try {
                        for (int i = 0; i < whereValues.length; i++)
                            targetValues[i] = DBPolicy.S().getItemInfoMaxId(whereValues[i]);
                        DBPolicy.S().updateChannelSet(DB.ColumnChannel.OLDLAST_ITEMID, targetValues,
                                                      DB.ColumnChannel.ID, whereValues);
                    } finally {
                        DBPolicy.S().putDelayedChannelUpdate();
                        bgtaskRunning = false;
                    }
                }
            });
        }

        @Override
        void onResume() {
            super.onResume();
            // Bind update task if needed
            for (long cid : cids) {
                RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
                if (RTTask.TaskState.Idle != state)
                    RTTask.S().bind(cid, RTTask.Action.Update, this, new UpdateBGTaskOnEvent(cid));
            }

            long[] ids = RTTask.S().getItemsDownloading(cids);
            for (long id : ids)
                RTTask.S().bind(id, RTTask.Action.Download, this, new DownloadDataBGTaskOnEvent(id));
        }

        @Override
        Cursor query() {
            return db.queryItem(cids, queryProjection, search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return db.getItemMinPubtime(cids);
        }
    }

    private class OpModeFavorite extends OpMode {
        OpModeFavorite(Intent intent) {
        }

        @Override
        void onCreate() {
            super.onCreate();
            setTitle(getResources().getString(R.string.favorite_items));
        }

        @Override
        void onResume() {
            super.onResume();
            long[] ids = RTTask.S().getItemsDownloading();
            DBPolicy.S().getDelayedChannelUpdate();
            try {
                for (long id : ids)
                    if (Feed.Item.isStatFavOn(DBPolicy.S().getItemInfoLong(id, DB.ColumnItem.STATE)))
                        RTTask.S().bind(id, RTTask.Action.Download, this, new DownloadDataBGTaskOnEvent(id));
            } finally {
                DBPolicy.S().putDelayedChannelUpdate();
            }
        }

        @Override
        ItemListAdapter.OnAction
        getAdapterActionHandler() {
            return new ItemListAdapter.OnAction() {
                @Override
                public void onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state) {
                    // Toggle Favorite bit.
                    state = state ^ Feed.Item.MStatFav;
                    DBPolicy.S().updateItemAsync_state(id, state);
                    eAssert(!Feed.Item.isStatFavOn(state));
                    adapter.removeItem(position);
                    dataSetChanged();
                }
            };
        }

        @Override
        Cursor query() {
            return db.queryItemMask(queryProjection, DB.ColumnItem.STATE,
                                    Feed.Item.MStatFav, Feed.Item.FStatFavOn,
                                    search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return db.getItemMinPubtime(DB.ColumnItem.STATE,
                                        Feed.Item.MStatFav,
                                        Feed.Item.FStatFavOn);
        }
    }

    private class OpModeAll extends OpMode {
        private long[] cids = null;

        OpModeAll(Intent intent) {
            cids = DBPolicy.S().getChannelIds();
        }

        long[] getCids() {
            return cids;
        }

        @Override
        void onCreate() {
            super.onCreate();
            setTitle(getResources().getString(R.string.all_items));

            bgtaskRunning = true;
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    DBPolicy.S().getDelayedChannelUpdate();
                    try {
                        Long[] whereValues = Utils.convertArraylongToLong(cids);
                        Long[] targetValues = new Long[whereValues.length];
                        DBPolicy.S().getDelayedChannelUpdate();
                        for (int i = 0; i < whereValues.length; i++)
                            targetValues[i] = DBPolicy.S().getItemInfoMaxId(whereValues[i]);
                        DBPolicy.S().updateChannelSet(DB.ColumnChannel.OLDLAST_ITEMID, targetValues,
                                                      DB.ColumnChannel.ID, whereValues);
                    } finally {
                        DBPolicy.S().putDelayedChannelUpdate();
                        bgtaskRunning = false;
                    }
                }
            });

        }

        @Override
        void onResume() {
            super.onResume();
            // Bind downloading tasks
            long[] ids = RTTask.S().getItemsDownloading();
            for (long id : ids)
                RTTask.S().bind(id, RTTask.Action.Download, this, new DownloadDataBGTaskOnEvent(id));
        }

        @Override
        Cursor query() {
            return db.queryItem(queryProjection, search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return db.getItemMinPubtime();
        }
    }

    public class UpdateBGTaskOnEvent implements BGTask.OnEvent<Long, Object> {
        private long chid = -1;
        UpdateBGTaskOnEvent(long chid) {
            this.chid = chid;
        }

        @Override
        public void
        onProgress(BGTask task, long progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskOnEvent.OnPostRun"
            if (isActivityFinishing())
                return;

            requestSetUpdateButton();
        }

        @Override
        public void
        onPreRun(BGTask task) {
            requestSetUpdateButton();
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
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

            if (Err.NoErr == result) {
                // Update "OLDLAST_ITEMID"
                // User already know that there is new feed because
                //   user starts to update in feed screen!.
                // So, we can assume that user knows latest updates here.
                DBPolicy.S().updateChannel_lastItemId(chid);
                refreshListAsync();
            }
        }
    }

    private class DownloadDataBGTaskOnEvent implements
    BGTask.OnEvent<Object, Object> {
        private long id = -1;
        DownloadDataBGTaskOnEvent(long id) {
            this.id = id;
        }

        @Override
        public void
        onProgress(BGTask task, long progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskOnEvent.OnPostRun"
            if (isActivityFinishing())
                return;

            dataSetChanged(id);
        }

        @Override
        public void
        onPreRun(BGTask task) {
            // icon should be changed from 'ready' to 'running'
            dataSetChanged(id);
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            //logI("+++ Item Activity DownloadData PostRun");
            // See comments at "ItemListActivity.UpdateBGTaskOnEvent.OnPostRun"
            if (isActivityFinishing())
                return;

            dataSetChanged(id);
        }
    }

    private class RTTaskManagerEventHandler implements RTTask.OnRTTaskManagerEvent {
        @Override
        public void
        onBGTaskRegister(long id, BGTask task, RTTask.Action act) {
            if (RTTask.Action.Update == act)
                RTTask.S().bind(id, act, ItemListActivity.this, new UpdateBGTaskOnEvent(id));
            else if (RTTask.Action.Download == act)
                RTTask.S().bind(id, act, ItemListActivity.this, new DownloadDataBGTaskOnEvent(id));
        }

        @Override
        public void onBGTaskUnregister(long id, BGTask task, RTTask.Action act) { }
    }

    private boolean
    isActivityFinishing() {
        return isFinishing();
    }

    private ItemListAdapter
    getListAdapter() {
        eAssert(null != list);
        return (ItemListAdapter)list.getAdapter();
    }

    private void
    dataSetChanged() {
        if (null == list || null == getListAdapter())
            return;

        //getListAdapter().clearChangeState();
        getListAdapter().notifyDataSetChanged();
    }

    private void
    dataSetChanged(long id) {
        if (null == list || null == getListAdapter())
            return;

        ItemListAdapter la = getListAdapter();
        /*
        la.clearChangeState();
        for (int i = list.getFirstVisiblePosition();
             i <= list.getLastVisiblePosition();
             i++) {
            long itemId = la.getItem(i).id;
            if (itemId == id)
                la.addChanged(la.getItemId(i));
            else
                la.addUnchanged(la.getItemId(i));
        }
        */
        la.notifyDataSetChanged();
    }

    private void
    refreshList(long id) {
        if (null == list || null == getListAdapter())
            return;

        Cursor newCursor = opMode.query();
        getListAdapter().changeCursor(newCursor);
        getListAdapter().reloadItem(getListAdapter().findItemId(id));
    }

    private void
    refreshListAsync() {
        if (null == list || null == getListAdapter())
            return;

        // [ NOTE ]
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = opMode.query();
        getListAdapter().changeCursor(newCursor);
        getListAdapter().reloadDataSetAsync();
    }

    private void
    setOnClick_startUpdate(final ImageView btn) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ItemListActivity.this.updateItems();
                requestSetUpdateButton();
            }
        });
    }

    private void
    setOnClick_cancelUpdate(final ImageView btn) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Update is supported only at channel item list.
                eAssert(opMode instanceof OpModeChannel);
                RTTask.S().cancel(((OpModeChannel)opMode).getChannelId(), RTTask.Action.Update, null);
                requestSetUpdateButton();
            }
        });
    }

    private void
    setOnClick_notification(final ImageView btn, final int msg) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LookAndFeel.showTextToast(ItemListActivity.this, msg);
            }
        });
    }

    private void
    setOnClick_errResult(final ImageView btn, final Err result) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Update is supported only at channel item list.
                eAssert(opMode instanceof OpModeChannel);
                LookAndFeel.showTextToast(ItemListActivity.this, result.getMsgId());
                RTTask.S().consumeResult(((OpModeChannel)opMode).getChannelId(), RTTask.Action.Update);
                requestSetUpdateButton();
            }
        });
    }

    private boolean
    changeItemState_opened(long id, int position) {
        // change state as 'opened' at this moment.
        long state = db.getItemInfoLong(id, DB.ColumnItem.STATE);
        if (Feed.Item.isStateOpenNew(state)) {
            state = Utils.bitSet(state, Feed.Item.FStatOpenOpened, Feed.Item.MStatOpen);
            db.updateItemAsync_state(id, state);
            getListAdapter().updateItemState(position, state);
            dataSetChanged(id);
            return true;
        }
        return false;
    }

    private void
    updateItems() {
        // Update is supported only at channel item list.
        eAssert(opMode instanceof OpModeChannel);
        long cid = ((OpModeChannel)opMode).getChannelId();
        BGTaskUpdateChannel updateTask = new BGTaskUpdateChannel(this, new BGTaskUpdateChannel.Arg(cid));
        RTTask.S().register(cid, RTTask.Action.Update, updateTask);
        RTTask.S().bind(cid, RTTask.Action.Update, this, new UpdateBGTaskOnEvent(cid));
        RTTask.S().start(cid, RTTask.Action.Update);
    }

    private void
    onActionOpen_http(long action, View view, long id, int position, String url, String protocol) {
        RTTask.TaskState state = RTTask.S().getState(id, RTTask.Action.Download);
        if (RTTask.TaskState.Failed == state) {
            LookAndFeel.showTextToast(this, RTTask.S().getErr(id, RTTask.Action.Download).getMsgId());
            RTTask.S().consumeResult(id, RTTask.Action.Download);
            dataSetChanged(id);
            return;
        }

        if (Feed.Channel.isActProgIn(action)) {
            Intent intent = new Intent(this, ItemViewActivity.class);
            intent.putExtra("id", id);
            startActivity(intent);
        } else if (Feed.Channel.isActProgEx(action)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                LookAndFeel.showTextToast(this,
                        getResources().getText(R.string.warn_find_app_to_open).toString() + protocol);
                return;
            }
        } else
            eAssert(false);

        changeItemState_opened(id, position);
    }

    private void
    onActionOpen_rtsp(long action, View view, long id, int position, String url, String protocol) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            LookAndFeel.showTextToast(this,
                    getResources().getText(R.string.warn_find_app_to_open).toString() + protocol);
            return;
        }

        changeItemState_opened(id, position);
    }

    private void
    onActionOpen(long action, View view, long id, int position, String url) {
        String protocol = url.substring(0, url.indexOf("://"));
        if (protocol.equalsIgnoreCase("rtsp"))
            onActionOpen_rtsp(action, view, id, position, url, protocol);
        else // default : handle as http
            onActionOpen_http(action, view, id, position, url, protocol);
    }

    private void
    onActionDn(long action, View view, long id, int position, String url) {
        // 'enclosure' is used.
        File f = UIPolicy.getItemDataFile(id);
        eAssert(null != f);
        if (f.exists()) {
            // "RSS described media type" vs "mime type by guessing from file extention".
            // Experimentally, later is more accurate! (lots of RSS doesn't care about describing exact media type.)
            String type = Utils.guessMimeTypeFromUrl(url);
            if (null == type)
                type = getListAdapter().getItemInfo_encType(position);

            if (!Utils.isMimeType(type))
                type = "text/plain"; // this is default.


            // File is already exists. Do action with it!
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (null == type)
                intent.setData(Uri.fromFile(f));
            else
                intent.setDataAndType(Uri.fromFile(f), type);

            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                LookAndFeel.showTextToast(this,
                        getResources().getText(R.string.warn_find_app_to_open).toString() + " [" + type + "]");
                return;
            }
            // change state as 'opened' at this moment.
            changeItemState_opened(id, position);
        } else {
            RTTask.TaskState state = RTTask.S().getState(id, RTTask.Action.Download);
            if (RTTask.TaskState.Idle == state) {
                BGTaskDownloadToFile dnTask
                    = new BGTaskDownloadToFile(this, new BGTaskDownloadToFile.Arg(url,
                                                                                  f,
                                                                                  UIPolicy.getNewTempFile()));
                RTTask.S().register(id, RTTask.Action.Download, dnTask);
                RTTask.S().start(id, RTTask.Action.Download);
                dataSetChanged(id);
            } else if (RTTask.TaskState.Running == state
                       || RTTask.TaskState.Ready == state) {
                RTTask.S().cancel(id, RTTask.Action.Download, null);
                dataSetChanged(id);
            } else if (RTTask.TaskState.Canceling == state) {
                LookAndFeel.showTextToast(this, R.string.wait_cancel);
            } else if (RTTask.TaskState.Failed == state) {
                Err result = RTTask.S().getErr(id, RTTask.Action.Download);
                LookAndFeel.showTextToast(this, result.getMsgId());
                RTTask.S().consumeResult(id, RTTask.Action.Download);
                dataSetChanged(id);
            } else
                eAssert(false);
        }
    }

    private void
    onAction(long action, View view, long id, final int position) {
        // NOTE
        // This is very simple policy!
        String url = null;
        if (Feed.Channel.isActTgtLink(action))
            url = getListAdapter().getItemInfo_link(position);
        else if (Feed.Channel.isActTgtEnclosure(action))
            url = getListAdapter().getItemInfo_encUrl(position);
        else
            eAssert(false);

        if (Feed.Channel.isActOpOpen(action))
            onActionOpen(action, view, id, position, url);
        else if (Feed.Channel.isActOpDn(action))
            onActionDn(action, view, id, position, url);
        else
            eAssert(false);
    }

    private void
    setUpdateButton() {
        // Update is supported only at channel item list.
        if (!(opMode instanceof OpModeChannel))
            return;

        ActionBar bar = getActionBar();
        if (null == bar.getCustomView())
            return; // action bar is not initialized yet.

        long cid = ((OpModeChannel)opMode).getChannelId();
        ImageView iv = (ImageView)bar.getCustomView().findViewById(R.id.update_button);
        Animation anim = iv.getAnimation();

        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        iv.setAlpha(1.0f);
        iv.setClickable(true);
        RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
        if (RTTask.TaskState.Idle == state) {
            iv.setImageResource(R.drawable.ic_refresh);
            setOnClick_startUpdate(iv);
        } else if (RTTask.TaskState.Ready == state) {
            iv.setImageResource(R.drawable.ic_pause);
            setOnClick_cancelUpdate(iv);
        }else if (RTTask.TaskState.Running == state) {
            iv.setImageResource(R.anim.download);
            ((AnimationDrawable)iv.getDrawable()).start();
            setOnClick_cancelUpdate(iv);
        } else if (RTTask.TaskState.Canceling == state) {
            iv.setImageResource(R.drawable.ic_block);
            iv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_inout));
            iv.setClickable(false);
            setOnClick_notification(iv, R.string.wait_cancel);
        } else if (RTTask.TaskState.Failed == state) {
            iv.setImageResource(R.drawable.ic_info);
            Err result = RTTask.S().getErr(cid, RTTask.Action.Update);
            setOnClick_errResult(iv, result);
        } else
            eAssert(false);
    }

    private void
    requestSetUpdateButton() {
        Utils.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                setUpdateButton();
            }
        });
    }

    private void
    onContext_deleteDnFile(final long id, final int position) {
        // Create "Enter Url" dialog
        AlertDialog dialog =
                LookAndFeel.createWarningDialog(this,
                                                R.string.delete_downloaded_file,
                                                R.string.delete_downloaded_file_msg);
        dialog.setButton(getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                if (!UIPolicy.getItemDataFile(id).delete())
                    LookAndFeel.showTextToast(ItemListActivity.this, Err.IOFile.getMsgId());
                else
                    dataSetChanged(id);

                dialog.dismiss();
            }
        });
        dialog.setButton2(getResources().getText(R.string.no),
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
        long dbId = getListAdapter().getItemInfo_id(info.position);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_context, menu);

        // check for "Delete Downloaded File" option
        if (UIPolicy.getItemDataFile(dbId).exists())
            menu.findItem(R.id.delete_dnfile).setVisible(true);
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem mItem) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)mItem.getMenuInfo();
        long dbId = getListAdapter().getItemInfo_id(info.position);
        switch (mItem.getItemId()) {
        case R.id.delete_dnfile:
            //logI(" Delete Downloaded File : ID : " + dbId + " / " + info.position);
            onContext_deleteDnFile(dbId, info.position);
            return true;
        case R.id.mark_unopened:
            //logI(" Mark As Unactioned : ID : " + dbId + " / " + info.position);
            return true;
        }
        return false;
    }

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        }
        return true;
    }

    /**
     * build string array of numbers. (from <= x < to)
     * @param from
     * @param to
     *   exclusive
     * @return
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
            = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, entries);
        sp.setAdapter(spinnerArrayAdapter);
        sp.setSelection(selectpos);
        if (null != selectedListener)
            sp.setOnItemSelectedListener(selectedListener);
    }

    private void
    onSearchBtnClick() {
        // Empty list SHOULD not have visible-search button.
        // I can skip testing return value of opMode.minPubtime()
        final Calendar since = Calendar.getInstance();
        since.setTimeInMillis(opMode.minPubtime());
        final Calendar now = Calendar.getInstance();
        if (now.getTimeInMillis() < since.getTimeInMillis()) {
            LookAndFeel.showTextToast(this, R.string.warn_no_item_before_now);
            return;
        }

        // Setup search dialog controls
        final View diagV  =  LookAndFeel.inflateLayout(this, R.layout.item_list_search);
        final AlertDialog diag =
                LookAndFeel.createEditTextDialog(this, diagV, R.string.feed_search);

        String[] years = buildStringArray(since.get(Calendar.YEAR), now.get(Calendar.YEAR) + 1);
        setSpinner(diagV, R.id.sp_year0, years, 0, new AdapterView.OnItemSelectedListener() {
            @Override
            public void
            onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] mons = Utils.getMonths(since, now, since.get(Calendar.YEAR) + position);
                String[] months = buildStringArray(mons[0], mons[1] + 1);
                setSpinner(diagV, R.id.sp_month0, months, 0, null); // select first (eariest) month
            }
            @Override
            public void
            onNothingSelected(AdapterView<?> parent) {}
        }); // select first (eariest) year

        int[] mons = Utils.getMonths(since, now, since.get(Calendar.YEAR));
        String[] months = buildStringArray(mons[0], mons[1] + 1);
        setSpinner(diagV, R.id.sp_month0, months, 0, null); // select first (eariest) month

        setSpinner(diagV, R.id.sp_year1, years, years.length - 1, new AdapterView.OnItemSelectedListener() {
            @Override
            public void
            onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] mons = Utils.getMonths(since, now, since.get(Calendar.YEAR) + position);
                String[] months = buildStringArray(mons[0], mons[1] + 1);
                setSpinner(diagV, R.id.sp_month1, months, months.length - 1, null); // select first (eariest) month
            }
            @Override
            public void
            onNothingSelected(AdapterView<?> parent) {}
        }); // select last (latest) year
        mons = Utils.getMonths(since, now, now.get(Calendar.YEAR));
        months = buildStringArray(mons[0], mons[1] + 1);
        setSpinner(diagV, R.id.sp_month1, months, months.length - 1, null); // select last (latest) month

        diag.setButton(getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dia, int which) {
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
                from.set(Calendar.MONDAY, Utils.monthToCalendarMonth(m0));
                from.set(Calendar.DAY_OF_MONTH, from.getMinimum(Calendar.DAY_OF_MONTH));
                from.set(Calendar.HOUR_OF_DAY, from.getMinimum(Calendar.HOUR_OF_DAY));
                from.set(Calendar.MINUTE, 0);
                from.set(Calendar.SECOND, 0);

                // Set as max value.
                to.set(Calendar.YEAR, y1);
                to.set(Calendar.MONDAY, Utils.monthToCalendarMonth(m1));
                to.set(Calendar.DAY_OF_MONTH, to.getMaximum(Calendar.DAY_OF_MONTH));
                to.set(Calendar.HOUR_OF_DAY, to.getMaximum(Calendar.HOUR_OF_DAY));
                to.set(Calendar.MINUTE, 59);
                to.set(Calendar.SECOND, 59);

                diag.setTitle(R.string.plz_wait);
                opMode.setFilter(search, from.getTimeInMillis(), to.getTimeInMillis());
                list.setSelection(0);
                getListAdapter().moveToFirstDataSet();
                Utils.getUiHandler().post(new Runnable() {
                   @Override
                   public void run() {
                       refreshListAsync();
                       diag.dismiss();
                   }
                });
            }
        });

        diag.setButton2(getResources().getText(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                diag.dismiss();
            }
        });

        diag.show();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ItemListActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.S().registerModule(this);
        super.onCreate(savedInstanceState);
        //logI("==> ItemListActivity : onCreate");
        getActionBar().show();

        int mode = getIntent().getIntExtra(IKeyMode, -1);
        eAssert(-1 != mode);
        switch (mode) {
        case ModeChannel:
            opMode = new OpModeChannel(getIntent());
            break;
        case ModeCategory:
            opMode = new OpModeCategory(getIntent());
            break;
        case ModeFavorite:
            opMode = new OpModeFavorite(getIntent());
            break;
        case ModeAll:
            opMode = new OpModeAll(getIntent());
            break;
        default:
            eAssert(false);
        }
        setContentView(R.layout.item_list);
        list = ((ListView)findViewById(R.id.list));
        eAssert(null != list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick (AdapterView<?> parent, View view, int position, long itemId) {
                long dbId = getListAdapter().getItemInfo_id(position);
                long cid = getListAdapter().getItemInfo_cid(position);
                long act = DBPolicy.S().getChannelInfoLong(cid, DB.ColumnChannel.ACTION);
                onAction(act, view, dbId, position);
            }
        });
        registerForContextMenu(list);

        ((ImageView)findViewById(R.id.searchbtn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                onSearchBtnClick();
            }
        });

        opMode.onCreate();

        list.setAdapter(new ItemListAdapter(ItemListActivity.this,
                                            opMode.query(),
                                            R.layout.item_row,
                                            list,
                                            DataReqSz,
                                            DataArrMax,
                                            opMode.getAdapterActionHandler()));
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
        RTTask.S().registerManagerEventListener(this, new RTTaskManagerEventHandler());
        View searchBtn = findViewById(R.id.searchbtn);
        if (getListAdapter().isEmpty())
            searchBtn.setVisibility(View.GONE);
        else
            searchBtn.setVisibility(View.VISIBLE);

        opMode.onResume();

        boolean fullRefresh = false;

        // NOTE
        // Check that whether list is needed to be fully refreshed or not.
        // How to check it!
        // If one of channel that are belongs to current item list, is changed
        //   and item table is changed, than I can decide that current viewing items are updated.
        long[] watchCids = new long[0];
        if (DBPolicy.S().isChannelWatcherRegistered(this))
            watchCids = DBPolicy.S().getChannelWatcherUpdated(this);

        // default is 'full-refreshing'
        boolean itemTableWatcherUpdated = true;

        if (DBPolicy.S().isItemTableWatcherRegistered(this))
            itemTableWatcherUpdated = DBPolicy.S().isItemTableWatcherUpdated(this);

        DBPolicy.S().unregisterChannelWatcher(this);
        DBPolicy.S().unregisterItemTableWatcher(this);

        long[] cids = new long[0];
        if (opMode instanceof OpModeChannel)
            cids = new long[] { ((OpModeChannel)opMode).getChannelId() };
        else if (opMode instanceof OpModeCategory)
            cids = ((OpModeCategory)opMode).getCids();
        else if (opMode instanceof OpModeAll)
            cids = ((OpModeAll)opMode).getCids();

        // Simple algorithm : nested loop because # of channel is small enough in most cases.
        boolean channelChanged = false;
        for (long wcid : watchCids) {
            for (long cid : cids)
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
        DBPolicy.S().registerChannelWatcher(this);
        DBPolicy.S().registerItemTableWatcher(this);
        // See comments in 'ChannelListActivity.onPause' around 'unregisterManagerEventListener'
        RTTask.S().unregisterManagerEventListener(this);
        // See comments in 'ChannelListActivity.onPause()'
        RTTask.S().unbind(this);
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
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    public boolean
    onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            try {
                while (opMode.doesRunningBGTaskExists())
                    Thread.sleep(50);
            } catch (InterruptedException e) {}
        }
        return super.onKeyDown(keyCode, event);
    }
}
