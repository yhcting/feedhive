package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.logI;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.FeederException;
import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class ItemReaderActivity extends ListActivity  implements ItemLoader.OnPostExecute {
    private long    channelid = -1;

    private Cursor
    adapterCursorQuery(long cid) {
        return DB.db().query(
            DB.getRssItemTableName(cid),
            new DB.ColumnRssItem[]
                    { DB.ColumnRssItem.ID, // Mandatory.
                      DB.ColumnRssItem.TITLE,
                      DB.ColumnRssItem.DESCRIPTION,
                      DB.ColumnRssItem.CHANNELID },
            null, null, null, null, null);
    }

    private void
    refreshList() {
        // [ NOTE ]
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery(channelid);
        ((ItemListCursorAdapter)getListAdapter()).swapCursor(newCursor).close();
        ((ItemListCursorAdapter)getListAdapter()).notifyDataSetChanged();
    }

    // Implements of ItemLoader.OnPostExecute
    // See ItemLoader for details of parameter 'result'
    public void
    onPostExecute(FeederException.Err result, long cid, boolean bChannelInfoUpdated) {
        // if fail to open url use existing DB information.
        if (result != FeederException.Err.NoErr) {
            // TODO : Error handling....
            return;
        }

        if (result == FeederException.Err.IOOpenUrl) {
            Toast.makeText(this, R.string.err_open_url, 2);
        }

        if (bChannelInfoUpdated)
            setResult(RESULT_OK, null);

        refreshList();
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        channelid = getIntent().getLongExtra("channelid", 0);
        logI("* RSS Item to read : " + channelid + "\n");
        setContentView(R.layout.item_reader);
        setListAdapter(new ItemListCursorAdapter(this, R.layout.item_list_row, adapterCursorQuery(channelid)));
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
            new ItemLoader(this, this).execute(channelid);
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
