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
    NO_ERR                      (R.string.ok),
    INTERRUPTED                 (R.string.err_interrupted),
    USER_CANCELLED              (R.string.err_user_cancelled),
    INVALID_URL                 (R.string.err_url),
    IO_NET                      (R.string.err_ionet),
    IO_FILE                     (R.string.err_iofile),
    GET_MEDIA                   (R.string.err_get_media),
    CODEC_DECODE                (R.string.err_codec_decode),
    PARSER_UNSUPPORTED_FORMAT   (R.string.err_parse_unsupported_format),
    PARSER_UNSUPPORTED_VERSION  (R.string.err_parse_unsupported_version),
    DB_DUPLICATED_CHANNEL       (R.string.err_duplicated_channel),
    DB_UNKNOWN                  (R.string.err_dbunknown),
    UNKNOWN                     (R.string.err_unknwon);

    private int msgId; // matching message id

    Err(int msgId) {
        this.msgId = msgId;
    }

    /**
     * Get string resource id.
     * This string has message to notify err to user.
     * @return
     */
    public int getMsgId() {
        return msgId;
    }
}
