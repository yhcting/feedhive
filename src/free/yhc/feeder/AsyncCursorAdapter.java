package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.content.Context;
import android.database.Cursor;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class AsyncCursorAdapter extends AsyncAdapter implements
AsyncAdapter.DataProvider,
AsyncAdapter.OnRequestData {
    private Cursor          cur;
    private final Object    curlock = new Object();
    private ItemBuilder     ibldr;

    interface ItemBuilder {
        Object buildItem(AsyncCursorAdapter adapter, Cursor c);
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
        setListeners(this, this);
    }

    public void
    setItemBuilder(ItemBuilder bldr) {
        ibldr = bldr;
    }

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

    @Override
    public void
    onRequestData(AsyncAdapter adapter, long nrseq, int from, int sz) {
    }

    @Override
    public void
    onDataProvided(AsyncAdapter adapter, long nrseq, int from, int sz) {
    }

    /**
     * reload only 1 item synchronously!
     * (This is NOT async call!)
     * @param itemId
     */
    public void
    reloadItem(int itemId) {
        reloadItem(new int[] { itemId } );
    }

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
        // TODO
        // May optimization is needed to update only for changed item.
        notifyDataSetChanged();
    }

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
    protected void
    finalize() throws Throwable {
        super.finalize();
        if (null != cur)
            cur.close();
    }
}
