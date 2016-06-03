/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

package free.yhc.feeder.feed;

import free.yhc.baselib.Logger;

import static free.yhc.baselib.util.Util.bitCompare;

// Naming notation
//   F[flag name][value name] : 'F' => Flag
//   M[flag_name][value name] : 'M' => Mask
public class Feed {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(Feed.class, Logger.LOGLV_DEFAULT);

    public static final long FINVALID = ~0;

    public static class Item {
        // ==================
        // Flag State
        // ==================
        // bit[0] : new / opened
        public static final long FSTAT_OPEN_NEW     = 0x00;
        public static final long FSTAT_OPEN_OPENED  = 0x01;
        public static final long MSTAT_OPEN         = 0x01;
        public static final long FSTAT_OPEN_DEFAULT = FSTAT_OPEN_NEW;

        // bit[1] : Fav off / on
        public static final long FSTAT_FAV_OFF     = 0x00;
        public static final long FSTAT_FAV_ON      = 0x02;
        public static final long MSTAT_FAV         = 0x02;
        public static final long FSTAT_FAV_DEFAULT = FSTAT_FAV_OFF;

        @SuppressWarnings("PointlessBitwiseExpression")
        public static final long FSTAT_DEFAULT = FSTAT_OPEN_DEFAULT | FSTAT_FAV_DEFAULT;

        public static boolean
        isStateOpenNew(long flag) {
            return bitCompare(FSTAT_OPEN_NEW, flag, MSTAT_OPEN);
        }

        public static boolean
        isStatFavOn(long flag) {
            return bitCompare(FSTAT_FAV_ON, flag, MSTAT_FAV);
        }

         // Information from parsing.
        public static class ParD {
            public String title = "";
            public String link = "";
            public String description = "";
            public String pubDate = "";
            public String enclosureUrl = "";
            public String enclosureLength = "";
            public String enclosureType = "";
        }

        // DB related data
        public static class DbD {
            public long id  = -1;
            public long cid = -1; // channel id
        }
    }

    public static class Channel {
        public static final String DEFAULT_SCHEDUPDATE_TIME = "" + (3 * 3600); // 3 o'clock

        // ==================
        // Flag State - reserved
        // ==================
        public static final long FSTAT_DEFAULT = 0;


        // ==================
        // Flag Action
        // ==================
        // bit[0:1] : Action target is 'link / enclosure' - default : link
        // action for enclosure => download
        // action for link      => open with ex/in browser
        public static final long FACT_TYPE_DYNAMIC        = 0x00;
        public static final long FACT_TYPE_EMBEDDED_MEDIA = 0x01;
        public static final long MACT_TYPE                = 0x03;
        public static final long FACT_TYPE_DEFAULT        = FACT_TYPE_DYNAMIC;

        // bit[2] : Action program is 'internal program / external - default - internal
        // Ex. in case of view web link, internal program means 'ItemViewActivity' and
        //   external program means 'other browsers'.
        // Internal program is very simple version so, it's fast.
        // External program is usually very powerful.
        // This value can be changed by user setting.
        public static final long FACT_PROG_IN = 0x00;
        public static final long FACT_PROG_EX = 0x04;
        public static final long MACT_PROG    = 0x04;
        public static final long FACT_PROG_DEFAULT = FACT_PROG_IN;

        @SuppressWarnings("PointlessBitwiseExpression")
        public static final long FACT_DEFAULT = FACT_TYPE_DEFAULT | FACT_PROG_DEFAULT;

        // ==================
        // Flag UpdateMode
        // ==================
        // bit[0] : update type 'normal / download'
        public static final long FUPD_LINK = 0x00; // update only feed link
        public static final long FUPD_DN   = 0x01; // download link during update.
        public static final long MUPD      = 0x01;

        public static final long FUPD_DEFAULT = FUPD_LINK;

        // ==================
        // Feed Type
        // ==================
        public enum Type {
            NORMAL,
            EMBEDDED_MEDIA
        }

        // 100 x 100 is enough size for channel icon.
        public static final int ICON_MAX_WIDTH  = 100;
        public static final int ICON_MAX_HEIGHT = 100;

        // Profile data.
        public static class ProfD {
            public String url   = ""; // channel url.
        }

        // Information from parsing.
        public static class ParD {
            // Type is usually determined by which namespace is used at XML.
            // For example.
            //   xmlns:itunes -> Media
            public Type type = Type.NORMAL;
            public String title = "";
            public String description = "";
            public String imageref = "";
        }

        // DB related data
        public static class DbD {
            public long id = -1;
            public long categoryid = -1;
            public long lastupdate = 0; // date when item DB is updated lastly
        }

        // ==================
        // Flag Functions
        // ==================
        public static long
        getActType(long flag) {
            return flag & MACT_TYPE;
        }

        public static boolean
        isActProgIn(long flag) {
            return bitCompare(FACT_PROG_IN, flag, MACT_PROG);
        }

        public static boolean
        isActProgEx(long flag) {
            return bitCompare(FACT_PROG_EX, flag, MACT_PROG);
        }

        public static boolean
        isUpdLink(long flag) {
            return bitCompare(FUPD_LINK, flag, MUPD);
        }

        public static boolean
        isUpdDn(long flag) {
            return bitCompare(FUPD_DN, flag, MUPD);
        }
    }

    public static class Category {
        public long id = -1;
        public String name = ""; // category name
        public Category() {}
        public Category(String aName) {
            name = aName;
        }
    }
}
