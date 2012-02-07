package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.isValidValue;

import java.io.File;
/*
 * Functions related with UIPolicy...
 *     - initial setting of values.
 */
public class UIPolicy {
    private static final String appRootDir = "/sdcard/.free.yhc.feeder/";
    // ext2, ext3, ext4 allows 255 bytes for filename.
    // but 'char' type in java is 2byte (16-bit unicode).
    // So, maximum character for filename in java on extN is 127.
    private static final int    maxFileNameLength = 127;

    // This should be called only at the begining of context.
    /*
    public static void
    setAppRootDir(String path) {
        appRootDir = path;
    }
    */

    static void
    setAsDefaultActionType(RSS.Channel ch) {
        // default is "open link"
        ch.actionType = RSS.ActionType.OPEN;
        if (0 < ch.items.length
            && null != ch.items[0].enclosure) {
            // action for enclosure.
            ch.actionType = RSS.ActionType.DNOPEN;
        }
    }

    // check and fix if possible.
    static boolean
    verifyConstraints(RSS.Item item) {
        // 'title' is mandatory!!!
        if (!isValidValue(item.title))
            return false;

        // Item should have one of link or enclosure url.
        if (!isValidValue(item.link)
             && (null == item.enclosure
                 || !isValidValue(item.enclosure.url)))
            return false;

        // 'pubDate' and 'description' is important in terms of UI.
        // So, these are used directly in UI with assumption of "not-null".
        // But, it's just UI policy - not related with DB and algorithm design!
        if (null == item.pubDate)
            item.pubDate = "";

        if (null == item.description)
            item.description = "";

        // member of enclosure cannot be NULL!
        if (null != item.enclosure) {
            if (!isValidValue(item.enclosure.url))
                return false;
            // 'url' is valid. others should NOT null - UI may assume belows are not null.
            if (null == item.enclosure.length)
                item.enclosure.length = "";
            if (null == item.enclosure.type)
                item.enclosure.type = "";
        }

        return true;
    }

    static boolean
    verifyConstraints(RSS.Channel ch) {
        if (!isValidValue(ch.title) || !isValidValue(ch.lastupdate))
            return false;

        if (null == ch.description)
            ch.description = "";

        return true;
    }

    public static boolean
    makeChannelDir(long cid) {
        return new File(appRootDir + cid).mkdirs();
    }

    public static boolean
    removeChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(appRootDir + cid));
    }

    // postfix : usually, extension;
    // NOTE
    //   DB for channel item is fully updated
    public static String
    getItemFilePath(long cid, String title, String url) {
        eAssert(null != url && null != title);

        String ext = Utils.getExtention(url);
        if (null == ext)
            ext = "";

        // Title may include character that is not allowed as file name
        // (ex. '/')
        String fname = Utils.convertToFilename(title);
        int endIndex = maxFileNameLength - ext.length() - 1; // '- 1' for '.'
        if (endIndex > fname.length())
            endIndex = fname.length();

        fname = fname.substring(0, endIndex);
        fname = fname + '.' + ext;

        // NOTE
        //   In most UNIX file systems, only '/' and 'null' are reserved.
        //   So, we don't worry about "converting string to valid file name".
        return appRootDir + cid + "/" + fname;
    }
}
