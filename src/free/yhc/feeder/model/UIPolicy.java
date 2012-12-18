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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
/*
 * Functions related with UIPolicy...
 *     - initial setting of values.
 */
public class UIPolicy implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(UIPolicy.class);

    public static final String  PREF_KEY_APP_ROOT = "app_root";
    public static final long    USAGE_INFO_UPDATE_PERIOD = 1000 * 60 * 60 * 24 * 7; // (ms) 7 days = 1 week

    // NOTE
    // UIPolicy shouldn't includes DBPolicy at it's constructor!
    // And in terms of design, UI policy SHOULD NOT have dependency on DB policy
    // See 'init' routine at FeederApp
    private static UIPolicy sInstance = null;

    private String mAppRootDir;
    private File   mAppTempDirFile;
    private File   mAppLogDirFile;
    private File   mAppErrLogFile;
    private File   mAppUsageLogFile;

    private UIPolicy() {
        // Dependency on only following modules are allowed
        // - Utils
        // - UnexpectedExceptionHandler
        // - DB / DBThread
        UnexpectedExceptionHandler.get().registerModule(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Utils.getAppContext());
        String appRoot = prefs.getString(PREF_KEY_APP_ROOT, "/sdcard/yhcFeeder");
        setAppDirectories(appRoot);
        cleanTempFiles();
    }

    public static UIPolicy
    get() {
        if (null == sInstance)
            sInstance = new UIPolicy();
        return sInstance;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ UIPolicy ]"
               + "App root dir  : " + mAppRootDir + "\n"
               + "App temp dir  : " + mAppTempDirFile.getAbsolutePath() + "\n"
               + "App log dir   : " + mAppLogDirFile.getAbsolutePath() + "\n"
               + "App err file  : " + mAppErrLogFile.getAbsolutePath() + "\n"
               + "App usage file: " + mAppUsageLogFile.getAbsolutePath() + "\n";
    }
    /**
     * Guessing default action type from Feed data.
     * @param cParD
     * @param iParD
     * @return
     *   Feed.Channel.FActxxxx
     */
    long
    decideActionType(long action, Feed.Channel.ParD cParD, Feed.Item.ParD iParD) {
        long    actFlag;

        if (null == iParD) {
            if (Feed.FINVALID == action)
                return Feed.FINVALID; // do nothing if there is no items at first insertion.

            // default value
            actFlag = Feed.Channel.FACT_DEFAULT;
        }

        switch (cParD.type) {
        case NORMAL:
            actFlag = Feed.Channel.FACT_TYPE_DYNAMIC | Feed.Channel.FACT_PROG_IN;
            break;
        case EMBEDDED_MEDIA: // special for youtube!
            actFlag = Feed.Channel.FACT_TYPE_EMBEDDED_MEDIA | Feed.Channel.FACT_PROG_EX;
            break;
        default:
            eAssert(false);
            actFlag = Feed.Channel.FACT_DEFAULT;
        }

        // NOTE
        // FACT_PROG_IN/EX can be configurable by user
        // So, this flag should not be changed except for action is invalid value.
        if (Feed.FINVALID == action)
            // In case of newly inserted channel (first decision), FACT_PROG_XX should be set as recommended one.
            return Utils.bitSet(action, actFlag, Feed.Channel.MACT_TYPE | Feed.Channel.MACT_PROG);
        else
            // If this is NOT first decision, user may change FACT_PROG_XX setting (UX scenario support this.)
            // So, in this case, FACT_PROG_XX SHOULD NOT be changed.
            return Utils.bitSet(action, actFlag, Feed.Channel.MACT_TYPE);
    }

    /**
     * Check that is this valid item?
     * (Result of parsing has enough information required by this application?)
     * @param item
     * @return
     */
    boolean
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
    boolean
    verifyConstraints(Feed.Channel.ParD ch) {
        if (!isValidValue(ch.title))
            return false;

        return true;
    }

    /**
     * SHOULD be called only by FeederPreferenceActivity.
     * @param root
     */
    public void
    setAppDirectories(String root) {
        mAppRootDir = root;
        new File(mAppRootDir).mkdirs();
        if (!root.endsWith("/"))
            mAppRootDir += "/";

        mAppTempDirFile = new File(mAppRootDir + "temp/");
        mAppTempDirFile.mkdirs();
        mAppLogDirFile = new File(mAppRootDir + "log/");
        mAppLogDirFile.mkdirs();
        mAppErrLogFile = new File(mAppLogDirFile.getAbsoluteFile() + "/last_error");
        mAppUsageLogFile = new File(mAppLogDirFile.getAbsoluteFile() + "/usage_file");
    }

    public String
    getAppRootDirectoryPath() {
        return mAppRootDir;
    }

    public String
    getPredefinedChannelsAssetPath() {
        Locale lc = java.util.Locale.getDefault();
        String file;
        if (Locale.KOREA.equals(lc) || Locale.KOREAN.equals(lc))
            file = "channels_kr.xml";
        else
            file = "channels_en.xml";
        return file;
    }

    /**
     * Create clean channel dir.
     * If directory already exists, all files in it are deleted.
     * @param cid
     * @return
     */
    public boolean
    makeChannelDir(long cid) {
        File f = new File(mAppRootDir + cid);
        if (f.exists())
            Utils.removeFileRecursive(f, true);
        return f.mkdir();
    }

    /**
     * This deletes channel directory itself.
     * @param cid
     * @return
     */
    public boolean
    removeChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(mAppRootDir + cid), true);
    }

    public boolean
    cleanChannelDir(long cid) {
        return Utils.removeFileRecursive(new File(mAppRootDir + cid), false);
    }

    public File
    getNewTempFile() {
        File ret = null;
        try {
            ret = File.createTempFile("free.yhc.feeder", null, mAppTempDirFile);
        } catch (IOException e){}
        return ret;
    }

    public void
    cleanTempFiles() {
        Utils.removeFileRecursive(mAppTempDirFile, false);
    }

    public File
    getErrLogFile() {
        return mAppErrLogFile;
    }

    public File
    getUsageLogFile() {
        return mAppUsageLogFile;
    }

    /**
     * Get BG task thread priority from shared preference.
     * @param context
     * @return
     *   Value of Java Thread priority (between Thread.MIN_PRIORITY and Thread.MAX_PRIORITY)
     */
    public int
    getPrefBGTaskPriority() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Utils.getAppContext());
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

    public String
    getDynamicActionTargetUrl(long action, String link, String enclosure) {
        if (Feed.Channel.FACT_TYPE_DYNAMIC != Feed.Channel.getActType(action))
            return null; // Not applicable for other case!

        if (Utils.isValidValue(enclosure)
            && Utils.isAudioOrVideo(enclosure))
            return enclosure;
        else {
            if (Utils.isValidValue(link))
                return link;
            else if (Utils.isValidValue(enclosure))
                return enclosure;
            else
                eAssert(false); // there is no valid link or enclosure value...
        }
        return null;
    }
}
