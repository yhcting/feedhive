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

public enum ColumnChannel implements DB.Column {
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
            + "FOREIGN KEY(categoryid) REFERENCES " + DB.TABLE_CATEGORY + "(" + ColumnCategory.ID.getName() + ")");


    private final String _mName;
    private final String _mType;
    private final String _mConstraint;

    ColumnChannel(String name, String type, String constraint) {
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
