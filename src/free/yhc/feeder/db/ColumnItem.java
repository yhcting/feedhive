/******************************************************************************
 *    Copyright (C) 2012, 2013, 2014 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of FeedHive
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
 *    along with this program.	If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

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
