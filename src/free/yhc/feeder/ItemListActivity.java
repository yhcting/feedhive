package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;
import java.lang.reflect.Method;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.Utils;

public class ItemListActivity extends ListActivity {
    private long            cid = -1; // channel id
    private Feed.ActionType action = null; // action type of this channel
    private DBPolicy        db = DBPolicy.get();

    private class ActionInfo {
        private int layout;
        private Method method;

        ActionInfo(int layout, String methodName) {
            this.layout = layout;
            try {
                method = ItemListActivity.this.getClass().getMethod(methodName, long.class, int.class);
            } catch (Exception e) {
                eAssert(false);
            }
        }

        int getLayout() {
            return layout;
        }

        void invokeAction(long id, int position) {
            try {
                method.invoke(ItemListActivity.this, id, position);
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
            else
                LookAndFeel.showTextToast(ItemListActivity.this, result.getMsgId());
        }
    }

    private class DownloadToFileEventHandler implements DownloadToFileTask.OnEvent {
        @Override
        public void onPostExecute(DownloadToFileTask task, Err result) {
            if (Err.NoErr == result)
                ((ItemListAdapter) getListAdapter()).notifyDataSetChanged();
            else
                LookAndFeel.showTextToast(ItemListActivity.this, result.getMsgId());
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
            return db.queryItem(cid, new DB.ColumnItem[] {
                DB.ColumnItem.ID, // Mandatory.
                DB.ColumnItem.TITLE,
                DB.ColumnItem.DESCRIPTION,
                DB.ColumnItem.ENCLOSURE_LENGTH,
                DB.ColumnItem.ENCLOSURE_URL,
                DB.ColumnItem.ENCLOSURE_TYPE,
                DB.ColumnItem.PUBDATE,
                DB.ColumnItem.LINK,
                DB.ColumnItem.STATE }, null);
        } catch (InterruptedException e) {
            finish();
        }
        return null;
    }

    private String
    getInfoString(DB.ColumnItem column, int position) {
        Cursor c = ((ItemListAdapter) getListAdapter()).getCursor();
        if (!c.moveToPosition(position)) {
            eAssert(false);
            return null;
        }
        return c.getString(c.getColumnIndex(column.getName()));
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

    private boolean
    doesEnclosureDnFileExists(long id, int position) {
        // enclosure-url is link of downloaded file.
        String f = UIPolicy.getItemFilePath(cid,
                                            getInfoString(DB.ColumnItem.TITLE, position),
                                            getInfoString(DB.ColumnItem.ENCLOSURE_URL, position));
        return new File(f).exists();
    }

    private boolean
    deleteEnclosureDnFile(long id, int position) {
        // enclosure-url is link of downloaded file.
        String f = UIPolicy.getItemFilePath(cid,
                                            getInfoString(DB.ColumnItem.TITLE, position),
                                            getInfoString(DB.ColumnItem.ENCLOSURE_URL, position));

        return new File(f).delete();
    }

    private void
    onItemClickUpdate() {
        if (Utils.isNetworkAvailable(this))
            new NetLoaderTask(this, new NetLoaderEventHandler()).execute(cid);
        else
            LookAndFeel.showTextToast(this, R.string.network_unavailable);
    }

    // 'public' to use java reflection
    public void
    onActionDnOpen(long id, int position) {
        // 'enclosure' is used.
        String fpath = UIPolicy.getItemFilePath(cid,
                                                getInfoString(DB.ColumnItem.TITLE, position),
                                                getInfoString(DB.ColumnItem.ENCLOSURE_URL, position));
        eAssert(null != fpath);
        File f = new File(fpath);
        if (f.exists()) {
            String type = getInfoString(DB.ColumnItem.ENCLOSURE_TYPE, position);
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
            dnTask.execute(getInfoString(DB.ColumnItem.ENCLOSURE_URL, position));
        }
    }

    // 'public' to use java reflection
    public void
    onActionOpen(long id, int position) {
        // 'link' is used.
        Intent intent = new Intent(Intent.ACTION_VIEW);
        // TODO : check. Does Uri.parse always return non-null-Uri reference???
        intent.setData(Uri.parse(getInfoString(DB.ColumnItem.LINK, position)));
        startActivity(intent);

        Feed.Item.State state = Feed.Item.State.convert(getInfoString(DB.ColumnItem.STATE, position));
        try {
            if (Feed.Item.State.NEW == state) {
                db.setItemInfo_state(cid, id, Feed.Item.State.OPENED);
                ((ItemListAdapter)getListAdapter()).notifyDataSetChanged();
            }
        } catch (InterruptedException e) {
            finish();
        }
    }

    private void
    onContext_deleteDnFile(final long id, final int position) {
        // Create "Enter Url" dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.confirm_delete_download_file);
        dialog.setButton(getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick (DialogInterface dialog, int which) {
                if (!deleteEnclosureDnFile(id, position))
                    LookAndFeel.showTextToast(ItemListActivity.this, Err.IOFile.getMsgId());
                else
                    ((ItemListAdapter) getListAdapter()).notifyDataSetChanged();

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

        cid = getIntent().getLongExtra("channelid", 0);
        logI("Item to read : " + cid + "\n");

        String[] s = null;
        try {
            s = db.getChannelInfoStrings(cid,
                new DB.ColumnChannel[] {
                    DB.ColumnChannel.ACTIONTYPE
                    });
        } catch (InterruptedException e) {
            finish();
            return;
        }

        for (Feed.ActionType act : Feed.ActionType.values()) {
            if (act.name().equals(s[0])) {
                action = act;
                break;
            }
        }
        eAssert(null != action);

        setContentView(R.layout.item_list);
        setListAdapter(new ItemListAdapter(this, getActionInfo(action).getLayout(), adapterCursorQuery(cid), cid));
        registerForContextMenu(getListView());
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;

        if (1 == info.id) {
            // Do not show context menu for 'update' row - first item
            eAssert(0 == info.position);
            return;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_context, menu);

        // check for "Delete Downloaded File" option
        if (Feed.ActionType.DNOPEN == action
            && doesEnclosureDnFileExists(info.id, info.position))
            menu.findItem(R.id.delete_dnfile).setVisible(true);

        // Check for "Mark as unactioned option"
        if (Feed.ActionType.OPEN == action
            && Feed.Item.State.OPENED == Feed.Item.State.convert(getInfoString(DB.ColumnItem.STATE, info.position)))
            menu.findItem(R.id.mark_unactioned).setVisible(true);
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
        if (1 == id)
            // dummy item for 'update'
            onItemClickUpdate();
        else
            getActionInfo(action).invokeAction(id, position);
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem mItem) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)mItem.getMenuInfo();
        switch (mItem.getItemId()) {
        case R.id.delete_dnfile:
            logI(" Delete Downloaded File : ID : " + info.id + " / " + info.position);
            onContext_deleteDnFile(info.id, info.position);
            return true;
        case R.id.mark_unactioned:
            logI(" Mark As Unactioned : ID : " + info.id + " / " + info.position);
            return true;
        }
        return false;
    }

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        }
        return true;
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
        ((ItemListAdapter) getListAdapter()).getCursor().close();
        super.onDestroy();
    }
}
