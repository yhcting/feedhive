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
            if (DBG) P.v("onPreDataProvide : anchorPos(" + anchorPos + ")");
            AsyncCursorListAdapter adpr = (AsyncCursorListAdapter)adapter;
            adpr.setAsyncLoadingAnchor(anchorPos);
        }

        @Override
        public void
        onPostDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq) {
            if (DBG) P.v("onPostDataProvide : anchorPos(" + anchorPos + ")");
            AsyncCursorListAdapter adpr = (AsyncCursorListAdapter)adapter;
            adpr.setAsyncLoadingAnchor(INVALID_POS);
        }

        @Override
        public void
        onCancelledDataProvide(AsyncAdapter adapter, int anchorPos, long nrseq) {
            if (DBG) P.v("onCancelledDataProvide : anchorPos(" + anchorPos + ")");
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

        View firstLoadingView = LookAndFeel.get().inflateLayout(context, rowLayout);
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
