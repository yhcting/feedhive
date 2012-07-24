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
import android.content.Context;
import android.database.Cursor;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class AsyncCursorAdapter extends AsyncAdapter implements
AsyncAdapter.DataProvider {
    private final Object    curlock = new Object();

    private Cursor          cur;
    private ItemBuilder     ibldr;

    interface ItemBuilder {
        Object buildItem(AsyncCursorAdapter adapter, Cursor c);
        void   destroyItem(AsyncCursorAdapter adapter, Object item);
    }

    AsyncCursorAdapter(Context        context,
                       Cursor         cursor,
                       ItemBuilder    bldr,
                       int            rowLayout,
                       ListView       lv,
                       Object         dummyItem,
                       final int      dataReqSz,
                       final int      maxArrSz) {
        super(context, rowLayout, lv, dummyItem, dataReqSz, maxArrSz);
        cur = cursor;
        ibldr = bldr;
        setDataProvider(this);
    }

    public void
    setItemBuilder(ItemBuilder bldr) {
        ibldr = bldr;
    }

    /**
     * Change cursor of this adapter.
     * Adapter items are NOT reloaded.
     * To loading from cursor, call {@link AsyncCursorAdapter#reloadItem(int)},
     *   {@link AsyncCursorAdapter#reloadItem(int[])} or {@link AsyncAdapter#reloadDataSetAsync()}
     * @param newCur
     */
    public void
    changeCursor(Cursor newCur) {
        //logI("AsyncCursorAdapter : changeCursor");
        synchronized (curlock) {
            if (null != cur)
                cur.close();
            cur = newCur;
        }
    }

    protected String
    getCursorString(Cursor c, DB.Column col) {
        return c.getString(c.getColumnIndex(col.getName()));
    }

    protected Long
    getCursorLong(Cursor c, DB.Column col) {
        return c.getLong(c.getColumnIndex(col.getName()));
    }

    protected byte[]
    getCursorBlob(Cursor c, DB.Column col) {
        return c.getBlob(c.getColumnIndex(col.getName()));
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return super.dump(lv)
                + "[ AsyncCursorAdapter ]"
                + "  curCount : " + ((null == cur)? "null": cur.getCount()) + "\n";
    }

    /**
     * reload only 1 item synchronously!
     * @param itemId
     */
    public void
    reloadItem(int itemId) {
        reloadItem(new int[] { itemId } );
    }

    /**
     * reload several items synchronously.
     * @param itemIds
     */
    public void
    reloadItem(int[] itemIds) {
        for (int id : itemIds) {
            int pos = id - getPosTop();
            synchronized (curlock) {
                if (cur.moveToPosition(id))
                    destroyItem(setItem(pos ,ibldr.buildItem(this, cur)));
                else
                    ;// ignore
            }
        }
    }

    /**
     * remove item.
     * underlying cursor data is not changed.
     * only loaded array is changed.
     */
    @Override
    public void
    removeItem(int position) {
        super.removeItem(position);
    }

    @Override
    public int
    requestData(final AsyncAdapter adapter, Object priv, long nrseq, final int from, final int sz) {
        //logI("AsyncCursorAdapter : requestData - START");
        Object[] items;
        boolean eod = true;
        synchronized (curlock) {
            eAssert(null != cur && null != ibldr);

            if (cur.isClosed())
                eAssert(false);

            int szAvail = cur.getCount() - from;
            // szAvail has range - [0, sz]
            if (szAvail < 0)
                szAvail = 0;
            else if (szAvail > sz) {
                szAvail = sz;
                eod = false;
            }

            items = new Object[szAvail];
            if (cur.moveToPosition(from)) {
                int i = 0;
                do {
                    items[i++] = ibldr.buildItem(this, cur);
                } while (i < szAvail && cur.moveToNext());
                eAssert(i == szAvail);
            } else
                eAssert(0 == cur.getCount() || 0 == szAvail);
        }
        adapter.provideItems(priv, nrseq, from, items, eod);
        //logI("AsyncCursorAdapter : requestData - END");
        return 0;
    }

    @Override
    public int
    requestDataCnt(AsyncAdapter adapter) {
        // cur.getCount() is very slow at first call.
        synchronized (curlock) {
            return cur.getCount();
        }
    }

    @Override
    public void
    destroyData(AsyncAdapter adapter, Object data) {
        ibldr.destroyItem(this, data);
    }

    @Override
    protected void
    finalize() throws Throwable {
        super.finalize();
        if (null != cur)
            cur.close();
    }
}
