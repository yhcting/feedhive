package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

// This is singleton
public final class DB extends SQLiteOpenHelper {
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

    private static final String itemQueryDefaultOrder = ColumnItem.ID.getName() + " DESC";
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
        IMAGEBLOB       ("imageblob",       "blob",     ""), // image from channel tag.
        LASTUPDATE      ("lastupdate",      "integer",  "not null"), // time when channel is updated, lastly
        ACTION          ("action",          "text",     "not null"),
        UPDATETYPE      ("updatetype",      "text",     "not null"),
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
        STATE           ("state",           "text",     "not null"), // new, read etc
        RAWDATA         ("rawdata",         "blob",     "not null"),
        // time when this item is inserted.(milliseconds since 1970.1.1....)
        INSTIME         ("instime",         "integer",  "not null"),
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

    private static String
    getItemTableName(long channelid) {
        return TABLE_ITEM + channelid;
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
        db.execSQL(buildTableSQL(TABLE_CHANNEL,  ColumnChannel.values()));
        db.execSQL(buildTableSQL(TABLE_CATEGORY, ColumnCategory.values()));
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
        super(context, NAME, null, VERSION);
    }

    public static DB
    newSession(Context context) {
        eAssert(null == instance);
        instance = new DB(context);
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

    /**************************************
     * RSS DB operation
     **************************************/

    long
    createItemTable(long cid) {
        try {
            db.execSQL(buildTableSQL(getItemTableName(cid), ColumnItem.values()));
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    long
    dropItemTable(long cid) {
        try {
            db.execSQL("DROP TABLE '" + getItemTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    Cursor
    queryCategory(ColumnCategory column) {
        return queryCategory(new ColumnCategory[] { column });
    }

    Cursor
    queryCategory(ColumnCategory[] columns) {
        return db.query(TABLE_CATEGORY,
                        getColumnNames(columns),
                        null, null, null, null, null);
    }

    Cursor
    queryCategory(ColumnCategory column, ColumnCategory where, String value) {
        return queryCategory(new ColumnCategory[] { column }, where, value);
    }

    Cursor
    queryCategory(ColumnCategory[] columns, ColumnCategory where, String value) {
        return db.query(TABLE_CATEGORY,
                        getColumnNames(columns),
                        where.getName() + " = " + DatabaseUtils.sqlEscapeString(value),
                        null, null, null, null);
    }

    Cursor
    queryChannel(ColumnChannel column) {
        return queryChannel(new ColumnChannel[] { column });
    }

    Cursor
    queryChannel(ColumnChannel[] columns) {
        return db.query(TABLE_CHANNEL,
                        getColumnNames(columns),
                        null, null, null, null,
                        channelQueryDefaultOrder);
    }

    Cursor
    queryChannel(long cid, ColumnChannel column) {
        return queryChannel(cid, new ColumnChannel[] { column });
    }

    Cursor
    queryChannel(long cid, ColumnChannel[] columns) {
        return db.query(TABLE_CHANNEL,
                        getColumnNames(columns),
                        ColumnChannel.ID.getName() + " = '" + cid + "'",
                        null, null, null,
                        channelQueryDefaultOrder);
    }

    Cursor
    queryChannel(ColumnChannel column, ColumnChannel where, String value) {
        return queryChannel(new ColumnChannel[] { column }, where, value);
    }

    Cursor
    queryChannel(ColumnChannel[] columns, ColumnChannel where, String value) {
        return db.query(TABLE_CHANNEL,
                        getColumnNames(columns),
                        where.getName() + " = " + DatabaseUtils.sqlEscapeString(value),
                        null, null, null,
                        channelQueryDefaultOrder);
    }

    Cursor
    queryChannel(ColumnChannel column, ColumnChannel where, long value) {
        return queryChannel(new ColumnChannel[] { column }, where, value);
    }

    Cursor
    queryChannel(ColumnChannel[] columns, ColumnChannel where, long value) {
        return db.query(TABLE_CHANNEL,
                        getColumnNames(columns),
                        where.getName() + " = '" + value + "'",
                        null, null, null,
                        channelQueryDefaultOrder);
    }

    Cursor
    queryChannel(ColumnChannel column, ColumnChannel orderColumn, boolean bAsc, long limit) {
        return queryChannel(new ColumnChannel[] { column }, orderColumn, bAsc, limit);
    }

    Cursor
    queryChannel(ColumnChannel[] columns, ColumnChannel orderColumn, boolean bAsc, long limit) {
        String order = orderColumn.getName() + (bAsc? " ASC": " DESC");
        return db.query(TABLE_CHANNEL,
                        getColumnNames(columns),
                        null, null, null, null,
                        order,
                        "" + limit);
    }

    Cursor
    queryChannelMax(ColumnChannel column) {
        return db.rawQuery("SELECT MAX(" + column.getName() + ") FROM " + TABLE_CHANNEL +"", null);
    }

    Cursor
    queryItem(long cid, ColumnItem[] columns) {
        return db.query(getItemTableName(cid),
                        getColumnNames(columns),
                        null, null, null, null,
                        itemQueryDefaultOrder);
    }

    Cursor
    queryItem(long cid, ColumnItem[] columns, long limit) {
        return db.query(getItemTableName(cid),
                        getColumnNames(columns),
                        null, null, null, null,
                        itemQueryDefaultOrder,
                        "" + limit);
    }

    Cursor
    queryItem(long cid, long id, ColumnItem column) {
        return queryItem(cid, id, new ColumnItem[] { column });
    }

    Cursor
    queryItem(long cid, long id, ColumnItem[] columns) {
        return db.query(getItemTableName(cid),
                        getColumnNames(columns),
                        ColumnItem.ID.getName() + " = '" + id + "'",
                        null, null, null, null);
    }

    Cursor
    queryItem(long cid, ColumnItem[] columns, ColumnItem where, String value) {
        return queryItem(cid, columns, new ColumnItem[] { where }, new String[] { value });
    }

    Cursor
    queryItem(long cid, ColumnItem[] columns, ColumnItem[] wheres, String[] values) {
        eAssert(wheres.length == values.length);
        String whereStr = "";
        for (int i = 0; i < wheres.length;) {
            whereStr += wheres[i].getName() + " = " + DatabaseUtils.sqlEscapeString(values[i]);
            if (++i < wheres.length)
                whereStr += " AND ";
        }

        // recently inserted item is located at top of rows.
        return db.query(getItemTableName(cid),
                        getColumnNames(columns),
                        whereStr,
                        null, null, null,
                        itemQueryDefaultOrder);
    }

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

    /*
     * IMPORTANT : This is not one-transaction!!!
     */
    long
    insertChannel(ContentValues values) {
        long cid = db.insert(TABLE_CHANNEL, null, values);
        if (cid >= 0) {
            if (0 > createItemTable(cid)) {
                deleteChannel(cid);
                return -1;
            }
        } else
            return -1; // fail to insert channel information.

        return cid;
    }

    /*
     * IMPORTANT : This is not one-transaction!!!
     */
    long
    deleteChannel(long cid) {
        long r = db.delete(TABLE_CHANNEL,
                           ColumnChannel.ID.getName() + " = " + cid,
                           null);
        if (0 != r)
            dropItemTable(cid);

        return r;
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
    updateChannel(long cid, ColumnChannel field, String v) {
        ContentValues cvs = new ContentValues();
        cvs.put(field.getName(), v);
        return updateChannel(cid, cvs);
    }

    long
    updateChannel(long cid, ColumnChannel field, Long v) {
        ContentValues values = new ContentValues();
        values.put(field.getName(), v);
        return updateChannel(cid, values);
    }

    long
    updateChannel(long cid, ColumnChannel field, byte[] v) {
        ContentValues values = new ContentValues();
        values.put(field.getName(), v);
        return updateChannel(cid, values);
    }

    long
    insertItem(long cid, ContentValues values) {
        return db.insert(getItemTableName(cid), null, values);
    }

    long
    updateItem(long cid, long id, ContentValues values) {
        return db.update(getItemTableName(cid),
                         values,
                         ColumnItem.ID.getName() + " = " + id,
                         null);
    }

    long
    updateItem(long cid, long id, ColumnItem field, String v) {
        ContentValues cvs = new ContentValues();
        cvs.put(field.getName(), v);
        return updateItem(cid, id, cvs);
    }

    long
    updateItem(long cid, long id, ColumnItem field, Long v) {
        ContentValues cvs = new ContentValues();
        cvs.put(field.getName(), v);
        return updateItem(cid, id, cvs);
    }

    long
    updateItem(long cid, long id, ColumnItem field, byte[] v) {
        ContentValues cvs = new ContentValues();
        cvs.put(field.getName(), v);
        return updateItem(cid, id, cvs);
    }
}
