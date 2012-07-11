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

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import free.yhc.feeder.LookAndFeel.ConfirmDialogAction;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class DBManagerActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    public static final String KEY_DB_UPDATED = "dbUpdated";


    private String exDBFilePath = null;
    private String inDBFilePath = null;
    private Intent intent       = new Intent();

    private boolean
    isDBInUse() {
        // There is updating channel
        // That means, DB is in use!
        // This NOT "if and only if" condition.
        // But, in most case, this is enough to know whether DB is in used or not,
        //   because UI scenario covers most other exceptional areas.
        return RTTask.S().getChannelsUpdating().length > 0;
    }

    private boolean
    verifyCandidateDB(File dbf) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbf.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e0) {
            return false;
        }
        return DB.verifyDB(db);
    }

    private void
    exportDB() {
        if (isDBInUse()) {
            LookAndFeel.showTextToast(this, R.string.warn_db_in_use);
            return;
        }

        SpinAsyncTask.OnEvent exportEvent = new SpinAsyncTask.OnEvent() {
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

        SpinAsyncTask task = new SpinAsyncTask(this, exportEvent, R.string.exporting, false);
        task.execute(new Object());
    }

    private void
    actionExportDB() {
        CharSequence title = getResources().getText(R.string.exportdb);
        CharSequence msg = getResources().getText(R.string.database) + " => " + exDBFilePath;
        LookAndFeel.buildConfirmDialog(this, title, msg, new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                exportDB();
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
    importDB() {
        if (isDBInUse()) {
            LookAndFeel.showTextToast(this, R.string.warn_db_in_use);
            return;
        }

        final String inDBBackupSuffix = "______backup";
        final File exDbf = new File(exDBFilePath);
        if (!exDbf.exists()) {
            LookAndFeel.showTextToast(this, R.string.warn_exdb_access_denied);
            return;
        }

        if (!verifyCandidateDB(exDbf)) {
            LookAndFeel.showTextToast(this, R.string.warn_exdb_not_compatible);
            return;
        }

        final File inDbf = new File(inDBFilePath);
        final File inDbfBackup = new File(inDbf.getAbsoluteFile() + inDBBackupSuffix);
        if (!inDbf.renameTo(inDbfBackup)) {
            LookAndFeel.showTextToast(this, Err.IO_FILE.getMsgId());
            return;
        }

        SpinAsyncTask.OnEvent importEvent = new SpinAsyncTask.OnEvent() {
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

        SpinAsyncTask task = new SpinAsyncTask(this, importEvent, R.string.importing, false);
        task.execute(new Object());
    }

    private void
    actionImportDB() {
        CharSequence title = getResources().getText(R.string.importdb);
        CharSequence msg = getResources().getText(R.string.database) + " <= " + exDBFilePath;
        LookAndFeel.buildConfirmDialog(this, title, msg, new ConfirmDialogAction() {
            @Override
            public void onOk(Dialog dialog) {
                importDB();
            }
        }).show();
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
