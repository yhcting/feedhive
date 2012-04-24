/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

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
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskDownloadToFile;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;
public class ItemListActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    private long                cid     = -1; // channel id
    private Handler             handler = new Handler();
    private long                action  = Feed.Channel.FActDefault; // action type of this channel
    private final DBPolicy      db      = DBPolicy.S();
    private ListView            list;

    private class ActionInfo {
        private long    act;
        private Method  method;

        ActionInfo(long act, String methodName) {
            this.act = act;
            try {
                method = ItemListActivity.this.getClass()
                            .getMethod(methodName, android.view.View.class, long.class, int.class);
            } catch (Exception e) {
                eAssert(false);
            }
        }

        long getAction() {
            return act;
        }

        void invokeAction(View view, long id, int position) {
            try {
                method.invoke(ItemListActivity.this, view, id, position);
            } catch (Exception e) {
                eAssert(false);
            }
        }
    }

    public class UpdateBGTaskOnEvent implements BGTask.OnEvent<Long, Object> {
        @Override
        public void
        onProgress(BGTask task, long progress) {
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

            if (Err.NoErr == result) {
                // Update "OLDLAST_ITEMID"
                // User already know that there is new feed because
                //   user starts to update in feed screen!.
                // So, we can assume that user knows latest updates here.
                DBPolicy.S().updateChannel_lastItemId(cid);
                refreshList();
            }
        }
    }

    private class DownloadDataBGTaskOnEvent implements
    BGTask.OnEvent<Object, Object> {
        @Override
        public void
        onProgress(BGTask task, long progress) {
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
            logI("+++ Item Activity DownloadData PostRun");
            getListAdapter().notifyDataSetChanged();
        }
    }


    private class RTTaskManagerEventHandler implements RTTask.OnRTTaskManagerEvent {
        @Override
        public void
        onBGTaskRegister(long id, BGTask task, RTTask.Action act) {
            if (RTTask.Action.Update == act)
                RTTask.S().bind(id, act, ItemListActivity.this, new UpdateBGTaskOnEvent());
            else if (RTTask.Action.Download == act)
                RTTask.S().bind(id, act, ItemListActivity.this, new DownloadDataBGTaskOnEvent());
        }

        @Override
        public void onBGTaskUnregister(long cid, BGTask task, RTTask.Action act) { }
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
    getActionInfo(long action) {
        if (Feed.Channel.isActTgtLink(action))
            return new ActionInfo(action, "onActionLink");
        else if (Feed.Channel.isActTgtEnclosure(action))
            return new ActionInfo(action, "onActionDnEnclosure");
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
                RTTask.S().cancel(cid, RTTask.Action.Update, null);
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
                RTTask.S().consumeResult(cid, RTTask.Action.Update);
                requestSetUpdateButton();
            }
        });
    }

    private boolean
    changeItemState_opened(long id, int position) {
        // change state as 'opened' at this moment.
        long state = db.getItemInfoLong(id, DB.ColumnItem.STATE);
        if (Feed.Item.isStateNew(state)) {
            db.updateItem_state(id, Feed.Item.FStatOpened);
            getListAdapter().notifyDataSetChanged();
            return true;
        }
        return false;
    }

    private void
    updateItems() {
        BGTaskUpdateChannel updateTask = new BGTaskUpdateChannel(this, new BGTaskUpdateChannel.Arg(cid));
        RTTask.S().register(cid, RTTask.Action.Update, updateTask);
        RTTask.S().bind(cid, RTTask.Action.Update, this, new UpdateBGTaskOnEvent());
        RTTask.S().start(cid, RTTask.Action.Update);
    }

    // 'public' to use java reflection
    public void
    onActionDnEnclosure(View view, long id, int position) {
        String enclosureUrl = getCursorInfoString(DB.ColumnItem.ENCLOSURE_URL, position);
        // 'enclosure' is used.
        File f = UIPolicy.getItemDataFile(id);
        eAssert(null != f);
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
            RTTask.TaskState state = RTTask.S().getState(id, RTTask.Action.Download);
            if (RTTask.TaskState.Idle == state) {
                BGTaskDownloadToFile dnTask
                    = new BGTaskDownloadToFile(this, new BGTaskDownloadToFile.Arg(enclosureUrl,
                                                                                  f,
                                                                                  UIPolicy.getNewTempFile()));
                RTTask.S().register(id, RTTask.Action.Download, dnTask);
                RTTask.S().start(id, RTTask.Action.Download);
                getListAdapter().notifyDataSetChanged();
            } else if (RTTask.TaskState.Running == state
                       || RTTask.TaskState.Ready == state) {
                RTTask.S().cancel(id, RTTask.Action.Download, null);
                getListAdapter().notifyDataSetChanged();
            } else if (RTTask.TaskState.Canceling == state) {
                LookAndFeel.showTextToast(this, R.string.wait_cancel);
            } else if (RTTask.TaskState.Failed == state) {
                Err result = RTTask.S().getErr(id, RTTask.Action.Download);
                LookAndFeel.showTextToast(this, result.getMsgId());
                RTTask.S().consumeResult(id, RTTask.Action.Download);
                getListAdapter().notifyDataSetChanged();
            } else
                eAssert(false);
        }
    }

    // 'public' to use java reflection
    public void
    onActionLink(View view, long id, final int position) {
        RTTask.TaskState state = RTTask.S().getState(id, RTTask.Action.Download);
        if (RTTask.TaskState.Failed == state) {
            LookAndFeel.showTextToast(this, RTTask.S().getErr(id, RTTask.Action.Download).getMsgId());
            RTTask.S().consumeResult(id, RTTask.Action.Download);
            getListAdapter().notifyDataSetChanged();
            return;
        }

        // 'link' is used.
        Intent intent = new Intent(this, ItemViewActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);

        changeItemState_opened(id, position);
    }

    private void
    setUpdateButton() {
        ActionBar bar = getActionBar();
        ImageView iv = (ImageView)bar.getCustomView().findViewById(R.id.update_button);
        Animation anim = iv.getAnimation();

        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        iv.setAlpha(1.0f);
        iv.setClickable(true);
        RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
        if (RTTask.TaskState.Idle == state) {
            iv.setImageResource(R.drawable.ic_refresh);
            setOnClick_startUpdate(iv);
        } else if (RTTask.TaskState.Running == state
                   || RTTask.TaskState.Ready == state) {
            iv.setImageResource(R.anim.download);
            ((AnimationDrawable)iv.getDrawable()).start();
            setOnClick_cancelUpdate(iv);
        } else if (RTTask.TaskState.Canceling == state) {
            iv.setImageResource(R.drawable.ic_block);
            iv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_inout));
            iv.setClickable(false);
            setOnClick_notification(iv, R.string.wait_cancel);
        } else if (RTTask.TaskState.Failed == state) {
            iv.setImageResource(R.drawable.ic_info);
            Err result = RTTask.S().getErr(cid, RTTask.Action.Update);
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
                if (!UIPolicy.getItemDataFile(id).delete())
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
        if (UIPolicy.getItemDataFile(info.id).exists())
            menu.findItem(R.id.delete_dnfile).setVisible(true);
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
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ItemListActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.S().registerModule(this);
        super.onCreate(savedInstanceState);
        logI("==> ItemListActivity : onCreate");

        cid = getIntent().getLongExtra("cid", 0);
        logI("Item to read : " + cid + "\n");

        final String title = db.getChannelInfoString(cid, DB.ColumnChannel.TITLE);
        action = db.getChannelInfoLong(cid, DB.ColumnChannel.ACTION);

        setContentView(R.layout.item_list);
        setTitle(title);

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
        list.setAdapter(new ItemListAdapter(this, R.layout.item_row, adapterCursorQuery(cid),
                                            getActionInfo(action).getAction()));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick (AdapterView<?> parent, View view, int position, long id) {
                getActionInfo(action).invokeAction(view, id, position);
            }
        });
        registerForContextMenu(list);

        // Update "OLDLAST_ITEMID" when user opens item views.
        DBPolicy.S().updateChannel_lastItemId(cid);
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

        // Register to get notification regarding RTTask.
        // See comments in 'ChannelListActivity.onResume' around 'registerManagerEventListener'
        RTTask.S().registerManagerEventListener(this, new RTTaskManagerEventHandler());

        // See comments in 'ChannelListActivity.onPause()'
        // Bind update task if needed
        RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
        if (RTTask.TaskState.Idle != state)
            RTTask.S().bind(cid, RTTask.Action.Update, this, new UpdateBGTaskOnEvent());

        // Bind downloading tasks
        long[] ids = RTTask.S().getItemsDownloading(cid);
        for (long id : ids)
            RTTask.S().bind(id, RTTask.Action.Download, this, new DownloadDataBGTaskOnEvent());

        setUpdateButton();
        getListAdapter().notifyDataSetChanged();
    }

    @Override
    protected void
    onPause() {
        logI("==> ItemListActivity : onPause");
        // See comments in 'ChannelListActivity.onPause' around 'unregisterManagerEventListener'
        RTTask.S().unregisterManagerEventListener(this);
        // See comments in 'ChannelListActivity.onPause()'
        RTTask.S().unbind(this);
        super.onPause();
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
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

}
