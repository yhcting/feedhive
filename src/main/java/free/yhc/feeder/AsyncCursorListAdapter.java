/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ListView;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Utils;

public class AsyncCursorListAdapter extends AsyncCursorAdapter {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(AsyncCursorListAdapter.class);

    private static final int INVALID_POS = -1;
    private static final DataProvideStateHandler sDpsHandler = new DataProvideStateHandler();

    private int mAsyncLoadingAnchor = INVALID_POS;

    private static class DataProvideStateHandler implements AsyncAdapter.DataProvideStateListener {
        @Override
        public void
        onPreDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq) {
            if (DBG) P.v("anchorPos(" + anchorPos + ")");
            AsyncCursorListAdapter adpr = (AsyncCursorListAdapter)adapter;
            adpr.setAsyncLoadingAnchor(anchorPos);
        }

        @Override
        public void
        onPostDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq) {
            if (DBG) P.v("anchorPos(" + anchorPos + ")");
            AsyncCursorListAdapter adpr = (AsyncCursorListAdapter)adapter;
            adpr.setAsyncLoadingAnchor(INVALID_POS);
        }

        @Override
        public void
        onCancelledDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq) {
            if (DBG) P.v("anchorPos(" + anchorPos + ")");
            AsyncCursorListAdapter adpr = (AsyncCursorListAdapter)adapter;
            adpr.setAsyncLoadingAnchor(INVALID_POS);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return super.dump(lv) + "[ AsyncCursorListAdapter ]";
    }

    AsyncCursorListAdapter(Context context,
                           Cursor cursor,
                           ItemBuilder bldr,
                           int rowLayout,
                           ListView lv,
                           Object firstLoadingDummyItem,
                           int dataReqSz,
                           int maxArrSz,
                           boolean hasLimit) {
        super(context,
              cursor,
              bldr,
              rowLayout,
              dataReqSz,
              maxArrSz,
              hasLimit);

        View firstLoadingView = UiHelper.inflateLayout(context, rowLayout);
        preBindView(firstLoadingView, context, INVALID_POS);
        init(firstLoadingView,
             lv,
             sDpsHandler,
             firstLoadingDummyItem);
    }

    private void
    setAsyncLoadingAnchor(int pos) {
        mAsyncLoadingAnchor = pos;
    }

    public boolean
    isLoadingItem(int pos) {
        return mAsyncLoadingAnchor == pos;
    }

    public void
    reloadDataSetAsync() {
        reloadDataSetAsync(sDpsHandler);
    }

    /**
     *
     * @return true : keep going to bind / false : stop binding.
     */
    protected final boolean
    preBindView(View v,
                @SuppressWarnings("unused") final Context context,
                int position)  {
        ImageView loadingIv = (ImageView)v.findViewById(R.id.loading);
        View contentv = v.findViewById(R.id.content);
        if (isLoadingItem(position)) {
            loadingIv.setVisibility(View.VISIBLE);
            loadingIv.setImageResource(R.drawable.spinner_48);
            contentv.setVisibility(View.GONE);
            loadingIv.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.rotate_spin));
            return false;
        }

        if (null != loadingIv.getAnimation()) {
            loadingIv.getAnimation().cancel();
            loadingIv.setAnimation(null);
        }

        loadingIv.setVisibility(View.GONE);
        contentv.setVisibility(View.VISIBLE);
        return true;
    }
}
