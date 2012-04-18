/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
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
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder.model;

import free.yhc.feeder.R;

// Err codes.
// TODO : set correct string resource id
public enum Err {
    NoErr                       (R.string.ok),
    Interrupted                 (R.string.err_interrupted),
    UserCancelled               (R.string.err_user_cancelled),
    InvalidURL                  (R.string.err_url),
    IONet                       (R.string.err_ionet),
    IOFile                      (R.string.err_iofile),
    MediaGet                    (R.string.err_media_get),
    CodecDecode                 (R.string.err_codec_decode),
    ParserUnsupportedFormat     (R.string.err_parse_unsupported_format),
    ParserUnsupportedVersion    (R.string.err_parse_unsupported_version),
    DBDuplicatedChannel         (R.string.err_duplicated_channel),
    DBUnknown                   (R.string.err_dbunknown),
    Unknown                     (R.string.err_unknwon);

    int msgId; // matching message id

    Err(int msgId) {
        this.msgId = msgId;
    }

    public int getMsgId() {
        return msgId;
    }
}
