package free.yhc.feeder;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import free.yhc.feeder.model.DB;

public class ChannelListAdapter extends ResourceCursorAdapter {

    ChannelListAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
    }

    @Override
    public void bindView(View view, Context context, Cursor c) {
        String title = c.getString(c.getColumnIndex(DB.ColumnFeedChannel.TITLE.getName()));
        String desc  = c.getString(c.getColumnIndex(DB.ColumnFeedChannel.DESCRIPTION.getName()));
        String date  = c.getString(c.getColumnIndex(DB.ColumnFeedChannel.LASTUPDATE.getName()));

        int ci; // column index;
        ci = c.getColumnIndex(DB.ColumnFeedChannel.IMAGEBLOB.getName());
        Bitmap bm = null;
        if (Cursor.FIELD_TYPE_NULL != c.getType(ci)) {
            byte[] imgRaw= c.getBlob(c.getColumnIndex(DB.ColumnFeedChannel.IMAGEBLOB.getName()));
            bm = BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length);
        }

        ((TextView)view.findViewById(R.id.title)).setText(title);
        ((TextView)view.findViewById(R.id.description)).setText(desc);
        ((TextView)view.findViewById(R.id.date)).setText(date);
        if (null == bm)
            // fail to decode.
            ((ImageView)view.findViewById(R.id.image)).setImageResource(R.drawable.fail_decode);
        else
            ((ImageView)view.findViewById(R.id.image)).setImageBitmap(bm);
    }

}
