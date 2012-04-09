package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.isValidValue;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
/*
 * Functions related with UIPolicy...
 *     - initial setting of values.
 */
public class UIPolicy {
    private static final String appRootDir = "/sdcard/yhcFeeder/";
    private static final String appTempDir = appRootDir + "temp/";
    private static final String predefinedChannelsFile = appRootDir + "channels.xml";
    // ext2, ext3, ext4 allows 255 bytes for filename.
    // but 'char' type in java is 2byte (16-bit unicode).
    // So, maximum character for filename in java on extN is 127.
    private static final int    maxFileNameLength = 127;

    private static final File   appRootTempDirFile = new File(appTempDir);

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
    verifyConstraints(Feed.Item.ParD item) {
        // 'title' is mandatory!!!
        if (!isValidValue(item.title))
            return false;

        // Item should have one of link or enclosure url.
        if (!isValidValue(item.link)
             && !isValidValue(item.enclosureUrl))
            return false;

        return true;
    }

    static boolean
    verifyConstraints(Feed.Channel.ParD ch) {
        if (!isValidValue(ch.title))
            return false;

        return true;
    }

    public static void
    initialise()  {
        new File(appRootDir).mkdirs();
        appRootTempDirFile.mkdir();
    }

    public static String
    getPredefinedChannelsFilePath() {
        return predefinedChannelsFile;
    }

    public static boolean
    makeChannelDir(long cid) {
        File f = new File(appRootDir + cid);
        if (f.exists())
            Utils.removeFileRecursive(f, true);
        return f.mkdir();
    }

    public static boolean
    removeChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(appRootDir + cid), true);
    }

    public static boolean
    cleanChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(appRootDir + cid), false);
    }

    public static File
    getTempFile() {
        File ret = null;
        try {
            ret = File.createTempFile("free.yhc.feeder", null, appRootTempDirFile);
        } catch (IOException e){}
        return ret;
    }

    public static File
    getItemDataFile(long id) {
        return getItemDataFile(id, -1, null, null);
    }

    public static void
    cleanTempFiles() {
        Utils.removeFileRecursive(appRootTempDirFile, false);
    }

    public static void
    applyAppPreference(Context context) {
        // Applying application preference
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String v = sharedPrefs.getString("maxnr_bgtask", "1");
        int value = 1;
        try {
            value = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            eAssert(false);
        }
        RTTask.S().setMaxConcurrent(value);
    }

    // NOTE
    // Why this parameter is given even if we can get from DB?
    // This is only for performance reason!
    // postfix : usually, extension;
    public static File
    getItemDataFile(long id, long cid, String title, String url) {
        if (cid < 0)
            cid = DBPolicy.S().getItemInfoLong(id, DB.ColumnItem.CHANNELID);

        if (!Utils.isValidValue(title))
            title = DBPolicy.S().getItemInfoString(id, DB.ColumnItem.TITLE);

        if (!Utils.isValidValue(url)) {
            long action = DBPolicy.S().getChannelInfoLong(cid, DB.ColumnChannel.ACTION);
            if (Feed.Channel.isActTgtLink(action))
                url = DBPolicy.S().getItemInfoString(id, DB.ColumnItem.LINK);
            else if (Feed.Channel.isActTgtEnclosure(action))
                url = DBPolicy.S().getItemInfoString(id, DB.ColumnItem.ENCLOSURE_URL);
            else
                url = "";
        }

        // we don't need to create valid filename with empty url value.
        if (url.isEmpty())
            return null;

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
        return new File(appRootDir + cid + "/" + fname);
    }
}
