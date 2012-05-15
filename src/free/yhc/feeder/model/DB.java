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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

// This is singleton
public final class DB extends SQLiteOpenHelper implements
UnexpectedExceptionHandler.TrackedModule {
    private static DB instance = null;

    /**************************************
     * Members
     **************************************/
    private SQLiteDatabase db = null;


    /**************************************
     *
     * Database design
     * ---------------
     *  - table for items per each channel.
     *  - naming for item-table
     *      <prefix string> + channel-id
     *
     **************************************/
    // NOTE
    // Oops... mistake on spelling - 'feeder.db' is right.
    // But, software is already released and this is not big problem...
    // So, let's ignore it until real DB structure is needed to be changed.
    // => this can be resolved by 'DB Upgrade operation'.
    private static final String NAME    = "feader.db";
    private static final int    VERSION = 1;

    private static final String TABLE_CATEGORY  = "category";
    private static final String TABLE_CHANNEL   = "channel";
    private static final String TABLE_ITEM      = "item";

    private static final String itemQueryDefaultOrder = ColumnItem.PUBTIME.getName() + " DESC";
    private static final String channelQueryDefaultOrder = ColumnChannel.POSITION.getName() + " ASC";

    public interface Column {
        String getName();
        String getType();
        String getConstraint();
    }

    public static enum ColumnCategory implements Column {
        NAME            ("name",            "text",     "not null"), // channel url of this rss.
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");

        private String name;
        private String type;
        private String constraint;

        ColumnCategory(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        @Override
        public String getName() { return name; }
        @Override
        public String getType() { return type; }
        @Override
        public String getConstraint() { return constraint; }
    }

    public static enum ColumnChannel implements Column {
        // Required Channel Elements
        TITLE           ("title",           "text",     "not null"),
        DESCRIPTION     ("description",     "text",     "not null"),

        // Columns for internal use.
        IMAGEBLOB       ("imageblob",       "blob",     "not null"), // image from channel tag.
        LASTUPDATE      ("lastupdate",      "integer",  "not null"), // time when channel is updated, lastly
        // For fast/simple comparison, flag of 'long' type is used instead of text.
        ACTION          ("action",          "integer",  "not null"),
        UPDATEMODE      ("updatemode",      "integer",  "not null"),
        // state of this channel : used / unused etc.
        // Why this is required?
        // When delete channel, deleting all items that are stored at DB, doesn't make sense.
        // Deleting channel means "I don't want to get feed anymore".
        // (This doesn't mean "I don't want to delete all items too.")
        // In this case, items of deleted channel still has foreign key to channel id.
        // So, deleting channel from DB leads to DB corruption - foreign key constraints is broken!
        // To avoid this, we can mark channel as 'unused' and keep in DB.
        // This can preserve DB constraints.
        // When, channel is re-used, we can do it by marking it as 'used'.
        STATE           ("state",           "integer",  "not null"),
        // time string of SECONDS OF DAY (0 - 23:59:59). ex. "3600/7200" => update 1 and 2 hour every day
        SCHEDUPDATETIME ("schedupdatetime", "text",     "not null"),
        // old last item id.
        // This is usually, last item id before update.
        // This will be updated to current last item id when user recognizes newly update items.
        // NOTE (WARNING)
        //   Recently inserted items SHOULD NOT be removed!
        //   (item ID indicated by 'OLDLAST_ITEMID' should be valid!)
        //   So, implement 'delete items' should be consider this constraints!
        OLDLAST_ITEMID  ("oldlastitemid",   "integer",  "not null"),
        // number of items to keep in item table.
        // This is not hard-limit but soft-limit!
        // (There is no reason to support hard-limit. Soft-limit is enough!)
        //
        // NOT USED YET! For future use.
        NRITEMS_SOFTMAX ("nritemssoftmax",  "integer",  "not null"),
        URL             ("url",             "text",     "not null"), // channel url of this rss.
        CATEGORYID      ("categoryid",      "integer",  ""),
        POSITION        ("position",        "integer",  "not null"), // position order used by UI.
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement, "
                + "FOREIGN KEY(categoryid) REFERENCES " + TABLE_CATEGORY + "(" + ColumnCategory.ID.getName() + ")");


        private String name;
        private String type;
        private String constraint;

        ColumnChannel(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        @Override
        public String getName() { return name; }
        @Override
        public String getType() { return type; }
        @Override
        public String getConstraint() { return constraint; }
    }

    public static enum ColumnItem implements Column {
        TITLE           ("title",           "text",     "not null"),
        DESCRIPTION     ("description",     "text",     "not null"),
        LINK            ("link",            "text",     "not null"),
        ENCLOSURE_URL   ("enclosureurl",    "text",     "not null"),
        ENCLOSURE_LENGTH("enclosurelength", "text",     "not null"),
        ENCLOSURE_TYPE  ("enclosuretype",   "text",     "not null"),
        PUBDATE         ("pubdate",         "text",     "not null"),

        // Columns for internal use.
        STATE           ("state",           "integer",  "not null"), // new, read etc
        // time when this item is inserted.(milliseconds since 1970.1.1....)
        PUBTIME         ("pubtime",         "integer",  "not null"),
        CHANNELID       ("channelid",       "integer",  ""),
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement, "
                // Add additional : foreign key
                + "FOREIGN KEY(channelid) REFERENCES " + TABLE_CHANNEL + "(" + ColumnChannel.ID.getName() + ")");

        private String name;
        private String type;
        private String constraint;

        ColumnItem(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        @Override
        public String getName() { return name; }
        @Override
        public String getType() { return type; }
        @Override
        public String getConstraint() { return constraint; }
    }

    class QueryArg {

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
        logI("SQL Cmd : " + sql + "\n");
        return sql;
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
    buildSQLClauseString(Column[] cols, Object[] vals, String operator, String join) {
        String clause = null;
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
        return clause;
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
        Cursor c = db.query("sqlite_master",
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
    private DB(Context context) {
        super(context, NAME, null, getVersion());
    }

    public static DB
    newSession(Context context) {
        eAssert(null == instance);
        instance = new DB(context);
        UnexpectedExceptionHandler.S().registerModule(instance);
        return instance;
    }

    public static DB
    db() {
        eAssert(null != instance);
        return instance;
    }

    public void
    open() {
        db = getWritableDatabase();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ DB ]";
    }

    /**************************************
     * DB operation
     **************************************/
    // ====================
    //
    // Common
    //
    // ====================
    void
    beginTransaction() {
        db.beginTransaction();
    }

    void
    setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    void
    endTransaction() {
        db.endTransaction();
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
        return db.insert(TABLE_CATEGORY, null, values);
    }

    long
    deleteCategory(long id) {
        return db.delete(TABLE_CATEGORY,
                         ColumnCategory.ID.getName() + " = " + id,
                         null);
    }

    long
    deleteCategory(String name) {
        return db.delete(TABLE_CATEGORY,
                         ColumnCategory.NAME.getName() + " = " + DatabaseUtils.sqlEscapeString(name),
                         null);
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
        return db.update(TABLE_CATEGORY,
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
        return db.query(TABLE_CATEGORY,
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
        return db.insert(TABLE_CHANNEL, null, values);
    }

    long
    deleteChannel(long cid) {
        return db.delete(TABLE_CHANNEL,
                        ColumnChannel.ID.getName() + " = " + cid,
                        null);
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
        return db.update(TABLE_CHANNEL,
                values,
                ColumnChannel.ID.getName() + " = " + cid,
                null);
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
        if (v instanceof String)
            cvs.put(field.getName(), (String)v);
        else if (v instanceof Long)
            cvs.put(field.getName(), (Long)v);
        else if (v instanceof byte[])
            cvs.put(field.getName(), (byte[])v);
        else
            eAssert(false);
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
        db.execSQL(sbldr.toString());
    }



    /**
     * @param columns
     * @param orderColumn
     * @param where
     * @param value
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
                        channelQueryDefaultOrder:
                        orderColumn.getName() + (bAsc? " ASC": " DESC");
        return db.query(TABLE_CHANNEL,
                        getColumnNames(columns),
                        buildSQLClauseString(wheres, values, "=", "AND"),
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
        return db.rawQuery("SELECT MAX(" + column.getName() + ") FROM " + TABLE_CHANNEL +"", null);
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
        String chWhereStr = buildSQLClauseString(wheres, values, "=", "OR");

        // getting channels to delete.
        Cursor c = db.query(TABLE_CHANNEL,
                            new String[] { ColumnChannel.ID.getName() },
                            chWhereStr,
                            null, null, null, null);

        if (!c.moveToFirst()) {
            c.close();
            return 0;
        }

        Long[] cids = new Long[c.getCount()];
        DB.ColumnItem[] cols = new DB.ColumnItem[cids.length];

        int i = 0;
        do {
            cids[i] = c.getLong(0);
            cols[i] = DB.ColumnItem.CHANNELID;
            i++;
        } while (c.moveToNext());

        // delete items first
        long nrItems = db.delete(TABLE_ITEM,
                                 buildSQLClauseString(cols, cids, "=", "OR"),
                                 null);
        // then delete channel.
        db.delete(TABLE_CHANNEL, chWhereStr, null);
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
        return db.insert(TABLE_ITEM, null, values);
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
        return db.update(TABLE_ITEM,
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
        eAssert(null != where && null != value);
        return queryItem(columns, new ColumnItem[] { where }, new Object[] { value }, limit);
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
     * @return
     */
    Cursor
    queryItem(ColumnItem[] columns, ColumnItem[] wheres, Object[] values, long limit) {
        // recently inserted item is located at top of rows.
        return db.query(TABLE_ITEM,
                        getColumnNames(columns),
                        buildSQLClauseString(wheres, values, "=", "AND"),
                        null, null, null,
                        itemQueryDefaultOrder,
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
     * @return
     */
    Cursor
    queryItemMask(ColumnItem[] columns, ColumnItem where, long mask, long value) {
        return db.query(TABLE_ITEM,
                getColumnNames(columns),
                where.getName() + " & " + mask + " = " + value,
                null, null, null,
                itemQueryDefaultOrder);
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
     * @param limit
     *   ( <= 0) means "All"
     * @return
     */
   Cursor
   queryItemOR(ColumnItem[] columns, ColumnItem[] wheres, Object[] values, long limit) {
       // recently inserted item is located at top of rows.
       return db.query(TABLE_ITEM,
                       getColumnNames(columns),
                       buildSQLClauseString(wheres, values, "=", "OR"),
                       null, null, null,
                       itemQueryDefaultOrder,
                       (limit > 0)? "" + limit: null);
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
       return db.delete(TABLE_ITEM,
                        buildSQLClauseString(wheres, values, "=", "OR"),
                        null);
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
    * @param cid
    * @param limit
    * @return
    */
   Cursor
   queryItemIds(long cid, long limit) {
       return db.query(TABLE_ITEM, new String[] {ColumnItem.ID.getName()},
                       ColumnItem.CHANNELID.getName() + " = " + cid,
                       null, null, null,
                       ColumnItem.ID.getName() + " DESC",
                       (limit > 0)? "" + limit: null);
   }
}
