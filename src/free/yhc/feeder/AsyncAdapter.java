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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class AsyncAdapter extends BaseAdapter implements
UnexpectedExceptionHandler.TrackedModule {
    // Variables to store information - not changed in dynamic
    protected final Context       mContext;
    private   final ListView      mLv;
    private   final int           mDataReqSz;
    private   final int           mMaxArrSz; // max array size of items
    private   final int           mRowLayout;
    private   final Object        mDummyItem;
    private   final View          mFirstDummyView;
    private   final int           mFirstLDahead;

    private   DataProvider mDp          = null;

    // Variables those are changed dynamically
    // NOTE!
    // Below variables are used(RW) by multiple threads.
    // But, any synchronization is not used for them. Why?
    // By using 'mDpDone' flag, below variables are free from race-condition.
    // Please check & confirm this.
    // (If not, I SHOULD USE LOCK for synchronization!)

    // position of top item
    // this is position of item located at item array[0].
    private   int           mPosTop     = 0;

    // NOTE
    // Accessed only in UI Thread Context!
    // real-data-count.
    // This is decided when provider set 'eod - End Of Data' flag.
    private   int           mDataCnt    = -1;

    // NOTE
    // variable 'items' SHOULD BE MODIFIED ONLY ON UI THREAD CONTEXT!!!
    protected Object[]      mItems;

    // This is used only on UI Thread context.
    // So, this is not needed to be 'volatile'
    private   int           mNrseq      = 0;

    // For synchronization
    // Read/Write operation to java primitive/reference is atomic!
    // So, use with 'volatile'
    private volatile boolean mDpDone    = false;
    private DiagAsyncTask    mDpTask    = null;

    /**
     * provide data to this adapter asynchronously
     */
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

        int requestDataCnt(AsyncAdapter adapter);
        /**
         * Let data provider know that item will not be used anymore.
         * Data provider may do some operation to prevent resource leak for the item
         * This callback will be called at main UI thread context.
         * @param adapter
         * @param items
         */
        void destroyData(AsyncAdapter adapter, Object data);
    }

    interface OnDataProvided{
        /**
         * called after data is provided and before notifying dataSetChanged on UI Thread context
         * @param adapter
         * @param nrseq
         * @param from
         * @param sz
         */
        void onDataProvided(AsyncAdapter adapter);

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
     *   {@link DataProvider#destroyData(AsyncAdapter, Object)} will not be called for dummyItem.
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
        UnexpectedExceptionHandler.get().registerModule(this);

        mContext     = context;
        mRowLayout   = rowLayout;
        mLv          = lv;
        mDummyItem   = dummyItem;
        mDataReqSz   = dataReqSz;
        mMaxArrSz    = maxArrSz;
        // NOTE
        // This is policy.
        // When reload data, some of previous data would better to be loaded.
        // 1/3 of dataReqSz is reloaded together.
        mFirstLDahead = mDataReqSz/ 3;
        mFirstDummyView = new View(mContext);
        mItems = new Object[] { mDummyItem };
    }

    protected boolean
    initalLoaded() {
        eAssert(Utils.isUiThread());
        return !(1 == mItems.length && mItems[0] == mDummyItem);
    }

    protected void
    setDataProvider(DataProvider dp) {
        mDp = dp;
    }

    /**
     * Should be run on main UI thread.
     * @param item
     */
    protected void
    destroyItem(Object item) {
        eAssert(Utils.isUiThread());
        if (mDummyItem != item && null != item)
            mDp.destroyData(this, item);
    }

    protected int
    getPosTop() {
        return mPosTop;
    }

    /**
     * return previous object
     */
    protected Object
    setItem(int pos, Object item) {
        eAssert(Utils.isUiThread());
        if (pos >= 0 && pos < mItems.length) {
            Object prev = mItems[pos];
            mItems[pos] = item;
            return prev;
        }
        return null;
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
        eAssert(Utils.isUiThread());
        eAssert(pos >= 0 && pos <= mItems.length);
        Object[] newItems = new Object[mItems.length + 1];
        System.arraycopy(mItems, 0, newItems, 0, pos);
        System.arraycopy(mItems, pos, newItems, pos + 1, mItems.length - pos);
        newItems[pos] = item;
        if (mDataCnt > 0)
            mDataCnt++;
        mItems = newItems;
    }

    /**
     * Remove item.
     * Item count is decreased by 1
     * @param pos
     */
    protected void
    removeItem(int pos) {
        eAssert(Utils.isUiThread());
        if (pos < 0 || pos >= mItems.length)
            // nothing to do
            return;
        Object[] newItems = new Object[mItems.length - 1];
        System.arraycopy(mItems, 0, newItems, 0, pos);
        System.arraycopy(mItems, pos + 1, newItems, pos, mItems.length - pos - 1);
        if (mDataCnt > 0)
            mDataCnt--;
        destroyItem(mItems[pos]);
        mItems = newItems;
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
        eAssert(Utils.isUiThread());
        eAssert(0 <= sz);
        Object[] newItems = null;

        if (LDType.INIT == ldtype || LDType.RELOAD == ldtype) {
            // new allocation.
            // Ignore all previous loading information.
            newItems = new Object[sz];
            mPosTop = from;
            for (Object o : mItems)
                destroyItem(o);
        } else if (LDType.NEXT == ldtype) {
            int sz2grow = sz;
            int sz2shrink = mItems.length + sz2grow - mMaxArrSz;
            sz2shrink = sz2shrink < 0? 0: sz2shrink;
            newItems = new Object[mItems.length + sz2grow - sz2shrink];
            System.arraycopy(mItems, sz2shrink, newItems, 0, mItems.length - sz2shrink);
            for (int i = 0; i < sz2shrink; i++)
                destroyItem(mItems[i]);
            mPosTop += sz2shrink;
        } else if (LDType.PREV == ldtype) {
            eAssert(0 < mPosTop && sz <= mPosTop);
            // After initial loading done in the middle of mItems
            int sz2grow = mPosTop;
            int sz2shrink = 0;
            sz2grow = sz2grow > sz? sz: sz2grow;
            sz2shrink = sz2grow + mItems.length - mMaxArrSz;
            sz2shrink = sz2shrink < 0? 0: sz2shrink;

            newItems = new Object[mItems.length + sz2grow - sz2shrink];
            System.arraycopy(mItems, 0, newItems, sz2grow, mItems.length - sz2shrink);
            for (int i = mItems.length - sz2shrink; i < mItems.length; i++)
                destroyItem(mItems[i]);
            mPosTop -= sz2grow;
        } else
            eAssert(false);
        return newItems;
    }

    private void
    waitDpDone(long reqSeq, int ms, int timeoutms) {
        int summs = 0;
        try {
            while (!mDpDone && reqSeq == mNrseq && summs < timeoutms) {
                Thread.sleep(ms);
                summs += ms;
            }
        } catch (InterruptedException e) {}
        eAssert(summs < timeoutms);
    }

    private void
    requestDataAsync(final LDType ldtype, final int from, final int sz,
                     final OnDataProvided onProvided) {
        //logI("Data request UI : from " + from + ", # " + sz);

        eAssert(Utils.isUiThread());

        final long reqSeq = ++mNrseq;
        mDpDone = false;

        if (null != mDpTask)
            mDpTask.cancel(true);

        DiagAsyncTask.Worker bgRun = new DiagAsyncTask.Worker() {
            @Override
            public void
            onPostExecute(DiagAsyncTask task, Err result) {
                if (null != onProvided)
                    onProvided.onDataProvided(AsyncAdapter.this);
            }
            @Override
            public Err
            doBackgroundWork(DiagAsyncTask task) {
                //logI(">>> async request RUN - START: from " + from + ", # " + sz);
                mDp.requestData(AsyncAdapter.this, ldtype, reqSeq, from, sz);
                waitDpDone(reqSeq, 50, (int)Utils.MIN_IN_MS * 3); // timeout 3 minutes.
                //logI(">>> async request RUN - END: from " + from + ", # " + sz);
                return Err.NO_ERR;
            }
        };
        mDpTask = new DiagAsyncTask(mContext,
                                    bgRun,
                                    DiagAsyncTask.Style.SPIN,
                                    R.string.plz_wait);
        mDpTask.setName("Asyn request : " + from + " #" + sz);
        mDpTask.run();
    }

    /**
     * This function would better to be called mainly when
     *   - number of data is changed (decreased / increased).
     *   - lots of change in item order (ex. one of item in the middle is deleted etc)
     */
    public void
    reloadDataSetAsync(OnDataProvided onProvided) {
        eAssert(Utils.isUiThread());
        int from = mPosTop + mLv.getFirstVisiblePosition() - mFirstLDahead;
        from = from < 0? 0: from;
        // dataset may be changed. So, reset mDataCnt to 'unknown'
        mDataCnt = -1;
        requestDataAsync(LDType.RELOAD, from, mDataReqSz, onProvided);
    }

    /**
     * This means "visible data range is changed."
     * So, after calling this function, reload should be called to apply.
     */
    public void
    moveToFirstDataSet() {
        eAssert(Utils.isUiThread());
        mPosTop = 0;
    }

    /**
     * This means "visible data range is changed."
     * So, after calling this function, reload should be called to apply.
     * And if new posTop is near data count - that is, there is too few items left to be shown
     *   at list, newly set top may be adjusted to smaller value. And it will be returned.
     * @param adataCnt
     *   number of real-data-count. This is larger than getCount() value which returns loaded data count.
     * @return
     *   changed posTop. This may different with given value.
     */
    public void
    moveToLastDataSet() {
        eAssert(Utils.isUiThread());
        mDataCnt = mDp.requestDataCnt(this);
        int newtop = mDataCnt - mDataReqSz + mFirstLDahead;
        mPosTop = (newtop < 0)? 0: newtop;
    }


    /**
     * @param priv
     *   Internal value passed by {@link AsyncAdapter}
     *   This value should be passed as it is.
     * @param reqSeq
     *   sequence number. (Given by adapter)
     *   This also should be passed as it is.
     * @param from
     * @param aitems
     * @param eod
     *   End Of Data
     *   If
     */
    public void
    provideItems(final Object priv, final long reqSeq, final int from, final Object[] items, final boolean eod) {
        //logI("AsyncAdapter provideItems - START : from " + from + ", # " + aitems.length);
        eAssert(mMaxArrSz > items.length);

        final LDType ldtype = (LDType)priv;
        // NOTE
        // Changing 'mItems' array SHOULD BE processed on UI Thread Context!!!
        // This is very important!
        // Why?
        // Most operations are accessing 'mItems' array on UI Thread Context.
        // So, to avoid race-condition!!
        Utils.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                //logI("AsyncAdapter Provide Item Post Run (" + reqSeq + ", " + mNrseq + ") - START");

                // Check that there is already new request or not.
                // This is run on UI thread. So, synchronization is not needed to be worried about.
                if (reqSeq != mNrseq)
                    return;

                // 'posTop' is changed in 'buildNewItemsArray'.
                // So backup it before building new mItems array.
                int posTopSv = mPosTop;
                final Object[] newItems;
                if (LDType.INIT == ldtype || LDType.RELOAD == ldtype) {
                    newItems = buildNewItemsArray(ldtype, from, items.length);
                    System.arraycopy(items, 0, newItems, 0, items.length);
                } else if (from == mPosTop + mItems.length) {
                    newItems = buildNewItemsArray(LDType.NEXT, from, items.length);
                    System.arraycopy(items, 0, newItems, newItems.length - items.length, items.length);
                } else if (from == mPosTop - items.length) {
                    newItems = buildNewItemsArray(LDType.PREV, from, items.length);
                    System.arraycopy(items, 0, newItems, 0, items.length);
                } else if (from >= mPosTop && from + items.length <= mPosTop + mItems.length) {
                    // We don't need to re-allocate item array.
                    // Just reuse it!
                    System.arraycopy(items, 0, mItems, from - mPosTop, items.length);
                    newItems = mItems;
                } else {
                    eAssert(false);
                    newItems = null;
                }

                View v = mLv.getChildAt(0);
                int topY = (v == null) ? 0 : v.getTop();
                int firstVisiblePos = mLv.getFirstVisiblePosition();

                mItems = newItems;

                if (eod)
                    mDataCnt = mPosTop + mItems.length;

                // This is run on UI Thread.
                // So, we don't need to worry about race-condition.
                int posDelta = mPosTop - posTopSv;

                notifyDataSetChanged();
                // Restore list view's previous location.
                int pos = firstVisiblePos - posDelta;
                pos = pos < 0? 0: pos;
                if (0 == pos && 0 == mPosTop && topY < 0)
                    topY = 0; // we cannot before 'first item'
                mLv.setSelectionFromTop(pos, topY);
                // check again at UI thread
                mDpDone = true;
                mDpTask = null;
            }
        });
        //logI("AsyncAdapter provideItems - END : from " + from + ", # " + aitems.length);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ AsyncAdapter ]"
                + "  mDataCnt       : " + mDataCnt + "\n"
                + "  mItems.length  : " + mItems.length + "\n"
                + "  mPosTop        : " + mPosTop + "\n";
    }

    /**
     * @return
     *   number of items that is loaded to array.(NOT real count.)
     */
    @Override
    public int getCount() {
        eAssert(Utils.isUiThread());
        //Log.i(TAG, ">>> getCount");
        return mItems.length;
    }

    @Override
    public Object
    getItem(int pos) {
        eAssert(Utils.isUiThread());
        //Log.i(TAG, ">>> getItem : " + position);
        if (pos < 0 || pos >= mItems.length)
            return null;
        return mItems[pos];
    }

    /**
     * item id is 'absolute' position of this item.
     * posTop + position
     */
    @Override
    public long
    getItemId(int position) {
        //Log.i(TAG, ">>> getItemId : " + position);
        return mPosTop + position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        //Log.i(TAG, ">>> getView : " + position);

        if (!initalLoaded()) {
            // reload some of previous item too.
            int from = mPosTop + mLv.getFirstVisiblePosition() - mFirstLDahead;
            from = from < 0? 0: from;
            requestDataAsync(LDType.INIT, from, mDataReqSz, null);
            return mFirstDummyView;
        }

        View v = convertView;
        if (null == convertView || convertView == mFirstDummyView) {
            LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(mRowLayout, null);
        }

        bindView(v, mContext, position);

        // At bindView, functions like 'getItem' 'getItemId' may be used.
        // And these members are at risk of race-condition
        // To avoid this, below codes SHOULD be processed after bindView
        if (position == 0 && mPosTop > 0) {
            int szReq = (mPosTop > mDataReqSz)? mDataReqSz: mPosTop;
            // This is first item
            requestDataAsync(LDType.PREV, mPosTop - szReq, szReq, null);
        } else if (mItems.length - 1 == position
                   && (mDataCnt < 0 || mPosTop + mItems.length < mDataCnt)) {
            // This is last item
            requestDataAsync(LDType.NEXT, mPosTop + position + 1, mDataReqSz, null);
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
        if (null != mItems) {
            for (Object o : mItems)
                destroyItem(o);
        }
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }
}
