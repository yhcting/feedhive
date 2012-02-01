package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.logI;




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

    // '<', '>' is not used as tag name.
    public static final char tagtree_element_delimiter = '>';
    public static final char tagtree_string_delimiter  = '<';

    public static class Xmlns {
        public String name = null;
        public String link = null;
    }

    public static class Image {
        // Numeric values used here are defined by RSS 2.0.1 spec.
        public static final String MAX_WIDTH       = "144";
        public static final String MAX_HEIGHT      = "400";
        public static final String DEFAULT_WIDTH   = "88";
        public static final String DEFAULT_HEIGHT  = "31";

        // Required
        public String url      = "";
        public String title    = "";
        public String link     = "";

        // Optional
        public String width    = null;
        public String height   = null;
        public String description = null;
    }

    public static class Category {
        // attributes
        public String domain   = null; // optional
        public String value    = "";
    }

    public static class Enclosure {
        // attributes - required
        public String url      = "";
        public String length   = ""; // In some cases, this is used as "play time" - out of spec.
        public String type     = "";
    }

    public static class Guid {
        // attributes
        public String isPermaLink  = "true"; // optional (true is default)
        public String value        = "";
    }

    public static class Source {
        // attributes
        public String url      = null; // required
        public String value    = "";
    }

    public static class Item {
        // For internal use
        public long   id = -1;
        public long   channelid = -1;

        // Values of user requirement
        // (values depend on argument passed when parsing)
        public String[] userValues = null;

        // Required Elements
        public String title        = "";
        public String link         = "";
        public String description  = "";

        // Optional Elements
        public String author       = null;
        public Category category   = null;
        public String comments     = null;
        public Enclosure enclosure = null;
        public Guid   guid         = null;
        public String pubDate      = null;
        public Source source       = null;
    }

    public static class Channel {
        public Item[] items        = null;

        // Values of user requirement
        // (values depend on argument passed when parsing)
        public String[] userValues = null;

        // For internal use.
        public long   id           = -1;
        public String url          = null; // channel url.

        // Required Elements.
        public String title        = "";
        public String link         = "";
        public String description  = "";

        // Optional Elements
        public String language     = null;
        public String copyright    = null;
        public String managingEditor = null;
        public String webMaster    = null;
        public String pubDate      = null; // RFC-822 TODO: parser not implemented yet...
        public String lastBuildDate= null; // RFC-822 TODO: parser not implemented yet...
        public Category category   = null;
        public String generator    = null;
        public String docs         = null;
        public String cloud        = null;
        public String ttl          = null;
        public Image  image        = null;
        public String rating       = null; // ignored
        public String textInput    = null; // ignored
        public String skipHours    = null;

        // Hack
        public String itunesImage  = null; // image reference at itunes namespace
    }

    public static class TagTree {
        // For internal use - dynamic information.

        // cels/iels is element's tag tree.
        // Example
        //     <a>
        //         <b/>
        //     </a>
        //     <c>
        //         <d/>
        //         <e/>
        //     </c>
        // els[0] = "a>b"
        // els[1] = "c>d"
        // els[2] = "c>e"
        public String[] ctags = null; // elements of channel
        public String[] itags = null; // elements of item
    }

    /*
     * Functions for debugging!!
     */
    public static void
    dump(TagTree tt) {
        logI("------- Channel Tag Tree -------\n");
        for (String s : tt.ctags)
            logI(s + "\n");
        logI("------- Item Tag Tree ---------\n");
        for (String s : tt.itags)
            logI(s + "\n");
        logI("--------------------------------\n");
    }

    public String  version     = "2.0"; // by default
    public Xmlns[] xmlns       = null;
    public Channel channel     = null;
    public TagTree tagtree     = null;
}
