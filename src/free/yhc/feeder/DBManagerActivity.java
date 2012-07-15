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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import free.yhc.feeder.LookAndFeel.ConfirmDialogAction;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class DBManagerActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    public static final String KEY_DB_UPDATED = "dbUpdated";


    private String exDBFilePath = null;
    private String inDBFilePath = null;
    private Intent intent       = new Intent();
    private DBInfo dbInfo       = new DBInfo();

    private static class DBInfo {
        int         sz;    // db file sz (KB)
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
            long    id;
            String  title;
            int     nrItmes; // items of this channel.
        }
    }

    private class ChannInfoAdapter extends ArrayAdapter<DBInfo.ChannInfo> {
        private int resId;
        ChannInfoAdapter(Context context, int aResId, DBInfo.ChannInfo[] data) {
            super(context, aResId, data);
            resId = aResId;
        }

        @Override
        public View
        getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (null == v) {
                LayoutInflater li = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = li.inflate(resId, null);
            }
            DBInfo.ChannInfo e = getItem(position);
            ((TextView)v.findViewById(R.id.chann_name)).setText(e.title);
            ((TextView)v.findViewById(R.id.nr_items)).setText("" + e.nrItmes);
            return v;
        }
    }

    private Err
    verifyCandidateDB(File dbf) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbf.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e0) {
            return Err.DB_UNKNOWN;
        }
        return DB.verifyDB(db);
    }

    private void
    exportDBAsync() {
        SpinAsyncTask.OnEvent exportWork = new SpinAsyncTask.OnEvent() {
            @Override
            public Err
            onDoWork(SpinAsyncTask task, Object... objs) {
                try {
                    FileInputStream fis = new FileInputStream(new File(inDBFilePath));
                    FileOutputStream fos = new FileOutputStream(new File(exDBFilePath));
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
            onPostExecute(SpinAsyncTask task, Err result) {
                if (result != Err.NO_ERR)
                    LookAndFeel.showTextToast(DBManagerActivity.this, result.getMsgId());
            }

            @Override
            public void onCancel(SpinAsyncTask task) {}
        };

        SpinAsyncTask task = new SpinAsyncTask(this, exportWork, R.string.exporting, false);
        task.execute((Object)null);
    }

    private void
    actionExportDB() {
        CharSequence title = getResources().getText(R.string.exportdb);
        CharSequence msg = getResources().getText(R.string.database) + " => " + exDBFilePath;
        LookAndFeel.buildConfirmDialog(this, title, msg, new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                exportDBAsync();
            }
        }).show();
    }

    /**
     * NOTE
     * This function should be implemented with great care.
     * Corrupting database file may make application be unavailable forever before
     *   all database is deleted.
     */
    private void
    importDBAsync() {
        final String inDBBackupSuffix = "______backup";
        final File exDbf = new File(exDBFilePath);
        if (!exDbf.exists()) {
            LookAndFeel.showTextToast(this, R.string.warn_exdb_access_denied);
            return;
        }

        switch(verifyCandidateDB(exDbf)) {
        case VERSION_MISMATCH:
            LookAndFeel.showTextToast(this, R.string.warn_db_version_mismatch);
            return;

        case NO_ERR:
            break;

        default:
            LookAndFeel.showTextToast(this, R.string.warn_exdb_not_compatible);
            return;
        }

        final File inDbf = new File(inDBFilePath);
        final File inDbfBackup = new File(inDbf.getAbsoluteFile() + inDBBackupSuffix);
        if (!inDbf.renameTo(inDbfBackup)) {
            LookAndFeel.showTextToast(this, Err.IO_FILE.getMsgId());
            return;
        }

        SpinAsyncTask.OnEvent importWork = new SpinAsyncTask.OnEvent() {
            @Override
            public Err
            onDoWork(SpinAsyncTask task, Object... objs) {
                try {
                    FileInputStream fis = new FileInputStream(exDbf);
                    FileOutputStream fos = new FileOutputStream(inDbf);
                    Utils.copy(fos, fis);
                    fis.close();
                    fos.close();
                } catch (Exception e) {
                    return Err.IO_FILE;
                }

                DBPolicy.S().reloadDatabase();

                return Err.NO_ERR;
            }

            @Override
            public void
            onPostExecute(SpinAsyncTask task, Err result) {
                if (result == Err.NO_ERR) {
                    // All are done successfully!
                    // Delete useless backup file!
                    inDbfBackup.delete();
                    intent.putExtra(KEY_DB_UPDATED, true);
                    return;
                }

                if (inDbfBackup.renameTo(inDbf))
                    LookAndFeel.showTextToast(DBManagerActivity.this, Err.DB_CRASH.getMsgId());
                else
                    LookAndFeel.showTextToast(DBManagerActivity.this, Err.IO_FILE.getMsgId());
            }

            @Override
            public void onCancel(SpinAsyncTask task) {}
        };

        SpinAsyncTask task = new SpinAsyncTask(this, importWork, R.string.importing, false);
        task.execute((Object)null);
    }

    private void
    actionImportDB() {
        CharSequence title = getResources().getText(R.string.importdb);
        CharSequence msg = getResources().getText(R.string.database) + " <= " + exDBFilePath;
        LookAndFeel.buildConfirmDialog(this, title, msg, new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                importDBAsync();
            }
        }).show();
    }

    private void
    shrinkChannelItemsAsync(final long cid, final int percent) {
        SpinAsyncTask.OnEvent shrinkWork = new SpinAsyncTask.OnEvent() {
            @Override
            public Err
            onDoWork(SpinAsyncTask task, Object... objs) {
                return Err.NO_ERR;
            }

            @Override
            public void
            onPostExecute(SpinAsyncTask task, Err result) {
            }

            @Override
            public void onCancel(SpinAsyncTask task) {}
        };
        SpinAsyncTask task = new SpinAsyncTask(this, shrinkWork, R.string.deleting, false);
        task.execute((Object)null);
    }

    private void
    onChannelItemClick(View view, final int position, long itemId) {
        PopupMenu popup = new PopupMenu(this, view);
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
                shrinkChannelItemsAsync(dbInfo.channs[position].id, delPercent);
                return true;
            }
        });
        popup.show();
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ DBManagerActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.S().registerModule(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.db_manager);

        exDBFilePath = UIPolicy.getAppRootDirectoryPath() + getResources().getText(R.string.app_name) + ".db";
        inDBFilePath = getDatabasePath(DB.getDBName()).getAbsolutePath();

        ((Button)this.findViewById(R.id.exportdb)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionExportDB();
            }
        });

        ((Button)this.findViewById(R.id.importdb)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionImportDB();
            }
        });

        SpinAsyncTask.OnEvent loadDBInfoWork = new SpinAsyncTask.OnEvent() {
            @Override
            public Err
            onDoWork(SpinAsyncTask task, Object... objs) {
                dbInfo.sz = (int)(new File(inDBFilePath).length() / 1024);

                // Load 'used channel information'
                Cursor c = DBPolicy.S().queryChannel(new DB.ColumnChannel[] { DB.ColumnChannel.ID,
                                                                              DB.ColumnChannel.TITLE });

                dbInfo.channs = new DBInfo.ChannInfo[c.getCount()];
                c.moveToFirst();
                for (int i = 0; i < dbInfo.channs.length; i++) {
                    dbInfo.channs[i] = new DBInfo.ChannInfo();
                    dbInfo.channs[i].id = c.getLong(0);
                    dbInfo.channs[i].nrItmes = DBPolicy.S().getChannelInfoNrItems(c.getLong(0));
                    dbInfo.channs[i].title = c.getString(1);
                    c.moveToNext();
                }
                c.close();

                // sorting by number of items
                Arrays.sort(dbInfo.channs, dbInfo.channInfoComparator);
                return Err.NO_ERR;
            }

            @Override
            public void
            onPostExecute(SpinAsyncTask task, Err result) {
                ListView lv = (ListView)DBManagerActivity.this.findViewById(R.id.list);
                ChannInfoAdapter adapter = new ChannInfoAdapter(DBManagerActivity.this, R.layout.db_manager_channel_row, dbInfo.channs);
                lv.setAdapter(adapter);
                /*
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void
                    onItemClick(AdapterView<?> parent, View view, int position, long itemId) {
                        onChannelItemClick(view, position, itemId);
                    }
                });
                */
            }

            @Override
            public void onCancel(SpinAsyncTask task) {}
        };
        SpinAsyncTask task = new SpinAsyncTask(this, loadDBInfoWork, R.string.analyzing_db, false);
        task.execute((Object)null);
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
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    public boolean
    onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            setResult(RESULT_OK, intent);
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }
}
