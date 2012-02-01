package free.yhc.feeder;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ResourceCursorAdapter;

public class ChannelListAdapter extends ResourceCursorAdapter {

    ChannelListAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // TODO Auto-generated method stub
        // String title = c.getString(c.getColumnIndex(DB.ColumnRssChannel.TITLE.getName()));
    }

}
