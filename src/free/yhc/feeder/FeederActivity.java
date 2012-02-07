package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.NetLoader;

public class FeederActivity extends ListActivity implements
AsyncTaskMy.OnPostExecute,
AsyncTaskMy.OnDoWork {
    // Request codes.
    private static final int ReqCReadChannel  = 0;

    public static final int  ResCReadChannelOk      = 0; // nothing special
    public static final int  ResCReadChannelUpdated = 1; // item list is updated.

    private Cursor
    adapterCursorQuery() {
        return new DBPolicy().queryChannel(
                    new DB.ColumnRssChannel[]
                        { DB.ColumnRssChannel.ID, // Mandatory.
                          DB.ColumnRssChannel.TITLE,
                          DB.ColumnRssChannel.DESCRIPTION,
                          DB.ColumnRssChannel.LASTUPDATE,
                          DB.ColumnRssChannel.IMAGEBLOB},
                    null);
    }

    private void
    refreshList() {
        // NOTE
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery();
        ((ChannelListAdapter)getListAdapter()).swapCursor(newCursor).close();
        ((ChannelListAdapter)getListAdapter()).notifyDataSetChanged();
    }


    private void
    onOpt_addChannel() {
        // Create "Enter Url" dialog
        LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.enter_url_dialog, (ViewGroup)findViewById(R.id.root));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);

        final AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.channel_url);
        // Set action for dialog.
        EditText edit = (EditText)layout.findViewById(R.id.url);
        edit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((KeyEvent.ACTION_DOWN == event.getAction())
                    && (KeyEvent.KEYCODE_ENTER == keyCode)) {
                    // Perform action on key press
                    //Toast.makeText(this, "hahah", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    //addChannel(((EditText)v).getText().toString());
                    //url = "http://old.ddanzi.com/appstream/ddradio.xml";
                    //url = "file:///data/test/total_news.xml";
                    //url = "http://www.khan.co.kr/rss/rssdata/total_news.xml";
                    //http://cast.vop.co.kr/kfline.xml
                    // addChannel("http://old.ddanzi.com/appstream/ddradio.xml"); // out-of spec.
                    // addChannel("http://cast.vop.co.kr/kfline.xml"); // good
                    // addChannel("http://cast.vop.co.kr/heenews.xml"); // good
                    // addChannel("http://www.khan.co.kr/rss/rssdata/total_news.xml"); // large xml
                    addChannel("http://cbspodcast.com/podcast/sisa/sisa.xml"); // large xml
                    return true;
                }
                return false;
            }
        });

        dialog.show();
    }

    // For NetLoaderTask
    @Override
    public void
    onPostExecute(Err result) {
        refreshList();
    }

    // For NetLoaderTask
    @Override
    public Err
    doWork(Object... objs) {
        Err err = Err.NoErr;

        for (Object o : objs) {
            String s = (String)o;
            err = new NetLoader().initialLoad(s);

            // TODO : handle returning error!!!
            if (Err.NoErr != err)
                break;
        }
        return err;
    }

    private void
    addChannel(String url) {
        eAssert(url != null);
        final FeederActivity fa = this;
        new NetLoaderTask(this, this, this).execute(url);
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setListAdapter(new ChannelListAdapter(this, R.layout.channel_row, adapterCursorQuery()));
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(this, ItemReaderActivity.class);
        intent.putExtra("channelid", id);
        startActivityForResult(intent, ReqCReadChannel);
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
		case R.id.add_channel:
		    onOpt_addChannel();
		    break;
		}
		return true;
	}

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (ReqCReadChannel == requestCode) {
            switch (resultCode) {
            case ResCReadChannelUpdated:
                refreshList();
                break;
            }
        } else
            eAssert(false);
    }

    @Override
    protected void
    onDestroy() {
        ((ChannelListAdapter)getListAdapter()).getCursor().close();
        super.onDestroy();
    }
}