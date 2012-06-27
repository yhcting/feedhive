package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.content.Context;
import android.database.Cursor;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class AsyncCursorAdapter extends AsyncAdapter implements
AsyncAdapter.DataProvider {
    private Cursor          cur;
    private final Object    curlock = new Object();
    private ItemBuilder     ibldr;

    interface ItemBuilder {
        Object buildItem(AsyncCursorAdapter adapter, Cursor c);
        void   destroyItem(AsyncCursorAdapter adapter, Object item);
    }

    AsyncCursorAdapter(Context        context,
                       Cursor         cursor,
                       ItemBuilder    ibldr,
                       int            rowLayout,
                       ListView       lv,
                       Object         dummyItem,
                       final int      dataReqSz,
                       final int      maxArrSz) {
        super(context, rowLayout, lv, dummyItem, dataReqSz, maxArrSz);
        this.cur = cursor;
        this.ibldr = ibldr;
        setDataProvider(this);
    }

    public void
    setItemBuilder(ItemBuilder bldr) {
        ibldr = bldr;
    }

    /**
     * Change cursor of this adapter.
     * To loading from cursor, call {@link AsyncCursorAdapter#reloadItem(int)},
     *   {@link AsyncCursorAdapter#reloadItem(int[])} or {@link AsyncAdapter#reloadDataSetAsync()}
     * @param newCur
     */
    public void
    changeCursor(Cursor newCur) {
        logI("AsyncCursorAdapter : changeCursor");
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
    public String dump(UnexpectedExceptionHandler.DumpLevel lv) {
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
                    setItem(pos ,ibldr.buildItem(this, cur));
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
        eAssert(null != cur && null != ibldr);
        logI("AsyncCursorAdapter : requestData - START");
        Object[] items;
        boolean eod = true;
        synchronized (curlock) {
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
        logI("AsyncCursorAdapter : requestData - END");
        return 0;
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
