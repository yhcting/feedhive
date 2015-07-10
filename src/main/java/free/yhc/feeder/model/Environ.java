/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.feeder.model;

import java.io.File;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;

public class Environ implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(Environ.class);

    public static final String  PREF_KEY_APP_ROOT = "app_root";
    public static final String  PREF_KEY_APP_VERSION = "app_version";
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

    public interface OnAppUpgrade {
        void onUpgrade(Context context, int from, int to);
    }

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
    init(Context aAppContext, OnAppUpgrade onUpgrade) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(aAppContext);
        int prev = prefs.getInt(PREF_KEY_APP_VERSION, -1);
        try {
            PackageInfo pi = aAppContext.getPackageManager().getPackageInfo(aAppContext.getPackageName(), -1);
            if (pi.versionCode > prev) {
                onUpgrade.onUpgrade(aAppContext, prev, pi.versionCode);
                SharedPreferences.Editor prefEd = prefs.edit();
                prefEd.putInt(Environ.PREF_KEY_APP_VERSION, pi.versionCode);
                prefEd.apply();
            }
        } catch (NameNotFoundException ignore) { }

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
