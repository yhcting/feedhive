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

public class FeederActivity extends ListActivity {
    // Request codes.
    static final int RCAddChannel   = 0;
    static final int RCReadChannel  = 1;

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

    public void
    onAllButtonClicked(View v) {
        // id '0' means 'all'
        Intent intent = new Intent(this, ItemReaderActivity.class);
        intent.putExtra("channelid", 0);
        startActivityForResult(intent, RCReadChannel);
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(this, ItemReaderActivity.class);
        intent.putExtra("channelid", id);
        startActivityForResult(intent, RCReadChannel);
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

        case RCReadChannel:
            refreshList();
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