package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;
import java.lang.reflect.Method;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.Utils;

public class ItemReaderActivity extends ListActivity {
    private long            cid = -1; // channel id
    private Feed.ActionType action = null; // action type of this channel
    // title of this channel - used as part of download filename.
    private String          cTitle = null;
    private DBPolicy        db = DBPolicy.get();

    private class ActionInfo {
        private int layout;
        private Method method;

        ActionInfo(int layout, String methodName) {
            this.layout = layout;
            try {
                method = ItemReaderActivity.this.getClass().getMethod(methodName, long.class);
            } catch (Exception e) {
                eAssert(false);
            }
        }

        int getLayout() {
            return layout;
        }

        void invokeAction(long id) {
            try {
                method.invoke(ItemReaderActivity.this, id);
            } catch (Exception e) {
                eAssert(false);
            }
        }
    }

    private class NetLoaderEventHandler implements NetLoaderTask.OnEvent {
        @Override
        public Err
        onDoWork(NetLoaderTask task, Object... objs) {
            try {
                return task.loadFeeds(objs);
            } catch (InterruptedException e) {
                return Err.Interrupted;
            }
        }

        @Override
        public void
        onPostExecute(NetLoaderTask task, Err result) {
            if (Err.NoErr == result)
                refreshList();
            else {
                ;// TODO Handle Error!!
            }
        }
    }

    private class DownloadToFileEventHandler implements DownloadToFileTask.OnEvent {
        @Override
        public void onPostExecute(DownloadToFileTask task, Err result) {
            if (Err.NoErr == result)
                refreshList();
            else {
                // TODO : Error handling.
            }
        }
    }

    // Putting these information inside 'Feed.ActionType' directly, is not good
    // idea in terms of code structure.
    // We would better to decouple 'Model' from 'View/Control' as much as
    // possible.
    // But, putting icon id to 'Feed.ItemState' makes another dependency between
    // View/Control/Model.
    // So, instead of putting this data to 'Feed.ActionType', below function is
    // used.
    private ActionInfo
    getActionInfo(Feed.ActionType type) {
        if (Feed.ActionType.OPEN == type)
            return new ActionInfo(R.layout.item_row_link, "onActionOpen");
        else if (Feed.ActionType.DNOPEN == type)
            return new ActionInfo(R.layout.item_row_enclosure, "onActionDnOpen");
        else
            eAssert(false);
        return null;
    }

    private Cursor
    adapterCursorQuery(long cid) {
        try {
            return db.queryItem(cid, new DB.ColumnFeedItem[] {
                DB.ColumnFeedItem.ID, // Mandatory.
                DB.ColumnFeedItem.TITLE,
                DB.ColumnFeedItem.DESCRIPTION,
                DB.ColumnFeedItem.ENCLOSURE_LENGTH,
                DB.ColumnFeedItem.ENCLOSURE_URL,
                DB.ColumnFeedItem.PUBDATE,
                DB.ColumnFeedItem.STATE }, null);
        } catch (InterruptedException e) {
            finish();
        }
        return null;
    }

    private void
    refreshList() {
        // [ NOTE ]
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery(cid);
        ((ItemListAdapter) getListAdapter()).swapCursor(newCursor).close();
        ((ItemListAdapter) getListAdapter()).notifyDataSetChanged();
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cid = getIntent().getLongExtra("channelid", 0);
        logI("* RSS Item to read : " + cid + "\n");

        String[] s = null;
        try {
            s = db.getFeedChannelInfoStrings(cid,
                new DB.ColumnFeedChannel[] {
                    DB.ColumnFeedChannel.ACTIONTYPE,
                    DB.ColumnFeedChannel.TITLE });
        } catch (InterruptedException e) {
            finish();
            return;
        }

        cTitle = s[1];
        for (Feed.ActionType act : Feed.ActionType.values()) {
            if (act.name().equals(s[0])) {
                action = act;
                break;
            }
        }
        eAssert(null != action);

        setContentView(R.layout.item_reader);

        setListAdapter(new ItemListAdapter(this, getActionInfo(action).getLayout(), adapterCursorQuery(cid), cid));
    }

    private void
    onItemClickUpdate() {
        new NetLoaderTask(this, new NetLoaderEventHandler()).execute(cid);
    }

    // 'public' to use java reflection
    public void
    onActionDnOpen(long id) {
        String[] data = null;
        try {
            data = db.getFeedItemInfoStrings(cid, id,
                // Column index is directly used below. Keep order!
                new DB.ColumnFeedItem[] {
                    DB.ColumnFeedItem.TITLE,
                    DB.ColumnFeedItem.ENCLOSURE_URL,
                    DB.ColumnFeedItem.ENCLOSURE_TYPE });
        } catch (InterruptedException e) {
            finish();
        }
        // 'enclosure' is used.
        String fpath = UIPolicy.getItemFilePath(cid, data[0], data[1]);
        eAssert(null != fpath);
        File f = new File(fpath);
        if (f.exists()) {
            String type = data[2];
            if (!Utils.isMimeType(type))
                type = Utils.guessMimeType(fpath);

            // File is already exists. Do action with it!
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (null == type)
                intent.setData(Uri.fromFile(f));
            else
                intent.setDataAndType(Uri.fromFile(f), type);

            startActivity(intent);
        } else {
            // File is not exists in local
            DownloadToFileTask dnTask = new DownloadToFileTask(fpath, this, new DownloadToFileEventHandler());
            dnTask.execute(data[1]);
        }
    }

    // 'public' to use java reflection
    public void
    onActionOpen(long id) {
        // 'link' is used.
        try {
            String[] data = db.getFeedItemInfoStrings(cid, id,
                new DB.ColumnFeedItem[] {
                    DB.ColumnFeedItem.LINK,
                    DB.ColumnFeedItem.STATE });
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // TODO : check. Does Uri.parse always return non-null-Uri reference???
            intent.setData(Uri.parse(data[0]));
            startActivity(intent);

            Feed.Item.State state = Feed.Item.State.convert(data[1]);
            if (Feed.Item.State.NEW == state) {
                db.setFeedItemInfo_state(cid, id, Feed.Item.State.OPENED);
                ((ItemListAdapter) getListAdapter()).notifyDataSetChanged();
            }
        } catch (InterruptedException e) {
            finish();
        }
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
        if (1 == id)
            // dummy item for 'update'
            onItemClickUpdate();
        else
            getActionInfo(action).invokeAction(id);
    }

    /*
     * === option menu is not used yet ===
     *
     * @Override public boolean onCreateOptionsMenu(Menu menu) { MenuInflater
     * inflater = getMenuInflater(); inflater.inflate(R.menu.itemreaderopt,
     * menu); return true; }
     */

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        }
        return true;
    }

    @Override
    protected void
    onDestroy() {
        ((ItemListAdapter) getListAdapter()).getCursor().close();
        super.onDestroy();
    }
}
