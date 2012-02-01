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
public class DB extends SQLiteOpenHelper {
    private static DB instance = null;

    /**************************************
     * Members
     **************************************/
    protected SQLiteDatabase db = null;


    /**************************************
     *
     * Database design
     * ---------------
     *  - table for rss items per each channel.
     *  - naming for rss-item-table
     *      <prefix string> + channel-id
     *
     **************************************/
    protected static final String NAME    = "feader.db";
    protected static final int    VERSION = 1;

    public interface Column {
        String getName();
        String getType();
        String getConstraint();
    }

    /**************************************
     * DB for RSS
     **************************************/
    public static final String TABLE_RSSCHANNEL         = "rsschannel";
    protected static final String TABLE_RSSITEM         = "rssitem";
    protected static final String TABLE_CHANNEL_USER    = "rsschanneluser";
    protected static final String TABLE_ITEM_USER       = "rssitemuser";

    public static enum ColumnRssChannel implements Column {
        // Required Channel Elements
        TITLE           ("title",           "text",     "not null"),
        LINK            ("link",            "text",     "not null"),
        DESCRIPTION     ("description",     "text",     "not null"),
        PUBDATE         ("pubDate",         "text",     ""),
        LASTBUILDDATE   ("lastBuildDate",   "text",     ""),

        // User Required Channel Elements
        USERTXT0        ("usertxt0",        "text",     ""),
        USERTXT1        ("usertxt1",        "text",     ""),
        USERTXT2        ("usertxt2",        "text",     ""),
        USERTXT3        ("usertxt3",        "text",     ""),
        USERTXT4        ("usertxt4",        "text",     ""),

        USERTAG0        ("usertag0",        "text",     ""),
        USERTAG1        ("usertag1",        "text",     ""),
        USERTAG2        ("usertag2",        "text",     ""),
        USERTAG3        ("usertag3",        "text",     ""),
        USERTAG4        ("usertag4",        "text",     ""),
        USERTAG5        ("usertag5",        "text",     ""),
        USERTAG6        ("usertag6",        "text",     ""),
        USERTAG7        ("usertag7",        "text",     ""),
        USERTAG8        ("usertag8",        "text",     ""),
        USERTAG9        ("usertag9",        "text",     ""),

        // Columns for internal use.
        IMAGEBLOB       ("imageblob",       "blob",     ""), // image from channel tag.
        LASTUPDATE      ("lastUpdate",      "text",     ""),
        LASTFULLUPDATE  ("lastFullUpdate",  "text",     ""),
        CHANNELVIEW     ("channelView",     "text",     ""),
        ITEMVIEW        ("itemView",        "text",     ""),
        TAGTREE         ("tagtree",         "text",     ""),
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
        LINK            ("link",            "text",     "not null"),
        DESCRIPTION     ("description",     "text",     "not null"),
        ENCLOSURE_URL   ("enclosure_url",   "text",     ""),
        ENCLOSURE_LENGTH("enclosure_length","text",     ""),
        ENCLOSURE_TYPE  ("enclosure_type",  "text",     ""),
        PUBDATE         ("pubdate",         "text",     ""),
        GUID            ("guid",            "text",     ""),
        GUID_ISPERMALINK("guid_isPermaLink","text",     ""),

        // User Required Channel Elements
        USERTXT0        ("usertxt0",        "text",     ""),
        USERTXT1        ("usertxt1",        "text",     ""),
        USERTXT2        ("usertxt2",        "text",     ""),
        USERTXT3        ("usertxt3",        "text",     ""),
        USERTXT4        ("usertxt4",        "text",     ""),

        USERTAG0        ("usertag0",        "text",     ""),
        USERTAG1        ("usertag1",        "text",     ""),
        USERTAG2        ("usertag2",        "text",     ""),
        USERTAG3        ("usertag3",        "text",     ""),
        USERTAG4        ("usertag4",        "text",     ""),
        USERTAG5        ("usertag5",        "text",     ""),
        USERTAG6        ("usertag6",        "text",     ""),
        USERTAG7        ("usertag7",        "text",     ""),
        USERTAG8        ("usertag8",        "text",     ""),
        USERTAG9        ("usertag9",        "text",     ""),

