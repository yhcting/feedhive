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
        // add foreign key
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement, "
                // Add additional
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
        // TODO Auto-generated method stub

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
     *      if (null == value) than this is ignored.
     * @param value
     *      if (null == where) than this is ignored.
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
     *      if (null == values) than this is ignored.
     * @param values
     *      if (null == wheres) than this is ignored.
     * @param orderColumn
     * @param bAsc
     *      if (null == orderColumn) than this is ignored.
     * @param limit
     *      ( <= 0) means "All"
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
        String whereStr = null;
        if (null != wheres && null != values) {
            eAssert(wheres.length == values.length);
            whereStr = "";
            for (int i = 0; i < wheres.length;) {
                whereStr += wheres[i].getName() + " = " + DatabaseUtils.sqlEscapeString(values[i].toString());
                if (++i < wheres.length)
                    whereStr += " AND ";
            }
        }
        return db.query(TABLE_CHANNEL,
                        getColumnNames(columns),
                        whereStr,
                        null, null, null,
                        order,
                        (limit > 0)? "" + limit: null);
    }

    Cursor
    queryChannelMax(ColumnChannel column) {
        return db.rawQuery("SELECT MAX(" + column.getName() + ") FROM " + TABLE_CHANNEL +"", null);
    }


    // ====================
    //
    // Item
    //
    // ====================
    long
    insertItem(ContentValues values) {
        return db.insert(TABLE_ITEM, null, values);
    }

    long
    updateItem(long id, ContentValues values) {
        return db.update(TABLE_ITEM,
                         values,
                         ColumnItem.ID.getName() + " = " + id,
                         null);
    }

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
     *
     * @param columns
     * @param wheres
     *      if (null == values) than this is ignored.
     * @param values
     *      if (null == wheres) than this is ignored.
     * @param limit
     *      ( <= 0) means "All"
     * @return
     */
    Cursor
    queryItem(ColumnItem[] columns, ColumnItem[] wheres, Object[] values, long limit) {
        String whereStr = null;
        if (null != wheres && null != values) {
            eAssert(wheres.length == values.length);
            whereStr = "";
            for (int i = 0; i < wheres.length;) {
                whereStr += wheres[i].getName() + " = " + DatabaseUtils.sqlEscapeString(values[i].toString());
                if (++i < wheres.length)
                    whereStr += " AND ";
            }
        }
        // recently inserted item is located at top of rows.
        return db.query(TABLE_ITEM,
                        getColumnNames(columns),
                        whereStr,
                        null, null, null,
                        itemQueryDefaultOrder,
                        (limit > 0)? "" + limit: null);
    }
}
