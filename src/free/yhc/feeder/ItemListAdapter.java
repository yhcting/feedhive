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
import free.yhc.feeder.model.RSS;
import free.yhc.feeder.model.UIPolicy;

public class ItemListAdapter extends ResourceCursorAdapter {
    private int       layout = -1;
    private long      cid    = -1;
    private String    cTitle = null;
    private DBPolicy  dbp    = new DBPolicy();

    // Putting icon information inside 'RSS.ItemState' directly, is not good idea in terms of code structure.
    // We would better to decouple 'Model' from 'View/Control' as much as possible.
    // But, putting icon id to 'RSS.ItemState' makes another dependency between View/Control/Model.
    // So, instead of putting this data to 'RSS.ItemState', below function is used.
    private int
    iconFromState(RSS.ItemState state) {
        if (RSS.ItemState.NEW == state)
            return R.drawable.unactioned;
        else if (RSS.ItemState.OPENED == state)
            return R.drawable.actioned;
        else
            eAssert(false);
        return -1;
    }

    public ItemListAdapter(Context context, int layout, Cursor c, long cid) {
        super(context, layout, c);
        this.layout = layout;
        this.cid = cid;
        cTitle = dbp.getRSSChannelInfoString(cid, DB.ColumnRssChannel.TITLE);
    }

    private void
    bindViewLink(View view, Context context, Cursor c) {
        TextView titlev = (TextView)view.findViewById(R.id.title);
        TextView descv  = (TextView)view.findViewById(R.id.description);
        TextView date   = (TextView)view.findViewById(R.id.date);
        ImageView img   = (ImageView)view.findViewById(R.id.image);

        titlev.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.TITLE.getName())));
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.DESCRIPTION.getName())));
        date.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.PUBDATE.getName())));

        // NOTE
        //   Check performance drop for this DB access...
        //   If this is critical, we need to find other solution for updating state.
        //   It seems OK on OMAP4430.
        //   But, definitely slower than before...
        // TODO
        //   Do performance check on low-end-device.
        String state = dbp.getRSSItemInfoString(cid,
                                                c.getLong(c.getColumnIndex(DB.ColumnRssItem.ID.getName())),
                                                DB.ColumnRssItem.STATE);
        img.setImageResource(iconFromState(RSS.ItemState.convert(state)));
    }

    private void
    bindViewEnclosure(View view, Context context, Cursor c) {
        TextView titlev = (TextView)view.findViewById(R.id.title);
        TextView descv  = (TextView)view.findViewById(R.id.description);
        TextView date   = (TextView)view.findViewById(R.id.date);
        TextView length = (TextView)view.findViewById(R.id.length);
        ImageView img   = (ImageView)view.findViewById(R.id.image);

        String title = c.getString(c.getColumnIndex(DB.ColumnRssItem.TITLE.getName()));
        String url = c.getString(c.getColumnIndex(DB.ColumnRssItem.ENCLOSURE_URL.getName()));

        titlev.setText(title);
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.DESCRIPTION.getName())));
        date.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.PUBDATE.getName())));
        length.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.ENCLOSURE_LENGTH.getName())));


        // In case of enclosure, icon is decided by file is in the disk or not.
        // TODO:
        //   add proper icon (or representation...)
        RSS.ItemState state;
        if (new File(UIPolicy.getItemFilePath(cid, title, url)).exists())
            state = RSS.ItemState.OPENED;
        else
            state = RSS.ItemState.NEW;

        img.setImageResource(iconFromState(state));
    }

    @Override
    public void
    bindView(View view, Context context, Cursor c) {
        String state = c.getString(c.getColumnIndex(DB.ColumnRssItem.STATE.getName()));
        if (RSS.ItemState.DUMMY.name().equals(state)) {
            // First row : dummy row for special usage.
            view.findViewById(R.id.tv_update).setVisibility(View.VISIBLE);
            view.findViewById(R.id.item_layout).setVisibility(View.GONE);
            return;
        }

        view.findViewById(R.id.tv_update).setVisibility(View.GONE);
        view.findViewById(R.id.item_layout).setVisibility(View.VISIBLE);

        switch (layout) {
        case R.layout.item_row_link:
            bindViewLink(view, context, c);
            break;
        case R.layout.item_row_enclosure:
            bindViewEnclosure(view, context, c);
            break;
        default:
            eAssert(false);
        }
    }

}
