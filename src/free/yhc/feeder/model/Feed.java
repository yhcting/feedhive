package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import free.yhc.feeder.R;

public class Feed {
    // This deault_date is NOT defined by spec.
    // It's just any value enough small.
    public static final String default_date = "THU, 1 Jan 1970 00:00:00 +0000";

    // 150 x 150 is enough size for channel icon.
    public static final int CHANNEL_IMAGE_MAX_WIDTH  = 150;
    public static final int CHANNEL_IMAGE_MAX_HEIGHT = 150;

    public static class Item {
        ParD   parD;
        DbD    dbD     = new DbD();
        DynD   dynD    = new DynD();

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

        // Dynamic data - changed in runtime dynamically (usually by user action.
        static class DynD {
            State  state = State.NEW;
            byte[] rawdata = new byte[0];
        }

        Item() {
            parD = new ParD();
        }

        Item(ParD parD) {
            this.parD = parD;
        }

        // for debugging
        public String
        dump() {
            return new StringBuilder()
                .append("----------- Item -----------\n")
                .append("title : ").append(parD.title).append("\n")
                .append("link  : ").append(parD.link).append("\n")
                .append("desc  : ").append(parD.description).append("\n")
                .append("date  : ").append(parD.pubDate).append("\n")
                .append("enclosure-url : ").append(parD.enclosureUrl).append("\n")
                .append("enclosure-len : ").append(parD.enclosureLength).append("\n")
                .append("enclosure-type: ").append(parD.enclosureType).append("\n")
                .append("state : ").append((null == dynD.state)? null: dynD.state.name()).append("\n")
                .toString();
        }

    }

    public static class Channel {
        ProfD profD = new ProfD();
        DbD   dbD   = new DbD();
        DynD  dynD  = new DynD();
        ParD  parD;

        Item[] items;

        public static enum Action {
            LINK,         // open link
            DN_ENCLOSURE; // download/open enclosure

            public static Action
            convert(String s) {
                for (Action a : Action.values())
                    if (s.equals(a.name()))
                        return a;
                return null;
            }
        }

        public static enum UpdateType {
            NORMAL,    // feed
            DN_LINK; // download link

            public static UpdateType
            convert(String s) {
                for (UpdateType a : UpdateType.values())
                    if (s.equals(a.name()))
                        return a;
                return null;
            }
        }

        public static enum ItemType {
            NORMAL, // normal feed - ex. news feed.
            MEDIA;  // media-based feed - ex. podcast.

            public static ItemType
            convert(String s) {
                for (ItemType a : ItemType.values())
                    if (s.equals(a.name()))
                        return a;
                eAssert(false);
                return null;
            }
        }

        // Profile data.
        static class ProfD {
            String url          = ""; // channel url.
        }

        // Information from parsing.
        static class ParD {
            // Type is usually determined by which namespace is used at XML.
            // For example.
            //   xmlns:itunes -> Media
            ItemType type         = ItemType.NORMAL;
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

        // Dynamic data - changed in runtime dynamically (usually by user action).
        static class DynD {
            Action      action       = Action.LINK;
            UpdateType  updatetype   = UpdateType.NORMAL;
            byte[]      imageblob    = null;
        }

        Channel() {
            parD  = new ParD();
            items = new Item[0];
        }

        Channel(ParD parD, Item.ParD[] itemParDs) {
            this.parD   = parD;
            items = new Item[itemParDs.length];
            for (int i = 0; i < itemParDs.length; i++) {
                items[i] = new Item();
                items[i].parD = itemParDs[i];
            }
        }

        // for debugging
        public String
        dump() {
            StringBuilder builder =  new StringBuilder();
            builder
            .append("=============== Channel Dump ===============\n")
            .append("title    : ").append(parD.title).append("\n")
            .append("desc     : ").append(parD.description).append("\n")
            .append("imageref : ").append(parD.imageref).append("\n");
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
}
