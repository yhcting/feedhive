package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.isValidValue;

import java.io.File;
/*
 * Functions related with UIPolicy...
 *     - initial setting of values.
 */
public class UIPolicy {
    private static final String appRootDir = "/sdcard/yhcFeeder/";
    private static final String predefinedChannelsFile = appRootDir + "channels.xml";
    // ext2, ext3, ext4 allows 255 bytes for filename.
    // but 'char' type in java is 2byte (16-bit unicode).
    // So, maximum character for filename in java on extN is 127.
    private static final int    maxFileNameLength = 127;

    static long
    decideDefaultActionType(Feed.Channel.ParD cParD, Feed.Item.ParD iParD) {
        if (Feed.Channel.ChannTypeMedia == cParD.type) {
            if (null != iParD)
                return isValidValue(iParD.enclosureUrl)?
                        Feed.Channel.FActTgtEnclosure | Feed.Channel.FActOpDn:
                        Feed.Channel.FActTgtLink | Feed.Channel.FActOpOpen;
            else
                return Feed.Channel.FActTgtEnclosure | Feed.Channel.FActOpDn;
        } else
            // default is "open link"
            return Feed.Channel.FActTgtLink | Feed.Channel.FActOpOpen;
    }

    // check and fix if possible.
    static boolean
    verifyConstraints(Feed.Item item) {
        // 'title' is mandatory!!!
        if (!isValidValue(item.parD.title))
            return false;

        // Item should have one of link or enclosure url.
        if (!isValidValue(item.parD.link)
             && !isValidValue(item.parD.enclosureUrl))
            return false;

        return true;
    }

    static boolean
    verifyConstraints(Feed.Channel ch) {
        if (!isValidValue(ch.parD.title))
            return false;

        return true;
    }

    public static void
    makeAppRootDir() {
        new File(appRootDir).mkdirs();
    }

    public static String
    getPredefinedChannelsFilePath() {
        return predefinedChannelsFile;
    }

    public static boolean
    makeChannelDir(long cid) {
        File f = new File(appRootDir + cid);
        if (f.exists())
            Utils.removeFileRecursive(f);
        return f.mkdir();
    }

    public static boolean
    removeChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(appRootDir + cid));
    }

    public static String
    getItemDownloadTempPath(long cid, long id) {
        return appRootDir + "____temp__" + cid + "___" + id + "__";
    }

    // postfix : usually, extension;
    // NOTE
    //   DB for channel item is fully updated
    public static String
    getItemFilePath(long id, String title, String url) {
        eAssert(null != url && null != title);
        long cid = DBPolicy.S().getItemInfoLong(id, DB.ColumnItem.CHANNELID);

        // we don't need to create valid filename with empty url value.
        if (url.isEmpty())
            return "";

        String ext = Utils.getExtentionFromUrl(url);

        // Title may include character that is not allowed as file name
        // (ex. '/')
        // Item is id is preserved even after update.
        // So, item ID can be used as file name to match item and file.
        String fname = Utils.convertToFilename(title) + "_" + id;
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
