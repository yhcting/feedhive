package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Feed;
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

        String title = c.getString(c.getColumnIndex(DB.ColumnItem.TITLE.getName()));
        String url = c.getString(c.getColumnIndex(DB.ColumnItem.ENCLOSURE_URL.getName()));

        titlev.setText(title);
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnItem.DESCRIPTION.getName())));
        date.setText(c.getString(c.getColumnIndex(DB.ColumnItem.PUBDATE.getName())));
        length.setText(c.getString(c.getColumnIndex(DB.ColumnItem.ENCLOSURE_LENGTH.getName())));


        // In case of enclosure, icon is decided by file is in the disk or not.
        // TODO:
        //   add proper icon (or representation...)
        int icon;
        if (new File(UIPolicy.getItemFilePath(cid, title, url)).exists())
            icon = R.drawable.ondisk;
        else
            icon = R.drawable.onweb;;

        img.setImageResource(icon);
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
