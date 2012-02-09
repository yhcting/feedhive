package free.yhc.feeder.model;

public class Feed {
    // This deault_date is NOT defined by spec.
    // It's just any value enough small.
    public static final String default_date = "THU, 1 Jan 1970 00:00:00 +0000";

    // See spec.
    public static final int CHANNEL_IMAGE_MAX_WIDTH  = 200;
    public static final int CHANNEL_IMAGE_MAX_HEIGHT = 200;

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

     public static class Item {
         public static enum State {
             DUMMY,  // dummy item for UI usage.
             NEW,    // new
             OPENED;   // item is read (in case of 'open' action.

             public static State
             convert(String s) {
                 for (State a : State.values())
                     if (s.equals(a.name()))
                         return a;
                 return null;
             }
         }

        // For internal use
        public long   id           = -1;
        public long   channelid    = -1;
        public State  state        = State.NEW;

        // Information from parsing.
        public String title        = "";
        public String link         = "";
        public String description  = "";
        public String pubDate      = "";
        public String enclosureUrl = "";
        public String enclosureLength = "";
        public String enclosureType = "";

        // for debugging
        public String
        dump() {
            return new StringBuilder()
                .append("----------- Item -----------\n")
                .append("title : ").append(title).append("\n")
                .append("link  : ").append(link).append("\n")
                .append("desc  : ").append(description).append("\n")
                .append("date  : ").append(pubDate).append("\n")
                .append("enclosure-url : ").append(enclosureUrl).append("\n")
                .append("enclosure-len : ").append(enclosureLength).append("\n")
                .append("enclosure-type: ").append(enclosureType).append("\n")
                .append("state : ").append((null == state)? null: state.toString()).append("\n")
                .toString();
        }

    }

    public static class Channel {
        public static enum Type {
            NORMAL, // normal feed - ex. news feed.
            MEDIA,  // media-based feed - ex. podcast.
        }

        public Type   type         = Type.NORMAL;
        public Item[] items        = new Item[0];

        // For internal use.
        public long   id           = -1;
        public String url          = ""; // channel url.
        public String lastupdate   = ""; // date updated lastly
        public byte[] imageblob    = null;
        public ActionType actionType = ActionType.OPEN;

        // Information from parsing.
        public String title        = "";
        public String description  = "";
        public String imageref     = "";

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

    public Channel  channel     = new Channel();
}
