/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.isValidValue;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

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
    private static final String appLogDir  = appRootDir + "log/";
    // ext2, ext3, ext4 allows 255 bytes for filename.
    // but 'char' type in java is 2byte (16-bit unicode).
    // So, maximum character for filename in java on extN is 127.
    private static final int    maxFileNameLength = 127;

    private static final File   appTempDirFile = new File(appTempDir);
    private static final File   appLogDirFile  = new File(appLogDir);

    /**
     * Guessing default action type from Feed data.
     * @param cParD
     * @param iParD
     * @return
     *   Feed.Channel.FActxxxx
     */
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

    /**
     * Check that is this valid item?
     * (Result of parsing has enough information required by this application?)
     * @param item
     * @return
     */
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

    /**
     * Check that is this valid channel?
     * (Result of parsing has enough information required by this application?)
     * @param ch
     * @return
     */
    static boolean
    verifyConstraints(Feed.Channel.ParD ch) {
        if (!isValidValue(ch.title))
            return false;

        return true;
    }

    /**
     * Initialize
     * @param context
     */
    public static void
    init(Context context)  {
        new File(appRootDir).mkdirs();
        appTempDirFile.mkdir();
        appLogDirFile.mkdir();
        cleanTempFiles();
    }

    public static String
    getPredefinedChannelsFilePath() {
        return appRootDir + "channels.xml";
    }

    public static String
    getPredefinedChannelsAssetFilePath() {
        Locale lc = java.util.Locale.getDefault();
        String file;
        if (Locale.KOREA.equals(lc) || Locale.KOREAN.equals(lc))
            file = "channels_kr.xml";
        else
            file = "channels_en.xml";
        return file;
    }

    public static boolean
    makeChannelDir(long cid) {
        File f = new File(appRootDir + cid);
        if (f.exists())
            Utils.removeFileRecursive(f, true);
        return f.mkdir();
    }

    /**
     * This deletes channel directory itself.
     * @param cid
     * @return
     */
    public static boolean
    removeChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(appRootDir + cid), true);
    }

    public static boolean
    cleanChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(appRootDir + cid), false);
    }

    public static File
    getNewTempFile() {
        File ret = null;
        try {
            ret = File.createTempFile("free.yhc.feeder", null, appTempDirFile);
        } catch (IOException e){}
        return ret;
    }

    public static void
    cleanTempFiles() {
        Utils.removeFileRecursive(appTempDirFile, false);
    }

    public static File[]
    getLogFiles() {
        return appLogDirFile.listFiles();
    }

    public static File
    getNewLogFile() {
        File ret = null;
        try {
            ret = File.createTempFile("exception", null, appLogDirFile);
        } catch (IOException e){}
        return ret;
    }

    /**
     * Remove all log files.
     */
    public static void
    cleanLogFiles() {
        Utils.removeFileRecursive(appLogDirFile, false);
    }

    /**
     * Get file which contains data for given feed item.
     * Usually, this file is downloaded from internet.
     * (Ex. downloaded web page / downloaded mp3 etc)
     * @param id
     * @return
     */
    public static File
    getItemDataFile(long id) {
        return getItemDataFile(id, -1, null, null);
    }

    // NOTE
    // Why this parameter is given even if we can get from DB?
    // This is only for performance reason!
    // postfix : usually, extension;
    /**
     * NOTE
     * Why these parameters - title, url - are given even if we can get from DB?
     * This is only for performance reason!
     * @param id
     *   item id
     * @param cid
     *   channel id of this item. if '< 0', than value read from DB is used.
     * @param title
     *   title of this item. if '== null' or 'isEmpty()', than value read from DB is used.
     * @param url
     *   target url of this item. link or enclosure is possible.
     *   if '== null' or is 'isEmpty()', then value read from DB is used.
     * @return
     */
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

    /**
     * Get BG task thread priority from shared preference.
     * @param context
     * @return
     *   Value of Java Thread priority (between Thread.MIN_PRIORITY and Thread.MAX_PRIORITY)
     */
    public static int
    getPrefBGTaskPriority(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String prio = prefs.getString("bgtask_prio", "low");
        if ("low".equals(prio))
            return Thread.MIN_PRIORITY;
        else if ("medium".equals(prio))
            return (Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2;
        else if ("high".equals(prio))
            return Thread.NORM_PRIORITY;
        else {
            eAssert(false);
            return Thread.MIN_PRIORITY;
        }
    }
}
