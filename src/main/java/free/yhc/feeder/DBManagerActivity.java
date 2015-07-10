/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.feeder;

import static free.yhc.feeder.core.Utils.eAssert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import free.yhc.feeder.UiHelper.OnConfirmDialogAction;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.Environ;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Utils;

public class DBManagerActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Utils.Logger P = new Utils.Logger(DBManagerActivity.class);

    @SuppressWarnings("unused")
    public static final String KEY_DB_UPDATED = "dbUpdated";

    private static final long ID_ALL_CHANNEL = -1;
    private static final int POS_ALL_CHANNEL = -1;

    private final DBPolicy mDbp = DBPolicy.get();
    private final RTTask mRtt = RTTask.get();

    private String mExDBFilePath = null;
    private String mInDBFilePath = null;
    private DBInfo mDbInfo = new DBInfo();

    private static class DBInfo {
        int sz;    // db file sz (KB)
        ChannInfo[] channs = new ChannInfo[0];
        Comparator<ChannInfo> channInfoComparator = new Comparator<ChannInfo>() {
            @Override
            public int compare(ChannInfo ci0, ChannInfo ci1) {
                // Descending order
                if (ci0.nrItmes < ci1.nrItmes)
                    return 1;
                else if (ci0.nrItmes > ci1.nrItmes)
                    return -1;
                else
                    return 0;
            }
        };

        static class ChannInfo {
            long id;
            String title;
            int nrItmes; // items of this channel.
        }
    }

    private static class ChannInfoAdapter extends ArrayAdapter<DBInfo.ChannInfo> {
        private int _mResId;
        ChannInfoAdapter(Context context, int resId, DBInfo.ChannInfo[] data) {
            super(context, resId, data);
            _mResId = resId;
        }

        @Override
        public View
        getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (null == v) {
                LayoutInflater li = (LayoutInflater)Environ.getAppContext()
                                                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = li.inflate(_mResId, null);
            }
            DBInfo.ChannInfo e = getItem(position);
            ((TextView)v.findViewById(R.id.chann_name)).setText(e.title);
            ((TextView)v.findViewById(R.id.nr_items)).setText("" + e.nrItmes);
            return v;
        }
    }

    /**
     * IMPORTANT : This function should be run on main UI thread!
     */
    private boolean
    getExclusiveDBAccess() {
        // There is BGtask for updating channel or there is active scheduled update instance.
        // That means, DB is in use!
        // And there isn't any other use case that db is in use except for these two cases.
        //
        // NOTE
        // Is there any other use case?
        // I'm not sure, but these two are enough I think.
        //
        // To avoid race condition this function should be run on main UI thread!
        if (ScheduledUpdateService.doesInstanceExist()
            || mRtt.getChannelsUpdating().length > 0)
            return false;

        // To get exclusive access right to DB, disabling scheduled updater service is enough
        // NOTE
        // Is there any other use case to consider???
        ScheduledUpdateService.disable();
        return true;
    }

    /**
     * Should be run on main UI thread!
     */
    private void
    putExclusiveDBAccess() {
        ScheduledUpdateService.enable();
    }

    private Err
    verifyCandidateDB(File dbf) {
        SQLiteDatabase db;
        try {
            db = SQLiteDatabase.openDatabase(dbf.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e0) {
            return Err.DB_UNKNOWN;
        }
        return DB.verifyDB(db);
    }

    /**
     * Only 'export DB' and 'import DB' requires exclusive DB access.
     * Because these two cases exceptionally uses 'File' operations (NOT SQLite DB operation),
     *   Synchronization supported SQLiteDatabase module, isn't used.
     * So, race-condition of file-system-level may be issued.
     */
    private void
    exportDBAsync() {
        if (!getExclusiveDBAccess()) {
            UiHelper.showTextToast(this, R.string.warn_db_in_use);
            return;
        }

        DiagAsyncTask.Worker exportWork = new DiagAsyncTask.Worker() {
            @Override
            public Err
            doBackgroundWork(DiagAsyncTask task) {
                try {
                    FileInputStream fis = new FileInputStream(new File(mInDBFilePath));
                    FileOutputStream fos = new FileOutputStream(new File(mExDBFilePath));
                    Utils.copy(fos, fis);
                    fis.close();
                    fos.close();
                } catch (Exception e) {
                    return Err.IO_FILE;
                }
                return Err.NO_ERR;
            }

            @Override
            public void
            onPostExecute(DiagAsyncTask task, Err result) {
                if (result != Err.NO_ERR)
                    UiHelper.showTextToast(DBManagerActivity.this, result.getMsgId());

                putExclusiveDBAccess();
            }

            @Override
            public void
            onCancelled(DiagAsyncTask task) {
                putExclusiveDBAccess();
            }
        };

        DiagAsyncTask task = new DiagAsyncTask(this,
                                               exportWork,
                                               DiagAsyncTask.Style.SPIN,
                                               R.string.exporting);
        task.run();
    }

    private void
    actionExportDB() {
        CharSequence title = getResources().getText(R.string.exportdb);
        CharSequence msg = getResources().getText(R.string.database) + " => " + mExDBFilePath;
        UiHelper.buildConfirmDialog(this, title, msg, new OnConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                exportDBAsync();
            }
            @Override
            public void onCancel(Dialog dialog) { }
        }).show();
    }

    /**
     * NOTE
     * This function should be implemented with great care.
     * Corrupting database file may make application be unavailable forever before
     *   all database is deleted.
     *
     * See {@link #exportDBAsync()} for more comments.
     */
    private void
    importDBAsync() {
        final String inDBBackupSuffix = "______backup";
        final File exDbf = new File(mExDBFilePath);
        if (!exDbf.exists()) {
            UiHelper.showTextToast(this, R.string.warn_exdb_access_denied);
            return;
        }

        switch(verifyCandidateDB(exDbf)) {
        case VERSION_MISMATCH:
            UiHelper.showTextToast(this, R.string.warn_db_version_mismatch);
            return;

        case NO_ERR:
            break;

        default:
            UiHelper.showTextToast(this, R.string.warn_exdb_not_compatible);
            return;
        }

        if (!getExclusiveDBAccess()) {
            UiHelper.showTextToast(this, R.string.warn_db_in_use);
            return;
        }

        final File inDbf = new File(mInDBFilePath);
        final File inDbfBackup = new File(inDbf.getAbsoluteFile() + inDBBackupSuffix);
        if (!inDbf.renameTo(inDbfBackup)) {
            UiHelper.showTextToast(this, Err.IO_FILE.getMsgId());
            putExclusiveDBAccess();
            return;
        }

        DiagAsyncTask.Worker importWork = new DiagAsyncTask.Worker() {
            @Override
            public Err
            doBackgroundWork(DiagAsyncTask task) {
                try {
                    FileInputStream fis = new FileInputStream(exDbf);
                    FileOutputStream fos = new FileOutputStream(inDbf);
                    Utils.copy(fos, fis);
                    fis.close();
                    fos.close();
                } catch (Exception e) {
                    return Err.IO_FILE;
                }

                mDbp.reloadDatabase();

                return Err.NO_ERR;
            }

            @Override
            public void
            onPostExecute(DiagAsyncTask task, Err result) {
                if (result == Err.NO_ERR) {
                    // All are done successfully!
                    // Delete useless backup file!
                    //noinspection ResultOfMethodCallIgnored
                    inDbfBackup.delete();
                    onDBChanged(ID_ALL_CHANNEL, 0);
                } else {
                    if (inDbfBackup.renameTo(inDbf))
                        UiHelper.showTextToast(DBManagerActivity.this, Err.DB_CRASH.getMsgId());
                    else
                        UiHelper.showTextToast(DBManagerActivity.this, Err.IO_FILE.getMsgId());
                }
                putExclusiveDBAccess();
            }

            @Override
            public void
            onCancelled(DiagAsyncTask task) {
                putExclusiveDBAccess();
            }
        };

        DiagAsyncTask task = new DiagAsyncTask(this,
                                               importWork,
                                               DiagAsyncTask.Style.SPIN,
                                               R.string.importing);
        task.run();
    }

    private void
    actionImportDB() {
        CharSequence title = getResources().getText(R.string.importdb);
        CharSequence msg = getResources().getText(R.string.database) + " <= " + mExDBFilePath;
        UiHelper.buildConfirmDialog(this, title, msg, new OnConfirmDialogAction() {
            @Override
            public void
            onOk(Dialog dialog) {
                importDBAsync();
            }
            @Override
            public void onCancel(Dialog dialog) { }
        }).show();
    }

    /**
     *
     * @param cid ID_ALL_CHANNEL means 'for all channel' - that is for whole DB.
     */
    private void
    shrinkItemsAsync(final long cid, final int percent) {
        DiagAsyncTask.Worker shrinkWork = new DiagAsyncTask.Worker() {
            private int nr = 0;
            @Override
            public Err
            doBackgroundWork(DiagAsyncTask task) {
                nr = mDbp.deleteOldItems(cid, percent);
                return Err.NO_ERR;
            }

            @Override
            public void
            onPostExecute(DiagAsyncTask task, Err result) {
                UiHelper.showTextToast(DBManagerActivity.this,
                                       nr + " " + getResources().getText(R.string.nr_deleted_items_noti));
                onDBChanged(cid, nr);
            }

            @Override
            public void
            onCancel(DiagAsyncTask task) {
                eAssert(false);
            }
        };
        DiagAsyncTask task = new DiagAsyncTask(this,
                                               shrinkWork,
                                               DiagAsyncTask.Style.SPIN,
                                               R.string.deleting);
        task.run();
    }

    /**
     * @param position '>= 0' for specific channel located at given position,
     *                 ID_ALL_CHANNEL for shrinking entire item table.
     */
    private void
    actionShrinkDB(final int position, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_dbmgmt_shrink, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int delPercent = 0;
                switch (item.getItemId()) {
                case R.id.all:
                    delPercent = 100;
                    break;
                case R.id.half:
                    delPercent = 50;
                    break;
                case R.id.per30:
                    delPercent = 30;
                    break;
                }
                if (POS_ALL_CHANNEL == position)
                    shrinkItemsAsync(ID_ALL_CHANNEL, delPercent);
                else
                    shrinkItemsAsync(mDbInfo.channs[position].id, delPercent);
                return true;
            }
        });
        popup.show();
    }

    private void
    loadChannInfoListAsync() {
        DiagAsyncTask.Worker loadDBInfoWork = new DiagAsyncTask.Worker() {
            @Override
            public Err
            doBackgroundWork(DiagAsyncTask task) {
                // Load 'used channel information'
                Cursor c = mDbp.queryChannel(new ColumnChannel[] { ColumnChannel.ID,
                                                                   ColumnChannel.TITLE });

                mDbInfo.channs = new DBInfo.ChannInfo[c.getCount()];
                c.moveToFirst();
                for (int i = 0; i < mDbInfo.channs.length; i++) {
                    mDbInfo.channs[i] = new DBInfo.ChannInfo();
                    mDbInfo.channs[i].id = c.getLong(0);
                    mDbInfo.channs[i].nrItmes = mDbp.getChannelInfoNrItems(c.getLong(0));
                    mDbInfo.channs[i].title = c.getString(1);
                    c.moveToNext();
                }
                c.close();

                // sorting by number of items
                Arrays.sort(mDbInfo.channs, mDbInfo.channInfoComparator);
                return Err.NO_ERR;
            }

            @Override
            public void
            onPostExecute(DiagAsyncTask task, Err result) {
                activateChannelInfoListView(true);
                ListView lv = (ListView)DBManagerActivity.this.findViewById(R.id.list);
                ChannInfoAdapter adapter = new ChannInfoAdapter(DBManagerActivity.this, R.layout.db_manager_channel_row, mDbInfo.channs);
                lv.setAdapter(adapter);
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void
                    onItemClick(AdapterView<?> parent, View view, int position, long itemId) {
                        actionShrinkDB(position, view);
                    }
                });
            }
        };
        DiagAsyncTask task = new DiagAsyncTask(this,
                                               loadDBInfoWork,
                                               DiagAsyncTask.Style.SPIN,
                                               R.string.analyzing_db);
        task.run();
    }

    /**
     *
     * @param cid ID_ALL_CHANNEL for all channels - entire item table.
     */
    private void
    onDBChanged(long cid, int nrDeleted) {
        mDbInfo.sz = (int)(new File(mInDBFilePath).length() / 1024);
        CharSequence text = getResources().getText(R.string.db_size) + " : " + mDbInfo.sz + " KB";
        ((TextView)findViewById(R.id.total_dbsz)).setText(text);
        if (ID_ALL_CHANNEL == cid)
            activateChannelInfoListView(false);
        else {
            for (DBInfo.ChannInfo ci : mDbInfo.channs) {
                if (ci.id == cid)
                    ci.nrItmes -= nrDeleted;
            }
            Arrays.sort(mDbInfo.channs, mDbInfo.channInfoComparator);
            ((ChannInfoAdapter)((ListView)findViewById(R.id.list)).getAdapter()).notifyDataSetChanged();
        }
    }

    private void
    activateChannelInfoListView(boolean activate) {
        if (activate) {
            findViewById(R.id.channinfo_list).setVisibility(View.VISIBLE);
            findViewById(R.id.per_chann_mgmt).setVisibility(View.GONE);
            int nrItems = 0;
            for (DBInfo.ChannInfo ci : mDbInfo.channs)
                nrItems += ci.nrItmes;
            ((TextView)findViewById(R.id.nr_all_items)).setText(
                    getResources().getText(R.string.nr_all_items) + " : " + nrItems);

        } else {
            findViewById(R.id.channinfo_list).setVisibility(View.GONE);
            findViewById(R.id.per_chann_mgmt).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ DBManagerActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.db_manager);

        mExDBFilePath = Environ.get().getAppRootDirectoryPath()
                        + getResources().getText(R.string.app_name) + ".db";
        mInDBFilePath = getDatabasePath(DB.getDBName()).getAbsolutePath();

        (findViewById(R.id.exportdb)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionExportDB();
            }
        });

        (findViewById(R.id.importdb)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionImportDB();
            }
        });

        (findViewById(R.id.shrinkdb)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionShrinkDB(POS_ALL_CHANNEL, v);
            }
        });

        (findViewById(R.id.per_chann_mgmt)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadChannInfoListAsync();
            }
        });

        onDBChanged(ID_ALL_CHANNEL, 0);
    }

    @Override
    protected void
    onStart() {
        super.onStart();
    }

    @Override
    protected void
    onResume() {
        super.onResume();
    }

    @Override
    protected void
    onPause() {
        super.onPause();
    }

    @Override
    protected void
    onStop() {
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    public void
    onBackPressed() {
        super.onBackPressed();
    }
}
