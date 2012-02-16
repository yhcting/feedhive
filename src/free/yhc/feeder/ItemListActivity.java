package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;
import java.lang.reflect.Method;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.Utils;

public class ItemListActivity extends Activity {
    private long                cid     = -1; // channel id
    private Feed.Channel.Action action  = null; // action type of this channel
    private Feed.Channel.Order  order   = null;
    private DBPolicy            db      = DBPolicy.get();
    private ListView            list;

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

    private class UpdateEventHandler implements SpinAsyncTask.OnEvent {
        @Override
        public Err
        onDoWork(SpinAsyncTask task, Object... objs) {
            try {
                return task.loadFeeds(objs[0]);
            } catch (InterruptedException e) {
                return Err.Interrupted;
            }
        }

        @Override
        public void
        onPostExecute(SpinAsyncTask task, Err result) {
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
                getListAdapter().notifyDataSetChanged();
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
    getActionInfo(Feed.Channel.Action action) {
        if (Feed.Channel.Action.OPEN == action)
            return new ActionInfo(R.layout.item_row_link, "onActionOpen");
        else if (Feed.Channel.Action.DNOPEN == action)
            return new ActionInfo(R.layout.item_row_enclosure, "onActionDnOpen");
        else
            eAssert(false);
        return null;
    }

    private ItemListAdapter
    getListAdapter() {
        return (ItemListAdapter)list.getAdapter();
    }

    private Cursor
    adapterCursorQuery(long cid) {
        try {
            DB.ColumnItem[] columns = new DB.ColumnItem[] {
                    DB.ColumnItem.ID, // Mandatory.
                    DB.ColumnItem.TITLE,
                    DB.ColumnItem.DESCRIPTION,
                    DB.ColumnItem.ENCLOSURE_LENGTH,
                    DB.ColumnItem.ENCLOSURE_URL,
                    DB.ColumnItem.ENCLOSURE_TYPE,
                    DB.ColumnItem.PUBDATE,
                    DB.ColumnItem.LINK,
                    DB.ColumnItem.STATE };
            if (Feed.Channel.Order.NORMAL == order)
                return db.queryItem(cid, columns);
            else
                return db.queryItem_reverse(cid, columns);
        } catch (InterruptedException e) {
            finish();
        }
        return null;
    }

    private String
    getInfoString(DB.ColumnItem column, int position) {
        Cursor c = getListAdapter().getCursor();
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
        getListAdapter().swapCursor(newCursor).close();
        getListAdapter().notifyDataSetChanged();
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

    private boolean
    changeItemState_opened(long id, int position) {
        // chanage state as 'opened' at this moment.
        Feed.Item.State state = Feed.Item.State.convert(getInfoString(DB.ColumnItem.STATE, position));
        try {
            if (Feed.Item.State.NEW == state) {
                db.setItemInfo_state(cid, id, Feed.Item.State.OPENED);
                getListAdapter().notifyDataSetChanged();
                return true;
            }
        } catch (InterruptedException e) {
            finish();
        }
        return false;
    }

    private void
    updateItems() {
        if (Utils.isNetworkAvailable(this))
            new SpinAsyncTask(this, new UpdateEventHandler(), R.string.load_progress).execute(cid);
        else
            LookAndFeel.showTextToast(this, R.string.warn_network_unavailable);
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

            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                LookAndFeel.showTextToast(this,
                        getResources().getText(R.string.warn_find_app_to_open).toString() + " [" + type + "]");
                return;
            }
            // chanage state as 'opened' at this moment.
            changeItemState_opened(id, position);
        } else {
            // File is not exists in local
            DownloadToFileTask dnTask = new DownloadToFileTask(this,
                                                               fpath,
                                                               UIPolicy.getItemDownloadTempPath(cid),
                                                               new DownloadToFileEventHandler());
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

        changeItemState_opened(id, position);
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
                    getListAdapter().notifyDataSetChanged();

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
        final int indexTITLE        = 0;
        final int indexACTION       = 1;
        final int indexORDER        = 2;
        try {
            s = db.getChannelInfoStrings(cid,
                new DB.ColumnChannel[] {
                    DB.ColumnChannel.TITLE,
                    DB.ColumnChannel.ACTION,
                    DB.ColumnChannel.ORDER,
                    });
        } catch (InterruptedException e) {
            finish();
            return;
        }

        action = Feed.Channel.Action.convert(s[indexACTION]);
        order = Feed.Channel.Order.convert(s[indexORDER]);

        setContentView(R.layout.item_list);
        setTitle(s[indexTITLE]);

        // TODO
        //   How to use custom view + default option menu ???
        //
        // Set custom action bar
        ActionBar bar= getActionBar();
        LinearLayout abView = (LinearLayout)getLayoutInflater().inflate(R.layout.item_list_actionbar,null);
        ((ImageButton)abView.findViewById(R.id.update_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ItemListActivity.this.updateItems();
            }
        });
        bar.setCustomView(abView, new ActionBar.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT));

        int change = bar.getDisplayOptions() ^ ActionBar.DISPLAY_SHOW_CUSTOM;
        bar.setDisplayOptions(change, ActionBar.DISPLAY_SHOW_CUSTOM);
        bar.setDisplayShowHomeEnabled(false);

        list = ((ListView)findViewById(R.id.list));
        eAssert(null != list);
        list.setAdapter(new ItemListAdapter(this, getActionInfo(action).getLayout(), adapterCursorQuery(cid), cid));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void
            onItemClick (AdapterView<?> parent, View view, int position, long id) {
                getActionInfo(action).invokeAction(id, position);
            }
        });
        registerForContextMenu(list);
    }
    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
        return true;
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
        if (Feed.Channel.Action.DNOPEN == action
            && doesEnclosureDnFileExists(info.id, info.position))
            menu.findItem(R.id.delete_dnfile).setVisible(true);

        // Check for "Mark as unopened option"
        if (Feed.Channel.Action.OPEN == action
            && Feed.Item.State.OPENED == Feed.Item.State.convert(getInfoString(DB.ColumnItem.STATE, info.position)))
            menu.findItem(R.id.mark_unopened).setVisible(true);
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
        case R.id.mark_unopened:
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
        getListAdapter().getCursor().close();
        // '0' is temporal value. (reserved for future use)
        setResult(0);
        super.onDestroy();
    }
}
