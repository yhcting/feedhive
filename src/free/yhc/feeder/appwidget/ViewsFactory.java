/*****************************************************************************
 *    Copyright (C) 2012, 2013 Younghyung Cho. <yhcting77@gmail.com>
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

package free.yhc.feeder.appwidget;

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.database.Cursor;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import free.yhc.feeder.R;
import free.yhc.feeder.UiHelper;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BaseBGTask;
import free.yhc.feeder.model.ContentsManager;
import free.yhc.feeder.model.Environ;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.ItemActionHandler;
import free.yhc.feeder.model.ListenerManager;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ViewsFactory implements
RemoteViewsService.RemoteViewsFactory,
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ViewsFactory.class);

    private static final int    COLI_ID                 = 0;
    private static final int    COLI_CHANNELID          = 1;
    private static final int    COLI_TITLE              = 2;
    private static final int    COLI_DESCRIPTION        = 3;
    private static final int    COLI_ENCLOSURE_LENGTH   = 4;
    private static final int    COLI_ENCLOSURE_URL      = 5;
    private static final int    COLI_ENCLOSURE_TYPE     = 6;
    private static final int    COLI_PUBDATE            = 7;
    private static final int    COLI_LINK               = 8;

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
    private final RTTask    mRtt = RTTask.get();
    private final int       mAppWidgetId;
    private final DBWatcher         mDbWatcher;
    private final ItemActionHandler mItemAction;

    private long    mCategoryId;
    private long[]  mCids = null;
    private Cursor  mCursor = null;
    private final Object mCursorLock = new Object();

    private class DBWatcher implements ListenerManager.Listener {
        private final HashSet<Long> _mCidSet = new HashSet<Long>();

        // See comment at 'onNotify'
        private AtomicReference<Boolean> _mClosed = new AtomicReference<Boolean>(false);

        private boolean
        isInCategory(long cid) {
            return _mCidSet.contains(cid);
        }

        private boolean
        isInCategoryItem(long id) {
            long cid = mDbp.getItemInfoLong(id, ColumnItem.CHANNELID);
            return isInCategory(cid);
        }

        void
        register() {
            mDbp.registerUpdatedListener(this, DB.UpdateType.CHANNEL_DATA.flag()
                                               | DB.UpdateType.CHANNEL_TABLE.flag());
            mDbp.registerChannelUpdatedListener(this, this);
            mCm.registerUpdatedListener(this, ContentsManager.UpdateType.ITEM_DATA.flag()
                                              | ContentsManager.UpdateType.CHAN_DATA.flag()
                                              | ContentsManager.UpdateType.CHANS_DATA.flag());

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
                    if (DBG) P.v("onDbUpdate : " + ((DB.UpdateType)type).name() + " : "
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
                    eAssert(false);
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
                    assert(false);
                }
            } else if (type instanceof ContentsManager.UpdateType) {
                switch ((ContentsManager.UpdateType)type) {
                case ITEM_DATA:
                    if (isInCategoryItem((Long)arg0))
                        refreshItemList();
                    break;

                case CHAN_DATA:
                    if (isInCategory((Long)arg0))
                        refreshItemList();
                    break;

                case CHANS_DATA: {
                    boolean in = false;
                    for (long cid : (long[])arg0) {
                        in = isInCategory(cid);
                        if (in) {
                            refreshItemList();
                            break;
                        }
                    }
                } break;
                }
            }
        }
    }

    private class RTTaskRegisterListener implements RTTask.OnRegisterListener {
        @Override
        public void
        onRegister(BGTask task, long id, RTTask.Action act) {
            if (RTTask.Action.DOWNLOAD == act
                && isWidgetItem(id))
                mRtt.bind(id, act, ViewsFactory.this, new DownloadDataBGTaskListener(id));
        }

        @Override
        public void
        onUnregister(BGTask task, long id, RTTask.Action act) { }
    }

    private class DownloadDataBGTaskListener extends BaseBGTask.OnEventListener {
        private long _mId = -1;
        DownloadDataBGTaskListener(long id) {
            _mId = id;
        }

        @Override
        public void
        onCancelled(BaseBGTask task, Object param) {
            notifyDataSetChanged();
        }

        @Override
        public void
        onPreRun(BaseBGTask task) {
            // icon should be changed from 'ready' to 'running'
            notifyDataSetChanged();
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            notifyDataSetChanged();
        }
    }

    private class AdapterBridge implements ItemActionHandler.AdapterBridge {
        @Override
        public void
        updateItemState(int pos, long state) {
            // do nothing.
        }

        @Override
        public void
        dataSetChanged(long id) {
            notifyDataSetChanged();
        }
    }

    private Cursor
    getCursor() {
        mCids = mDbp.getChannelIds(mCategoryId);
        mDbWatcher.updateCategoryChannels(mCids);
        if (DBG) P.v("getCursor : Channels : " + Utils.nrsToNString(mCids));
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
        AppWidgetManager awm = AppWidgetManager.getInstance(Environ.getAppContext());
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
        mRtt.registerRegisterEventListener(this, new RTTaskRegisterListener());
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
        eAssert(Utils.isUiThread());
        if (DBG) P.v("OnItemClick : " + position);
        Long cidLong = mDbp.getItemInfoLong(id, ColumnItem.CHANNELID);
        if (null == cidLong) {
            // CASE (reproducing step)
            //   - Channel is deleted
            //   - App widget is NOT updated yet.
            //   - User select ALREADY-DELETED-ITEM
            UiHelper.showTextToast(Environ.getAppContext(), R.string.warn_bad_request);
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
        if (DBG) P.v("getLoadingView");
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
        RemoteViews rv = new RemoteViews(Environ.getAppContext().getPackageName(),
                                         R.layout.appwidget_row);
        rv.setTextViewText(R.id.channel, mDbp.getChannelInfoString(cid, ColumnChannel.TITLE));
        rv.setTextViewText(R.id.title, title);
        rv.setTextViewText(R.id.description, desc);


        File df = ContentsManager.get().getItemInfoDataFile(iid);
        if (DBG) P.v("getViewAt : " + position
                     + " : Path(" + df.exists() + ") ["
                     + ((null == df)? "<null>": df.getAbsolutePath()) + "]");
        if (null != df && df.exists()) {
            rv.setViewVisibility(R.id.image, View.VISIBLE);
            rv.setImageViewResource(R.id.image, R.drawable.ic_save);
        } else {
            RTTask.TaskState dnState = mRtt.getState(iid, RTTask.Action.DOWNLOAD);
            rv.setViewVisibility(R.id.image, View.VISIBLE);
            switch(dnState) {
            case IDLE:
                rv.setViewVisibility(R.id.image, View.GONE);
                break;

            case READY:
                rv.setImageViewResource(R.id.image, R.drawable.ic_pause);
                break;

            case RUNNING:
                rv.setImageViewResource(R.id.image, R.drawable.ic_refresh);
                break;

            case FAILED:
                rv.setImageViewResource(R.id.image, R.drawable.ic_info);
                break;

            case CANCELING:
                rv.setImageViewResource(R.id.image, R.drawable.ic_block);
                break;

            default:
                eAssert(false);
            }
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
        if (DBG) P.v("onCreate : " + mAppWidgetId + " / " + mCategoryId);
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
        Environ.getUiHandler().post(new Runnable() {
            @Override
            public void
            run() {
                mDbWatcher.unregister();
                mRtt.unregisterRegisterEventListener(this);
            }
        });
        if (DBG) P.v("onDestroy");
    }

    @Override
    protected void
    finalize() throws Throwable {
        super.finalize();
        if (null != mCursor)
            mCursor.close();
    }
}
