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

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.database.StaleDataException;
import android.graphics.drawable.AnimationDrawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.net.NetReadTask;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.feed.Feed;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.task.DownloadTask;

public class ItemListAdapter extends AsyncCursorListAdapter implements
AsyncCursorAdapter.ItemBuilder {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ItemListAdapter.class, Logger.LOGLV_DEFAULT);

    private final DBPolicy mDbp = DBPolicy.get();
    private final RTTask mRtt = RTTask.get();

    private final OnActionListener  mActionListener;
    // To avoid using mutex in "DownloadProgressListener", mDummyTextView is used.
    // See "DownloadProgressListener" for details
    private final TextView  mDummyTextView;
    private final View.OnClickListener mFavOnClick;

    public static class ProgressTextView extends TextView {
        private DownloadProgressListener _mListener = null;

        public ProgressTextView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        void
        changeListener(DownloadProgressListener newListener) {
            if (null != _mListener)
                _mListener.setTextView(null);
            _mListener = newListener;
            _mListener.setTextView(this);
        }
    }

    private static class ItemInfo {
        long id = -1;
        long state = 0;
        long cid = -1;
        boolean hasDnFile = false;
        boolean bChannel = false;
        String cTitle = "";
        String title = "";
        String desc = "";
        String pubDate = "";
        String link = "";
        String enclosureLen = "";
        String enclosureUrl = "";
        String enclosureType = "";
    }

    private class DownloadProgressListener extends DownloadTask.EventListener<DownloadTask, NetReadTask.Result> {
        // mDummyTextView is used for default 'tv' value.
        // If not, 'null' should be used.
        // In this case, code in 'onProgress' should be like below
        //     if (null != tv)
        //         tv.setText(....)
        // Then, above two line of code should be preserved with mutex or 'synchronized'
        // Why?
        // 'tv' value can be changed after (null != tv) comparison.
        // To avoid this synchronization, mDummyTextView is used.
        // Keep in mind that assigning reference is atomic operation in Java.
        private volatile TextView mTv = mDummyTextView;
        private long mMaxProgress = 0;

        DownloadProgressListener(TextView tv) {
            this.mTv = tv;
        }

        private void
        handleTaskDone(@NonNull DownloadTask task) {
            task.removeEventListener(this);
        }

        void
        setTextView(TextView tv) {
            this.mTv = (null == tv)? mDummyTextView: tv;
        }

        @Override
        public void
        onPostRun(@NonNull DownloadTask task,
                  NetReadTask.Result result,
                  Exception ex) {
            handleTaskDone(task);
        }

        @Override
        public void
        onCancelled(@NonNull DownloadTask task, Object param) {
            handleTaskDone(task);
        }

        @Override
        public void
        onProgressInit(@NonNull DownloadTask task, long maxProgress) {
            if (DBG) P.v("ItemListAdapter: MaxProg: " + maxProgress);
            mMaxProgress = maxProgress;
        }

        @Override
        public void
        onProgress(@NonNull DownloadTask task, long progress) {
            mTv.setText((0 > progress)? "??%": progress * 100 / mMaxProgress + "%");
            mTv.postInvalidate();
        }
    }

    interface OnActionListener {
        void onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state);
    }

    public static class ImageViewFavorite extends ImageView {
        long id = -1;
        long state = 0;
        int position = -1;

        public
        ImageViewFavorite(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    private int
    getTitleColor(long stateFlag) {
        return Feed.Item.isStateOpenNew(stateFlag)?
                R.color.title_color_new: R.color.text_color_opened;
    }

    private int
    getTextColor(long stateFlag) {
        return Feed.Item.isStateOpenNew(stateFlag)?
                R.color.text_color_new: R.color.text_color_opened;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return super.dump(lv) + "[ ItemListAdapter ]";
    }

    public
    ItemListAdapter(Context context,
                    Cursor cursor,
                    ListView lv,
                    final int dataReqSz,
                    final int maxArrSz,
                    OnActionListener listener) {
        super(context,
              cursor,
              null,
              R.layout.item_row,
              lv,
              new ItemInfo(),
              dataReqSz,
              maxArrSz,
              false);
        setItemBuilder(this);
        mDummyTextView = new TextView(context);
        mActionListener = listener;
        mFavOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == mActionListener)
                    return;
                ImageViewFavorite iv = (ImageViewFavorite)v;
                mActionListener.onFavoriteClick(ItemListAdapter.this, iv, iv.position, iv.id, iv.state);
            }
        };
    }

    public long
    getItemInfo_id(int position) {
        return ((ItemInfo)super.getItem(position)).id;
    }

    public long
    getItemInfo_cid(int position) {
        return ((ItemInfo)super.getItem(position)).cid;
    }

    public String
    getItemInfo_link(int position) {
        return ((ItemInfo)super.getItem(position)).link;
    }

    public String
    getItemInfo_encUrl(int position) {
        return ((ItemInfo)super.getItem(position)).enclosureUrl;
    }

    public String
    getItemInfo_encType(int position) {
        return ((ItemInfo)super.getItem(position)).enclosureType;
    }

    public int
    findPosition(long id) {
        for (int i = 0; i < getCount(); i++) {
            if (getItemInfo_id(i) == id)
                    return i;
        }
        return -1;
    }

    public int
    findItemId(long id) {
        int pos = findPosition(id);
        if (pos < 0)
            return -1;
        else
            return (int)getItemId(pos);
    }

    public void
    updateItemHasDnFile(int position, boolean v) {
        ItemInfo ii = (ItemInfo)getItem(position);
        if (null != ii)
            ii.hasDnFile = v;
    }

    public void
    updateItemState(int position, long v) {
        ItemInfo ii = (ItemInfo)getItem(position);
        if (null != ii)
            ii.state = v;
    }

    public void
    notifyItemDataChanged(long id) {
        int pos = findPosition(id);
        int firstVisPos = getListView().getFirstVisiblePosition();
        View v = getListView().getChildAt(pos - firstVisPos);
        if (null == v)
            return; // This is NOT visible item.
        bindView(v, getListView().getContext(), pos);
    }

    @Override
    public Object
    buildItem(AsyncCursorAdapter adapter, Cursor c) {
        //logI("ChannelListAdapter : buildItem - START");
        ItemInfo i = new ItemInfo();
        try {
            i.id = getCursorLong(c, ColumnItem.ID);
            i.state = mDbp.getItemInfoLong(i.id, ColumnItem.STATE);
            i.title = getCursorString(c, ColumnChannel.TITLE);
            i.desc = getCursorString(c, ColumnChannel.DESCRIPTION);
            i.pubDate = getCursorString(c, ColumnItem.PUBDATE);
            i.enclosureLen = getCursorString(c, ColumnItem.ENCLOSURE_LENGTH);
            i.enclosureUrl = getCursorString(c, ColumnItem.ENCLOSURE_URL);
            i.link = getCursorString(c, ColumnItem.LINK);
            // This runs on background thread.
            // So, assert on this thread doesn't stop application and reporting bug.
            // Therefore, this should be endurable for unexpected result.
            File df = ContentsManager.get().getItemInfoDataFile(i.id);
            i.hasDnFile = null != df && df.exists();

            int cidx = c.getColumnIndex(ColumnItem.CHANNELID.getName());
            i.bChannel = (0 <= cidx);
            if (i.bChannel) {
                i.cid = c.getLong(cidx);
                i.cTitle = mDbp.getChannelInfoString(i.cid, ColumnChannel.TITLE);
            }

        } catch (StaleDataException e) {
            P.bug(false);
        }
        //logI("ChannelListAdapter : buildItem - END");
        return i;
    }

    @Override
    public void
    destroyItem(AsyncCursorAdapter adapter, Object item) {
        // Nothing to do
    }

    @Override
    public int
    requestData(final AsyncAdapter adapter, Object priv, long nrseq, final int from, final int sz) {
        // Override to use "delayed item update"
        int ret;
        try {
            mDbp.getDelayedChannelUpdate();
            ret = super.requestData(adapter, priv, nrseq, from, sz);
        } finally {
            mDbp.putDelayedChannelUpdate();
        }
        return ret;
    }

    @Override
    public void
    bindView(View v, final Context context, int position) {
        if (!preBindView(v, context, position))
            return;
        doBindView(v, context, position);
    }

    private void
    doBindView(View v, Context context, int position)  {
        ItemInfo ii = (ItemInfo)getItem(position);

        final boolean favorite = Feed.Item.isStatFavOn(ii.state);
        final TextView channelv = (TextView)v.findViewById(R.id.channel);
        final TextView titlev = (TextView)v.findViewById(R.id.title);
        final TextView descv = (TextView)v.findViewById(R.id.description);
        final ProgressTextView progressv = (ProgressTextView)v.findViewById(R.id.progress);
        final TextView datev = (TextView)v.findViewById(R.id.date);
        final TextView infov = (TextView)v.findViewById(R.id.info);
        final ImageView imgv = (ImageView)v.findViewById(R.id.image);
        final ImageViewFavorite favImgv = (ImageViewFavorite)v.findViewById(R.id.favorite);

        if (ii.bChannel) {
            channelv.setVisibility(View.VISIBLE);
            channelv.setText(ii.cTitle);
        } else
            channelv.setVisibility(View.GONE);

        // Set favorite button.
        favImgv.id = ii.id;
        favImgv.state = ii.state;
        favImgv.position = position;
        favImgv.setOnClickListener(mFavOnClick);

        if (favorite)
            favImgv.setImageResource(R.drawable.favorite_on);
        else
            favImgv.setImageResource(R.drawable.favorite_off);

        titlev.setText(ii.title);
        descv.setText(ii.desc);
        datev.setText(ii.pubDate);

        if (ii.enclosureUrl.isEmpty())
            infov.setText("html");
        else
            infov.setText(Util.getExtentionFromUrl(ii.enclosureUrl) + " : " + ii.enclosureLen);

        // In case of enclosure, icon is decided by file is in the disk or not.
        // TODO:
        //   add proper icon (or representation...)
        Animation anim = imgv.getAnimation();
        if (null != anim) {
            anim.cancel();
            imgv.setAnimation(null);
        }
        imgv.setAlpha(1.0f);
        progressv.setVisibility(View.GONE);

        if (ii.hasDnFile) {
            imgv.setImageResource(R.drawable.ic_save);
        } else {
            DownloadTask t = mRtt.getDownloadTask(ii.id);
            switch(mRtt.getRtState(t)) {
            case IDLE:
                imgv.setImageResource(R.drawable.download_anim0);
                break;

            case READY:
                imgv.setImageResource(R.drawable.ic_pause);
                break;

            case RUN:
                //noinspection ResourceType
                imgv.setImageResource(R.drawable.download);
                // Why "post runnable and start animation?"
                // In Android 4.0.3 (ICS)
                //   putting "((AnimationDrawable)img.getDrawable()).start();" is enough.
                //
                // In Android 3.2 (HC)
                //   without using 'post', animation doesn't start when start itemListActivity.
                //   It's definitely HC bug.
                //   In this case, below code works.
                //
                // Another interesting point is, using 'imgv.post' doesn't work.
                // But, in case of using handler, it works.
                // I think it's definitely Android platform's BUG!
                //
                // This program's target platform is ICS
                /*
                Util.getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        ((AnimationDrawable)imgv.getDrawable()).start();
                    }
                });
                */
                ((AnimationDrawable)imgv.getDrawable()).start();

                // bind event listener to show progress
                DownloadProgressListener listener = new DownloadProgressListener(progressv);
                progressv.changeListener(listener);
                /* Note that task may be already done. And listener may NOT receive onPostRun, or onCancelled.
                 * But, it's no problem.
                 */
                t.addEventListener(AppEnv.getUiHandlerAdapter(), listener, true);
                progressv.setText(""); // Clear text at first
                progressv.setVisibility(View.VISIBLE);
                break;

            case FAIL:
                imgv.setImageResource(R.drawable.ic_info);
                break;

            case CANCEL:
                imgv.setImageResource(R.drawable.ic_block);
                imgv.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_inout));
                break;

            default:
                P.bug(false);
            }
        }

        titlev.setTextColor(context.getResources().getColor(getTitleColor(ii.state)));
        descv.setTextColor(context.getResources().getColor(getTextColor(ii.state)));
        datev.setTextColor(context.getResources().getColor(getTextColor(ii.state)));
        infov.setTextColor(context.getResources().getColor(getTextColor(ii.state)));
    }
}
