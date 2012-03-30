package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;

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

public class ItemListAdapter extends ResourceCursorAdapter {
    private long      act = Feed.Channel.FActDefault;
    private DBPolicy  dbp = DBPolicy.S();

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
        public void onProgress(BGTask task, int progress) {
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

    public ItemListAdapter(Context context, int layout, Cursor c,
                           long act) {
        super(context, layout, c);
        this.act = act;
        dummyTextView = new TextView(context);
    }

    @Override
    public void
    bindView(View view, Context context, Cursor c) {
        // NOTE
        //   Check performance drop for this DB access...
        //   If this is critical, we need to find other solution for updating state.
        //   It seems OK on OMAP4430.
        //   But, definitely slower than before...
        // TODO
        //   Do performance check on low-end-device.
        final long state = dbp.getItemInfoLong(c.getLong(c.getColumnIndex(DB.ColumnItem.ID.getName())),
                                               DB.ColumnItem.STATE);

        final TextView titlev = (TextView)view.findViewById(R.id.title);
        final TextView descv  = (TextView)view.findViewById(R.id.description);
        final ProgressTextView progressv = (ProgressTextView)view.findViewById(R.id.progress);
        final TextView datev   = (TextView)view.findViewById(R.id.date);
        final TextView lengthv = (TextView)view.findViewById(R.id.length);
        final ImageView imgv   = (ImageView)view.findViewById(R.id.image);

        long id = c.getLong(c.getColumnIndex(DB.ColumnItem.ID.getName()));
        String title = c.getString(c.getColumnIndex(DB.ColumnItem.TITLE.getName()));

        titlev.setText(title);
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnItem.DESCRIPTION.getName())));
        datev.setText(c.getString(c.getColumnIndex(DB.ColumnItem.PUBDATE.getName())));

        String length = c.getString(c.getColumnIndex(DB.ColumnItem.ENCLOSURE_LENGTH.getName()));
        if (length.isEmpty())
            length = " ";

        lengthv.setText(length);


        boolean bDataSaved = false;
        if (Feed.Channel.isActTgtLink(act)) {
            // This is dynamic data - changed by user in runtime.
            // So, let's read from database directly.
            byte[] htmldata = DBPolicy.S().getItemInfoData(id, DB.ColumnItem.RAWDATA);
            bDataSaved = (htmldata.length > 0);
        } else if (Feed.Channel.isActTgtEnclosure(act)) {
            String url = c.getString(c.getColumnIndex(DB.ColumnItem.ENCLOSURE_URL.getName()));
            bDataSaved = new File(UIPolicy.getItemFilePath(id, title, url)).exists();
        } else
            eAssert(false);


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

        if (bDataSaved) {
            imgv.setImageResource(R.drawable.ic_save);
        } else {
            RTTask.StateDownload dnState = RTTask.S().getDownloadState(id);
            if (RTTask.StateDownload.Idle == dnState) {
                imgv.setImageResource(R.drawable.download_anim0);
            } else if (RTTask.StateDownload.Downloading == dnState) {
                imgv.setImageResource(R.drawable.download);
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
                RTTask.S().bindDownload(id, progressv, onEvent);
                progressv.setVisibility(View.VISIBLE);
            } else if (RTTask.StateDownload.DownloadFailed == dnState) {
                imgv.setImageResource(R.drawable.ic_info);
            } else if (RTTask.StateDownload.Canceling == dnState) {
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

}
