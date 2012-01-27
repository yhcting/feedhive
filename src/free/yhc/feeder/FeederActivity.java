package free.yhc.feeder;

import static free.yhc.feeder.Utils.eAssert;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class FeederActivity extends ListActivity implements ItemLoader.OnPostExecute {
    // Request codes.
    static final int RCAddChannel = 0;

    private Cursor
    adapterCursorQuery() {
        return DB.db().query(
                DB.TABLE_RSSCHANNEL,
                new DB.ColumnRssChannel[]
                        { DB.ColumnRssChannel.ID, // Mandatory.
                          DB.ColumnRssChannel.TITLE,
                          DB.ColumnRssChannel.DESCRIPTION,
                          DB.ColumnRssChannel.URL }
                );
    }

    private void
    refreshList() {
        // [ NOTE ]
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery();
        ((ChannelListCursorAdapter)getListAdapter()).swapCursor(newCursor).close();
        ((ChannelListCursorAdapter)getListAdapter()).notifyDataSetChanged();
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setListAdapter(new ChannelListCursorAdapter(this, R.layout.channel_list_row, adapterCursorQuery()));
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

        Intent intent = new Intent(this, ItemReaderActivity.class);
        intent.putExtra("channelid", cid);
        startActivity(intent);

        if (bChannelInfoUpdated)
            refreshList();

    }

    public void
    onAllButtonClicked(View v) {
        // id '0' means 'all'
        new ItemLoader(this, this).execute(0L);
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
        new ItemLoader(this, this).execute(id);
    }
    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainopt, menu);
		return true;
	}

	@Override
	public boolean
	onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case R.id.add_channel: {
	        Intent intent = new Intent(this, AddChannelActivity.class);
	        startActivityForResult(intent, RCAddChannel);
		} break;
		}
		return true;
	}

	protected void
	onResult_addChannel(Intent data) {
	    String url = data.getStringExtra("url");
	    eAssert(url != null);

	    RSS.Channel ch = new RSS.Channel();
	    ch.url = url;

	    // Just add url. at this times.
	    new DBPolicy().insertRSSChannel(ch);

	    refreshList();
	}

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;

        switch(requestCode) {
        case RCAddChannel:
            onResult_addChannel(data);
            break;
        }
    }

    @Override
    protected void
    onDestroy() {
        ((ChannelListCursorAdapter)getListAdapter()).getCursor().close();
        super.onDestroy();
    }
}