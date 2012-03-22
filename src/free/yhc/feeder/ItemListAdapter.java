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
    private int       layout = -1;
    private long      cid    = -1;
    private DBPolicy  dbp    = DBPolicy.S();

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

    public ItemListAdapter(Context context, int layout, Cursor c, long cid) {
        super(context, layout, c);
        this.layout = layout;
        this.cid = cid;
        dummyTextView = new TextView(context);
    }

    private void
    bindViewLink(View view, Context context, Cursor c, Feed.Item.State state) {
        TextView titlev = (TextView)view.findViewById(R.id.title);
        TextView descv  = (TextView)view.findViewById(R.id.description);
        TextView date   = (TextView)view.findViewById(R.id.date);

        titlev.setText(c.getString(c.getColumnIndex(DB.ColumnItem.TITLE.getName())));
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnItem.DESCRIPTION.getName())));
        date.setText(c.getString(c.getColumnIndex(DB.ColumnItem.PUBDATE.getName())));


        titlev.setTextColor(context.getResources().getColor(state.getTitleColor()));
        descv.setTextColor(context.getResources().getColor(state.getTextColor()));
        date.setTextColor(context.getResources().getColor(state.getTextColor()));
    }

    private void
    bindViewEnclosure(View view, Context context, Cursor c, Feed.Item.State state) {
        final TextView titlev = (TextView)view.findViewById(R.id.title);
        final TextView descv  = (TextView)view.findViewById(R.id.description);
        final ProgressTextView progress = (ProgressTextView)view.findViewById(R.id.progress);
        final TextView date   = (TextView)view.findViewById(R.id.date);
        final TextView length = (TextView)view.findViewById(R.id.length);
        final ImageView img   = (ImageView)view.findViewById(R.id.image);

        long id = c.getLong(c.getColumnIndex(DB.ColumnItem.ID.getName()));
        String title = c.getString(c.getColumnIndex(DB.ColumnItem.TITLE.getName()));
        String url = c.getString(c.getColumnIndex(DB.ColumnItem.ENCLOSURE_URL.getName()));

        titlev.setText(title);
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnItem.DESCRIPTION.getName())));
        date.setText(c.getString(c.getColumnIndex(DB.ColumnItem.PUBDATE.getName())));
        length.setText(c.getString(c.getColumnIndex(DB.ColumnItem.ENCLOSURE_LENGTH.getName())));


        // In case of enclosure, icon is decided by file is in the disk or not.
        // TODO:
        //   add proper icon (or representation...)
        Animation anim = img.getAnimation();
        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        img.setAlpha(1.0f);
        progress.setVisibility(View.GONE);

        if (new File(UIPolicy.getItemFilePath(cid, id, title, url)).exists()) {
            img.setImageResource(R.drawable.ic_save);
        } else {
            RTTask.StateDownload dnState = RTTask.S().getDownloadState(cid, id);
            if (RTTask.StateDownload.Idle == dnState) {
                img.setImageResource(R.drawable.download_anim0);
            } else if (RTTask.StateDownload.Downloading == dnState) {
                img.setImageResource(R.drawable.download);
                // Why "post runnable and start animation?"
                // In Android 4.0.3 (ICS)
                //   putting "((AnimationDrawable)img.getDrawable()).start();" is enough.
                //   So, below code works well enough.
                ((AnimationDrawable)img.getDrawable()).start();
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
                DownloadProgressOnEvent onEvent = new DownloadProgressOnEvent(progress);
                progress.switchOnEvent(onEvent);
                RTTask.S().unbind(progress);
                RTTask.S().bindDownload(cid, id, progress, onEvent);
                progress.setVisibility(View.VISIBLE);
            } else if (RTTask.StateDownload.DownloadFailed == dnState) {
                img.setImageResource(R.drawable.ic_info);
            } else if (RTTask.StateDownload.Canceling == dnState) {
                img.setImageResource(R.drawable.ic_block);
                img.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_inout));
            } else
                eAssert(false);
        }

        titlev.setTextColor(context.getResources().getColor(state.getTitleColor()));
        descv.setTextColor(context.getResources().getColor(state.getTextColor()));
        date.setTextColor(context.getResources().getColor(state.getTextColor()));
        length.setTextColor(context.getResources().getColor(state.getTextColor()));
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
        String statestr;
        statestr = dbp.getItemInfoString(
                            cid,
                            c.getLong(c.getColumnIndex(DB.ColumnItem.ID.getName())),
                            DB.ColumnItem.STATE);
        Feed.Item.State state = Feed.Item.State.convert(statestr);

        switch (layout) {
        case R.layout.item_row_link:
            bindViewLink(view, context, c, state);
            break;
        case R.layout.item_row_enclosure:
            bindViewEnclosure(view, context, c, state);
            break;
        default:
            eAssert(false);
        }
    }

}
