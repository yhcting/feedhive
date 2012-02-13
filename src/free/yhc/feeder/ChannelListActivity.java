package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Utils;

public class ChannelListActivity extends Activity {
    // Request codes.
    private static final int ReqCReadChannel  = 0;

    public static final int  ResCReadChannelOk      = 0; // nothing special
    public static final int  ResCReadChannelUpdated = 1; // item list is updated.

    private ListView    list;

    class NetLoaderEventHandler implements NetLoaderTask.OnEvent {
        @Override
        public Err
        onDoWork(NetLoaderTask task, Object... objs) {
            try {
                return task.initialLoad(objs);
            } catch (InterruptedException e) {
                return Err.Interrupted;
            }
        }

        @Override
        public void
        onPostExecute(NetLoaderTask task, Err result) {
            if (Err.NoErr == result)
                refreshList();
            else
                LookAndFeel.showTextToast(ChannelListActivity.this, result.getMsgId());
        }
    }

    private ChannelListAdapter
    getListAdapter() {
        return (ChannelListAdapter)list.getAdapter();
    }

    private Cursor
    adapterCursorQuery() {
        try {
            return DBPolicy.get().queryChannel(
                    new DB.ColumnChannel[]
                        { DB.ColumnChannel.ID, // Mandatory.
                          DB.ColumnChannel.TITLE,
                          DB.ColumnChannel.DESCRIPTION,
                          DB.ColumnChannel.LASTUPDATE,
                          DB.ColumnChannel.IMAGEBLOB});
        } catch (InterruptedException e) {
            finish();
        }
        return null;
    }

    private void
    refreshList() {
        // NOTE
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery();
        getListAdapter().swapCursor(newCursor).close();
        getListAdapter().notifyDataSetChanged();
    }

    private void
    addChannel(String url) {
        eAssert(url != null);
        new NetLoaderTask(this, new NetLoaderEventHandler()).execute(url);
    }

    private void
    deleteChannel(long cid) {
        try {
            DBPolicy.get().deleteChannel(cid);
        } catch (InterruptedException e) {
            e.printStackTrace();
            finish();
        }
        refreshList();
    }

    private void
    onOpt_addChannel() {
        if (!Utils.isNetworkAvailable(this)) {
            // TODO Handling error
            LookAndFeel.showTextToast(ChannelListActivity.this, R.string.network_unavailable);
            return;
        }

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
                    // addChannel("http://cbspodcast.com/podcast/sisa/sisa.xml"); // large xml
                    addChannel("file:///sdcard/tmp/heenews.xml");
                    //addChannel("file:///sdcard/tmp/total_news.xml");
                    return true;
                }
                return false;
            }
        });
        dialog.show();
    }

    private void
    onContext_deleteChannel(final long cid) {
        // Create "Enter Url" dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.confirm_delete_channel);
        dialog.setButton(getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                deleteChannel(cid);
                dialog.dismiss();
            }
        });
        dialog.setButton2(getResources().getText(R.string.no),
                          new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                // Do nothing
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.channel_list);

        list = ((ListView)findViewById(R.id.list));
        eAssert(null != list);

        list.setAdapter(new ChannelListAdapter(this, R.layout.channel_row, adapterCursorQuery()));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
                intent.putExtra("channelid", id);
                startActivityForResult(intent, ReqCReadChannel);
            }
        });

        registerForContextMenu(list);
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.channel_context, menu);
    }

    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.channel_opt, menu);
        return true;
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem mItem) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)mItem.getMenuInfo();

        switch (mItem.getItemId()) {
        case R.id.delete:
            logI(" ID : " + info.id + " / " + info.position);
            onContext_deleteChannel(info.id);
            return true;
        }
        return false;
    }

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        case R.id.add:
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
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    protected void
    onDestroy() {
        getListAdapter().getCursor().close();
        super.onDestroy();
    }
}
