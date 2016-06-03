/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.Task;
import free.yhc.abaselib.util.AUtil;
import free.yhc.abaselib.util.UxUtil;
import free.yhc.abaselib.ux.DialogTask;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.Environ;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

public class DBManagerActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(DBManagerActivity.class, Logger.LOGLV_DEFAULT);

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
                LayoutInflater li = (LayoutInflater)AppEnv.getAppContext()
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
        P.bug(AUtil.isUiThread());
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
            UxUtil.showTextToast(R.string.warn_db_in_use);
            return;
        }

        Task<Void> t = new Task<Void>() {
            @Override
            protected Void
            doAsync() throws IOException {
                Err err = Err.UNKNOWN;
                try (FileInputStream fis = new FileInputStream(new File(mInDBFilePath));
                     FileOutputStream fos = new FileOutputStream(new File(mExDBFilePath))) {
                    Util.copy(fos, fis);
                    err = Err.NO_ERR;
                } catch (IOException e) {
                    err = Err.IO_FILE;
                } finally {
                    final Err r = err;
                    AppEnv.getUiHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (r != Err.NO_ERR)
                                UxUtil.showTextToast(r.getMsgId());
                            putExclusiveDBAccess();
                        }
                    });
                }
                return null;
            }
        };

        DialogTask.Builder<DialogTask.Builder> bldr
                = new DialogTask.Builder<>(this, t);
        bldr.setMessage(R.string.exporting);
        if (!bldr.create().start())
            P.bug();
    }

    private void
    actionExportDB() {
        CharSequence title = getResources().getText(R.string.exportdb);
        CharSequence msg = getResources().getText(R.string.database) + " => " + mExDBFilePath;
        UiHelper.buildConfirmDialog(this, title, msg, new UxUtil.ConfirmAction() {
            @Override
            public void onPositive(@NonNull Dialog dialog) {
                exportDBAsync();
            }
            @Override
            public void onNegative(@NonNull Dialog dialog) { }
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
            UxUtil.showTextToast(R.string.warn_exdb_access_denied);
            return;
        }

        switch(verifyCandidateDB(exDbf)) {
        case VERSION_MISMATCH:
            UxUtil.showTextToast(R.string.warn_db_version_mismatch);
            return;

        case NO_ERR:
            break;

        default:
            UxUtil.showTextToast(R.string.warn_exdb_not_compatible);
            return;
        }

        if (!getExclusiveDBAccess()) {
            UxUtil.showTextToast(R.string.warn_db_in_use);
            return;
        }

        final File inDbf = new File(mInDBFilePath);
        final File inDbfBackup = new File(inDbf.getAbsoluteFile() + inDBBackupSuffix);
        if (!inDbf.renameTo(inDbfBackup)) {
            UxUtil.showTextToast(Err.IO_FILE.getMsgId());
            putExclusiveDBAccess();
            return;
        }

        Task<Void> t = new Task<Void>() {
            @Override
            protected Void
            doAsync() throws IOException {
                Err err = Err.UNKNOWN;
                try (FileInputStream fis = new FileInputStream(exDbf);
                     FileOutputStream fos = new FileOutputStream(inDbf)) {
                    Util.copy(fos, fis);
                    mDbp.reloadDatabase();
                    err = Err.NO_ERR;
                } catch (IOException e) {
                    err = Err.IO_FILE;
                } finally {
                    final Err r = err;
                    AppEnv.getUiHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (r == Err.NO_ERR) {
                                // All are done successfully!
                                // Delete useless backup file!
                                //noinspection ResultOfMethodCallIgnored
                                inDbfBackup.delete();
                                onDBChanged(ID_ALL_CHANNEL, 0);
                            } else {
                                if (inDbfBackup.renameTo(inDbf))
                                    UxUtil.showTextToast(Err.DB_CRASH.getMsgId());
                                else
                                    UxUtil.showTextToast(r.getMsgId());
                            }
                            putExclusiveDBAccess();
                        }
                    });
                }
                return null;
            }
        };

        DialogTask.Builder<DialogTask.Builder> bldr
                = new DialogTask.Builder<>(this, t);
        bldr.setMessage(R.string.importing);
        if (!bldr.create().start())
            P.bug();
    }

    private void
    actionImportDB() {
        CharSequence title = getResources().getText(R.string.importdb);
        CharSequence msg = getResources().getText(R.string.database) + " <= " + mExDBFilePath;
        UiHelper.buildConfirmDialog(this, title, msg, new UxUtil.ConfirmAction() {
            @Override
            public void
            onPositive(@NonNull Dialog dialog) {
                importDBAsync();
            }
            @Override
            public void onNegative(@NonNull Dialog dialog) { }
        }).show();
    }

    /**
     *
     * @param cid ID_ALL_CHANNEL means 'for all channel' - that is for whole DB.
     */
    private void
    shrinkItemsAsync(final long cid, final int percent) {
        Task t = new Task<Void>() {
            private int nr = 0;
            @Override
            protected Void
            doAsync() {
                nr = mDbp.deleteOldItems(cid, percent);
                AppEnv.getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        UxUtil.showTextToast(nr + " " + getResources().getText(R.string.nr_deleted_items_noti));
                        onDBChanged(cid, nr);
                    }
                });
                return null;
            }
        };

        DialogTask.Builder<DialogTask.Builder> bldr
                = new DialogTask.Builder<>(this, t);
        bldr.setMessage(R.string.deleting);
        if (!bldr.create().start())
            P.bug();
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
        Task t = new Task<Void>() {
            @Override
            protected Void
            doAsync() {
                // Load 'used channel information'
                try (Cursor c = mDbp.queryChannel(new ColumnChannel[] { ColumnChannel.ID,
                                                                   ColumnChannel.TITLE })) {

                    mDbInfo.channs = new DBInfo.ChannInfo[c.getCount()];
                    c.moveToFirst();
                    for (int i = 0; i < mDbInfo.channs.length; i++) {
                        mDbInfo.channs[i] = new DBInfo.ChannInfo();
                        mDbInfo.channs[i].id = c.getLong(0);
                        mDbInfo.channs[i].nrItmes = mDbp.getChannelInfoNrItems(c.getLong(0));
                        mDbInfo.channs[i].title = c.getString(1);
                        c.moveToNext();
                    }
                }
                AppEnv.getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // sorting by number of items
                        Arrays.sort(mDbInfo.channs, mDbInfo.channInfoComparator);
                        activateChannelInfoListView(true);
                        ListView lv = (ListView)DBManagerActivity.this.findViewById(R.id.list);
                        ChannInfoAdapter adapter = new ChannInfoAdapter(
                                DBManagerActivity.this,
                                R.layout.db_manager_channel_row, mDbInfo.channs);
                        lv.setAdapter(adapter);
                        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void
                            onItemClick(AdapterView<?> parent, View view, int position, long itemId) {
                                actionShrinkDB(position, view);
                            }
                        });
                    }
                });
                return null;
            }
        };
        DialogTask.Builder<DialogTask.Builder> bldr
                = new DialogTask.Builder<>(this, t);
        bldr.setMessage(R.string.analyzing_db);
        if (!bldr.create().start())
            P.bug();
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

        mExDBFilePath = Environ.get().getAppRootDirectoryPath() + "/"
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
