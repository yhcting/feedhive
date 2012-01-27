package free.yhc.feeder;



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
class RSS {
    // This deault_date is NOT defined by spec.
    // It's just any value enough small.
    static final String default_date = "THU, 1 Jan 1970 00:00:00 +0000";

    static class Xmlns {
        String name = null;
        String link = null;
    }

    static class Image {
        // Numeric values used here are defined by RSS 2.0.1 spec.
        static final String MAX_WIDTH       = "144";
        static final String MAX_HEIGHT      = "400";
        static final String DEFAULT_WIDTH   = "88";
        static final String DEFAULT_HEIGHT  = "31";

        // Required
        String url      = "";
        String title    = "";
        String link     = "";

        // Optional
        String width    = null;
        String height   = null;
        String description = null;
    }

    static class Category {
        // attributes
        String domain   = null; // optional
        String value    = "";
    }

    static class Enclosure {
        // attributes - required
        String url      = "";
        String length   = ""; // In some cases, this is used as "play time" - out of spec.
        String type     = "";
    }

    static class Guid {
        // attributes
        String isPermaLink  = "true"; // optional (true is default)
        String value        = "";
    }

    static class Source {
        // attributes
        String url      = null; // required
        String value    = "";
    }

    static class Item {
        // For internal use
        long   id = -1;
        long   channelid = -1;

        // Required Elements
        String title        = "";
        String link         = "";
        String description  = "";

        // Optional Elements
        String author       = null;
        Category category   = null;
        String comments     = null;
        Enclosure enclosure = null;
        Guid   guid         = null;
        String pubDate      = null;
        Source source       = null;
    }

    static class Channel {
        // For internal use - dynamic information.
        Item[] items        = null;

        // For internal use.
        long   id           = -1;
        String url          = null; // channel url.

        // Required Elements.
        String title        = "";
        String link         = "";
        String description  = "";

        // Optional Elements
        String language     = null;
        String copyright    = null;
        String managingEditor = null;
        String webMaster    = null;
        String pubDate      = null; // RFC-822 TODO: parser not implemented yet...
        String lastBuildDate= null; // RFC-822 TODO: parser not implemented yet...
        Category category   = null;
        String generator    = null;
        String docs         = null;
        String cloud        = null;
        String ttl          = null;
        Image  image        = null;
        String rating       = null; // ignored
        String textInput    = null; // ignored
        String skipHours    = null;
    }

    String  version     = "2.0"; // by default
    Xmlns[] xmlns       = null;
    Channel channel     = null;
}
