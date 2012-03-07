package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
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
        String title = c.getString(c.getColumnIndex(DB.ColumnChannel.TITLE.getName()));
        String desc  = c.getString(c.getColumnIndex(DB.ColumnChannel.DESCRIPTION.getName()));
        String date  = c.getString(c.getColumnIndex(DB.ColumnChannel.LASTUPDATE.getName()));

        int ci; // column index;
        ci = c.getColumnIndex(DB.ColumnChannel.IMAGEBLOB.getName());
        Bitmap bm = null;
        if (Cursor.FIELD_TYPE_NULL != c.getType(ci)) {
            byte[] imgRaw= c.getBlob(c.getColumnIndex(DB.ColumnChannel.IMAGEBLOB.getName()));
            bm = BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length);
        }

        long cid = c.getLong(c.getColumnIndex(DB.ColumnChannel.ID.getName()));

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
    }

}
