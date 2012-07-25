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

import static free.yhc.feeder.model.Utils.bitIsSet;

// Naming notation
//   F[flag name][value name] : 'F' => Flag
//   M[flag_name][value name] : 'M' => Mask
public class Feed {
    public static final long FINVALID = ~0;

    public static class Item {
        // ==================
        // Flag State
        // ==================
        // bit[0] : new / opened
        public static final long FSTAT_OPEN_NEW      = 0x00;
        public static final long FSTAT_OPEN_OPENED   = 0x01;
        public static final long MSTAT_OPEN          = 0x01;
        public static final long FSTAT_OPEN_DEFAULT  = FSTAT_OPEN_NEW;

        // bit[1] : Fav off / on
        public static final long FSTAT_FAV_OFF       = 0x00;
        public static final long FSTAT_FAV_ON        = 0x02;
        public static final long MSTAT_FAV           = 0x02;
        public static final long FSTAT_FAV_DEFAULT   = FSTAT_FAV_OFF;

        public static final long FSTAT_DEFAULT = FSTAT_OPEN_DEFAULT | FSTAT_FAV_DEFAULT;

        public static final boolean
        isStateOpenNew(long flag) {
            return bitIsSet(flag, FSTAT_OPEN_NEW, MSTAT_OPEN);
        }

        public static final boolean
        isStatFavOn(long flag) {
            return bitIsSet(flag, FSTAT_FAV_ON, MSTAT_FAV);
        }

         // Information from parsing.
        static class ParD {
            String title        = "";
            String link         = "";
            String description  = "";
            String pubDate      = "";
            String enclosureUrl = "";
            String enclosureLength = "";
            String enclosureType = "";
        }

        // DB related data
        static class DbD {
            long   id  = -1;
            long   cid = -1; // channel id
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
        public static final long FACT_TYPE_DYNAMIC          = 0x00;
        public static final long FACT_TYPE_EMBEDDED_MEDIA   = 0x01;
        public static final long MACT_TYPE                  = 0x03;
        public static final long FACT_TYPE_DEFAULT          = FACT_TYPE_DYNAMIC;

        // bit[2] : Action program is 'internal program / external - default - internal
        // Ex. in case of view web link, internal program means 'ItemViewActivity' and
        //   external program means 'other browsers'.
        // Internal program is very simple version so, it's fast.
        // External program is usually very powerful.
        // This value can be changed by user setting.
        public static final long FACT_PROG_IN       = 0x00;
        public static final long FACT_PROG_EX       = 0x04;
        public static final long MACT_PROG          = 0x04;
        public static final long FACT_PROG_DEFAULT  = FACT_PROG_IN;

        public static final long FACT_DEFAULT      = FACT_TYPE_DEFAULT | FACT_PROG_DEFAULT;

        // ==================
        // Flag UpdateMode
        // ==================
        // bit[0] : update type 'normal / download'
        public static final long FUPD_LINK       = 0x00; // update only feed link
        public static final long FUPD_DN         = 0x01; // download link during update.
        public static final long MUPD            = 0x01;

        public static final long FUPD_DEFAULT    = FUPD_LINK;

        // ==================
        // Feed Type
        // ==================
        enum Type {
            NORMAL,
            EMBEDDED_MEDIA;
        }

        // 100 x 100 is enough size for channel icon.
        public static final int ICON_MAX_WIDTH  = 100;
        public static final int ICON_MAX_HEIGHT = 100;

        // Profile data.
        static class ProfD {
            String url          = ""; // channel url.
        }

        // Information from parsing.
        static class ParD {
            // Type is usually determined by which namespace is used at XML.
            // For example.
            //   xmlns:itunes -> Media
            Type     type         = Type.NORMAL;
            String   title        = "";
            String   description  = "";
            String   imageref     = "";
        }

        // DB related data
        static class DbD {
            long   id           = -1;
            long   categoryid   = -1;
            long   lastupdate   = 0; // date when item DB is updated lastly
        }

        // ==================
        // Flag Functions
        // ==================
        public static final long
        getActType(long flag) {
            return flag & MACT_TYPE;
        }

        public static final boolean
        isActProgIn(long flag) {
            return bitIsSet(flag, FACT_PROG_IN, MACT_PROG);
        }

        public static final boolean
        isActProgEx(long flag) {
            return bitIsSet(flag, FACT_PROG_EX, MACT_PROG);
        }

        public static final boolean
        isUpdLink(long flag) {
            return bitIsSet(flag, FUPD_LINK, MUPD);
        }

        public static final boolean
        isUpdDn(long flag) {
            return bitIsSet(flag, FUPD_DN, MUPD);
        }
    }

    public static class Category {
        public long     id      = -1;
        public String   name    = ""; // category name
        public Category() {}
        public Category(String name) {
            this.name = name;
        }
    }
}
