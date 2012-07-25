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
    private static final int DATA_REQ_SZ    = 20;
    private static final int DATA_ARR_MAX   = 500;

    private static final int REQC_ITEM_VIEW = 0;

    // Keys for extra value of intent : IKey (Intent Key)
    public static final String IKEY_MODE    = "mode";  // mode
    public static final String IKEY_FILTER  = "filter";// filter

    // Option flags

    // bit[0:2] mode : scope of items to list
    // others are reserved (ex. ALL or ALL_LINK or ALL_ENCLOSURE)
    // Base query outline to create cursor, depends on this Mode value.
    public static final int MODE_CHANNEL    = 0; // items of channel
    public static final int MODE_CATEGORY   = 1; // items of category
    public static final int MODE_FAVORITE   = 2; // favorite items
    public static final int MODE_ALL        = 3; // all items

    // bit[3:7] filter : select items
    // others are reserved (ex. filtering items with time (specific period of time) etc)
    // Filtering from cursor depends on this value.
    public static final int FILTER_NONE     = 0; // no filter
    public static final int FILTER_NEW      = 1; // new items of each channel.

    private final UIPolicy      uip = UIPolicy.get();
    private final DBPolicy      dbp = DBPolicy.get();
    private final RTTask        rtt = RTTask.get();
    private final LookAndFeel   lnf = LookAndFeel.get();

    private OpMode      opMode  = null;
    private ListView    list    = null;

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
        long[]  getCids()  { return new long[0]; }
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

        ItemListAdapter.OnActionListener
        getAdapterActionHandler() {
            return new ItemListAdapter.OnActionListener() {
                @Override
                public void onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state) {
                    // Toggle Favorite bit.
                    state = state ^ Feed.Item.MSTAT_FAV;
                    dbp.updateItemAsync_state(id, state);
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

        @Override
        long[] getCids() {
            return new long[] { cid };
        }

        @Override
        void onCreate() {
            super.onCreate();
            setTitle(dbp.getChannelInfoString(cid, DB.ColumnChannel.TITLE));
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
            dbp.updateChannel_lastItemId(cid);
        }

        @Override
        void onResume() {
            super.onResume();
            // See comments in 'ChannelListActivity.onPause()'
            // Bind update task if needed
            RTTask.TaskState state = rtt.getState(cid, RTTask.Action.UPDATE);
            if (RTTask.TaskState.IDLE != state)
                rtt.bind(cid, RTTask.Action.UPDATE, this, new UpdateBGTaskListener(cid));

            // Bind downloading tasks
            long[] ids = rtt.getItemsDownloading(cid);
            for (long id : ids)
                rtt.bind(id, RTTask.Action.DOWNLOAD, this, new DownloadDataBGTaskListener(id));

            setUpdateButton();
        }

        @Override
        Cursor query() {
            return dbp.queryItem(cid, queryProjection, search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return dbp.getItemMinPubtime(cid);
        }
    }

    private class OpModeCategory extends OpMode {
        private long categoryid; // category id
        private long[] cids = null;

        OpModeCategory(Intent intent) {
            categoryid = intent.getLongExtra("categoryid", -1);
            eAssert(-1 != categoryid);
            cids = dbp.getChannelIds(categoryid);
        }

        @Override
        long[] getCids() {
            return cids;
        }

        @Override
        void onCreate() {
            super.onCreate();
            setTitle(getResources().getString(R.string.category) + ":" + dbp.getCategoryName(categoryid));
            bgtaskRunning = true;
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        dbp.getDelayedChannelUpdate();
                        dbp.updateChannel_lastItemIds(cids);
                    } finally {
                        dbp.putDelayedChannelUpdate();
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
                RTTask.TaskState state = rtt.getState(cid, RTTask.Action.UPDATE);
                if (RTTask.TaskState.IDLE != state)
                    rtt.bind(cid, RTTask.Action.UPDATE, this, new UpdateBGTaskListener(cid));
            }

            long[] ids = rtt.getItemsDownloading(cids);
            for (long id : ids)
                rtt.bind(id, RTTask.Action.DOWNLOAD, this, new DownloadDataBGTaskListener(id));
        }

        @Override
        Cursor query() {
            return dbp.queryItem(cids, queryProjection, search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return dbp.getItemMinPubtime(cids);
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
            long[] ids = rtt.getItemsDownloading();
            try {
                dbp.getDelayedChannelUpdate();
                for (long id : ids)
                    if (Feed.Item.isStatFavOn(dbp.getItemInfoLong(id, DB.ColumnItem.STATE)))
                        rtt.bind(id, RTTask.Action.DOWNLOAD, this, new DownloadDataBGTaskListener(id));
            } finally {
                dbp.putDelayedChannelUpdate();
            }
        }

        @Override
        ItemListAdapter.OnActionListener
        getAdapterActionHandler() {
            return new ItemListAdapter.OnActionListener() {
                @Override
                public void onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state) {
                    // Toggle Favorite bit.
                    state = state ^ Feed.Item.MSTAT_FAV;
                    dbp.updateItemAsync_state(id, state);
                    eAssert(!Feed.Item.isStatFavOn(state));
                    adapter.removeItem(position);
                    dataSetChanged();
                }
            };
        }

        @Override
        Cursor query() {
            return dbp.queryItemMask(queryProjection, DB.ColumnItem.STATE,
                                    Feed.Item.MSTAT_FAV, Feed.Item.FSTAT_FAV_ON,
                                    search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return dbp.getItemMinPubtime(DB.ColumnItem.STATE,
                                        Feed.Item.MSTAT_FAV,
                                        Feed.Item.FSTAT_FAV_ON);
        }
    }

    private class OpModeAll extends OpMode {
        private long[] cids = null;

        OpModeAll(Intent intent) {
            cids = dbp.getChannelIds();
        }

        @Override
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
                    try {
                        dbp.getDelayedChannelUpdate();
                        dbp.updateChannel_lastItemIds(cids);
                    } finally {
                        dbp.putDelayedChannelUpdate();
                        bgtaskRunning = false;
                    }
                }
            });

        }

        @Override
        void onResume() {
            super.onResume();
            // Bind downloading tasks
            long[] ids = rtt.getItemsDownloading();
            for (long id : ids)
                rtt.bind(id, RTTask.Action.DOWNLOAD, this, new DownloadDataBGTaskListener(id));
        }

        @Override
        Cursor query() {
            return dbp.queryItem(queryProjection, search, fromPubtime, toPubtime);
        }

        @Override
        long minPubtime() {
            return dbp.getItemMinPubtime();
        }
    }

    public class UpdateBGTaskListener implements BGTask.OnEventListener<Long, Object> {
        private long chid = -1;
        UpdateBGTaskListener(long chid) {
            this.chid = chid;
        }

        @Override
        public void
        onProgress(BGTask task, long progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
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

            if (Err.NO_ERR == result) {
                // Update "OLDLAST_ITEMID"
                // User already know that there is new feed because
                //   user starts to update in feed screen!.
                // So, we can assume that user knows latest updates here.
                dbp.updateChannel_lastItemId(chid);
                refreshListAsync();
            }
        }
    }

    private class DownloadDataBGTaskListener implements BGTask.OnEventListener<Object, Object> {
        private long id = -1;
        DownloadDataBGTaskListener(long id) {
            this.id = id;
        }

        @Override
        public void
        onProgress(BGTask task, long progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
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
            // See comments at "ItemListActivity.UpdateBGTaskListener.OnPostRun"
            if (isActivityFinishing())
                return;

            if (Err.NO_ERR == result)
                getListAdapter().updateItemHasDnFile(getListAdapter().findPosition(id), true);

            dataSetChanged(id);
        }
    }

    private class RTTaskRegisterListener implements RTTask.OnRegisterListener {
        @Override
        public void
        onRegister(BGTask task, long id, RTTask.Action act) {
            if (RTTask.Action.UPDATE == act)
                rtt.bind(id, act, ItemListActivity.this, new UpdateBGTaskListener(id));
            else if (RTTask.Action.DOWNLOAD == act)
                rtt.bind(id, act, ItemListActivity.this, new DownloadDataBGTaskListener(id));
        }

        @Override
        public void onUnregister(BGTask task, long id, RTTask.Action act) { }
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
        getListAdapter().reloadDataSetAsync(null);
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
                rtt.cancel(opMode.getCids()[0], RTTask.Action.UPDATE, null);
                requestSetUpdateButton();
            }
        });
    }

    private void
    setOnClick_notification(final ImageView btn, final int msg) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lnf.showTextToast(ItemListActivity.this, msg);
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
                lnf.showTextToast(ItemListActivity.this, result.getMsgId());
                rtt.consumeResult(opMode.getCids()[0], RTTask.Action.UPDATE);
                requestSetUpdateButton();
            }
        });
    }

    private boolean
    changeItemState_opened(long id, int position) {
        // change state as 'opened' at this moment.
        long state = dbp.getItemInfoLong(id, DB.ColumnItem.STATE);
        if (Feed.Item.isStateOpenNew(state)) {
            state = Utils.bitSet(state, Feed.Item.FSTAT_OPEN_OPENED, Feed.Item.MSTAT_OPEN);
            dbp.updateItemAsync_state(id, state);
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
        long cid = opMode.getCids()[0];
        BGTaskUpdateChannel updateTask = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid));
        rtt.register(cid, RTTask.Action.UPDATE, updateTask);
        rtt.bind(cid, RTTask.Action.UPDATE, this, new UpdateBGTaskListener(cid));
        rtt.start(cid, RTTask.Action.UPDATE);
    }

    private void
    onActionOpen_http(long action, View view, long id, int position, String url, String protocol) {
        RTTask.TaskState state = rtt.getState(id, RTTask.Action.DOWNLOAD);
        if (RTTask.TaskState.FAILED == state) {
            lnf.showTextToast(this, rtt.getErr(id, RTTask.Action.DOWNLOAD).getMsgId());
            rtt.consumeResult(id, RTTask.Action.DOWNLOAD);
            dataSetChanged(id);
            return;
        }

        if (Feed.Channel.isActProgIn(action)) {
            Intent intent = new Intent(this, ItemViewActivity.class);
            intent.putExtra("id", id);
            startActivityForResult(intent, REQC_ITEM_VIEW);
        } else if (Feed.Channel.isActProgEx(action)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                lnf.showTextToast(this,
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
            lnf.showTextToast(this,
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
        File f = dbp.getItemInfoDataFile(id);
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
                lnf.showTextToast(this,
                        getResources().getText(R.string.warn_find_app_to_open).toString() + " [" + type + "]");
                return;
            }
            // change state as 'opened' at this moment.
            changeItemState_opened(id, position);
        } else {
            RTTask.TaskState state = rtt.getState(id, RTTask.Action.DOWNLOAD);
            switch(state) {
            case IDLE: {
                BGTaskDownloadToFile.Arg arg
                    = new BGTaskDownloadToFile.Arg(url, f, uip.getNewTempFile());
                BGTaskDownloadToFile dnTask = new BGTaskDownloadToFile(arg);
                rtt.register(id, RTTask.Action.DOWNLOAD, dnTask);
                rtt.start(id, RTTask.Action.DOWNLOAD);
                dataSetChanged(id);
            } break;

            case RUNNING:
            case READY:
                rtt.cancel(id, RTTask.Action.DOWNLOAD, null);
                dataSetChanged(id);
                break;

            case CANCELING:
                lnf.showTextToast(this, R.string.wait_cancel);
                break;

            case FAILED: {
                Err result = rtt.getErr(id, RTTask.Action.DOWNLOAD);
                lnf.showTextToast(this, result.getMsgId());
                rtt.consumeResult(id, RTTask.Action.DOWNLOAD);
                dataSetChanged(id);
            } break;

            default:
                eAssert(false);
            }
        }
    }

    private void
    onAction(long action, View view, long id, final int position) {
        // NOTE
        // This is very simple policy!
        long actionType = Feed.Channel.getActType(action);
        String link = getListAdapter().getItemInfo_link(position);
        String enclosure = getListAdapter().getItemInfo_encUrl(position);

        if (Feed.Channel.FACT_TYPE_DYNAMIC == actionType) {
            String url = uip.getDynamicActionTargetUrl(action, link, enclosure);
            // NOTE
            // Items that have both invalid link and enclosure url, SHOULD NOT be added to DB.
            // Parser give those away in parsing phase.
            // See RSSParser/AtomParser.
            eAssert(null != url);
            if (enclosure.equals(url))
                onActionDn(action, view, id, position, url);
            else
                onActionOpen(action, view, id, position, url);
        } else if (Feed.Channel.FACT_TYPE_EMBEDDED_MEDIA == actionType) {
            // In case of embedded media, external program should be used in force.
            action = Utils.bitSet(action, Feed.Channel.FACT_PROG_EX, Feed.Channel.MACT_PROG);
            onActionOpen(action, view, id, position, enclosure);
        } else
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

        long cid = opMode.getCids()[0];
        ImageView iv = (ImageView)bar.getCustomView().findViewById(R.id.update_button);
        Animation anim = iv.getAnimation();

        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        iv.setAlpha(1.0f);
        iv.setClickable(true);
        RTTask.TaskState state = rtt.getState(cid, RTTask.Action.UPDATE);
        switch(state) {
        case IDLE:
            iv.setImageResource(R.drawable.ic_refresh);
            setOnClick_startUpdate(iv);
            break;

        case READY:
            iv.setImageResource(R.drawable.ic_pause);
            setOnClick_cancelUpdate(iv);
            break;

        case RUNNING:
            iv.setImageResource(R.anim.download);
            ((AnimationDrawable)iv.getDrawable()).start();
            setOnClick_cancelUpdate(iv);
            break;

        case CANCELING:
            iv.setImageResource(R.drawable.ic_block);
            iv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_inout));
            iv.setClickable(false);
            setOnClick_notification(iv, R.string.wait_cancel);
            break;

        case FAILED: {
            iv.setImageResource(R.drawable.ic_info);
            Err result = rtt.getErr(cid, RTTask.Action.UPDATE);
            setOnClick_errResult(iv, result);
        } break;

        default:
            eAssert(false);
        }
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
                lnf.createWarningDialog(this,
                                        R.string.delete_downloaded_file,
                                        R.string.delete_downloaded_file_msg);
        dialog.setButton(getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                // NOTE
                // There is no use case that "null == f" here.
                File f = dbp.getItemInfoDataFile(id);
                eAssert(null != f);
                if (!f.delete())
                    lnf.showTextToast(ItemListActivity.this, Err.IO_FILE.getMsgId());
                else {
                    getListAdapter().updateItemHasDnFile(getListAdapter().findPosition(id), false);
                    dataSetChanged(id);
                }
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
        File f = dbp.getItemInfoDataFile(dbId);
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
            lnf.showTextToast(this, R.string.warn_no_item_before_now);
            return;
        }

        // Setup search dialog controls
        final View diagV  =  lnf.inflateLayout(this, R.layout.item_list_search);
        final AlertDialog diag =
                lnf.createEditTextDialog(this, diagV, R.string.feed_search);

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
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case REQC_ITEM_VIEW:
            if (resultCode == ItemViewActivity.RESULT_DOWNLOAD) {
                long id = data.getLongExtra("id", -1);
                if (id > 0) {
                    getListAdapter().updateItemHasDnFile(getListAdapter().findPosition(id), true);
                    dataSetChanged(id);
                }
            }
            break;

        default:
            eAssert(false);
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
        getActionBar().show();

        int mode = getIntent().getIntExtra(IKEY_MODE, -1);
        eAssert(-1 != mode);
        switch (mode) {
        case MODE_CHANNEL:
            opMode = new OpModeChannel(getIntent());
            break;
        case MODE_CATEGORY:
            opMode = new OpModeCategory(getIntent());
            break;
        case MODE_FAVORITE:
            opMode = new OpModeFavorite(getIntent());
            break;
        case MODE_ALL:
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
                long act = dbp.getChannelInfoLong(cid, DB.ColumnChannel.ACTION);
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
                                            DATA_REQ_SZ,
                                            DATA_ARR_MAX,
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
        rtt.registerRegisterEventListener(this, new RTTaskRegisterListener());
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
        if (dbp.isChannelWatcherRegistered(this))
            watchCids = dbp.getChannelWatcherUpdated(this);

        // default is 'full-refreshing'
        boolean itemTableWatcherUpdated = true;

        if (dbp.isItemTableWatcherRegistered(this))
            itemTableWatcherUpdated = dbp.isItemTableWatcherUpdated(this);

        dbp.unregisterChannelWatcher(this);
        dbp.unregisterItemTableWatcher(this);

        long[] cids = opMode.getCids();

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
        dbp.registerChannelWatcher(this);
        dbp.registerItemTableWatcher(this);
        // See comments in 'ChannelListActivity.onPause' around 'unregisterManagerEventListener'
        rtt.unregisterRegisterEventListener(this);
        // See comments in 'ChannelListActivity.onPause()'
        rtt.unbind(this);
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
            while (opMode.doesRunningBGTaskExists())
                Thread.sleep(50);
        } catch (InterruptedException e) {}
        super.onBackPressed();
    }
}
