package free.yhc.feeder;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class ChannelListCursorAdapter extends ResourceCursorAdapter {
    ChannelListCursorAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
    }

    @Override
    public void
    bindView(View view, Context context, Cursor c) {
        TextView titlev = (TextView)view.findViewById(R.id.title);
        TextView descv = (TextView)view.findViewById(R.id.description);

        String title = c.getString(c.getColumnIndex(DB.ColumnRssChannel.TITLE.getName()));
        if (title.isEmpty()) {
            titlev.setText(c.getString(c.getColumnIndex(DB.ColumnRssChannel.URL.getName())));
            descv.setText("");
        } else {
            titlev.setText(title);
            descv.setText(c.getString(c.getColumnIndex(DB.ColumnRssChannel.DESCRIPTION.getName())));
        }
    }
}
