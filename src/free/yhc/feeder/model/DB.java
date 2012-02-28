package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.util.HashMap;

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

    static final String         TABLE_CATEGORY  = "category";
    static final String         TABLE_CHANNEL   = "channel";
    private static final String TABLE_ITEM      = "item";

    private HashMap<Long, Boolean> updatingChannels = new HashMap<Long, Boolean>();

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
        LASTUPDATE      ("lastupdate",      "text",     "not null"), // time when channel is updated, lastly
        ACTION          ("action",          "text",     "not null"),
        // 'order' is reserved word at DB. so make it's column name as 'listing order'
        ORDER           ("listingorder",    "text",     "not null"), // normal / reverse
        URL             ("url",             "text",     "not null"), // channel url of this rss.
        UNOPENEDCOUNT   ("unopenedcount",   "integer",  "not null"),
        CATEGORYID      ("categoryid",      "integer",  ""),
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

    static String
    getItemTableName(long channelid) {
        return TABLE_ITEM + channelid;
    }

    static String
    getTempItemTableName(long channelid) {
        return TABLE_ITEM + "temp" + channelid;
    }

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

    private long
    createItemTable(long cid) {
        try {
            db.execSQL(buildTableSQL(getItemTableName(cid), ColumnItem.values()));
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    createTempItemTable(long cid) {
        try {
            db.execSQL(buildTableSQL(getTempItemTableName(cid), ColumnItem.values()));
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }


    private long
    alterItemTable_toTemp(long cid) {
        try {
            db.execSQL("ALTER TABLE '" + getItemTableName(cid) + "' RENAME TO '" + getTempItemTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    alterItemTable_toMain(long cid) {
        try {
            db.execSQL("ALTER TABLE '" + getTempItemTableName(cid) + "' RENAME TO '" + getItemTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    dropItemTable(long cid) {
        try {
            db.execSQL("DROP TABLE '" + getItemTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    dropTempItemTable(long cid) {
        try {
            db.execSQL("DROP TABLE '" + getTempItemTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private boolean
    isUnderUpdating(long cid) {
        return (null != updatingChannels.get(cid));
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
    /*
    Cursor
    query(String table,
          String[] columns,
          String selection,
          String[] selectionArgs,
          String groupBy,
          String having,
          String orderBy,
          String limit) {
        eAssert(false); // do not use if possible.
        return db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    Cursor
    query(String table,
          String[] columns,
          String selection,
          String[] selectionArgs,
          String groupBy,
          String having,
          String orderBy) {
        eAssert(false); // do not use if possible.
        return db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    }
    */

    Cursor
    query(String table, Column[] columns) {
        return db.query(table,
                        getColumnNames(columns),
                        null, null, null, null, null);
    }

    Cursor
    query(String table,
          Column[] columns,
          String selection,
          String[] selectionArgs,
          String groupBy,
          String having,
          String orderBy) {
        return db.query(table,
                        getColumnNames(columns),
                        selection, selectionArgs, groupBy, having, orderBy);
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

    /*
     * IMPORTANT : This is not one-transaction!!!
     *
     * Insert channel and it's items.
     * This is used by DBPolicy.(Not public!)
     *
     * @param ch
     * @return channel row id if success / -1 if fails.
     */
    long
    insertChannel(Feed.Channel ch) {
        // New insertion. So, full update!!

        ContentValues values = new ContentValues();
        // application's internal information
        values.put(ColumnChannel.URL.getName(),              ch.profD.url);
        values.put(ColumnChannel.ACTION.getName(),           ch.dynD.action.name());
        values.put(ColumnChannel.ORDER.getName(),            ch.dynD.order.name());
        values.put(ColumnChannel.CATEGORYID.getName(),       ch.dbD.categoryid);
        values.put(ColumnChannel.LASTUPDATE.getName(),       ch.dbD.lastupdate);

        // temporal : this column is for future use.
        values.put(ColumnChannel.UNOPENEDCOUNT.getName(),    0);

        if (null != ch.dynD.imageblob)
            values.put(ColumnChannel.IMAGEBLOB.getName(),    ch.dynD.imageblob);

        // information defined by spec.
        values.put(ColumnChannel.TITLE.getName(),            ch.parD.title);
        values.put(ColumnChannel.DESCRIPTION.getName(),      ch.parD.description);

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
        eAssert(!isUnderUpdating(cid));
        long r = db.delete(TABLE_CHANNEL,
                           ColumnChannel.ID.getName() + " = " + cid,
                           null);
        if (0 != r) {
            dropItemTable(cid);
            dropTempItemTable(cid);
        }

        return r;
    }

    long
    updateChannel(long cid, ColumnChannel column, String value) {
        eAssert(!isUnderUpdating(cid));
        ContentValues cvs = new ContentValues();
        cvs.put(column.getName(), value);

        return db.update(TABLE_CHANNEL,
                         cvs,
                         ColumnChannel.ID.getName() + " = " + cid,
                         null);
    }

    long
    updateChannel(long cid, ColumnChannel[] columns, String[] values) {
        eAssert(!isUnderUpdating(cid));
        ContentValues cvs = new ContentValues();
        eAssert(columns.length == values.length);
        for (int i = 0; i < columns.length; i++)
            cvs.put(columns[i].getName(), values[i]);

        return db.update(TABLE_CHANNEL,
                         cvs,
                         ColumnChannel.ID.getName() + " = " + cid,
                         null);
    }

    long
    updateChannel(long cid, ColumnChannel column, long value) {
        eAssert(!isUnderUpdating(cid));
        ContentValues cvs = new ContentValues();
        cvs.put(column.getName(), value);

        return db.update(TABLE_CHANNEL,
                         cvs,
                         ColumnChannel.ID.getName() + " = " + cid,
                         null);
    }

    long
    updateChannel(long cid, ColumnChannel column, byte[] data) {
        eAssert(!isUnderUpdating(cid));
        ContentValues cvs = new ContentValues();
        cvs.put(column.getName(), data);

        return db.update(TABLE_CHANNEL,
                         cvs,
                         ColumnChannel.ID.getName() + " = " + cid,
                         null);
    }

    long
    updateChannel(long cid, Feed.Channel.Order order) {
        eAssert(!isUnderUpdating(cid));
        ContentValues cvs = new ContentValues();
        cvs.put(ColumnChannel.ORDER.getName(), order.name());
        return db.update(TABLE_CHANNEL,
                         cvs,
                         ColumnChannel.ID.getName() + " = " + cid,
                         null);
    }

    long
    updateChannel(long cid, Feed.Channel.Action action) {
        eAssert(!isUnderUpdating(cid));
        ContentValues cvs = new ContentValues();
        cvs.put(ColumnChannel.ACTION.getName(), action.name());
        return db.update(TABLE_CHANNEL,
                         cvs,
                         ColumnChannel.ID.getName() + " = " + cid,
                         null);
    }

    long
    insertItem(long cid, Feed.Item item) {
        ContentValues values = new ContentValues();

        // information defined by spec.
        values.put(ColumnItem.CHANNELID.getName(),           cid);
        values.put(ColumnItem.TITLE.getName(),               item.parD.title);
        values.put(ColumnItem.LINK.getName(),                item.parD.link);
        values.put(ColumnItem.DESCRIPTION.getName(),         item.parD.description);
        values.put(ColumnItem.PUBDATE.getName(),             item.parD.pubDate);
        values.put(ColumnItem.ENCLOSURE_URL.getName(),       item.parD.enclosureUrl);
        values.put(ColumnItem.ENCLOSURE_LENGTH.getName(),    item.parD.enclosureLength);
        values.put(ColumnItem.ENCLOSURE_TYPE.getName(),      item.parD.enclosureType);
        values.put(ColumnItem.STATE.getName(),               item.dynD.state.name());

        String tableName = isUnderUpdating(cid)? getTempItemTableName(cid): getItemTableName(cid);
        return db.insert(tableName, null, values);
    }

    long
    updateItem(long cid, long id, Feed.Item.State state) {
        // Update item during 'updating channel' is not expected!!
        eAssert(!isUnderUpdating(cid));
        ContentValues values = new ContentValues();
        values.put(ColumnItem.STATE.getName(), state.name());
        return db.update(getItemTableName(cid),
                         values,
                         ColumnItem.ID.getName() + " = " + id,
                         null);
    }

    long
    prepareUpdateItemTable(long cid) {
        if (!doesTableExists(getTempItemTableName(cid)))
            logW("Temp Item Table exists : cid(" + cid + ")");
        // to make sure.
        dropTempItemTable(cid);

        updatingChannels.put(cid, true);
        if (0 > createTempItemTable(cid)) {
            updatingChannels.remove(cid);
            return -1;
        }
        return 0;
    }

    long
    completeUpdateItemTable(long cid) {
        dropItemTable(cid);
        alterItemTable_toMain(cid);
        updatingChannels.remove(cid);
        return 0;
    }

    long
    rollbackUpdateItemTable(long cid) {
        dropTempItemTable(cid);
        updatingChannels.remove(cid);
        return 0;
    }
}
