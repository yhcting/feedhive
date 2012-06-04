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

package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

// NOTE
// This module is NOT THREAD SAFE!
public class AssetSQLiteHelper {
    // Constructing arguments
    private Context context;
    private String  dbName;
    private String  assetDBFile;
    private int     version;

    private SQLiteDatabase db = null;

    /**
     *
     * @param context
     * @param dbName
     *   db file name of application private directory.
     * @param assetDBFile
     *   asset database file
     * @param version
     *   Current version of asset database file.
     *   If this is greater than existing db file in application private directory,
     *     existing db is replaced with new db file by copying from asset db file.
     *   Starts from 1.
     */
    public AssetSQLiteHelper(Context context, String dbName, String assetDBFile, int version) {
        this.context    = context;
        this.dbName     = dbName;
        this.assetDBFile= assetDBFile;
        this.version    = version;
    }

    public void open() {
        File dbf = context.getDatabasePath(dbName);
        try {
            InputStream is = context.getAssets().open(assetDBFile);
            if (!dbf.exists()) {
                FileOutputStream fos = new FileOutputStream(dbf);
                // This is first time. Just copy it!
                Utils.copy(fos, is);
                fos.close();
            }

            db = SQLiteDatabase.openDatabase(dbf.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            logI("AssetSQLiteHelper : App DB version: " + version + " / current DB version: " + db.getVersion());
            if (version > db.getVersion()) {
                // need to overwrite old db with new asset db.
                db.close();
                FileOutputStream fos = new FileOutputStream(dbf);
                Utils.copy(fos, is);
                fos.close();
                db = SQLiteDatabase.openDatabase(dbf.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            }
            is.close();
        } catch (SQLiteException e0) {
            eAssert(false);
        } catch (IOException e1) {
            eAssert(false);
        }
    }

    public SQLiteDatabase
    sqlite() {
        return db;
    }

    public void close() {
        if (null != db)
            db.close();
    }
}
