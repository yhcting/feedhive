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
    public static final long FInvalid = ~0;

    public static class Item {
        // bit[0] : new / opened
        public static final long FStatNew      = 0x00;
        public static final long FStatOpened   = 0x01;
        public static final long MStat         = 0x01;

        public static final long FStatDefault  = FStatNew;

        public static final boolean
        isStateNew(long flag) {
            return bitIsSet(flag, FStatNew, MStat);
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
        public static final String defaultSchedUpdateTime = "" + (3 * 3600); // 3 o'clock

        // ==================
        // Flag State
        // ==================
        // bit[0] : State 'used / unused'
        //   unused : Feeder doens't care about this channel.
        //            When user decide to delete it.
        //   used   : This channel is cared.
        //            When user newly inserts this.
        //            Or, it is inserted again after removing.
        public static final long FStatUsed    = 0x00;
        public static final long FStatUnused  = 0x01;
        public static final long MStat        = 0x01;
        public static final long FStatDefault = FStatUsed;


        // ==================
        // Flag Action
        // ==================
        // bit[0] : Action target is 'link / enclosure' - default : link
        public static final long FActTgtLink      = 0x00;
        public static final long FActTgtEnclosure = 0x01;
        public static final long MActTgt          = 0x01;
        public static final long FActTgtDefault   = 0x00;

        // bit[1] : Action type is 'open / download' - default - open
        public static final long FActOpOpen       = 0x00;
        public static final long FActOpDn         = 0x02;
        public static final long MActOp           = 0x02;
        public static final long FActOpDefault    = 0x00;

        // bit[2] : Action program is 'internal program / external - default - internal
        // Ex. in case of view web link, internal program means 'ItemViewActivity' and
        //   external program means 'other browsers'.
        // Internal program is very simple version so, it's fast.
        // External program is usually very powerful.
        public static final long FActProgIn       = 0x00;
        public static final long FActProgEx       = 0x04;
        public static final long MActProg         = 0x04;
        public static final long FActProgDefault  = 0x00;

        public static final long FActDefault      = FActTgtDefault | FActOpDefault | FActProgDefault;

        // ==================
        // Flag UpdateMode
        // ==================
        // bit[0] : update type 'normal / download'
        public static final long FUpdLink       = 0x00; // update only feed link
        public static final long FUpdDn         = 0x01; // download link during update.
        public static final long MUpd           = 0x01;

        public static final long FUpdDefault    = FUpdLink;

        // ==================
        // Feed Type
        // ==================
        public static final long ChannTypeNormal = 0; // for news/article etc
        public static final long ChannTypeMedia  = 1; // for link and description for media data (etc. podcast)

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
            long     type         = ChannTypeNormal;
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
        public static final boolean
        isStatUsed(long flag) {
            return bitIsSet(flag, FStatUsed, MStat);
        }

        public static final boolean
        isActOpOpen(long flag) {
            return bitIsSet(flag, FActOpOpen, MActOp);
        }

        public static final boolean
        isActTgtLink(long flag) {
            return bitIsSet(flag, FActTgtLink, MActTgt);
        }

        public static final boolean
        isActTgtEnclosure(long flag) {
            return bitIsSet(flag, FActTgtEnclosure, MActTgt);
        }

        public static final boolean
        isActProgIn(long flag) {
            return bitIsSet(flag, FActProgIn, MActProg);
        }

        public static final boolean
        isActProgEx(long flag) {
            return bitIsSet(flag, FActProgEx, MActProg);
        }

        public static final boolean
        isUpdLink(long flag) {
            return bitIsSet(flag, FUpdLink, MUpd);
        }

        public static final boolean
        isUpdDn(long flag) {
            return bitIsSet(flag, FUpdDn, MUpd);
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
