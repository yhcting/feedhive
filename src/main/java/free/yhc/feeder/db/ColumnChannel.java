/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
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
