package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class AsyncAdapter extends BaseAdapter implements
UnexpectedExceptionHandler.TrackedModule {

    private   final Thread        uiThread = Thread.currentThread();

    // Variables to store information - not changed in dynamic
    protected final Context       context;
    protected final Handler       uiHandler = new Handler();
    private   final ListView      lv;
    private         DataProvider  dp;
    private         OnRequestData onRD;
    private   final int           dataReqSz;
    private   final int           maxArrSz; // max array size of items
    private   final int           rowLayout;
    private   final Object        dummyItem;
    private   final View          firstDummyView;
    private   final int           firstLDahead;


    // Variables those are changed dynamically
    // NOTE!
    // Below variables are used(RW) by multiple threads.
    // But, any synchronization is not used for them. Why?
    // By using 'dpDone' flag, below variables are free from race-condition.
    // Please check & confirm this.
    // (If not, I SHOULD USE LOCK for synchronization!)

    // position of top item
    // this is position of item located at item array[0].
    private   int           posTop      = 0;

    // NOTE
    // Accessed only in UI Thread Context!
    // real-data-count.
    // This is decided when provider set 'eod - End Of Data' flag.
    private   int           dataCnt     = -1;

    // NOTE
    // variable 'items' SHOULD BE MODIFIED ONLY ON UI THREAD CONTEXT!!!
    protected Object[]      items;

    private   int           nrseq       = 0;    // This is used only on UI Thread context.

    // For synchronization
    // Read/Write operation to java primitive/reference is atomic!
    // So, use with 'volatile'
    private volatile boolean dpDone     = false;
    private SpinAsyncTask    dpTask     = null;

    interface DataProvider {
        /**
         * NOTE
         * At this function, item array of this adapter may be changed (even can be shrink).
         * And this function may be called at multiple thread.
         * So, function should be "MULTI-THREAD SAFE"
         * Or, thread may be interrupted in the middle of running.
         * @param adapter
         * @param nrseq
         *   request sequence number
         * @param from
         * @param sz
         * @return
         *   return value is not used yet. it is just reserved.
         */
        int requestData(AsyncAdapter adapter, Object priv, long nrseq, int from, int sz);
    }

    interface OnRequestData {
        /**
         * called when requesting data on UI Thread context
         * @param adapter
         * @param nrseq
         * @param from
         * @param sz
         */
        void onRequestData(AsyncAdapter adapter, long nrseq, int from, int sz);
        /**
         * called after data is provided and before notifying dataSetChanged on UI Thread context
         * @param adapter
         * @param nrseq
         * @param from
         * @param sz
         */
        void onDataProvided(AsyncAdapter adapter, long nrseq, int from, int sz);

    }

    private enum LDType {
        INIT,
        NEXT,
        PREV,
        RELOAD
    }

    /**
     * SHOULD BE created on UI Thread Context!
     * @param context
     * @param rowLayout
     * @param lv
     * @param dummyItem
     * @param dataReqSz
     * @param maxArrSz
     */
    protected
    AsyncAdapter(Context        context,
                 int            rowLayout,
                 ListView       lv,
                 Object         dummyItem, // dummy item for first load
                 final int      dataReqSz,
                 final int      maxArrSz) {
        eAssert(dataReqSz < maxArrSz);
        UnexpectedExceptionHandler.S().registerModule(this);

        this.context = context;
        this.rowLayout = rowLayout;
        this.lv = lv;
        this.dummyItem = dummyItem;
        this.dataReqSz = dataReqSz;
        this.maxArrSz = maxArrSz;
        // NOTE
        // This is policy.
        // When reload data, some of previous data would better to be loaded.
        // 1/3 of dataReqSz is reloaded together.
        this.firstLDahead = dataReqSz / 3;
        firstDummyView = new View(context);
        items = new Object[] { dummyItem };
    }

    protected boolean
    isUiThread() {
        return Thread.currentThread() == uiThread;
    }

    protected void
    setListeners(DataProvider dp, OnRequestData requestDataListener) {
        this.dp = dp;
        this.onRD = requestDataListener;
    }

    protected int
    getPosTop() {
        return posTop;
    }

    protected void
    setItem(int pos, Object item) {
        eAssert(isUiThread());
        if (pos >= 0 && pos < items.length)
            items[pos] = item;
    }

    /**
     *
     * @param pos
     *   position of this value is like below.
     *   +--------+---------+--------+---------+-----
     *   | pos[0] | item[0] | pos[1] | item[1] | ...
     *   +--------+---------+--------+---------+-----
     * @param item
     */
    protected void
    insertItem(int pos, Object item) {
        eAssert(isUiThread());
        eAssert(pos >= 0 && pos <= items.length);
        Object[] newItems = new Object[items.length + 1];
        System.arraycopy(items, 0, newItems, 0, pos);
        System.arraycopy(items, pos, newItems, pos + 1, items.length - pos);
        newItems[pos] = item;
        if (dataCnt > 0)
            dataCnt++;
        items = newItems;
    }

    protected void
    removeItem(int pos) {
        eAssert(isUiThread());
        if (pos < 0 || pos >= items.length)
            // nothing to do
            return;
        Object[] newItems = new Object[items.length - 1];
        System.arraycopy(items, 0, newItems, 0, pos);
        System.arraycopy(items, pos + 1, newItems, pos, items.length - pos - 1);
        if (dataCnt > 0)
            dataCnt--;
        items = newItems;
    }

    /**
     *
     * @param ldtype
     * @param from
     *   meaningful only for LDType.RELOAD
     * @param sz
     * @return
     *   new items
     */
    private Object[]
    buildNewItemsArray(LDType ldtype, int from, int sz) {
        eAssert(isUiThread());
        eAssert(0 <= sz);
        Object[] newItems = null;

        if (LDType.RELOAD == ldtype) {
            // new allocation.
            // Ignore all previous loading information.
            newItems = new Object[sz];
            posTop = from;
        } else if (LDType.NEXT == ldtype) {
            int sz2grow = sz;
            int sz2shrink = items.length + sz2grow - maxArrSz;
            sz2shrink = sz2shrink < 0? 0: sz2shrink;
            newItems = new Object[items.length + sz2grow - sz2shrink];
            System.arraycopy(items, sz2shrink, newItems, 0, items.length - sz2shrink);
            posTop += sz2shrink;
        } else if (LDType.PREV == ldtype) {
            eAssert(0 < posTop && sz <= posTop);
            // After initial loading done in the middle of items
            int sz2grow = posTop;
            int sz2shrink = 0;
            sz2grow = sz2grow > sz? sz: sz2grow;
            sz2shrink = sz2grow + items.length - maxArrSz;
            sz2shrink = sz2shrink < 0? 0: sz2shrink;

            newItems = new Object[items.length + sz2grow - sz2shrink];
            System.arraycopy(items, 0, newItems, sz2grow, items.length - sz2shrink);
            posTop -= sz2grow;
        } else
            eAssert(false);
        return newItems;
    }

    private void
    waitDpDone(long reqSeq, int ms) {
        try {
            while (!dpDone && reqSeq == nrseq)
                Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }

    private void
    requestDataAsync(final LDType ldtype, final int from, final int sz) {
        logI("Data request UI : from " + from + ", # " + sz);

        eAssert(isUiThread());

        final long reqSeq = ++nrseq;
        dpDone = false;

        if (null != dpTask)
            dpTask.cancel(true);

        SpinAsyncTask.OnEvent bgRun = new SpinAsyncTask.OnEvent() {
            @Override
            public void onPostExecute(SpinAsyncTask task, Err result) {}
            @Override
            public Err
            onDoWork(SpinAsyncTask task, Object... objs) {
                logI(">>> async request RUN - START: from " + from + ", # " + sz);
                dp.requestData(AsyncAdapter.this, ldtype, reqSeq, from, sz);
                waitDpDone(reqSeq, 50);
                logI(">>> async request RUN - END: from " + from + ", # " + sz);
                return Err.NoErr;
            }
            @Override
            public void onCancel(SpinAsyncTask task) {}
        };
        dpTask = new SpinAsyncTask(context, bgRun, R.string.plz_wait, false);
        dpTask.setName("Asyn request : " + from + " #" + sz);
        dpTask.execute(null, null);

        if (null != onRD)
            onRD.onRequestData(this, reqSeq, from, sz);
    }

    /**
     * This function would better to be called mainly when
     *   - number of data is changed.
     *   - lots of change in item order (ex. one of item in the middle is deleted etc)
     */
    public void
    reloadDataSetAsync() {
        eAssert(isUiThread());
        int from = posTop + lv.getFirstVisiblePosition() - firstLDahead;
        from = from < 0? 0: from;
        requestDataAsync(LDType.RELOAD, from, dataReqSz);
    }

    /**
     *
     * @param from
     * @param aitems
     * @param eod
     *   End Of Data
     *   If
     */
    public void
    provideItems(final Object priv, final long reqSeq, final int from, final Object[] aitems, final boolean eod) {
        logI("AsyncAdapter provideItems - START : from " + from + ", # " + aitems.length);
        eAssert(maxArrSz > aitems.length);

        final LDType ldtype = (LDType)priv;
        // NOTE
        // Changing 'items' array SHOULD BE processed on UI Thread Context!!!
        // This is very important!
        // Why?
        // Most operations are accessing 'items' array on UI Thread Context.
        // So, to avoid race-condition!!
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                logI("AsyncAdapter Provide Item Post Run (" + reqSeq + ", " + nrseq + ") - START");

                // Check that there is already new request or not.
                // This is run on UI thread. So, synchronization is not needed to be worried about.
                if (reqSeq != nrseq)
                    return;

                final Object[] newItems;
                if (LDType.INIT == ldtype || LDType.RELOAD == ldtype) {
                    newItems = buildNewItemsArray(LDType.RELOAD, from, aitems.length);
                    System.arraycopy(aitems, 0, newItems, 0, aitems.length);
                } else if (from == posTop + items.length) {
                    newItems = buildNewItemsArray(LDType.NEXT, from, aitems.length);
                    System.arraycopy(aitems, 0, newItems, newItems.length - aitems.length, aitems.length);
                } else if (from == posTop - aitems.length) {
                    newItems = buildNewItemsArray(LDType.PREV, from, aitems.length);
                    System.arraycopy(aitems, 0, newItems, 0, aitems.length);
                } else if (from >= posTop && from + aitems.length <= posTop + items.length) {
                    // We don't need to re-allocate item array.
                    // Just reuse it!
                    System.arraycopy(aitems, 0, items, from - posTop, aitems.length);
                    newItems = items;
                } else {
                    eAssert(false);
                    newItems = null;
                }

                View v = lv.getChildAt(0);
                int posTopSv = posTop;
                int topY = (v == null) ? 0 : v.getTop();
                int firstVisiblePos = lv.getFirstVisiblePosition();

                items = newItems;

                if (eod)
                    dataCnt = posTop + items.length;

                // This is run on UI Thread.
                // So, we don't need to worry about race-condition.
                int posDelta = posTop - posTopSv;

                if (null != onRD)
                    onRD.onDataProvided(AsyncAdapter.this, reqSeq, from, aitems.length);
                notifyDataSetChanged();
                // Restore list view's previous location.
                int pos = firstVisiblePos - posDelta;
                pos = pos < 0? 0: pos;
                if (0 == pos && 0 == posTop && topY < 0)
                    topY = 0; // we cannot before 'first item'
                lv.setSelectionFromTop(pos, topY);
                // check again at UI thread
                dpDone = true;
                dpTask = null;
                logI("AsyncAdapter Provide Item Post Run (" + reqSeq + ", " + nrseq + " - END");
            }
        });
        logI("AsyncAdapter provideItems - END : from " + from + ", # " + aitems.length);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ AsyncAdapter ]"
                + "  dataCnt       : " + dataCnt + "\n"
                + "  items.length  : " + items.length + "\n"
                + "  posTop        : " + posTop + "\n";
    }

    @Override
    public int getCount() {
        eAssert(isUiThread());
        //Log.i(TAG, ">>> getCount");
        return items.length;
    }

    @Override
    public Object
    getItem(int pos) {
        eAssert(isUiThread());
        //Log.i(TAG, ">>> getItem : " + position);
        if (pos < 0 || pos >= items.length)
            return null;
        return items[pos];
    }

    @Override
    public long
    getItemId(int position) {
        //Log.i(TAG, ">>> getItemId : " + position);
        return posTop + position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        //Log.i(TAG, ">>> getView : " + position);

        if (1 == items.length && items[0] == dummyItem) {
            // reload some of previous item too.
            int from = posTop + lv.getFirstVisiblePosition() - firstLDahead;
            from = from < 0? 0: from;
            requestDataAsync(LDType.INIT, from, dataReqSz);
            return firstDummyView;
        }

        View v = convertView;
        if (null == convertView || convertView == firstDummyView) {
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(rowLayout, null);
        }

        bindView(v, context, position);

        // At bindView, functions like 'getItem' 'getItemId' may be used.
        // And these members are at risk of race-condition
        // To avoid this, below codes SHOULD be processed after bindView
        if (position == 0 && posTop > 0) {
            int szReq = (posTop > dataReqSz)? dataReqSz: posTop;
            // This is first item
            requestDataAsync(LDType.PREV, posTop - szReq, szReq);
        } else if (items.length - 1 == position
                   && (dataCnt < 0 || posTop + items.length < dataCnt)) {
            // This is last item
            requestDataAsync(LDType.NEXT, posTop + position + 1, dataReqSz);
        }
        return v;
    }

    protected void
    bindView(View v, final Context context, int position) {
        // FIXME
        // This is for test
        // ((TextView)v.findViewById(R.id.text)).setText((String)getItem(position));
    }

    @Override
    protected void
    finalize() throws Throwable {
        super.finalize();
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }
}
