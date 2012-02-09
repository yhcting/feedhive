package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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
     *  - table for rss items per each channel.
     *  - naming for rss-item-table
     *      <prefix string> + channel-id
     *
     **************************************/
    private static final String NAME    = "feader.db";
    private static final int    VERSION = 1;

    public interface Column {
        String getName();
        String getType();
        String getConstraint();
    }

    /**************************************
     * DB for RSS
     **************************************/
    static final String TABLE_FEEDCHANNEL        = "feedchannel";
    private static final String TABLE_FEEDITEM   = "feeditem";

    public static enum ColumnFeedChannel implements Column {
        // Required Channel Elements
        TITLE           ("title",           "text",     "not null"),
        DESCRIPTION     ("description",     "text",     "not null"),

        // Columns for internal use.
        IMAGEBLOB       ("imageblob",       "blob",     ""), // image from channel tag.
        LASTUPDATE      ("lastupdate",      "text",     "not null"), // time when channel is updated, lastly
        ACTIONTYPE      ("actiontype",      "text",     "not null"),
        URL             ("url",             "text",     "not null"), // channel url of this rss.
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");


        private String name;
        private String type;
        private String constraint;

        ColumnFeedChannel(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getConstraint() { return constraint; }
    }

    public static enum ColumnFeedItem implements Column {
        TITLE           ("title",           "text",     "not null"),
        DESCRIPTION     ("description",     "text",     "not null"),
        LINK            ("link",            "text",     "not null"),
        ENCLOSURE_URL   ("enclosureurl",    "text",     "not null"),
        ENCLOSURE_LENGTH("enclosurelength", "text",     "not null"),
        ENCLOSURE_TYPE  ("enclosuretype",   "text",     "not null"),
        PUBDATE         ("pubdate",         "text",     "not null"),

        // Columns for internal use.
        STATE           ("state",           "text",     "not null"), // new, read etc
        CHANNELID       ("channelid",       "integer",  "not null"),
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");

        private String name;
        private String type;
        private String constraint;

        ColumnFeedItem(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getConstraint() { return constraint; }
    }

    static String
    getFeedItemTableName(long channelid) {
        return TABLE_FEEDITEM + channelid;
    }

    static String
    getFeedItemTempTableName(long channelid) {
        return TABLE_FEEDITEM + "temp" + channelid;
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
            db.execSQL(buildTableSQL(getFeedItemTableName(cid), ColumnFeedItem.values()));
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    alterItemTable_toTemp(long cid) {
        try {
            db.execSQL("ALTER TABLE '" + getFeedItemTableName(cid) + "' RENAME TO '" + getFeedItemTempTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    alterItemTable_toMain(long cid) {
        try {
            db.execSQL("ALTER TABLE '" + getFeedItemTempTableName(cid) + "' RENAME TO '" + getFeedItemTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    dropItemTable(long cid) {
        try {
            db.execSQL("DROP TABLE '" + getFeedItemTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }

    private long
    dropTempItemTable(long cid) {
        try {
            db.execSQL("DROP TABLE '" + getFeedItemTempTableName(cid) + "';");
        } catch (SQLException e) {
            logI(e.getMessage());
            return -1;
        }
        return 0;
    }
    /**************************************
     * Overriding.
     **************************************/

    @Override
    public void
    onCreate(SQLiteDatabase db) {
        db.execSQL(buildTableSQL(TABLE_FEEDCHANNEL, ColumnFeedChannel.values()));
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
    insertItem(long cid, Feed.Item item) {
        ContentValues values = new ContentValues();

        // information defined by spec.
        values.put(ColumnFeedItem.CHANNELID.getName(),           cid);
        values.put(ColumnFeedItem.TITLE.getName(),               item.title);
        values.put(ColumnFeedItem.LINK.getName(),                item.link);
        values.put(ColumnFeedItem.DESCRIPTION.getName(),         item.description);
        values.put(ColumnFeedItem.PUBDATE.getName(),             item.pubDate);
        values.put(ColumnFeedItem.STATE.getName(),               item.state.name());
        values.put(ColumnFeedItem.ENCLOSURE_URL.getName(),       item.enclosureUrl);
        values.put(ColumnFeedItem.ENCLOSURE_LENGTH.getName(),    item.enclosureLength);
        values.put(ColumnFeedItem.ENCLOSURE_TYPE.getName(),      item.enclosureType);

        return db.insert(getFeedItemTableName(cid), null, values);
    }

    long
    updateItem_state(long cid, long id, Feed.Item.State state) {
        ContentValues values = new ContentValues();
        values.put(ColumnFeedItem.STATE.getName(), state.name());
        return db.update(getFeedItemTableName(cid),
                         values,
                         ColumnFeedItem.ID.getName() + " = " + id,
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
        values.put(ColumnFeedChannel.URL.getName(),              ch.url);
        values.put(ColumnFeedChannel.ACTIONTYPE.getName(),       ch.actionType.name());
        values.put(ColumnFeedChannel.LASTUPDATE.getName(),       ch.lastupdate);

        if (null != ch.imageblob)
            values.put(ColumnFeedChannel.IMAGEBLOB.getName(),    ch.imageblob);

        // information defined by spec.
        values.put(ColumnFeedChannel.TITLE.getName(),            ch.title);
        values.put(ColumnFeedChannel.DESCRIPTION.getName(),      ch.description);

        long cid = db.insert(TABLE_FEEDCHANNEL, null, values);
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
        long r = db.delete(TABLE_FEEDCHANNEL,
                           ColumnFeedChannel.ID.getName() + " = " + cid,
                           null);
        if (0 != r) {
            dropItemTable(cid);
            dropTempItemTable(cid);
        }

        return r;
    }

    long
    prepareUpdateItemTable(long cid) {
        eAssert(!doesTableExists(getFeedItemTempTableName(cid)));
        // to make sure.
        dropTempItemTable(cid);
        if (0 > alterItemTable_toTemp(cid))
            return -1;

        if (0 > createItemTable(cid)) {
            alterItemTable_toMain(cid);
            return -1;
        }

        return 0;
    }

    long
    completeUpdateItemTable(long cid) {
        dropTempItemTable(cid);
        return 0;
    }

    long
    rollbackUpdateItemTable(long cid) {
        dropItemTable(cid);
        alterItemTable_toMain(cid);
        return 0;
    }
}
