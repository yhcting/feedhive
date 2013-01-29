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

package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import android.content.Context;
import android.database.Cursor;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class AsyncCursorAdapter extends AsyncAdapter implements
AsyncAdapter.DataProvider {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(AsyncCursorAdapter.class);

    private final Object    mCurlock = new Object();

    private Cursor          mCur;
    private ItemBuilder     mIbldr;

    interface ItemBuilder {
        Object buildItem(AsyncCursorAdapter adapter, Cursor c);
        void   destroyItem(AsyncCursorAdapter adapter, Object item);
    }

    AsyncCursorAdapter(Context        context,
                       Cursor         cursor,
                       ItemBuilder    bldr,
                       int            rowLayout,
                       final int      dataReqSz,
                       final int      maxArrSz,
                       boolean        hasLimit) {
        super(context,
              rowLayout,
              dataReqSz,
              maxArrSz,
              hasLimit);
        mCur = cursor;
        mIbldr = bldr;
        setDataProvider(this);
    }

    public void
    setItemBuilder(ItemBuilder bldr) {
        mIbldr = bldr;
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
        synchronized (mCurlock) {
            if (null != mCur)
                mCur.close();
            mCur = newCur;
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
                + "  curCount : " + ((null == mCur)? "null": mCur.getCount()) + "\n";
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
            synchronized (mCurlock) {
                if (mCur.moveToPosition(id))
                    destroyItem(setItem(pos ,mIbldr.buildItem(this, mCur)));
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
        synchronized (mCurlock) {
            eAssert(null != mCur && null != mIbldr);

            if (mCur.isClosed())
                eAssert(false);

            int szAvail = mCur.getCount() - from;
            // szAvail has range - [0, sz]
            if (szAvail < 0)
                szAvail = 0;
            else if (szAvail > sz) {
                szAvail = sz;
                eod = false;
            }

            items = new Object[szAvail];
            if (mCur.moveToPosition(from)) {
                int i = 0;
                do {
                    items[i++] = mIbldr.buildItem(this, mCur);
                } while (i < szAvail && mCur.moveToNext());
                eAssert(i == szAvail);
            } else
                eAssert(0 == mCur.getCount() || 0 == szAvail);
        }
        adapter.provideItems(priv, nrseq, from, items, eod);
        //logI("AsyncCursorAdapter : requestData - END");
        return 0;
    }

    @Override
    public int
    requestDataCnt(AsyncAdapter adapter) {
        // mCur.getCount() is very slow at first call.
        synchronized (mCurlock) {
            return mCur.getCount();
        }
    }

    @Override
    public void
    destroyData(AsyncAdapter adapter, Object data) {
        mIbldr.destroyItem(this, data);
    }

    @Override
    protected void
    finalize() throws Throwable {
        super.finalize();
        if (null != mCur)
            mCur.close();
    }
}
