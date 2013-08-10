/*****************************************************************************
 *    Copyright (C) 2013 Younghyung Cho. <yhcting77@gmail.com>
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

import java.io.File;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;

public class Environ implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(Environ.class);

    public static final String  PREF_KEY_APP_ROOT = "app_root";
    public static final long    USAGE_INFO_UPDATE_PERIOD = 1000 * 60 * 60 * 24 * 7; // (ms) 7 days = 1 week


    // Even if these two variables are not 'final', those should be handled like 'final'
    //   because those are set only at init() function, and SHOULD NOT be changed.
    private static Context  sAppContext  = null;
    private static Handler  sUiHandler   = null;

    // NOTE
    // UIPolicy shouldn't includes DBPolicy at it's constructor!
    // And in terms of design, UI policy SHOULD NOT have dependency on DB policy
    // See 'init' routine at FeederApp
    private static Environ sInstance = null;

    private String mAppRootDir;
    private File   mAppTempDirFile;
    private File   mAppLogDirFile;
    private File   mAppErrLogFile;
    private File   mAppUsageLogFile;


    private Environ() {
        // Dependency on only following modules are allowed
        // - Utils
        // - UnexpectedExceptionHandler
        UnexpectedExceptionHandler.get().registerModule(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppContext());
        String appRoot = prefs.getString(PREF_KEY_APP_ROOT,
                                         Environment.getExternalStorageDirectory().getAbsolutePath() + "/yhcFeeder");
        setAppDirectories(appRoot);
    }

    /**
     * This is called very early stage.
     * So, this SHOULD NOT have any dependencies on other modules!
     * @param aAppContext
     */
    public static void
    init(Context aAppContext) {
        sAppContext = aAppContext;
        sUiHandler = new Handler();
        Utils.eAssert(null == sInstance);
        sInstance = new Environ();
    }

    public static Environ
    get() {
        return sInstance;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ Environ ]"
               + "App root dir  : " + mAppRootDir + "\n"
               + "App temp dir  : " + mAppTempDirFile.getAbsolutePath() + "\n"
               + "App log dir   : " + mAppLogDirFile.getAbsolutePath() + "\n"
               + "App err file  : " + mAppErrLogFile.getAbsolutePath() + "\n"
               + "App usage file: " + mAppUsageLogFile.getAbsolutePath() + "\n";
    }

    public static Context
    getAppContext() {
        return sAppContext;
    }

    public static Handler
    getUiHandler() {
        return sUiHandler;
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

    /**
     * returned directory path ends with '/'
     * @return
     */
    public String
    getAppRootDirectoryPath() {
        return mAppRootDir;
    }

    public File
    getTempDirFile() {
        return mAppTempDirFile;
    }

    public File
    getErrLogFile() {
        return mAppErrLogFile;
    }

    public File
    getUsageLogFile() {
        return mAppUsageLogFile;
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
}
