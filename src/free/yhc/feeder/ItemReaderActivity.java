package free.yhc.feeder;

import static free.yhc.feeder.Utils.logI;
import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

public class ItemReaderActivity extends ListActivity {
    private Cursor
    adapterCursorQuery(long cid) {
        String selection = null;
        if (0 != cid)
            selection = DB.ColumnRssItem.CHANNELID.getName() + " = '" + cid + "'";

        return DB.db().query(
            DB.getRssItemTableName(cid),
            new DB.ColumnRssItem[]
                    { DB.ColumnRssItem.ID, // Mandatory.
                      DB.ColumnRssItem.TITLE,
                      DB.ColumnRssItem.DESCRIPTION,
                      DB.ColumnRssItem.CHANNELID },
            selection, null, null, null, null);
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long cid = getIntent().getLongExtra("channelid", 0);
        logI("* RSS Item to read : " + cid + "\n");
        setContentView(R.layout.item_reader);
        setListAdapter(new ItemListCursorAdapter(this, R.layout.item_list_row, adapterCursorQuery(cid)));
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
    }

    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.itemreaderopt, menu);
        return true;
    }

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        case R.id.refresh: {
        } break;
        }
        return true;
    }

    @Override
    protected void
    onDestroy() {
        ((ItemListCursorAdapter)getListAdapter()).getCursor().close();
        super.onDestroy();
    }
}
