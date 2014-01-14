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
