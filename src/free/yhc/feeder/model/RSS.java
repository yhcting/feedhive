package free.yhc.feeder.model;





//
//http://www.rssboard.org/rss-specification
//( RSS 2.0.1 )
//

//
// Most members are not used yet.
//
// Default value of required element is set as "".
//   => at DB this column is set as 'not null'
// Default value of optional element is set as null.
//
public class RSS {
    // This deault_date is NOT defined by spec.
    // It's just any value enough small.
    public static final String default_date = "THU, 1 Jan 1970 00:00:00 +0000";

    // See spec.
    public static final int CHANNEL_IMAGE_MAX_WIDTH  = 144;
    public static final int CHANNEL_IMAGE_MAX_HEIGHT = 400;

    public static enum ActionType {
        OPEN,   // open link
        DNOPEN; // download/open link or enclosure

        public static ActionType
        convert(String s) {
            for (ActionType a : ActionType.values())
                if (s.equals(a.name()))
                    return a;
            return null;
        }
    }

    public static enum ItemState {
        DUMMY,  // dummy item for UI usage.
        NEW,    // new
        OPENED;   // item is read (in case of 'open' action.

        public static ItemState
        convert(String s) {
            for (ItemState a : ItemState.values())
                if (s.equals(a.name()))
                    return a;
            return null;
        }
    }

    public static class Enclosure {
        public String url      = null;
        public String length   = null; // In some cases, this is used as "play time" - out of spec.
        public String type     = null;
    }

     public static class Item {
        // For internal use
        public long   id           = -1;
        public long   channelid    = -1;
        public ItemState state     = null;

        // Information from parsing.
        public String title        = null;
        public String link         = null;
        public String description  = null;
        public String pubDate      = null;
        public Enclosure enclosure = null;

        // for debugging
        public String
        dump() {
            return new StringBuilder()
                .append("----------- Item -----------\n")
                .append("title : ").append(title).append("\n")
                .append("link  : ").append(link).append("\n")
                .append("desc  : ").append(description).append("\n")
                .append("date  : ").append(pubDate).append("\n")
                .append("enclosure-url : ").append(enclosure.url).append("\n")
                .append("enclosure-len : ").append(enclosure.length).append("\n")
                .append("enclosure-type: ").append(enclosure.type).append("\n")
                .append("state : ").append((null == state)? null: state.toString()).append("\n")
                .toString();
        }

    }

    public static class Channel {
        public Item[] items        = new Item[0];

        // For internal use.
        public long   id           = -1;
        public String url          = null; // channel url.
        public String lastupdate   = null; // date updated lastly
        public byte[] imageblob    = null;
        public ActionType actionType = null;

        // Information from parsing.
        public String title        = null;
        public String description  = null;
        public String imageref     = null;

        // for debuggin
        public String
        dump() {
            StringBuilder builder =  new StringBuilder();
            builder
            .append("=============== Channel Dump ===============\n")
            .append("title    : ").append(title).append("\n")
            .append("desc     : ").append(description).append("\n")
            .append("imageref : ").append(imageref).append("\n")
            .append("imageblob: ").append((null == imageblob)? null: imageblob.toString()).append("\n");
            for (Item item : items)
                builder.append(item.dump());
            builder.append("\n\n");
            return builder.toString();
        }
    }

    public Channel  channel     = null;
}
