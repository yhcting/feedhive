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

package free.yhc.feeder.appwidget;

import android.content.Intent;
import android.database.Cursor;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import free.yhc.feeder.R;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ViewsFactory implements
RemoteViewsService.RemoteViewsFactory,
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = true;
    private static final Utils.Logger P = new Utils.Logger(ViewsFactory.class);

    private static final int    MAX_LIST_ITEM_COUNT = 100;

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
    private final long      mCategoryId;
    private Cursor          mCursor = null;

    private Cursor
    getCursor() {
        long[] cids = mDbp.getChannelIds(mCategoryId);
        if (DBG) P.v("getCursor : Channels : " + Utils.nrsToNString(cids));
        return mDbp.queryItem(cids, sQueryProjection);
    }

    public ViewsFactory(long categoryId) {
        mCategoryId = categoryId;
    }

    void
    onItemClick(int position) {
        if (DBG) P.v("OnItemClick : " + position);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .appwidget.ViewsFactory ]";
    }

    @Override
    public int
    getCount() {
        int count = mCursor.getCount() > MAX_LIST_ITEM_COUNT?
                    MAX_LIST_ITEM_COUNT:
                    mCursor.getCount();
        if (DBG) P.v("getCount : " + count);
        return count;
    }

    @Override
    public long
    getItemId(int position) {
        mCursor.moveToPosition(position);
        return mCursor.getLong(COLI_ID);
    }

    @Override
    public RemoteViews
    getLoadingView() {
        // TODO
        // Loading view here.
        if (DBG) P.v("getLoadingView");
        return null;
    }

    @Override
    public RemoteViews
    getViewAt(int position) {
        if (DBG) P.v("getViewAt : " + position);
        mCursor.moveToPosition(position);
        RemoteViews rv = new RemoteViews(Utils.getAppContext().getPackageName(),
                                         R.layout.appwidget_row);
        rv.setTextViewText(R.id.channel, mDbp.getChannelInfoString(mCursor.getLong(COLI_CHANNELID),
                                                                   ColumnChannel.TITLE));
        rv.setTextViewText(R.id.title, mCursor.getString(COLI_TITLE));
        rv.setTextViewText(R.id.description, mCursor.getString(COLI_DESCRIPTION));

        Intent ei = new Intent();
        ei.putExtra(AppWidgetUtils.MAP_KEY_POSITION, position);
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
        if (DBG) P.v("onCreate");
        mCursor = getCursor();
        // Doing time-consuming job in advance.
        mCursor.getCount();
        mCursor.moveToFirst();
    }

    @Override
    public void
    onDataSetChanged() {
        if (null != mCursor)
            mCursor.close();
        mCursor = getCursor();
    }

    @Override
    public void
    onDestroy() {
        if (null != mCursor)
            mCursor.close();
        if (DBG) P.v("onDestroy");
    }
}
