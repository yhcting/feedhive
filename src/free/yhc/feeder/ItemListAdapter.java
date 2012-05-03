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

import java.util.HashMap;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
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

public class ItemListAdapter extends ResourceCursorAdapter implements
UnexpectedExceptionHandler.TrackedModule {
    private DBPolicy  dbp = DBPolicy.S();
    // To speed up refreshing list and dataSetChanged in case of only few-list-item are changed.
    // (usually, only one item is changed.)
    // This SHOULD NOT used when number of list item or order of list item are changed.
    private HashMap<Long, Object> unchangedMap = new HashMap<Long, Object>();

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

    public static final int
    getTitleColor(long stateFlag) {
        return Feed.Item.isStateNew(stateFlag)?
                R.color.title_color_new: R.color.text_color_opened;
    }

    public static final int
    getTextColor(long stateFlag) {
        return Feed.Item.isStateNew(stateFlag)?
                R.color.text_color_new: R.color.text_color_opened;
    }

    public void
    addUnchanged(long id) {
        unchangedMap.put(id, new Object());
    }

    public void
    clearUnchanged() {
        unchangedMap.clear();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ItemListAdapter ]";
    }

    public ItemListAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
        UnexpectedExceptionHandler.S().registerModule(this);
        dummyTextView = new TextView(context);
    }

    @Override
    public void
    bindView(View view, Context context, Cursor c) {
        final long id = c.getLong(c.getColumnIndex(DB.ColumnItem.ID.getName()));

        if (null != unchangedMap.get(id)) {
            unchangedMap.remove(id);
            return;
        }

        // NOTE
        //   Check performance drop for this DB access...
        //   If this is critical, we need to find other solution for updating state.
        //   It seems OK on OMAP4430.
        //   But, definitely slower than before...
        // TODO
        //   Do performance check on low-end-device.
        final long state = dbp.getItemInfoLong(id, DB.ColumnItem.STATE);

        final TextView channelv = (TextView)view.findViewById(R.id.channel);
        final TextView titlev = (TextView)view.findViewById(R.id.title);
        final TextView descv  = (TextView)view.findViewById(R.id.description);
        final ProgressTextView progressv = (ProgressTextView)view.findViewById(R.id.progress);
        final TextView datev   = (TextView)view.findViewById(R.id.date);
        final TextView lengthv = (TextView)view.findViewById(R.id.length);
        final ImageView imgv   = (ImageView)view.findViewById(R.id.image);

        int cidx = c.getColumnIndex(DB.ColumnItem.CHANNELID.getName());
        if (cidx < 0)
            channelv.setVisibility(View.GONE);
        else
            channelv.setText(DBPolicy.S().getChannelInfoString(c.getLong(cidx), DB.ColumnChannel.TITLE));

        String title = c.getString(c.getColumnIndex(DB.ColumnItem.TITLE.getName()));

        titlev.setText(title);
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnItem.DESCRIPTION.getName())));
        datev.setText(c.getString(c.getColumnIndex(DB.ColumnItem.PUBDATE.getName())));

        String length = c.getString(c.getColumnIndex(DB.ColumnItem.ENCLOSURE_LENGTH.getName()));
        if (length.isEmpty())
            length = " ";

        lengthv.setText(length);

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
                ((AnimationDrawable)imgv.getDrawable()).start();
                //
                // In Android 3.2 (HC)
                //   without using 'post', animation doesn't start when start itemListActivity.
                //   It's definitely HC bug.
                //   In this case, below code works.
                //
                // This program's target platform is ICS
                /*
                img.post(new Runnable() {
                    @Override
                    public void run() {
                        ((AnimationDrawable)img.getDrawable()).start();
                    }
                });
                */
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
        lengthv.setTextColor(context.getResources().getColor(getTextColor(state)));
    }

    @Override
    protected void finalize() throws Throwable {
        UnexpectedExceptionHandler.S().unregisterModule(this);
        super.finalize();
    }
}
