package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;

public class ItemListAdapter extends ResourceCursorAdapter {
    private int       layout = -1;
    private long      cid    = -1;
    private DBPolicy  dbp    = DBPolicy.S();

    public ItemListAdapter(Context context, int layout, Cursor c, long cid) {
        super(context, layout, c);
        this.layout = layout;
        this.cid = cid;
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
        TextView titlev = (TextView)view.findViewById(R.id.title);
        TextView descv  = (TextView)view.findViewById(R.id.description);
        TextView date   = (TextView)view.findViewById(R.id.date);
        TextView length = (TextView)view.findViewById(R.id.length);
        ImageView img   = (ImageView)view.findViewById(R.id.image);

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
        if (new File(UIPolicy.getItemFilePath(cid, id, title, url)).exists())
            img.setImageResource(R.drawable.ondisk);
        else {
            Animation anim = img.getAnimation();
            RTTask.StateDownload dnState = RTTask.S().getDownloadState(cid, id);
            if (RTTask.StateDownload.Idle == dnState) {
                if (null != anim)
                    anim.cancel();
                img.setImageResource(R.drawable.onweb);
            } else if (RTTask.StateDownload.Downloading == dnState) {
                img.setImageResource(R.drawable.onweb);
                img.startAnimation(AnimationUtils.loadAnimation(context, R.anim.rotate_spin));
            } else if (RTTask.StateDownload.DownloadFailed == dnState) {
                if (null != anim)
                    anim.cancel();
                img.setImageResource(R.drawable.ic_info);
            } else if (RTTask.StateDownload.Canceling == dnState) {
                img.setImageResource(R.drawable.ic_info);
                img.startAnimation(AnimationUtils.loadAnimation(context, R.anim.rotate_spin));
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
        try {
            statestr = dbp.getItemInfoString(
                            cid,
                            c.getLong(c.getColumnIndex(DB.ColumnItem.ID.getName())),
                            DB.ColumnItem.STATE);
        } catch (InterruptedException e) {
            eAssert(false);
            statestr = "NEW";
        }
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