        // Columns for internal use.
        CHANNELID       ("channelid",       "integer",  "not null"),
        DOWNLOADFILE    ("downloadfile",    "text",     ""), // downloaded file path
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

    public static enum ColumnRssUserMap implements Column {
        // Required Channel Elements
        NAME           ("name",             "text",     "not null"), // name of value (tag name or etc.)
        TYPE           ("type",             "text",     "not null"), // channel table? / item table?
        COLUMN         ("column",           "text",     "not null"), // column name for value of this tag.
                                                                     // (ex. usertext0, usertag1 ...)

        // Columns for internal use.
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");

        private String name;
        private String type;
        private String constraint;

        ColumnRssUserMap(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getConstraint() { return constraint; }
    }

    public static enum ColumnRssChannelUser implements Column {
        // Required Channel Elements
        USERTXT0           ("usertxt0",           "text",     ""),
        USERTXT1           ("usertxt1",           "text",     ""),
        USERTXT2           ("usertxt2",           "text",     ""),
        USERTXT3           ("usertxt3",           "text",     ""),
        USERTXT4           ("usertxt4",           "text",     ""),

        USERTAG0           ("usertag0",           "text",     ""),
        USERTAG1           ("usertag1",           "text",     ""),
        USERTAG2           ("usertag2",           "text",     ""),
        USERTAG3           ("usertag3",           "text",     ""),
        USERTAG4           ("usertag4",           "text",     ""),
        USERTAG5           ("usertag5",           "text",     ""),
        USERTAG6           ("usertag6",           "text",     ""),
        USERTAG7           ("usertag7",           "text",     ""),
        USERTAG8           ("usertag8",           "text",     ""),
        USERTAG9           ("usertag9",           "text",     ""),

        // Columns for internal use.
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");

        private String name;
        private String type;
        private String constraint;

        ColumnRssChannelUser(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getConstraint() { return constraint; }
    }

    public static enum ColumnRssItemUser implements Column {
        // Required Channel Elements
        USERTXT0           ("usertxt0",           "text",     ""),
        USERTXT1           ("usertxt1",           "text",     ""),
        USERTXT2           ("usertxt2",           "text",     ""),
        USERTXT3           ("usertxt3",           "text",     ""),
        USERTXT4           ("usertxt4",           "text",     ""),

        USERTAG0           ("usertag0",           "text",     ""),
        USERTAG1           ("usertag1",           "text",     ""),
        USERTAG2           ("usertag2",           "text",     ""),
        USERTAG3           ("usertag3",           "text",     ""),
        USERTAG4           ("usertag4",           "text",     ""),
        USERTAG5           ("usertag5",           "text",     ""),
        USERTAG6           ("usertag6",           "text",     ""),
        USERTAG7           ("usertag7",           "text",     ""),
        USERTAG8           ("usertag8",           "text",     ""),
        USERTAG9           ("usertag9",           "text",     ""),

        // Columns for internal use.
        ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");

        private String name;
        private String type;
        private String constraint;

        ColumnRssItemUser(String name, String type, String constraint) {
            this.name = name;
            this.type = type;
            this.constraint = constraint;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getConstraint() { return constraint; }
    }


    public static String
    getRssItemTableName(long channelid) {
        return TABLE_RSSITEM + channelid;
    }

    /**************************************
     * DB operation
     **************************************/
    protected static String
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

    protected static String[]
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

    public Cursor
    query(String table, Column[] columns) {
        return db.query(table,
                        getColumnNames(columns),
                        null, null, null, null, null);
    }

    public Cursor
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

    protected ContentValues
    buildChannelValues(RSS.Channel ch) {
        ContentValues values = new ContentValues();
        // application's internal information
        values.put(ColumnRssChannel.URL.getName(),              ch.url);

        // information defined by spec.
        values.put(ColumnRssChannel.TITLE.getName(),            ch.title);
        values.put(ColumnRssChannel.LINK.getName(),             ch.link);
        values.put(ColumnRssChannel.DESCRIPTION.getName(),      ch.description);
        values.put(ColumnRssChannel.PUBDATE.getName(),          ch.pubDate);
        values.put(ColumnRssChannel.LASTBUILDDATE.getName(),    ch.lastBuildDate);
        return values;
    }

