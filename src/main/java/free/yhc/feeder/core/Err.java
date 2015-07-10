/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

package free.yhc.feeder.core;

import free.yhc.feeder.R;

// Err codes.
// TODO : set correct string resource id
public enum Err {
    NO_ERR                      (R.string.ok),
    INTERRUPTED                 (R.string.err_interrupted),
    VERSION_MISMATCH            (R.string.err_version_mismatch),
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
    DB_CRASH                    (R.string.err_dbcrash),
    UNKNOWN                     (R.string.err_unknwon);

    private int msgId; // matching message id

    Err(int msgId) {
        this.msgId = msgId;
    }

    /**
     * Get string resource id.
     * This string has message to notify err to user.
     */
    public int getMsgId() {
        return msgId;
    }
}
