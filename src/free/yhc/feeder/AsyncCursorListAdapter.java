/******************************************************************************
 *    Copyright (C) 2012, 2013, 2014 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of FeedHive
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
 *    along with this program.	If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package free.yhc.feeder;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ListView;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class AsyncCursorListAdapter extends AsyncCursorAdapter {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(AsyncCursorListAdapter.class);

    private static final int INVALID_POS = -1;
    private static final DataProvideStateHandler   sDpsHandler = new DataProvideStateHandler();

    private int     mAsyncLoadingAnchor = INVALID_POS;

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

    AsyncCursorListAdapter(Context      context,
                           Cursor       cursor,
                           ItemBuilder  bldr,
                           int          rowLayout,
                           ListView     lv,
                           Object       firstLoadingDummyItem,
                           int          dataReqSz,
                           int          maxArrSz,
                           boolean      hasLimit) {
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
     * @param v
     * @param context
     * @param position
     * @return
     *   true : keep going to bind / false : stop binding.
     */
    protected final boolean
    preBindView(View v, final Context context, int position)  {
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
