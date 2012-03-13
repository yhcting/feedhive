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
import android.os.Handler;
import android.text.Layout;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskDownloadToFile;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.Utils;
public class ItemListActivity extends Activity {
    private long                cid     = -1; // channel id
    private Handler             handler = new Handler();
    private Feed.Channel.Action action  = null; // action type of this channel
    private final DBPolicy      db      = DBPolicy.S();
    private ListView            list;

    private class ActionInfo {
        private int layout;
        private Method method;

        ActionInfo(int layout, String methodName) {
            this.layout = layout;
            try {
                method = ItemListActivity.this.getClass()
                            .getMethod(methodName, android.view.View.class, long.class, int.class);
            } catch (Exception e) {
                eAssert(false);
            }
        }

        int getLayout() {
            return layout;
        }

        void invokeAction(View view, long id, int position) {
            try {
                method.invoke(ItemListActivity.this, view, id, position);
            } catch (Exception e) {
                eAssert(false);
            }
        }
    }

    public class UpdateBGTask extends BGTaskUpdateChannel implements BGTask.OnEvent<Long, Object> {
        UpdateBGTask() {
            super();
        }

        @Override
        public void
        onProgress(BGTask task, int progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            requestSetUpdateButton();
        }

        @Override
        public void
        onPreRun(BGTask task) {
            ; // nothing to do
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            requestSetUpdateButton();

            if (Err.NoErr == result)
                refreshList();
        }
    }

    private class DownloadToFileBGTask extends BGTaskDownloadToFile implements
    BGTaskDownloadToFile.OnEvent<BGTaskDownloadToFile.Arg, Object> {
        DownloadToFileBGTask() {
            super();
        }

        @Override
        public void
        onProgress(BGTask task, int progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            getListAdapter().notifyDataSetChanged();
        }

        @Override
        public void
        onPreRun(BGTask task) {
            ; // nothing to do
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            getListAdapter().notifyDataSetChanged();
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
        // 'State' of item may be changed often dynamically (ex. when open item)
        // So, to synchronized cursor information with item state, we need to refresh cursor
        //   whenever item state is changed.
        // But it's big overhead.
        // So, in case STATE, it didn't included in list cursor, but read from DB if needed.
        DB.ColumnItem[] columns = new DB.ColumnItem[] {
                    DB.ColumnItem.ID, // Mandatory.
                    DB.ColumnItem.TITLE,
                    DB.ColumnItem.DESCRIPTION,
                    DB.ColumnItem.ENCLOSURE_LENGTH,
                    DB.ColumnItem.ENCLOSURE_URL,
                    DB.ColumnItem.ENCLOSURE_TYPE,
                    DB.ColumnItem.PUBDATE,
                    DB.ColumnItem.LINK};
        return db.queryItem(cid, columns);
    }

    private String
    getCursorInfoString(DB.ColumnItem column, int position) {
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

    private void
    startUpdatingAnim(ImageView btn) {
        btn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_spin));
    }

    private void
    endUpdatingAnim(ImageView btn, boolean bCancel) {
        btn.getAnimation().cancel();
        if (bCancel)
            btn.setImageResource(R.drawable.ic_info);
        else
            btn.setImageResource(R.drawable.ic_refresh);
    }