    public long
    insertChannel(RSS.Channel ch) {
        long r = db.insert(TABLE_RSSCHANNEL, null, buildChannelValues(ch));
        if (r >= 0)
            // r is channel id.
            db.execSQL(buildTableSQL(getRssItemTableName(r), ColumnRssChannel.values()));

        return r;
    }

    public long
    updateChannel(RSS.Channel ch) {
        return db.update(TABLE_RSSCHANNEL,
                         buildChannelValues(ch),
                         ColumnRssChannel.ID.getName() + " = '" + ch.id + "'",
                         null);
    }

    public long
    deleteChannel(long id) {
        long r = db.delete(TABLE_RSSCHANNEL,
                            ColumnRssChannel.ID.getName() + " = " + id,
                            null);
        if (0 != r)
            db.execSQL("drop table " + getRssItemTableName(id));

        return r;
    }

    public long
    cleanChannelItems(long cid) {
        eAssert(cid >= 0);
        db.execSQL("drop table " + getRssItemTableName(cid));
        db.execSQL(buildTableSQL(getRssItemTableName(cid), ColumnRssItem.values()));
        return 0;
    }

    public long
    insertItem(long cid, RSS.Item item) {
        ContentValues values = new ContentValues();

        // information defined by spec.
        values.put(ColumnRssItem.CHANNELID.getName(),           item.channelid);
        values.put(ColumnRssItem.TITLE.getName(),               item.title);
        values.put(ColumnRssItem.LINK.getName(),                item.link);
        values.put(ColumnRssItem.DESCRIPTION.getName(),         item.description);
        values.put(ColumnRssItem.PUBDATE.getName(),             item.pubDate);

        if (null != item.enclosure) {
            values.put(ColumnRssItem.ENCLOSURE_URL.getName(),       item.enclosure.url);
            values.put(ColumnRssItem.ENCLOSURE_LENGTH.getName(),    item.enclosure.length);
            values.put(ColumnRssItem.ENCLOSURE_TYPE.getName(),      item.enclosure.type);
        }

        if (null != item.guid) {
            values.put(ColumnRssItem.GUID.getName(),                item.guid.value);
            values.put(ColumnRssItem.GUID_ISPERMALINK.getName(),    item.guid.isPermaLink);
        }

        return db.insert(getRssItemTableName(cid), null, values);
    }

    protected RSS.Channel
    assignRssChannelInfo(RSS.Channel ch, Cursor c) {
        // application's internal information
        ch.id           = c.getLong(c.getColumnIndex(ColumnRssChannel.ID.getName()));
        ch.url          = c.getString(c.getColumnIndex(ColumnRssChannel.URL.getName()));

        // information defined by spec.
        ch.title        = c.getString(c.getColumnIndex(ColumnRssChannel.TITLE.getName()));
        ch.link         = c.getString(c.getColumnIndex(ColumnRssChannel.LINK.getName()));
        ch.description  = c.getString(c.getColumnIndex(ColumnRssChannel.DESCRIPTION.getName()));
        ch.pubDate      = c.getString(c.getColumnIndex(ColumnRssChannel.PUBDATE.getName()));
        ch.lastBuildDate= c.getString(c.getColumnIndex(ColumnRssChannel.LASTBUILDDATE.getName()));
        return ch;
    }

    public RSS.Channel
    getRssChannelFromId(long id) {
        Cursor c = db.query(TABLE_RSSCHANNEL,
                            getColumnNames(ColumnRssChannel.values()),
                            ColumnRssChannel.ID.getName() + " = '" + id + "'",
                            null,null,null,null);
        RSS.Channel ch = null;
        if (c.getCount() > 0) {
            // There shouldn't have duplicated channel
            eAssert(1 == c.getCount());
            ch = new RSS.Channel();
            c.moveToFirst();
            assignRssChannelInfo(ch, c);
        }
        c.close();

        return ch;
    }

    public RSS.Channel[]
    getRssChannels() {
        Cursor c = db.query(TABLE_RSSCHANNEL,
                            getColumnNames(ColumnRssChannel.values()),
                            null, null,null,null,null);
        RSS.Channel[] chs = new RSS.Channel[c.getCount()];
        int i = 0;
        c.moveToFirst();
        while (!c.isAfterLast()) {
            assignRssChannelInfo(chs[i], c);
            i++;
            c.moveToNext();
        }
        c.close();
        return chs;
    }
}
