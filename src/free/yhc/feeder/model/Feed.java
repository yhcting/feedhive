package free.yhc.feeder.model;


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
            return FStatNew == (flag & MStat);
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

        public static final long FActDefault      = FActTgtDefault | FActOpDefault;


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

        // 150 x 150 is enough size for channel icon.
        public static final int ICON_MAX_WIDTH  = 150;
        public static final int ICON_MAX_HEIGHT = 150;

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
            return FStatUsed == (flag & MStat);
        }

        public static final boolean
        isActOpOpen(long flag) {
            return FActOpOpen == (flag & MActOp);
        }

        public static final boolean
        isActTgtLink(long flag) {
            return FActTgtLink == (flag & MActTgt);
        }

        public static final boolean
        isActTgtEnclosure(long flag) {
            return FActTgtEnclosure == (flag & MActTgt);
        }

        public static final boolean
        isUpdLink(long flag) {
            return FUpdLink == (flag & MUpd);
        }

        public static final boolean
        isUpdDn(long flag) {
            return FUpdDn == (flag & MUpd);
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
