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

import java.util.LinkedList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.ThreadEx;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Util;

import static free.yhc.abaselib.util.AUtil.isUiThread;

public class AsyncAdapter extends BaseAdapter implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(AsyncAdapter.class, Logger.LOGLV_DEFAULT);

    // Variables to store information - not changed in dynamic
    protected final Context mContext;
    private final int mDataReqSz;
    private final int mMaxArrSz; // max array size of items
    private final boolean mHasLimit;
    private final int mRowLayout;
    private final int mFirstLDahead;
    // We cannot control the moment of GC is triggered.
    // So, to GC memory used for items ASAP, below list is used.
    private final LinkedList<Object> mGarbageIteml = new LinkedList<>();


    // Variables set at init() function
    private ListView mLv = null;
    private DataProvideStateListener mDpsListener = null;
    private Object mDummyItem = null;
    private View mFirstLoadingView = null;


    private DataProvider mDp = null;

    // Variables those are changed dynamically
    // NOTE!
    // Below variables are used(RW) by multiple threads.
    // But, any synchronization is not used for them. Why?
    // By using 'mDpDone' flag, below variables are free from race-condition.
    // Please check & confirm this.
    // (If not, I SHOULD USE LOCK for synchronization!)

    // position of top item
    // this is position of item located at item array[0].
    private int mPosTop = 0;

    // NOTE
    // Accessed only in UI Thread Context!
    // real-data-count.
    // This is decided when provider set 'eod - End Of Data' flag.
    private int mDataCnt = -1;

    // NOTE
    // variable 'items' SHOULD BE MODIFIED ONLY ON UI THREAD CONTEXT!!!
    protected Object[] mItems;

    // This is used only on UI Thread context.
    // So, this is not needed to be 'volatile'
    private int mNrseq = 0;

    // For synchronization
    // Read/Write operation to java primitive/reference is atomic!
    // So, use with 'volatile'
    private volatile boolean mDpDone = false;
    private ThreadEx<Err> mDpTask = null;

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
         * @return return value is not used yet. it is just reserved.
         */
        int requestData(AsyncAdapter adapter, Object priv, long nrseq, int from, int sz);

        int requestDataCnt(AsyncAdapter adapter);
        /**
         * Let data provider know that item will not be used anymore.
         * Data provider may do some operation to prevent resource leak for the item
         * This callback will be called at main UI thread context.
         */
        void destroyData(AsyncAdapter adapter, Object data);
    }

    interface DataProvideStateListener {
        /**
         * Called before starting async data provider.
         */
        void onPreDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq);
        /**
         * called after data is provided and before notifying dataSetChanged on UI Thread context
         */
        void onPostDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq);

        /**
         * called when dataProvide is cancelled
         */
        void onCancelledDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq);
    }

    private enum LDType {
        INIT,
        NEXT,
        PREV,
        RELOAD
    }

    /**
     * SHOULD BE created on UI Thread Context!
     * @param maxArrSz if hasLimit == false than
     *                 this value SHOULD BE LARGER than number of items that can be shown at one-screen!
     */
    protected
    AsyncAdapter(Context context,
                 int rowLayout,
                 final int dataReqSz,
                 final int maxArrSz,
                 boolean hasLimit) {
        P.bug(dataReqSz < maxArrSz);

        mContext = context;
        mRowLayout = rowLayout;
        mDataReqSz = dataReqSz;
        mMaxArrSz = maxArrSz;
        mHasLimit = hasLimit;
        // NOTE
        // This is policy.
        // When reload data, some of previous data would better to be loaded.
        // 1/3 of dataReqSz is reloaded together.
        mFirstLDahead = mDataReqSz/ 3;
    }

    protected void
    init(View firstLoadingView,
         ListView lv,
         DataProvideStateListener dpsListener,
         Object dummyItem) { // dummy item for first load
        mLv = lv;
        mDpsListener = dpsListener;
        mFirstLoadingView = firstLoadingView;
        mDummyItem = dummyItem;
        mItems = new Object[] { mDummyItem };
    }

    protected boolean
    initalLoaded() {
        P.bug(isUiThread());
        return !(1 == mItems.length && mItems[0] == mDummyItem);
    }

    protected void
    setDataProvider(DataProvider dp) {
        mDp = dp;
    }

    /**
     * Should be run on main UI thread.
     */
    protected void
    destroyItem(Object item) {
        P.bug(isUiThread());
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
        P.bug(isUiThread());
        if (pos >= 0 && pos < mItems.length) {
            Object prev = mItems[pos];
            mItems[pos] = item;
            return prev;
        }
        return null;
    }

    /**
     *
     * @param pos position of this value is like below.
     *            +--------+---------+--------+---------+-----
     *            | pos[0] | item[0] | pos[1] | item[1] | ...
     *            +--------+---------+--------+---------+-----
     */
    protected void
    insertItem(int pos, Object item) {
        P.bug(isUiThread());
        P.bug(pos >= 0 && pos <= mItems.length);
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
     */
    protected void
    removeItem(int pos) {
        P.bug(isUiThread());
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
     * @param from meaningful only for LDType.RELOAD
     * @return new items
     */
    private Object[]
    buildNewItemsArray(LDType ldtype, int from, int sz) {
        P.bug(isUiThread());
        P.bug(0 <= sz);
        Object[] newItems = null;

        if (LDType.INIT == ldtype || LDType.RELOAD == ldtype) {
            // new allocation.
            // Ignore all previous loading information.
            newItems = new Object[sz];
            mPosTop = from;
            //noinspection ManualArrayToCollectionCopy
            for (Object o : mItems)
                mGarbageIteml.add(o);
        } else if (LDType.NEXT == ldtype) {
            //noinspection UnnecessaryLocalVariable
            int sz2grow = sz;
            int sz2shrink = mItems.length + sz2grow - mMaxArrSz;
            sz2shrink = sz2shrink < 0? 0: sz2shrink;
            newItems = new Object[mItems.length + sz2grow - sz2shrink];
            System.arraycopy(mItems, sz2shrink, newItems, 0, mItems.length - sz2shrink);
            //noinspection ManualArrayToCollectionCopy
            for (int i = 0; i < sz2shrink; i++)
                mGarbageIteml.add(mItems[i]);
            mPosTop += sz2shrink;
        } else if (LDType.PREV == ldtype) {
            P.bug(0 < mPosTop && sz <= mPosTop);
            // After initial loading done in the middle of mItems
            int sz2grow = mPosTop;
            int sz2shrink;
            sz2grow = sz2grow > sz? sz: sz2grow;
            sz2shrink = sz2grow + mItems.length - mMaxArrSz;
            sz2shrink = sz2shrink < 0? 0: sz2shrink;

            newItems = new Object[mItems.length + sz2grow - sz2shrink];
            System.arraycopy(mItems, 0, newItems, sz2grow, mItems.length - sz2shrink);
            //noinspection ManualArrayToCollectionCopy
            for (int i = mItems.length - sz2shrink; i < mItems.length; i++)
                mGarbageIteml.add(mItems[i]);
            mPosTop -= sz2grow;
        } else
            P.bug(false);
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
        } catch (InterruptedException ignored) {}
        P.bug(summs < timeoutms);
    }

    private void
    requestDataAsync(final LDType ldtype,
                     final int from, int sz) {
        //logI("Data request UI : from " + from + ", # " + sz);

        P.bug(isUiThread());

        if (mHasLimit
            && from + sz > mMaxArrSz)
            sz = mMaxArrSz - from;

        if (sz <= 0)
            return; // nothing to do.

        final long reqSeq = ++mNrseq;
        mDpDone = false;

        if (null != mDpTask)
            mDpTask.cancel(true);

        final int szToReq = sz;
        int anchorPos;
        switch (ldtype) {
        case INIT:
        case RELOAD:
        case NEXT:
            anchorPos = from - mPosTop - 1;
            if (anchorPos < 0)
                anchorPos = 0;
            break;

        case PREV:
            anchorPos = from + sz - mPosTop;
            break;

        default:
            anchorPos = 0;
            P.bug(false);
        }

        // NOTE
        // 'onPreDataProvide' SHOULD be called BEFORE bindView().
        // See getView() for details.
        if (null != mDpsListener)
            mDpsListener.onPreDataProvide(AsyncAdapter.this, anchorPos, reqSeq);

        final int anchorItemPos = anchorPos;
        mDpTask = new ThreadEx<Err>(
                "AsyncAdatprDp", AppEnv.getUiHandlerAdapter(), ThreadEx.TASK_PRIORITY_NORM) {
            private void
            onFinished() {
                notifyDataSetChanged();
                while (!mGarbageIteml.isEmpty())
                    destroyItem(mGarbageIteml.removeFirst());
            }

            @Override
            protected void
            onPostRun(Err result, Exception ex) {
                if (null != mDpsListener)
                    mDpsListener.onPostDataProvide(AsyncAdapter.this, anchorItemPos, reqSeq);
                onFinished();
            }

            @Override
            protected void
            onCancelled(Exception ex) {
                if (null != mDpsListener)
                    mDpsListener.onCancelledDataProvide(AsyncAdapter.this, anchorItemPos, reqSeq);
                onFinished();
            }

            @Override
            protected Err
            doAsync() {
                //logI(">>> async request RUN - START: from " + from + ", # " + sz);
                mDp.requestData(AsyncAdapter.this, ldtype, reqSeq, from, szToReq);
                waitDpDone(reqSeq, 50, (int)Util.MIN_IN_MS * 3); // timeout 3 minutes.
                //logI(">>> async request RUN - END: from " + from + ", # " + sz);
                return Err.NO_ERR;
            }
        };

        mDpTask.setName("Asyn request : " + from + " #" + sz);
        mDpTask.start();
    }

    /**
     * This function would better to be called mainly when
     *   - number of data is changed (decreased / increased).
     *   - lots of change in item order (ex. one of item in the middle is deleted etc)
     */
    public void
    reloadDataSetAsync(@SuppressWarnings("unused") DataProvideStateListener dpsListener) {
        P.bug(isUiThread());
        int from = mPosTop + mLv.getFirstVisiblePosition() - mFirstLDahead;
        from = from < 0? 0: from;
        // dataset may be changed. So, reset mDataCnt to 'unknown'
        mDataCnt = -1;
        requestDataAsync(LDType.RELOAD, from, mDataReqSz);
    }

    /**
     * This means "visible data range is changed."
     * So, after calling this function, reload should be called to apply.
     */
    public void
    moveToFirstDataSet() {
        P.bug(isUiThread());
        mPosTop = 0;
    }

    /**
     * This means "visible data range is changed."
     * So, after calling this function, reload should be called to apply.
     * And if new posTop is near data count - that is, there is too few items left to be shown
     *   at list, newly set top may be adjusted to smaller value. And it will be returned.
     */
    @SuppressWarnings("unused")
    public void
    moveToLastDataSet() {
        P.bug(isUiThread());
        mDataCnt = mDp.requestDataCnt(this);
        int newtop = mDataCnt - mDataReqSz + mFirstLDahead;
        mPosTop = (newtop < 0)? 0: newtop;
    }

    public ListView
    getListView() {
        return mLv;
    }

    /**
     * @param priv Internal value passed by {@link AsyncAdapter}
     *             This value should be passed as it is.
     * @param reqSeq sequence number. (Given by adapter)
     *               This also should be passed as it is.
     * @param eod  End Of Data
     */
    public void
    provideItems(final Object priv,
                 @SuppressWarnings("unused") final long reqSeq,
                 final int from,
                 final Object[] items,
                 final boolean eod) {
        //logI("AsyncAdapter provideItems - START : from " + from + ", # " + aitems.length);
        P.bug(mMaxArrSz > items.length);

        final LDType ldtype = (LDType)priv;
        // NOTE
        // Changing 'mItems' array SHOULD BE processed on UI Thread Context!!!
        // This is very important!
        // Why?
        // Most operations are accessing 'mItems' array on UI Thread Context.
        // So, to avoid race-condition!!
        AppEnv.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                //logI("AsyncAdapter Provide Item Post Run (" + reqSeq + ", " + mNrseq + ") - START");

                // Check that there is already new request or not.
                // This is run on UI thread. So, synchronization is not needed to be worried about.
                // NOTE
                // Below check is really required? I don't think so,
                // especially, in case that there should be more than one data request to fill one screen,
                //   below check issues unexpected bug!
                // Let's comment out and monitor it...
                /*
                if (reqSeq != mNrseq)
                    return;
                */

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
                    P.bug(false);
                    newItems = null;
                }

                View v = mLv.getChildAt(0);
                int topY = (v == null) ? 0 : v.getTop();
                int firstVisiblePos = mLv.getFirstVisiblePosition();

                mItems = newItems;
                if (eod) {
                    P.bug(null != mItems);
                    assert mItems != null;
                    mDataCnt = mPosTop + mItems.length;
                }

                // This is run on UI Thread.
                // So, we don't need to worry about race-condition.
                int posDelta = mPosTop - posTopSv;

                // NOTE
                // Below code is move to requestDataAsync().
                // Are there any side effects??? I'm not sure about it until now.
                //notifyDataSetChanged();
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
     * @return number of items that is loaded to array.(NOT real count.)
     */
    @Override
    public int
    getCount() {
        P.bug(isUiThread());
        //Log.i(TAG, ">>> getCount");
        return mItems.length;
    }

    @Override
    public Object
    getItem(int pos) {
        P.bug(isUiThread());
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
    public View
    getView(int position, View convertView, ViewGroup parent) {
        //Log.i(TAG, ">>> getView : " + position);

        if (!initalLoaded()) {
            // reload some of previous item too.
            int from = mPosTop + mLv.getFirstVisiblePosition() - mFirstLDahead;
            from = from < 0? 0: from;
            requestDataAsync(LDType.INIT, from, mDataReqSz);
            return mFirstLoadingView;
        }

        View v = convertView;
        if (null == convertView || convertView == mFirstLoadingView) {
            LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(mRowLayout, null);
        }

        // At bindView, functions like 'getItem' 'getItemId' may be used.
        // And these members are at risk of race-condition
        // To avoid this, below codes SHOULD be processed after bindView
        if (position == 0 && mPosTop > 0) {
            int szReq = (mPosTop > mDataReqSz)? mDataReqSz: mPosTop;
            // This is first item
            requestDataAsync(LDType.PREV, mPosTop - szReq, szReq);
        } else if (mItems.length - 1 == position
                   && (mDataCnt < 0 || mPosTop + mItems.length < mDataCnt)) {
            // This is last item
            requestDataAsync(LDType.NEXT, mPosTop + position + 1, mDataReqSz);
        }

        bindView(v, mContext, position);

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
    }
}
