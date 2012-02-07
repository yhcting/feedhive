package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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
    static final String TABLE_RSSCHANNEL        = "rsschannel";
    private static final String TABLE_RSSITEM   = "rssitem";

    public static enum ColumnRssChannel implements Column {
        // Required Channel Elements
        TITLE           ("title",           "text",     "not null"),
        DESCRIPTION     ("description",     "text",     ""),

        // Columns for internal use.
        IMAGEBLOB       ("imageblob",       "blob",     ""), // image from channel tag.
        LASTUPDATE      ("lastupdate",      "text",     "not null"), // time when channel is updated, lastly
        ACTIONTYPE      ("actiontype",      "text",     "not null"),
        URL             ("url",             "text",     "not null"), // channel url of this rss.
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");


        private String name;
        private String type;
        private String constraint;

        ColumnRssChannel(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getConstraint() { return constraint; }
    }

    public static enum ColumnRssItem implements Column {
        TITLE           ("title",           "text",     "not null"),
        DESCRIPTION     ("description",     "text",     ""),
        LINK            ("link",            "text",     ""),
        ENCLOSURE_URL   ("enclosureurl",    "text",     ""),
        ENCLOSURE_LENGTH("enclosurelength", "text",     ""),
        ENCLOSURE_TYPE  ("enclosuretype",   "text",     ""),
        PUBDATE         ("pubdate",         "text",     ""),

        // Columns for internal use.
        STATE           ("state",           "text",     "not null"), // new, read etc
        CHANNELID       ("channelid",       "integer",  "not null"),
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");

        private String name;
        private String type;
        private String constraint;

        ColumnRssItem(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getConstraint() { return constraint; }
    }

    static String
    getRssItemTableName(long channelid) {
        return TABLE_RSSITEM + channelid;
    }

    /**************************************
     * DB operation
     **************************************/
    private static String
    buildTableSQL(String table, Column[] cols) {
        String sql = "create table " + table + " (";
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

    /**************************************
     * Overriding.
     **************************************/

    @Override
    public void
    onCreate(SQLiteDatabase db) {
        db.execSQL(buildTableSQL(TABLE_RSSCHANNEL, ColumnRssChannel.values()));
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
    insertItem(long cid, RSS.Item item) {
        ContentValues values = new ContentValues();

        // information defined by spec.
        values.put(ColumnRssItem.CHANNELID.getName(),           cid);
        values.put(ColumnRssItem.TITLE.getName(),               item.title);
        values.put(ColumnRssItem.LINK.getName(),                item.link);
        values.put(ColumnRssItem.DESCRIPTION.getName(),         item.description);
        values.put(ColumnRssItem.PUBDATE.getName(),             item.pubDate);
        values.put(ColumnRssItem.STATE.getName(),               item.state.name());

        if (null != item.enclosure) {
            values.put(ColumnRssItem.ENCLOSURE_URL.getName(),       item.enclosure.url);
            values.put(ColumnRssItem.ENCLOSURE_LENGTH.getName(),    item.enclosure.length);
            values.put(ColumnRssItem.ENCLOSURE_TYPE.getName(),      item.enclosure.type);
        }

        return db.insert(getRssItemTableName(cid), null, values);
    }

    long
    updateItem_state(long cid, long id, RSS.ItemState state) {
        ContentValues values = new ContentValues();
        values.put(ColumnRssItem.STATE.getName(), state.name());
        return db.update(getRssItemTableName(cid),
                         values,
                         ColumnRssItem.ID.getName() + " = " + id,
                         null);
    }


    /**
     * Insert channel and it's items.
     * This is used by DBPolicy.(Not public!)
     * @param ch
     * @return channel row id if success / -1 if fails.
     */
    long
    insertChannel(RSS.Channel ch) {
        // New insertion. So, full update!!

        ContentValues values = new ContentValues();
        // application's internal information
        values.put(ColumnRssChannel.URL.getName(),              ch.url);
        values.put(ColumnRssChannel.ACTIONTYPE.getName(),       ch.actionType.name());
        values.put(ColumnRssChannel.LASTUPDATE.getName(),       ch.lastupdate);

        if (null != ch.imageblob)
            values.put(ColumnRssChannel.IMAGEBLOB.getName(),    ch.imageblob);

        // information defined by spec.
        values.put(ColumnRssChannel.TITLE.getName(),            ch.title);
        values.put(ColumnRssChannel.DESCRIPTION.getName(),      ch.description);

        long cid = db.insert(TABLE_RSSCHANNEL, null, values);
        if (cid >= 0)
            // r is channel id.
            db.execSQL(buildTableSQL(getRssItemTableName(cid), ColumnRssItem.values()));
        else
            return -1; // fail to insert channel information.

        return cid;
    }

    long
    deleteChannel(long cid) {
        long r = db.delete(TABLE_RSSCHANNEL,
                           ColumnRssChannel.ID.getName() + " = " + cid,
                           null);
        if (0 != r)
            db.execSQL("drop table " + getRssItemTableName(cid));

        return r;
    }

    long
    cleanChannelItems(long cid) {
        eAssert(cid >= 0);
        db.execSQL("drop table " + getRssItemTableName(cid));
        db.execSQL(buildTableSQL(getRssItemTableName(cid), ColumnRssItem.values()));
        return 0;
    }
}
