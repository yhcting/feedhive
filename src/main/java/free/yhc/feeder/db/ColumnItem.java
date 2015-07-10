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
