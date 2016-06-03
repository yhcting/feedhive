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

package free.yhc.feeder.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;

// NOTE
// This module is NOT THREAD SAFE!
public class AssetSQLiteHelper {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(AssetSQLiteHelper.class, Logger.LOGLV_DEFAULT);

    // Constructing arguments
    private String mDbName;
    private String mAssetDBFile;
    private int mVersion;

    private SQLiteDatabase mDb = null;

    /**
     *
     * @param dbName db file name of application private directory.
     * @param assetDBFile asset database file
     * @param version Current version of asset database file.
     *                If this is greater than existing db file in application private directory,
     *                existing db is replaced with new db file by copying from asset db file.
     *                Starts from 1.
     */
    public AssetSQLiteHelper(String dbName, String assetDBFile, int version) {
        mDbName = dbName;
        mAssetDBFile = assetDBFile;
        mVersion = version;
    }

    public void
    open() {
        File dbf = AppEnv.getAppContext().getDatabasePath(mDbName);
        try {
            InputStream is = AppEnv.getAppContext().getAssets().open(mAssetDBFile);
            if (!dbf.exists()) {
                FileOutputStream fos = new FileOutputStream(dbf);
                // This is first time. Just copy it!
                Util.copy(fos, is);
                fos.close();
            }

            mDb = SQLiteDatabase.openDatabase(dbf.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            if (DBG) P.d("AssetSQLiteHelper : App DB version: " + mVersion + " / current DB version: " + mDb.getVersion());
            if (mVersion > mDb.getVersion()) {
                // need to overwrite old db with new asset db.
                mDb.close();
                FileOutputStream fos = new FileOutputStream(dbf);
                Util.copy(fos, is);
                fos.close();
                mDb = SQLiteDatabase.openDatabase(dbf.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            }
            is.close();
        } catch (SQLiteException | IOException e) {
            P.bug(false);
        }
    }

    public SQLiteDatabase
    sqlite() {
        return mDb;
    }

    public void
    close() {
        if (null != mDb)
            mDb.close();
    }
}