    private void
    setOnClick_startUpdate(final ImageView btn) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ItemListActivity.this.updateItems();
                requestSetUpdateButton();
            }
        });
    }

    private void
    setOnClick_cancelUpdate(final ImageView btn) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BGTask task = RTTask.S().getUpdate(cid);
                if (null != task)
                    task.cancel(null);
                requestSetUpdateButton();
            }
        });
    }

    private void
    setOnClick_notification(final ImageView btn, final int msg) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LookAndFeel.showTextToast(ItemListActivity.this, msg);
            }
        });
    }

    private void
    setOnClick_errResult(final ImageView btn, final Err result) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LookAndFeel.showTextToast(ItemListActivity.this, result.getMsgId());
                RTTask.S().consumeUpdateResult(cid);
                requestSetUpdateButton();
            }
        });
    }

    private boolean
    doesEnclosureDnFileExists(long id, int position) {
        // enclosure-url is link of downloaded file.
        String f = UIPolicy.getItemFilePath(cid, id,
                                            getCursorInfoString(DB.ColumnItem.TITLE, position),
                                            getCursorInfoString(DB.ColumnItem.ENCLOSURE_URL, position));
        return new File(f).exists();
    }

    private boolean
    deleteEnclosureDnFile(long id, int position) {
        // enclosure-url is link of downloaded file.
        String f = UIPolicy.getItemFilePath(cid, id,
                                            getCursorInfoString(DB.ColumnItem.TITLE, position),
                                            getCursorInfoString(DB.ColumnItem.ENCLOSURE_URL, position));

        return new File(f).delete();
    }

    private boolean
    changeItemState_opened(long id, int position) {
        // change state as 'opened' at this moment.
        Feed.Item.State state = Feed.Item.State.convert(db.getItemInfoString(cid, id, DB.ColumnItem.STATE));
        if (Feed.Item.State.NEW == state) {
            db.updateItem_state(cid, id, Feed.Item.State.OPENED);
            getListAdapter().notifyDataSetChanged();
            return true;
        }
        return false;
    }

    private void
    updateItems() {
        UpdateBGTask updateTask = new UpdateBGTask();
        RTTask.S().registerUpdate(cid, updateTask);
        RTTask.S().bindUpdate(cid, updateTask);
        updateTask.start(new BGTaskUpdateChannel.Arg(cid));
    }

    // 'public' to use java reflection
    public void
    onActionDnOpen(View view, long id, int position) {
        String enclosureUrl = getCursorInfoString(DB.ColumnItem.ENCLOSURE_URL, position);
        // 'enclosure' is used.
        String fpath = UIPolicy.getItemFilePath(cid, id,
                                                getCursorInfoString(DB.ColumnItem.TITLE, position),
                                                enclosureUrl);
        eAssert(null != fpath);
        File f = new File(fpath);
        if (f.exists()) {
            // "RSS described media type" vs "mime type by guessing from file extention".
            // Experimentally, later is more accurate! (lots of RSS doesn't care about describing exact media type.)
            String type = Utils.guessMimeTypeFromUrl(enclosureUrl);
            if (null == type)
                type = getCursorInfoString(DB.ColumnItem.ENCLOSURE_TYPE, position);

            if (!Utils.isMimeType(type))
                type = "text/plain"; // this is default.


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
            // change state as 'opened' at this moment.
            changeItemState_opened(id, position);
        } else {
            RTTask.StateDownload state = RTTask.S().getDownloadState(cid, id);
            if (RTTask.StateDownload.Idle == state) {
                DownloadToFileBGTask dnTask = new DownloadToFileBGTask();
                RTTask.S().registerDownload(cid, id, dnTask);
                RTTask.S().bindDownload(cid, id, dnTask);
                dnTask.start(new BGTaskDownloadToFile.Arg(enclosureUrl, fpath,
                                                          UIPolicy.getItemDownloadTempPath(cid, id)));
                getListAdapter().notifyDataSetChanged();
            } else if (RTTask.StateDownload.Downloading == state) {
                BGTask task = RTTask.S().getDownload(cid, id);
                task.cancel(null);
                getListAdapter().notifyDataSetChanged();
            } else if (RTTask.StateDownload.Canceling == state) {
                LookAndFeel.showTextToast(this, R.string.cancel_processing);
            } else if (RTTask.StateDownload.DownloadFailed == state) {
                Err result = RTTask.S().getDownloadErr(cid, id);
                LookAndFeel.showTextToast(this, result.getMsgId());
                RTTask.S().consumeDownloadResult(cid, id);
                getListAdapter().notifyDataSetChanged();
            } else
                eAssert(false);
        }
    }

    // 'public' to use java reflection
    public void
    onActionOpen(View view, long id, final int position) {
        // 'link' is used.

        // Description is "end ellipsis"
        boolean bEllipsed = false;
        // See R.layout.item_row_link for below code.
        TextView desc = (TextView)((LinearLayout)view).findViewById(R.id.description);
        Layout l = desc.getLayout();
        if (null != l){
            int lines = l.getLineCount();
            if (lines > 0)
                if (l.getEllipsisCount(lines - 1) > 0)
                    bEllipsed = true;
        }

        // UIPolicy
        //   To support same-experience, description dialog is always shown to user.
        //   Ignoring above checking for 'eclipsed' is intentional here.
        //   Someday, I need to clean up code. But not now.
        //   Remained garbage code is for future reference and for history!
        bEllipsed = true;
        if (bEllipsed) {
            // Description is end-ellipsis
            // open text view dialog to see details...
            TextView title = (TextView)((LinearLayout)view).findViewById(R.id.title);
            final AlertDialog dialog = LookAndFeel.createAlertDialog(this, 0, title.getText(), desc.getText());
            dialog.setButton(getResources().getText(R.string.open_link),
                             new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(Intent.ACTION_VIEW)
                                    .setData(Uri.parse(getCursorInfoString(DB.ColumnItem.LINK, position))));
                    dialog.dismiss();
                }
            });
            dialog.setButton2(getResources().getText(R.string.ok),
                    new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                   dialog.dismiss();
               }
           });
            dialog.show();
        } else {
            // Full text is already shown at 'description' summary.
            // So, open link directly!
            startActivity(new Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse(getCursorInfoString(DB.ColumnItem.LINK, position))));
        }

        changeItemState_opened(id, position);
    }

    private void
    setUpdateButton() {
        ActionBar bar = getActionBar();
        ImageView iv = (ImageView)bar.getCustomView().findViewById(R.id.update_button);
        Animation anim = iv.getAnimation();

        if (null != anim)
            anim.cancel();
        RTTask.StateUpdate state = RTTask.S().getUpdateState(cid);
        if (RTTask.StateUpdate.Idle == state) {
            iv.setImageResource(R.drawable.ic_refresh);
            setOnClick_startUpdate(iv);
        } else if (RTTask.StateUpdate.Updating == state) {
            iv.setImageResource(R.drawable.ic_refresh);
            iv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_spin));
            setOnClick_cancelUpdate(iv);
        } else if (RTTask.StateUpdate.Canceling == state) {
            iv.setImageResource(R.drawable.ic_info);
            iv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_spin));
            setOnClick_notification(iv, R.string.wait_cancel);
        } else if (RTTask.StateUpdate.UpdateFailed == state) {
            iv.setImageResource(R.drawable.ic_info);
            Err result = RTTask.S().getUpdateErr(cid);
            setOnClick_errResult(iv, result);
        } else
            eAssert(false);
    }

    private void
    requestSetUpdateButton() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                setUpdateButton();
            }
        });
    }

    private void
    onContext_deleteDnFile(final long id, final int position) {
        // Create "Enter Url" dialog
        AlertDialog dialog =
                LookAndFeel.createWarningDialog(this,
                                                R.string.delete_downloaded_file,
                                                R.string.delete_downloaded_file_msg);
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
    public boolean
    onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_context, menu);

        // check for "Delete Downloaded File" option
        if (Feed.Channel.Action.DNOPEN == action
            && doesEnclosureDnFileExists(info.id, info.position))
            menu.findItem(R.id.delete_dnfile).setVisible(true);

        // Check for "Mark as unopened option"
        if (Feed.Channel.Action.OPEN == action
            && Feed.Item.State.OPENED == Feed.Item.State.convert(db.getItemInfoString(cid, info.id, DB.ColumnItem.STATE)))
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
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logI("==> ItemListActivity : onCreate");

        cid = getIntent().getLongExtra("channelid", 0);
        logI("Item to read : " + cid + "\n");

        String[] s = null;
        final int indexTITLE        = 0;
        final int indexACTION       = 1;
        s = db.getChannelInfoStrings(cid,
                new DB.ColumnChannel[] {
                    DB.ColumnChannel.TITLE,
                    DB.ColumnChannel.ACTION,
                    });
        action = Feed.Channel.Action.convert(s[indexACTION]);

        setContentView(R.layout.item_list);
        setTitle(s[indexTITLE]);

        // TODO
        //   How to use custom view + default option menu ???
        //
        // Set custom action bar
        ActionBar bar = getActionBar();
        LinearLayout abView = (LinearLayout)getLayoutInflater().inflate(R.layout.item_list_actionbar,null);
        bar.setCustomView(abView, new ActionBar.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT));

        int change = bar.getDisplayOptions() ^ ActionBar.DISPLAY_SHOW_CUSTOM;
        bar.setDisplayOptions(change, ActionBar.DISPLAY_SHOW_CUSTOM);
        bar.setDisplayShowHomeEnabled(false);

        list = ((ListView)findViewById(R.id.list));
        eAssert(null != list);
        list.setAdapter(new ItemListAdapter(this, getActionInfo(action).getLayout(), adapterCursorQuery(cid), cid));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick (AdapterView<?> parent, View view, int position, long id) {
                getActionInfo(action).invokeAction(view, id, position);
            }
        });
        registerForContextMenu(list);

    }

    @Override
    protected void
    onStart() {
        logI("==> ItemListActivity : onStart");
        super.onStart();
    }

    @Override
    protected void
    onResume() {
        logI("==> ItemListActivity : onResume");
        super.onResume();

        // See comments in 'ChannelListActivity.onPause()'
        // Bind update task if needed
        RTTask.StateUpdate state = RTTask.S().getUpdateState(cid);
        if (RTTask.StateUpdate.Idle != state)
            RTTask.S().bindUpdate(cid, new UpdateBGTask());

        // Bind downloading tasks
        Cursor c = db.queryItem(cid, new DB.ColumnItem[] { DB.ColumnItem.ID });
        if (c.moveToFirst()) {
            do {
                long id = c.getLong(0);
                if (RTTask.StateDownload.Idle != RTTask.S().getDownloadState(cid, id))
                    RTTask.S().bindDownload(cid, id, new DownloadToFileBGTask());
            } while (c.moveToNext());
        }
        c.close();

        setUpdateButton();
        getListAdapter().notifyDataSetChanged();
    }

    @Override
    protected void
    onPause() {
        logI("==> ItemListActivity : onPause");
        super.onPause();

        // See comments in 'ChannelListActivity.onPause()'
        RTTask.S().unbind();
    }

    @Override
    protected void
    onStop() {
        logI("==> ItemListActivity : onStop");
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        logI("==> ItemListActivity : onDestroy");
        getListAdapter().getCursor().close();
        super.onDestroy();
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

}
