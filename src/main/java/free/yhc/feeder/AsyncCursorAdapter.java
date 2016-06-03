/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

import android.content.Context;
import android.database.Cursor;

import free.yhc.baselib.Logger;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

public class AsyncCursorAdapter extends AsyncAdapter implements
AsyncAdapter.DataProvider {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(AsyncCursorAdapter.class, Logger.LOGLV_DEFAULT);

    private final Object mCurlock = new Object();

    private Cursor mCur;
    private ItemBuilder mIbldr;

    interface ItemBuilder {
        Object buildItem(AsyncCursorAdapter adapter, Cursor c);
        void destroyItem(AsyncCursorAdapter adapter, Object item);
    }

    AsyncCursorAdapter(Context context,
                       Cursor cursor,
                       ItemBuilder bldr,
                       int rowLayout,
                       final int dataReqSz,
                       final int maxArrSz,
                       boolean hasLimit) {
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
     *   {@link AsyncCursorAdapter#reloadItem(int[])} or {@link AsyncAdapter#reloadDataSetAsync(DataProvideStateListener)}
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

    @SuppressWarnings("unused")
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
     */
    public void
    reloadItem(int itemId) {
        reloadItem(new int[] { itemId } );
    }

    /**
     * reload several items synchronously.
     */
    public void
    reloadItem(int[] itemIds) {
        for (int id : itemIds) {
            int pos = id - getPosTop();
            synchronized (mCurlock) {
                if (mCur.moveToPosition(id))
                    destroyItem(setItem(pos ,mIbldr.buildItem(this, mCur)));
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
            P.bug(null != mCur && null != mIbldr);

            if (mCur.isClosed())
                P.bug(false);

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
                P.bug(i == szAvail);
            } else
                P.bug(0 == mCur.getCount() || 0 == szAvail);
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
