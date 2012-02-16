package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import free.yhc.feeder.R;

public class Feed {
    // This deault_date is NOT defined by spec.
    // It's just any value enough small.
    public static final String default_date = "THU, 1 Jan 1970 00:00:00 +0000";

    // See spec.
    public static final int CHANNEL_IMAGE_MAX_WIDTH  = 200;
    public static final int CHANNEL_IMAGE_MAX_HEIGHT = 200;

    public static class Item {
         public static enum State {
             NEW        (R.color.title_color_new,
                         R.color.text_color_new),
             // For 'open'  : item is read
             // For 'dnopen': item is downloaded
             OPENED     (R.color.title_color_opened,
                         R.color.text_color_opened);

             private int titleColor;
             private int textColor;

             State(int titleColor, int textColor) {
                 this.titleColor = titleColor;
                 this.textColor = textColor;
             }

             public int
             getTitleColor() {
                 return titleColor;
             }

             public int
             getTextColor() {
                 return textColor;
             }

             public static State
             convert(String s) {
                 for (State a : State.values())
                     if (s.equals(a.name()))
                         return a;
                 eAssert(false);
                 return null;
             }
         }


        // For internal use
        long   id           = -1;
        long   channelid    = -1;
        State  state        = State.NEW;

        // Information from parsing.
        String title        = "";
        String link         = "";
        String description  = "";
        String pubDate      = "";
        String enclosureUrl = "";
        String enclosureLength = "";
        String enclosureType = "";

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
        public static enum Action {
            OPEN,   // open link
            DNOPEN; // download/open link or enclosure

            public static Action
            convert(String s) {
                for (Action a : Action.values())
                    if (s.equals(a.name()))
                        return a;
                return null;
            }
        }

        public static enum Type {
            NORMAL, // normal feed - ex. news feed.
            MEDIA;  // media-based feed - ex. podcast.

            public static Type
            convert(String s) {
                for (Type a : Type.values())
                    if (s.equals(a.name()))
                        return a;
                eAssert(false);
                return null;
            }
        }

        public static enum Order {
            NORMAL,  // item will be listed by same order with xml.
            REVERSE; // reverse.

            public static Order
            convert(String s) {
                for (Order a : Order.values())
                    if (s.equals(a.name()))
                        return a;
                eAssert(false);
                return null;
            }
        }

        Type   type         = Type.NORMAL;
        Item[] items        = new Item[0];

        // For internal use.
        long   id           = -1;
        long   categoryid   = -1;
        String url          = ""; // channel url.
        String lastupdate   = ""; // date updated lastly
        byte[] imageblob    = null;
        Action action       = Action.OPEN;
        Order  order        = Order.NORMAL;

        // Information from parsing.
        String title        = "";
        String description  = "";
        String imageref     = "";

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

    public static class Category {
        public long     id      = -1;
        public String   name    = ""; // category name
        public Category() {}
        public Category(String name) {
            this.name = name;
        }
    }

    public Channel  channel     = new Channel();
}
