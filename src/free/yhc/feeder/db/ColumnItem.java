package free.yhc.feeder.db;

import android.provider.BaseColumns;

public enum ColumnItem implements DB.Column {
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
            + "FOREIGN KEY(channelid) REFERENCES " + DB.TABLE_CHANNEL + "(" + ColumnChannel.ID.getName() + ")");

    private final String _mName;
    private final String _mType;
    private final String _mConstraint;

    ColumnItem(String name, String type, String constraint) {
        _mName = name;
        _mType = type;
        _mConstraint = constraint;
    }
    @Override
    public String getName() { return _mName; }
    @Override
    public String getType() { return _mType; }
    @Override
    public String getConstraint() { return _mConstraint; }
}
