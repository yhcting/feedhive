package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.text.DateFormat;
import java.util.Date;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DB.ColumnChannel;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.RTTask;

public class ChannelListAdapter extends ResourceCursorAdapter {
    private OnAction  onAction = null;

    interface OnAction {
        void onUpdateClick(ImageView ibtn, long cid);
    }

    public static class ImageViewEx extends ImageView {
        long cid = -1;

        public
        ImageViewEx(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    ChannelListAdapter(Context context, int layout, Cursor c, OnAction actionListener) {
        super(context, layout, c);
        onAction = actionListener;
    }

    @Override
    public void
    bindView(View view, final Context context, final Cursor c) {
        long cid = c.getLong(c.getColumnIndex(DB.ColumnChannel.ID.getName()));

        String title = c.getString(c.getColumnIndex(DB.ColumnChannel.TITLE.getName()));
        String desc;

        // If title is empty, Just show URL of this channel.
        // (This is to show that "this url is not normal or well-formatted.")
        if (title.isEmpty()) {
            title = c.getString(c.getColumnIndex(DB.ColumnChannel.URL.getName()));
            desc = title;
        } else {
            desc  = c.getString(c.getColumnIndex(DB.ColumnChannel.DESCRIPTION.getName()));
        }

        // date to readable string
        Date lastupdate = new Date(c.getLong(c.getColumnIndex(DB.ColumnChannel.LASTUPDATE.getName())));
        String date = DateFormat.getInstance().format(lastupdate);

        // === Set 'age' ===
        // calculate age and convert to readable string.
        String age;
        { // just for temporal variable scope
            long ageTime = new Date().getTime() - lastupdate.getTime();
            // Show "day:hours"
            long ageHours = ageTime/ (1000 * 60 * 60);
            long ageDay = ageHours / 24;
            ageHours %= 24;
            age = String.format("%2d:%2d", ageDay, ageHours);
        }

        // === Set 'nr_new' ===
        String nrNew = "" + (DBPolicy.S().getItemInfoLastId(cid)
                              - DBPolicy.S().getChannelInfoLong(cid, ColumnChannel.OLDLAST_ITEMID));

        int ci; // column index;
        ci = c.getColumnIndex(DB.ColumnChannel.IMAGEBLOB.getName());
        Bitmap bm = null;
        if (Cursor.FIELD_TYPE_NULL != c.getType(ci)) {
            byte[] imgRaw = c.getBlob(c.getColumnIndex(DB.ColumnChannel.IMAGEBLOB.getName()));
            bm = BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length);
        }

        ImageViewEx chIcon = (ImageViewEx)view.findViewById(R.id.image);
        chIcon.cid = cid;
        chIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewEx iv = (ImageViewEx)v;
                onAction.onUpdateClick(iv, iv.cid);
            }
        });

        // NOTE
        //   "anim.cancel -> anim.reset -> setAlpha(1.0f)", all these three are required
        //     to restore from animation to normal image viewing.
        //   without them, alpha value during animation remains even after animation is cancelled.
        Animation anim = chIcon.getAnimation();
        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        chIcon.setAlpha(1.0f);

        if (null == bm)
            // fail to decode.
            chIcon.setImageResource(R.drawable.ic_block);
        else
            chIcon.setImageBitmap(bm);

        RTTask.StateUpdate state = RTTask.S().getUpdateState(cid);
        if (RTTask.StateUpdate.Idle == state) {
            ;
        } else if (RTTask.StateUpdate.Updating == state) {
            chIcon.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_inout));
        } else if (RTTask.StateUpdate.Canceling == state) {
            chIcon.setImageResource(R.drawable.ic_info);
            chIcon.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_inout));
        } else if (RTTask.StateUpdate.UpdateFailed == state) {
            chIcon.setImageResource(R.drawable.ic_info);
        } else
            eAssert(false);

        ((TextView)view.findViewById(R.id.title)).setText(title);
        ((TextView)view.findViewById(R.id.description)).setText(desc);
        ((TextView)view.findViewById(R.id.date)).setText(date);
        ((TextView)view.findViewById(R.id.age)).setText(age);
        ((TextView)view.findViewById(R.id.nr_new)).setText(nrNew);
    }

}
