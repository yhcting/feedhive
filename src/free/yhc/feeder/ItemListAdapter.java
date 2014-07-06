/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
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

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.database.StaleDataException;
import android.graphics.drawable.AnimationDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.BaseBGTask;
import free.yhc.feeder.model.ContentsManager;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ItemListAdapter extends AsyncCursorListAdapter implements
AsyncCursorAdapter.ItemBuilder {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ItemListAdapter.class);

    private final DBPolicy  mDbp = DBPolicy.get();
    private final RTTask    mRtt = RTTask.get();

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
        long        id              = -1;
        long        state           = 0;
        long        cid             = -1;
        boolean     hasDnFile       = false;
        boolean     bChannel        = false;
        String      cTitle          = "";
        String      title           = "";
        String      desc            = "";
        String      pubDate         = "";
        String      link            = "";
        String      enclosureLen    = "";
        String      enclosureUrl    = "";
        String      enclosureType   = "";
    }

    private class DownloadProgressListener extends BaseBGTask.OnEventListener {
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
        private volatile TextView tv = mDummyTextView;

        DownloadProgressListener(TextView tv) {
            this.tv = tv;
        }

        void
        setTextView(TextView tv) {
            if (null == tv)
                this.tv = mDummyTextView;
            else
                this.tv = tv;
        }

        @Override
        public void
        onProgress(BaseBGTask task, int progress) {
            if (0 > progress) // Fail to get progress.
                tv.setText("??%");
            else
                tv.setText(progress + "%");
            tv.postInvalidate();
        }
    }

    interface OnActionListener {
        void onFavoriteClick(ItemListAdapter adapter, ImageView ibtn, int position, long id, long state);
    }

    public static class ImageViewFavorite extends ImageView {
        long id     = -1;
        long state  = 0;
        int  position = -1;

        public
        ImageViewFavorite(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    private final int
    getTitleColor(long stateFlag) {
        return Feed.Item.isStateOpenNew(stateFlag)?
                R.color.title_color_new: R.color.text_color_opened;
    }

    private final int
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
    ItemListAdapter(Context             context,
                    Cursor              cursor,
                    ListView            lv,
                    final int           dataReqSz,
                    final int           maxArrSz,
                    OnActionListener    listener) {
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
            eAssert(false);
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
    bindView(View v, final Context context, int position)  {
        if (!preBindView(v, context, position))
            return;

        ItemInfo ii = (ItemInfo)getItem(position);

        final boolean           favorite    = Feed.Item.isStatFavOn(ii.state);
        final TextView          channelv    = (TextView)v.findViewById(R.id.channel);
        final TextView          titlev      = (TextView)v.findViewById(R.id.title);
        final TextView          descv       = (TextView)v.findViewById(R.id.description);
        final ProgressTextView  progressv   = (ProgressTextView)v.findViewById(R.id.progress);
        final TextView          datev       = (TextView)v.findViewById(R.id.date);
        final TextView          infov       = (TextView)v.findViewById(R.id.info);
        final ImageView         imgv        = (ImageView)v.findViewById(R.id.image);
        final ImageViewFavorite favImgv     = (ImageViewFavorite)v.findViewById(R.id.favorite);

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
            infov.setText(Utils.getExtentionFromUrl(ii.enclosureUrl) + " : " + ii.enclosureLen);

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
            RTTask.TaskState dnState = mRtt.getState(ii.id, RTTask.Action.DOWNLOAD);
            switch(dnState) {
            case IDLE:
                imgv.setImageResource(R.drawable.download_anim0);
                break;

            case READY:
                imgv.setImageResource(R.drawable.ic_pause);
                break;

            case RUNNING:
                imgv.setImageResource(R.anim.download);
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
                Utils.getUiHandler().post(new Runnable() {
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
                mRtt.unbind(progressv);
                mRtt.bind(ii.id, RTTask.Action.DOWNLOAD, progressv, listener);
                progressv.setVisibility(View.VISIBLE);
                break;

            case FAILED:
                imgv.setImageResource(R.drawable.ic_info);
                break;

            case CANCELING:
                imgv.setImageResource(R.drawable.ic_block);
                imgv.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_inout));
                break;

            default:
                eAssert(false);
            }
        }

        titlev.setTextColor(context.getResources().getColor(getTitleColor(ii.state)));
        descv.setTextColor(context.getResources().getColor(getTextColor(ii.state)));
        datev.setTextColor(context.getResources().getColor(getTextColor(ii.state)));
        infov.setTextColor(context.getResources().getColor(getTextColor(ii.state)));
    }
}
