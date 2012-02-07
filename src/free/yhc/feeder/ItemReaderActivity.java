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
import free.yhc.feeder.model.NetLoader;
import free.yhc.feeder.model.RSS;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.Utils;

public class ItemReaderActivity extends ListActivity {
    private long           cid = -1; // channel id
    private RSS.ActionType action = null; // action type of this channel
    // title of this channel - used as part of download filename.
    private String         cTitle = null;
    private DBPolicy       db = new DBPolicy();

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

    private class WorkNetLoader implements AsyncTaskMy.OnDoWork {
        @Override
        public Err doWork(Object... objs) {
            Err err = Err.NoErr;

            for (Object o : objs) {
                Long l = (Long) o;
                err = new NetLoader().loadFeeds(l.longValue());

                // TODO : handle returning error!!!
                if (Err.NoErr != err)
                    break;
            }
            return err;
        }
    }

    private class PostNetLoader implements AsyncTaskMy.OnPostExecute {
        @Override
        public void onPostExecute(Err result) {
            refreshList();
        }
    }

    private class PostDownloadToFile implements AsyncTaskMy.OnPostExecute {
        @Override
        public void onPostExecute(Err result) {
            if (Err.NoErr == result)
                refreshList();
            else {
                // TODO : Error handling.
            }
        }
    }

    // Putting these information inside 'RSS.ActionType' directly, is not good
    // idea in terms of code structure.
    // We would better to decouple 'Model' from 'View/Control' as much as
    // possible.
    // But, putting icon id to 'RSS.ItemState' makes another dependency between
    // View/Control/Model.
    // So, instead of putting this data to 'RSS.ActionType', below function is
    // used.
    private ActionInfo
    getActionInfo(RSS.ActionType type) {
        if (RSS.ActionType.OPEN == type)
            return new ActionInfo(R.layout.item_row_link, "onActionOpen");
        else if (RSS.ActionType.DNOPEN == type)
            return new ActionInfo(R.layout.item_row_enclosure, "onActionDnOpen");
        else
            eAssert(false);
        return null;
    }

    private Cursor
    adapterCursorQuery(long cid) {
        return db.queryItem(cid, new DB.ColumnRssItem[] {
                DB.ColumnRssItem.ID, // Mandatory.
                DB.ColumnRssItem.TITLE,
                DB.ColumnRssItem.DESCRIPTION,
                DB.ColumnRssItem.ENCLOSURE_LENGTH,
                DB.ColumnRssItem.ENCLOSURE_URL,
                DB.ColumnRssItem.PUBDATE,
                DB.ColumnRssItem.STATE }, null);
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

        String[] s = db.getRSSChannelInfoStrings(cid,
                new DB.ColumnRssChannel[] {
                    DB.ColumnRssChannel.ACTIONTYPE,
                    DB.ColumnRssChannel.TITLE });

        cTitle = s[1];
        for (RSS.ActionType act : RSS.ActionType.values()) {
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
        new NetLoaderTask(this, new PostNetLoader(), new WorkNetLoader()).execute(cid);
    }

    // 'public' to use java reflection
    public void
    onActionDnOpen(long id) {
        String[] data = db.getRSSItemInfoStrings(cid, id,
                // Column index is directly used below. Keep order!
                new DB.ColumnRssItem[] {
                    DB.ColumnRssItem.TITLE,
                    DB.ColumnRssItem.ENCLOSURE_URL,
                    DB.ColumnRssItem.ENCLOSURE_TYPE });
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
            DownloadToFileTask dnTask = new DownloadToFileTask(fpath, this, new PostDownloadToFile(), null);
            dnTask.execute(data[1]);
        }
    }

    // 'public' to use java reflection
    public void
    onActionOpen(long id) {
        // 'link' is used.
        String[] data = db.getRSSItemInfoStrings(cid, id,
                new DB.ColumnRssItem[] {
                    DB.ColumnRssItem.LINK,
                    DB.ColumnRssItem.STATE });
        Intent intent = new Intent(Intent.ACTION_VIEW);
        // TODO : check. Does Uri.parse always return non-null-Uri reference???
        intent.setData(Uri.parse(data[0]));
        startActivity(intent);

        RSS.ItemState state = RSS.ItemState.convert(data[1]);
        if (RSS.ItemState.NEW == state) {
            db.setRSSItemInfo_state(cid, id, RSS.ItemState.OPENED);
            ((ItemListAdapter) getListAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
        if (0 == id)
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
