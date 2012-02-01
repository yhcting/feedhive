package free.yhc.feeder;

import free.yhc.feeder.model.DB;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

class ItemListCursorAdapter extends ResourceCursorAdapter {
    ItemListCursorAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
    }

    @Override
    public void bindView(View view, Context context, Cursor c) {
        TextView titlev = (TextView)view.findViewById(R.id.title);
        TextView descv = (TextView)view.findViewById(R.id.description);

        titlev.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.TITLE.getName())));
        descv.setText(c.getString(c.getColumnIndex(DB.ColumnRssItem.DESCRIPTION.getName())));
    }

}
