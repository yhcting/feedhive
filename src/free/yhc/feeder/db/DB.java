/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
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

package free.yhc.feeder.db;

import static free.yhc.feeder.model.Utils.eAssert;

import java.lang.reflect.Method;
import java.util.HashMap;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import free.yhc.feeder.model.Environ;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.ListenerManager;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

// This is singleton
public final class DB extends SQLiteOpenHelper implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(DB.class);

    public static final long INVALID_ITEM_ID    = -1;
    /**************************************
     *
     * Database design
     * ---------------
     *  - table for items per each channel.
     *  - naming for item-table
     *      <prefix string> + channel-id
     *
     **************************************/
    static final String TABLE_CATEGORY  = "category";
    static final String TABLE_CHANNEL   = "channel";
    static final String TABLE_ITEM      = "item";

    // NOTE
    // Oops... mistake on spelling - 'feeder.db' is right.
    // But, software is already released and this is not big problem...
    // So, let's ignore it until real DB structure is needed to be changed.
    // => this can be resolved by 'DB Upgrade operation'.
    private static final String NAME            = "feader.db";
    private static final int    VERSION         = 3;

    private static final long FLAG_CATEGORY_TABLE = 0x1;
    private static final long FLAG_CHANNEL_TABLE  = 0x10;
    private static final long FLAG_CHANNEL_DATA   = 0x20;
    private static final long FLAG_ITEM_TABLE     = 0x100;

    private static final String ITEM_QUERY_DEFAULT_ORDER = ColumnItem.PUBTIME.getName() + " DESC";
    private static final String CHANNEL_QUERY_DEFAULT_ORDER = ColumnChannel.POSITION.getName() + " ASC";

    private static DB sInstance = null;

    /**************************************
     * Members
     **************************************/
    private SQLiteDatabase mDb = null;

    private final ListenerManager mLm = new ListenerManager();

    public enum UpdateType implements ListenerManager.Type {
        CATEGORY_TABLE  (FLAG_CATEGORY_TABLE),
        CHANNEL_TABLE   (FLAG_CHANNEL_TABLE),
        CHANNEL_DATA    (FLAG_CHANNEL_DATA), // arg0 : <Long> channel id.
        ITEM_TABLE      (FLAG_ITEM_TABLE);

        private final long _mFlag;

        private UpdateType(long flag) {
            _mFlag = flag;
        }

        @Override
        public long
        flag() {
            return _mFlag;
        }
    }

    public interface Column {
        String getName();
        String getType();
        String getConstraint();
    }

    // =====================================================
    // Helper function for generating query
    // =====================================================
    public static String
    getDBName() {
        return NAME;
    }

    /**
     * Verify that given DB is compatible with this application?
     * @param db
     * @return
     */
    public static Err
    verifyDB(SQLiteDatabase db) {
        if (VERSION != db.getVersion())
            return Err.VERSION_MISMATCH;

        final int iTblName = 0;
        final int iTblSql  = 1;
        String[][] tbls = new String[][] {
                new String[] { "android_metadata",  "CREATE TABLE android_metadata (locale TEXT);" },
                new String[] { TABLE_CATEGORY,      buildTableSQL(TABLE_CATEGORY, ColumnCategory.values())},
                new String[] { TABLE_CHANNEL,       buildTableSQL(TABLE_CHANNEL,  ColumnChannel.values())},
                new String[] { TABLE_ITEM,          buildTableSQL(TABLE_ITEM,     ColumnItem.values())},
        };

        Cursor c = db.query("sqlite_master",
                            new String[] {"name", "sql"},
                            "type = 'table'",
                            null, null, null, null);

        HashMap<String, String> map = new HashMap<String, String>();
        if (c.moveToFirst()) {
            do {
                // Key : table name, Value : sql text
                map.put(c.getString(0), c.getString(1));
            } while (c.moveToNext());
        }
        c.close();

        // Verify
        for (String[] ts : tbls) {
            // Remove tailing ';' of sql statement.
            String tssql = ts[iTblSql].substring(0, ts[iTblSql].length() - 1);
            String sql = map.get(ts[iTblName]);
            if (null == sql || !sql.equalsIgnoreCase(tssql))
                return Err.DB_UNKNOWN;
        }
        return Err.NO_ERR;
    }

    public static String
    buildSQLWhere(String column, String search) {
        String[] toks = search.split("\\s+");
        String where = "";
        int i = 0;
        while (i < toks.length && !toks[0].isEmpty()) {
            where += column + " LIKE " + DatabaseUtils.sqlEscapeString("%" + toks[i] + "%");
            if (++i >= toks.length)
                break;
            where += " AND ";
        }
        if (!where.isEmpty())
            where = "(" + where + ")";

        return where;
    }

    public static String
    buildSQLWhere(String[] columns, String[] searchs, long fromPubtime, long toPubtime) {
        String wh = "";
        // check that pubtime is valid value or not.
        if (fromPubtime >= 0 && toPubtime >= fromPubtime)
            wh += "("
                  + ColumnItem.PUBTIME.getName() + " >= " + fromPubtime + " AND "
                  + ColumnItem.PUBTIME.getName() + " <= " + toPubtime
                  + ")";

        if (null != columns && null != searchs) {
            eAssert(columns.length == searchs.length);
            String whSearch = "";
            int i = 0;
            while (i < columns.length) {
                String tmp = buildSQLWhere(columns[i], searchs[i]);
                if (!tmp.isEmpty())
                    whSearch += whSearch.isEmpty()? tmp: " OR " + tmp;
                i++;
            }

            if (!whSearch.isEmpty())
                wh += wh.isEmpty()? whSearch: " AND (" + whSearch + ")";
        }

        if (!wh.isEmpty())
            wh = "(" + wh + ")";

        return wh;
    }


    /**
     * This function will generate SQL string like below
     * cols[0] 'operator' vals[0] 'join' cols[1] 'operator' vals[1] 'join' ...
     * @param cols
     * @param vals
     * @param operator
     * @param join
     * @return
     */
    private static String
    buildSQLWhere(Column[] cols, Object[] vals, String operator, String join) {
        String clause = "";
        if (null != cols && null != vals) {
            eAssert(cols.length == vals.length);
            clause = "";
            operator = " " + operator + " ";
            join = " " + join + " ";
            for (int i = 0; i < cols.length;) {
                clause += cols[i].getName() + operator
                            + DatabaseUtils.sqlEscapeString(vals[i].toString());
                if (++i < cols.length)
                    clause += join;
            }
        }

        if (!clause.isEmpty())
            clause = "(" + clause + ")";

        return clause;
    }


    static long
    getDefaultCategoryId() {
        return 0;
    }

    static int
    getVersion() {
        return VERSION;
    }

    /**************************************
     * Data
     **************************************/


    /**************************************
     * DB operation
     **************************************/
    /**
     * Get SQL statement for creating table
     * @param table
     *   name of table
     * @param cols
     *   columns of table.
     * @return
     */
    private static String
    buildTableSQL(String table, Column[] cols) {
        String sql = "CREATE TABLE " + table + " (";
        for (Column col : cols) {
            sql += col.getName() + " "
                    + col.getType() + " "
                    + col.getConstraint() + ", ";
        }
        sql += ");";
        sql = sql.replace(", );", ");");
        if (DBG) P.v("SQL Cmd : " + sql + "\n");
        return sql;
    }

    /**
     * Convert column[] to string[] of column's name
     * @param cols
     * @return
     */
    private static String[]
    getColumnNames(Column[] cols) {
        String[] strs = new String[cols.length];
        for (int i = 0; i < cols.length; i++)
            strs[i] = cols[i].getName();
        return strs;
    }

    private boolean
    doesTableExists(String tablename) {
        Cursor c = mDb.query("sqlite_master",
                             new String[] {"name"},
                             "type = 'table' AND name = '" + tablename + "'",
                             null, null, null, null);
        boolean ret = c.moveToFirst();
        c.close();
        return ret;
    }

    /**************************************
     *
     * DB UPGRADE
     *
     **************************************/
    /*
    private static String
    buildAddColumnSQL(String table, Column col) {
        //return "ALTER TABLE " + table + " ADD COLUMN "
    }
    */

    private void
    upgradeTo2(SQLiteDatabase db) {
        // New constraints is introduced.
        // All url of channel SHOULD NOT ends with '/'.

        Cursor c = db.query(TABLE_CHANNEL,
                            new String[] { ColumnChannel.ID.getName(),
                                           ColumnChannel.URL.getName() },
                            null, null, null, null, null);
        if (!c.moveToFirst()) {
            c.close();
            return; // nothing to do
        }

        do {
            String url = c.getString(1);
            if (url.endsWith("/")) {
                url = Utils.removeTrailingSlash(url);
                ContentValues cvs = new ContentValues();
                cvs.put(ColumnChannel.URL.getName(), url);
                db.update(TABLE_CHANNEL, cvs,
                          ColumnChannel.ID.getName() + " = " + c.getLong(0),
                          null);
            }
        } while (c.moveToNext());
        c.close();
    }

    private void
    upgradeTo3(SQLiteDatabase db) {

    }
    /**************************************
     * Overriding.
     **************************************/

    @Override
    public void
    onCreate(SQLiteDatabase db) {
        db.execSQL(buildTableSQL(TABLE_CATEGORY, ColumnCategory.values()));
        db.execSQL(buildTableSQL(TABLE_CHANNEL,  ColumnChannel.values()));
        db.execSQL(buildTableSQL(TABLE_ITEM,     ColumnItem.values()));
        // default category is empty-named-category
        db.execSQL("INSERT INTO " + TABLE_CATEGORY + " ("
                    + ColumnCategory.NAME.getName() + ", " + ColumnCategory.ID.getName() + ") "
                    + "VALUES (" + "'', " + getDefaultCategoryId() + ");");
    }

    @Override
    public void
    onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        int dbv = oldVersion;
        while (dbv < newVersion) {
            switch (dbv) {
            case 1:
                upgradeTo2(db);
                break;
            }
            dbv++;
        }
    }

    @Override
    public void
    close() {
        super.close();
        // Something to do???
    }

    @Override
    public void
    onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Something to do???
    }

    /**************************************
     * Operation
     **************************************/
    private DB() {
        super(Environ.getAppContext(), NAME, null, getVersion());
        UnexpectedExceptionHandler.get().registerModule(sInstance);
    }

    public static DB
    get() {
        if (null == sInstance)
            sInstance = new DB();
        return sInstance;
    }

    public void
    open() {
        mDb = getWritableDatabase();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ DB ]";
    }
    /**************************************
     * DB monitoring
     **************************************/
    void
    registerUpdatedListener(ListenerManager.Listener listener, long flag) {
        mLm.registerListener(listener, null, flag);
    }

    void
    unregisterUpdatedListener(ListenerManager.Listener listener) {
        mLm.unregisterListener(listener);
    }
    /**************************************
     * DB operation
     **************************************/
    /**
     * In case that DB file is changed, database should be reloaded.
     *   by using this function.
     * This function may takes lots of time.
     */
    void
    reloadDatabase() {
        mDb.close();
        open();

        // All DB information is changed now!.
        mLm.notifyIndirect(UpdateType.CATEGORY_TABLE);
        mLm.notifyIndirect(UpdateType.CHANNEL_TABLE);
        mLm.notifyIndirect(UpdateType.ITEM_TABLE);

        // Mark as all channel is changed
        Cursor c = mDb.query(TABLE_CHANNEL,
                             getColumnNames(new ColumnChannel[] { ColumnChannel.ID }),
                             null, null, null, null, null);
        if (c.moveToFirst()) {
            do {
                mLm.notifyIndirect(UpdateType.CHANNEL_DATA, c.getLong(0));
            } while (c.moveToNext());
        }
        c.close();

    }

    // ====================
    //
    // Common
    //
    // ====================
    void
    beginTransaction() {
        mDb.beginTransaction();
    }

    void
    setTransactionSuccessful() {
        mDb.setTransactionSuccessful();
    }

    void
    endTransaction() {
        mDb.endTransaction();
    }

    // ====================
    //
    // Category
    //
    // ====================
    long
    insertCategory(Feed.Category category) {
        ContentValues values = new ContentValues();
        values.put(ColumnCategory.NAME.getName(), category.name);
        long catid = mDb.insert(TABLE_CATEGORY, null, values);
        if (catid > 0)
            mLm.notifyIndirect(UpdateType.CATEGORY_TABLE);
        return catid;
    }

    long
    deleteCategory(long id) {
        int nr =  mDb.delete(TABLE_CATEGORY,
                             ColumnCategory.ID.getName() + " = " + id,
                             null);
        if (nr > 0)
            mLm.notifyIndirect(UpdateType.CATEGORY_TABLE);
        return nr;
    }

    long
    deleteCategory(String name) {
        int nr =  mDb.delete(TABLE_CATEGORY,
                              ColumnCategory.NAME.getName() + " = " + DatabaseUtils.sqlEscapeString(name),
                              null);
        if (nr > 0)
            mLm.notifyIndirect(UpdateType.CATEGORY_TABLE);
        return nr;
    }

    /**
     * Update category name
     * @param id
     * @param name
     * @return
     */
    long
    updateCategory(long id, String name) {
        eAssert(Utils.isValidValue(name));
        ContentValues cvs = new ContentValues();
        cvs.put(ColumnCategory.NAME.getName(), name);
        return mDb.update(TABLE_CATEGORY,
                          cvs,
                          ColumnCategory.ID.getName() + " = " + id,
                          null);
    }

    /**
     *
     * @param column
     * @param where
     * @param value
     * @return
     */
    Cursor
    queryCategory(ColumnCategory column, ColumnCategory where, Object value) {
        return queryCategory(new ColumnCategory[] { column }, where, value);
    }

    /**
     *
     * @param columns
     * @param where
     *   if (null == value) than this is ignored.
     * @param value
     *   if (null == where) than this is ignored.
     * @return
     */
    Cursor
    queryCategory(ColumnCategory[] columns, ColumnCategory where, Object value) {
        String whereStr;
        if (null == where || null == value)
            whereStr = null;
        else
            whereStr = where.getName() + " = " + DatabaseUtils.sqlEscapeString(value.toString());
        return mDb.query(TABLE_CATEGORY,
                         getColumnNames(columns),
                         whereStr,
                         null, null, null, null);
    }

    // ====================
    //
    // Channel
    //
    // ====================
    /**
     * BE CAREFUL FOR USING THIS.
     * This will insert values without any sanity checking.
     * This function is visible to outside only for PERFORMANCE.
     * @param values
     * @return
     */
    long
    insertChannel(ContentValues values) {
        long cid = mDb.insert(TABLE_CHANNEL, null, values);
        if (cid > 0) {
            mLm.notifyIndirect(UpdateType.CHANNEL_TABLE);
            mLm.notifyIndirect(UpdateType.CHANNEL_DATA, cid);
        }
        return cid;
    }

    /**
     * This function doesn't do any sanity check for passing arguments.
     * So, if there is invalid column name in ContentValues, this function issues exception.
     * Please be careful when use this function!
     * @param cid
     * @param values
     * @return
     */
    long
    updateChannel(long cid, ContentValues values) {
        int nr = mDb.update(TABLE_CHANNEL,
                            values,
                            ColumnChannel.ID.getName() + " = " + cid,
                            null);
        if (nr > 0)
            mLm.notifyIndirect(UpdateType.CHANNEL_DATA, cid);
        return nr;
    }

    /**
     *
     * @param cid
     * @param field
     * @param v
     *   only String, Long and byte[] are supported.
     * @return
     */
    long
    updateChannel(long cid, ColumnChannel field, Object v) {
        ContentValues cvs = new ContentValues();
        try {
            Method m = cvs.getClass().getMethod("put", String.class, v.getClass());
            m.invoke(cvs, field.getName(), v);
        } catch (Exception e) {
            eAssert(false);
        }
        return updateChannel(cid, cvs);
    }

    /**
     * Update set of channel rows.
     * SQL statement will be created like below
     * [ SQL ]
     * UPDATE TABLE_CHANNEL
     *   SET 'target' = CASE 'where'
     *     WHEN 'whereValues[0]' THEN 'targetValues[0]'
     *     WHEN 'whereValues[1]' THEN 'targetValues[1]'
     *     ...
     *   END
     * WHERE id IN (whereValues[0], whereValues[1], ...)
     * @param target
     *   Column to be changed.
     * @param targetValues
     *   Target value array
     * @param where
     *   Column to compare
     * @param whereValues
     *   Values to compare with value of 'where' field.
     */
    void
    updateChannelSet(ColumnChannel target, Object[] targetValues,
                     ColumnChannel where,  Object[] whereValues) {
        eAssert(targetValues.length == whereValues.length);
        if (targetValues.length <= 0)
            return;

        StringBuilder sbldr = new StringBuilder();
        sbldr.append("UPDATE " + TABLE_CHANNEL + " ")
             .append(" SET " + target.getName() + " = CASE " + where.getName());
        for (int i = 0; i < targetValues.length; i++) {
            sbldr.append(" WHEN " + DatabaseUtils.sqlEscapeString(whereValues[i].toString()))
                 .append(" THEN " + DatabaseUtils.sqlEscapeString(targetValues[i].toString()));
        }
        sbldr.append(" END WHERE " + where.getName() + " IN (");
        for (int i = 0; i < whereValues.length;) {
            sbldr.append(DatabaseUtils.sqlEscapeString(whereValues[i].toString()));
            if (++i < whereValues.length)
                sbldr.append(", ");
        }
        sbldr.append(");");
        mDb.execSQL(sbldr.toString());

        if (where.equals(ColumnChannel.ID)) {
            for (Object o : whereValues)
                mLm.notifyIndirect(UpdateType.CHANNEL_DATA, o);
        }
    }

    /**
     * @param columns
     * @param where
     * @param value
     * @param orderColumn
     * @param bAsc
     * @param limit
     * @return
     */
    Cursor
    queryChannel(ColumnChannel[] columns,
                 ColumnChannel where, Object value,
                 ColumnChannel orderColumn, boolean bAsc,
                 long limit) {
        ColumnChannel[] wheres = (null == where)? null: new ColumnChannel[] { where };
        Object[] values = (null == value)? null: new Object[] { value };
        return queryChannel(columns,
                            wheres, values,
                            orderColumn, bAsc, limit);
    }

    /**
     *
     * @param columns
     * @param wheres
     *   if (null == values) than this is ignored.
     * @param values
     *   if (null == wheres) than this is ignored.
     * @param orderColumn
     * @param bAsc
     *   if (null == orderColumn) than this is ignored.
     * @param limit
     *   ( <= 0) means "All"
     * @return
     */
    Cursor
    queryChannel(ColumnChannel[] columns,
                 ColumnChannel[] wheres, Object[] values,
                 ColumnChannel orderColumn, boolean bAsc,
                 long limit) {
        String order = (null == orderColumn)?
                        CHANNEL_QUERY_DEFAULT_ORDER:
                        orderColumn.getName() + (bAsc? " ASC": " DESC");
        String whereClause = buildSQLWhere(wheres, values, "=", "AND");

        return mDb.query(TABLE_CHANNEL,
                         getColumnNames(columns),
                         whereClause.isEmpty()? null: whereClause,
                         null, null, null,
                         order,
                         (limit > 0)? "" + limit: null);
    }

    /**
     * Select channel that has max 'column' value.
     * @param column
     * @return
     */
    Cursor
    queryChannelMax(ColumnChannel column) {
        return mDb.rawQuery("SELECT MAX(" + column.getName() + ") FROM " + TABLE_CHANNEL +"", null);
    }

    /**
     * Delete channel
     * @param where
     * @param value
     * @return
     *   number of items deleted.
     */
    long
    deleteChannel(ColumnChannel where, Object value) {
        return deleteChannelOR(new ColumnChannel[] { where }, new Object[] { value });
    }

    /**
     * Delete channels and all belonging items
     * wheres and values are joined with "OR".
     * That is, wheres[0] == values[0] OR wheres[1] == values[1] ...
     * @param wheres
     * @param values
     * @return
     *   number of items deleted.
     */
    long
    deleteChannelOR(ColumnChannel[] wheres, Object[] values) {
        // NOTE
        // Deleting order should be,
        //   items -> channels.
        // Why?
        // Item has channel id as it's foreign key.
        // So, once channel is deleted, all items that has that channel as it's foreign key,
        //   should be deleted as one-transaction.
        // This will block DB access from other thread for this transaction.
        // (BAD for concurrence)
        // But, deleting whole item doesn't need to be done as one transaction.
        // That is, cancel between deleting items of channel is ok.
        // This doens't break DB's constraints.
        // So, we don't need to block BD with 'transaction' concept.
        String chWhereStr = buildSQLWhere(wheres, values, "=", "OR");

        // getting channels to delete.
        Cursor c = mDb.query(TABLE_CHANNEL,
                             new String[] { ColumnChannel.ID.getName() },
                             chWhereStr.isEmpty()? null: chWhereStr,
                             null, null, null, null);

        if (!c.moveToFirst()) {
            c.close();
            return 0;
        }

        Long[] cids = new Long[c.getCount()];
        ColumnItem[] cols = new ColumnItem[cids.length];
        int i = 0;
        do {
            cids[i] = c.getLong(0);
            cols[i] = ColumnItem.CHANNELID;
            i++;
        } while (c.moveToNext());

        String wh = buildSQLWhere(cols, cids, "=", "OR");
        // delete items first
        long nrItems = mDb.delete(TABLE_ITEM,
                                  wh.isEmpty()? null: wh,
                                  null);
        // then delete channel.
        int nr = mDb.delete(TABLE_CHANNEL, chWhereStr, null);

        // channel is deleted (NOT updated!)
        // So, notifyUpdated SHOULD NOT be called for deleted channel!
        if (nrItems > 0)
            mLm.notifyIndirect(UpdateType.ITEM_TABLE);

        // channel table is updated.
        if (nr > 0)
            mLm.notifyIndirect(UpdateType.CHANNEL_TABLE);

        return nrItems;
    }

    // ====================
    //
    // Item
    //
    // ====================
    /**
     * BE CAREFUL FOR USING THIS.
     * This will insert values without any sanity checking.
     * This function is visible to outside only for PERFORMANCE.
     * @param values
     * @return
     */
    long
    insertItem(ContentValues values) {
        long id = mDb.insert(TABLE_ITEM, null, values);
        if (id > 0)
            mLm.notifyIndirect(UpdateType.ITEM_TABLE);
        return id;
    }

    /**
     * BE CAREFUL FOR USING THIS.
     * This will insert values without any sanity checking.
     * This function is visible to outside only for PERFORMANCE.
     * @param values
     * @return
     */
    long
    updateItem(long id, ContentValues values) {
        return mDb.update(TABLE_ITEM,
                          values,
                          ColumnItem.ID.getName() + " = " + id,
                          null);
    }

    /**
     *
     * @param id
     * @param field
     * @param v
     *   only String, Long and byte[] type are allowed
     * @return
     */
    long
    updateItem(long id, ColumnItem field, Object v) {
        ContentValues cvs = new ContentValues();
        if (v instanceof String)
            cvs.put(field.getName(), (String)v);
        else if (v instanceof Long)
            cvs.put(field.getName(), (Long)v);
        else if (v instanceof byte[])
            cvs.put(field.getName(), (byte[])v);
        else
            eAssert(false);
        return updateItem(id, cvs);
    }

    /**
     * @param columns
     * @param where
     * @param value
     * @param limit
     * @return
     */
    Cursor
    queryItem(ColumnItem[] columns, ColumnItem where, Object value, long limit) {
        return queryItemAND(columns,
                            null != where? new ColumnItem[] { where }: null,
                            null != value? new Object[] { value }: null,
                            limit);
    }

    /**
     * wheres and values are joined with "AND".
     * That is, wheres[0] == values[0] AND wheres[1] == values[1] ...
     * @param columns
     * @param wheres
     *   if (null == values) than this is ignored.
     * @param values
     *   if (null == wheres) than this is ignored.
     * @param limit
     *   ( <= 0) means "All"
     * @param ordered
     *   true for ordered by pubtime
     * @return
     */
    Cursor
    queryItemAND(ColumnItem[] columns, ColumnItem[] wheres, Object[] values, long limit) {
        // recently inserted item is located at top of rows.
        String wh = buildSQLWhere(wheres, values, "=", "AND");
        return mDb.query(TABLE_ITEM,
                         getColumnNames(columns),
                         wh.isEmpty()? null: wh,
                         null, null, null,
                         ITEM_QUERY_DEFAULT_ORDER,
                         (limit > 0)? "" + limit: null);
    }

    /**
     * where clause is generated as follows.
     *   "(where & mask) = value"
     * @param columns
     * @param where
     * @param mask
     *   mask value used to masking 'where' value.
     * @param value
     *   value should be same after masking operation.
     * @param searchFields
     * @param searchs
     * @param fromPubtime
     * @param toPubtime
     * @param ordered
     *   true for ordered by pubtime
     * @return
     */
    Cursor
    queryItemMask(ColumnItem[] columns,
                  ColumnItem where, long mask, long value,
                  ColumnItem[] searchFields, String[] searchs,
                  long fromPubtime, long toPubtime,
                  boolean ordered) {
        // NOTE
        // To improve DB query performance, query for search would better to
        //   be located at later as possible.
        // (query for search is most expensive operation)
        String wh = where.getName() + " & " + mask + " = " + value;
        String search = buildSQLWhere(getColumnNames(searchFields), searchs,
                                      fromPubtime, toPubtime);
        if (!search.isEmpty())
            wh = "(" + wh + ") AND " + search;
        return mDb.query(TABLE_ITEM,
                         getColumnNames(columns),
                         wh,
                         null, null, null,
                         ordered? ITEM_QUERY_DEFAULT_ORDER: null);
    }

    /**
     * wheres and values are joined with "OR".
     * That is, wheres[0] == values[0] OR wheres[1] == values[1] ...
     *
     *
     * @param columns
     * @param wheres
     *   if (null == values) than this is ignored.
     * @param values
     *   if (null == wheres) than this is ignored.
     * @param searchFields
     * @param searchs
     * @param fromPubtime
     * @param toPubtime
     * @param limit
     *   ( <= 0) means "All"
     * @param ordered
     *   true for ordered by pubtime
     * @return
     */
    Cursor
    queryItemOR(ColumnItem[] columns,
                ColumnItem[] wheres, Object[] values,
                ColumnItem[] searchFields, String[] searchs,
                long fromPubtime, long toPubtime,
                long limit, boolean ordered) {
        // NOTE
        // To improve DB query performance, query for search would better to
        //   be located at later as possible.
        // (query for search is most expensive operation)
        String wh = buildSQLWhere(wheres, values, "=", "OR");
        String search = buildSQLWhere(getColumnNames(searchFields), searchs,
                                      fromPubtime, toPubtime);
        if (wh.isEmpty())
            wh = search;
        else if (!search.isEmpty())
            wh += " AND " + search;
        // recently inserted item is located at top of rows.
        return mDb.query(TABLE_ITEM,
                         getColumnNames(columns),
                         wh.isEmpty()? null: wh,
                         null, null, null,
                         ordered? ITEM_QUERY_DEFAULT_ORDER: null,
                         (limit > 0)? "" + limit: null);
    }

    /**
     *
     * @param column
     *   column to count.
     * @param where
     * @param value
     * @return
     */
    Cursor
    queryItemCount(ColumnItem column, ColumnItem where, long value) {
        return mDb.query(TABLE_ITEM,
                         new String[] { "COUNT(" + column.getName() + ")" },
                         where.getName() + " = " + value,
                         null, null, null, null);
    }

    /**
     * Delete item from item table.
     * @param where
     * @param value
     * @return
     *   number of items deleted.
     */
    long
    deleteItem(ColumnItem where, Object value) {
        return deleteItemOR(new ColumnItem[] { where }, new Object[] { value });
    }

    /**
     * Delete item from item table.
     * wheres and values are joined with "OR".
     * That is, wheres[0] == values[0] OR wheres[1] == values[1] ...
     * @param wheres
     * @param values
     * @return
     */
    long
    deleteItemOR(ColumnItem[] wheres, Object[] values) {
        String wh = buildSQLWhere(wheres, values, "=", "OR");
        int nr = mDb.delete(TABLE_ITEM,
                            wh.isEmpty()? null: wh,
                            null);
        if (nr > 0)
            mLm.notifyIndirect(UpdateType.ITEM_TABLE);
        return nr;
    }

    // ========================================================================
    //
    // NOT GENERAL functions.
    // (Used only for special reasons - usually due to performance reason)
    //
    // ========================================================================
    /**
     * Get ids of items belongs to given channel by descending order.
     * (That is, latest inserted one on first).
     * Why? Due to performance reason.
     * Getting descending-ordered-id by quering items to DB, is quite fast.
     * (Because id is auto-incrementing primary key.)
     *
     * Take your attention that this is NOT usual default order of item query at this application.
     * (default order is descending order of publish time.
     *  Without any description, 'descending order of publish time' is default for all other DB query functions.)
     *
     * NOTE
     * Why this function is NOT general form?
     * That is only for performance (Nothing else!).
     *
     * @param cid
     * @param limit
     * @return
     */
    Cursor
    queryItemIds(long cid, long limit) {
        return mDb.query(TABLE_ITEM, new String[] {ColumnItem.ID.getName()},
                         ColumnItem.CHANNELID.getName() + " = " + cid,
                         null, null, null,
                         ColumnItem.ID.getName() + " DESC",
                         (limit > 0)? "" + limit: null);
    }

    /**
     *
     * @param cids
     *   'null' for all items.
     * @param column
     * @param bMax
     * @return
     */
    Cursor
    queryItemMinMax(long[] cids, ColumnItem column, boolean bMax) {
        String where = "";
        int i = 0;
        if (null != cids) {
            while (i < cids.length) {
                where += ColumnItem.CHANNELID.getName() + " = " + cids[i];
                if (++i < cids.length)
                    where += " OR ";
            }
        }
        if (!where.isEmpty())
            where = " WHERE " + where;

        return mDb.rawQuery("SELECT " + (bMax? "MAX": "MIN") + "(" + column.getName()
                            + ") FROM " + TABLE_ITEM
                            + where, null);
    }

    /**
     *
     * @param where
     * @param mask
     * @param value
     * @param column
     * @param bMax
     * @return
     */
    Cursor
    queryItemMinMax(ColumnItem where, long mask, long value, ColumnItem column, boolean bMax) {
        String wh = where.getName() + " & " + mask + " = " + value;
        return mDb.rawQuery("SELECT " + (bMax? "MAX": "MIN") + "(" + column.getName()
                            + ") FROM " + TABLE_ITEM
                            + " WHERE " + wh, null);
    }

    // -----------------------------------------------------------------------
    // To support shrinking DB size
    // -----------------------------------------------------------------------
    /**
     *
     * @param cid
     *   channel id to delete old items.
     *   '-1' means 'for all channel'.
     * @param percent
     *   percent to delete.
     * @return
     *   number of items deleted
     */
    int
    deleteOldItems(long cid, int percent) {
        eAssert(0 <= percent && percent <= 100);

        if (0 == percent)
            return 0;

        String wh = "";
        if (100 == percent)
            // Special where clause value to delete all rows in the table.
            // See comment of "SQLiteDatabase.delete".
            wh = "1";
        else {
            Cursor c = null;

            // Default is sorted by ID in decrementing order.
            if (cid >= 0)
                c = queryItem(new ColumnItem[] { ColumnItem.PUBTIME },
                              ColumnItem.CHANNELID, cid, 0);
            else
                c = queryItem(new ColumnItem[] { ColumnItem.PUBTIME },
                        null, null, 0);

            // cursor position to delete from.
            long curCount = c.getCount();
            int pos = (int)(curCount - curCount * percent / 100);
            if (!c.moveToPosition(pos))
                eAssert(false);
            long putTimeFrom = c.getLong(0);
            c.close();

            if (cid >= 0)
                wh = ColumnItem.CHANNELID.getName() + " = " + cid + " AND ";
            wh += ColumnItem.PUBTIME.getName() + " < " + putTimeFrom;
        }
        int nr = mDb.delete(TABLE_ITEM, wh, null);

        // NOTE
        // Important fact that should be considered here is,
        //   "Channel is NOT changed."
        // Even if item is deleted, item ID is auto incrementing value.
        // So, OLDLAST_ITEMID of channel doesn't affected by this deleting work.
        // And any other channel value is not changed too.
        // So, DB doesn't need to notify that "channel is changed".
        if (nr > 0)
            mLm.notifyIndirect(UpdateType.ITEM_TABLE);

        return nr;
    }
}
