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
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskDownloadToFile;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ItemListAdapter extends CustomResourceCursorAdapter implements
UnexpectedExceptionHandler.TrackedModule {
    private Handler   handler = new Handler();
    private DBPolicy  dbp = DBPolicy.S();
    private OnAction  onAction = null;
    // To avoid using mutex in "DownloadProgressOnEvent", dummyTextView is used.
    // See "DownloadProgressOnEvent" for details
    private TextView  dummyTextView;

    public static class ProgressTextView extends TextView {
        private DownloadProgressOnEvent onEvent = null;

        public ProgressTextView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        void switchOnEvent(DownloadProgressOnEvent newOnEvent) {
            if (null != onEvent)
                onEvent.setTextView(null);
            onEvent = newOnEvent;
            newOnEvent.setTextView(this);
        }
    }

    private class DownloadProgressOnEvent implements BGTask.OnEvent<BGTaskDownloadToFile.Arg, Object> {
        // dummyTextView is used for default 'tv' value.
        // If not, 'null' should be used.
        // In this case, code in 'onProgress' should be like below
        //     if (null != tv)
        //         tv.setText(....)
        // Then, above two line of code should be preserved with mutex or 'synchronized'
        // Why?
        // 'tv' value can be changed after (null != tv) comparison.
        // To avoid this synchronization, dummyTextView is used.
        // Keep in mind that assigning reference is atomic operation in Java.
        private volatile TextView tv = dummyTextView;

        DownloadProgressOnEvent(TextView tv) {
            this.tv = tv;
        }

        void setTextView(TextView tv) {
            if (null == tv)
                this.tv = dummyTextView;
            else
                this.tv = tv;
        }

        @Override
        public void onPreRun(BGTask task) {}
        @Override
        public void onPostRun(BGTask task, Err result) {}
        @Override
        public void onCancel(BGTask task, Object param) {}
        @Override
        public void onProgress(BGTask task, long progress) {
            if (0 > progress) // Fail to get progress.
                tv.setText("??%");
            else
                tv.setText(progress + "%");
            tv.postInvalidate();
        }
    }

    interface OnAction {
        void onFavoriteClick(ImageView ibtn, long id, long state);
    }

    public static class ImageViewFavorite extends ImageView {
        long id     = -1;
        long state  = 0;

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
        return "[ ItemListAdapter ]";
    }

    public ItemListAdapter(Context context, int layout, Cursor c, OnAction actionListener) {
        super(context, layout, c);
        UnexpectedExceptionHandler.S().registerModule(this);
        dummyTextView = new TextView(context);
        onAction = actionListener;
    }

    @Override
    public void
    bindView(View view, Context context, Cursor c) {
        final long id = getCursorLong(c, DB.ColumnItem.ID);

        try {
            if (!isChanged(id))
                return;
        } finally {
            clearChangeState(id);
        }

        // NOTE
        //   Check performance drop for this DB access...
        //   If this is critical, we need to find other solution for updating state.
        //   It seems OK on OMAP4430.
        //   But, definitely slower than before...
        // TODO
        //   Do performance check on low-end-device.
        final long state = dbp.getItemInfoLong(id, DB.ColumnItem.STATE);
        final boolean favorite = Feed.Item.isStatFavOn(state);

        final TextView channelv     = (TextView)view.findViewById(R.id.channel);
        final TextView titlev       = (TextView)view.findViewById(R.id.title);
        final TextView descv        = (TextView)view.findViewById(R.id.description);
        final ProgressTextView progressv = (ProgressTextView)view.findViewById(R.id.progress);
        final TextView datev        = (TextView)view.findViewById(R.id.date);
        final TextView infov        = (TextView)view.findViewById(R.id.info);
        final ImageView imgv        = (ImageView)view.findViewById(R.id.image);
        final ImageViewFavorite favImgv   = (ImageViewFavorite)view.findViewById(R.id.favorite);

        int cidx = c.getColumnIndex(DB.ColumnItem.CHANNELID.getName());
        if (cidx < 0)
            channelv.setVisibility(View.GONE);
        else
            channelv.setText(DBPolicy.S().getChannelInfoString(c.getLong(cidx), DB.ColumnChannel.TITLE));

        // Set favorite button.
        favImgv.id = id;
        favImgv.state = state;
        favImgv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewFavorite iv = (ImageViewFavorite)v;
                onAction.onFavoriteClick(iv, iv.id, iv.state);
            }
        });

        if (favorite)
            favImgv.setImageResource(R.drawable.favorite_on);
        else
            favImgv.setImageResource(R.drawable.favorite_off);

        String title = getCursorString(c, DB.ColumnItem.TITLE);

        titlev.setText(title);
        descv.setText(getCursorString(c, DB.ColumnItem.DESCRIPTION));
        datev.setText(getCursorString(c, DB.ColumnItem.PUBDATE));

        String length = getCursorString(c, DB.ColumnItem.ENCLOSURE_LENGTH);
        String url = getCursorString(c, DB.ColumnItem.ENCLOSURE_URL);
        if (url.isEmpty())
            infov.setText("html");
        else
            infov.setText(Utils.getExtentionFromUrl(url) + " : " + length);

        // In case of enclosure, icon is decided by file is in the disk or not.
        // TODO:
        //   add proper icon (or representation...)
        Animation anim = imgv.getAnimation();
        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        imgv.setAlpha(1.0f);
        progressv.setVisibility(View.GONE);

        if (UIPolicy.getItemDataFile(id).exists()) {
            imgv.setImageResource(R.drawable.ic_save);
        } else {
            RTTask.TaskState dnState = RTTask.S().getState(id, RTTask.Action.Download);
            if (RTTask.TaskState.Idle == dnState) {
                imgv.setImageResource(R.drawable.download_anim0);
            } else if (RTTask.TaskState.Running == dnState
                       || RTTask.TaskState.Ready == dnState) {
                imgv.setImageResource(R.anim.download);
                // Why "post runnable and start animation?"
                // In Android 4.0.3 (ICS)
                //   putting "((AnimationDrawable)img.getDrawable()).start();" is enough.
                //   So, below code works well enough.
                // But in case of using UILifecycle below code doesn't work as expected just like
                //   the case of Android 3.2(HC).
                // ((AnimationDrawable)imgv.getDrawable()).start(); // <- this is not enough
                // =>> IT'S ANDROID PLATFORM'S BUG!!
                //
                // In Android 3.2 (HC)
                //   without using 'post', animation doesn't start when start itemListActivity.
                //   It's definitely HC bug.
                //   In this case, below code works.
                //
                // This program's target platform is ICS
                //
                // Another interesting point is, using 'imgv.post' doesn't work.
                // But, in case of using handler, it works.
                // I think it's definitely Android platform's BUG!
                // So, below code is just workaround of platform BUG!
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ((AnimationDrawable)imgv.getDrawable()).start();
                    }
                });

                // bind event listener to show progress
                DownloadProgressOnEvent onEvent = new DownloadProgressOnEvent(progressv);
                progressv.switchOnEvent(onEvent);
                RTTask.S().unbind(progressv);
                RTTask.S().bind(id, RTTask.Action.Download, progressv, onEvent);
                progressv.setVisibility(View.VISIBLE);
            } else if (RTTask.TaskState.Failed == dnState) {
                imgv.setImageResource(R.drawable.ic_info);
            } else if (RTTask.TaskState.Canceling == dnState) {
                imgv.setImageResource(R.drawable.ic_block);
                imgv.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_inout));
            } else
                eAssert(false);
        }

        titlev.setTextColor(context.getResources().getColor(getTitleColor(state)));
        descv.setTextColor(context.getResources().getColor(getTextColor(state)));
        datev.setTextColor(context.getResources().getColor(getTextColor(state)));
        infov.setTextColor(context.getResources().getColor(getTextColor(state)));
    }

    @Override
    protected void finalize() throws Throwable {
        UnexpectedExceptionHandler.S().unregisterModule(this);
        super.finalize();
    }
}
