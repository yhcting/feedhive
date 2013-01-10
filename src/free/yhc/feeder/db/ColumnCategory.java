package free.yhc.feeder.db;

import android.provider.BaseColumns;

public enum ColumnCategory implements DB.Column {
    NAME            ("name",            "text",     "not null"), // channel url of this rss.
    ID              (BaseColumns._ID,   "integer",  "primary key autoincrement");

    private final String _mName;
    private final String _mType;
    private final String _mConstraint;

    ColumnCategory(String name, String type, String constraint) {
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
