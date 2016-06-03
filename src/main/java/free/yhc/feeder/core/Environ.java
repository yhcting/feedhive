/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

package free.yhc.feeder.core;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

import free.yhc.abaselib.ABaselib;
import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;

public class Environ implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(Environ.class, Logger.LOGLV_DEFAULT);

    private static final String[] ESSENTIAL_PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    public static final String PREF_KEY_APP_ROOT = "app_root";
    public static final String PREF_KEY_APP_VERSION = "app_version";
    @SuppressWarnings("unused")
    public static final long USAGE_INFO_UPDATE_PERIOD = 1000 * 60 * 60 * 24 * 7; // (ms) 7 days = 1 week


    // NOTE
    // UIPolicy shouldn't includes DBPolicy at it's constructor!
    // And in terms of design, UI policy SHOULD NOT have dependency on DB policy
    // See 'init' routine at FeederApp
    private static Environ sInstance = null;

    private File mAppRootDirFile;
    private File mAppTempDirFile;
    private File mAppLogDirFile;
    private File mAppErrLogFile;
    private File mAppUsageLogFile;

    public interface OnAppUpgrade {
        void onUpgrade(Context context, int from, int to);
    }

    private Environ() {
        // Dependency on only following modules are allowed
        // - Util
        // - UnexpectedExceptionHandler
        UnexpectedExceptionHandler.get().registerModule(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(AppEnv.getAppContext());
        String appRoot = prefs.getString(PREF_KEY_APP_ROOT,
                                         Environment.getExternalStorageDirectory().getAbsolutePath() + "/yhcFeeder");
        setAppDirectories(appRoot);
    }

    /**
     * This is called very early stage.
     * So, this SHOULD NOT have any dependencies on other modules!
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

        P.bug(null == sInstance);
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
               + "App root dir  : " + mAppRootDirFile.getAbsolutePath() + "\n"
               + "App temp dir  : " + mAppTempDirFile.getAbsolutePath() + "\n"
               + "App log dir   : " + mAppLogDirFile.getAbsolutePath() + "\n"
               + "App err file  : " + mAppErrLogFile.getAbsolutePath() + "\n"
               + "App usage file: " + mAppUsageLogFile.getAbsolutePath() + "\n";
    }

    public void
    setAppDirectories(@NonNull String root) {
        mAppRootDirFile = new File(root);
        mAppTempDirFile = new File(mAppRootDirFile.getAbsolutePath() + "/temp");
        mAppLogDirFile = new File(mAppRootDirFile.getAbsolutePath() + "/log");
        mAppErrLogFile = new File(mAppLogDirFile.getAbsolutePath() + "/last_error");
        mAppUsageLogFile = new File(mAppLogDirFile.getAbsolutePath() + "/usage_file");
    }

    public void
    initAppDirectories() throws IOException {
        //noinspection ResultOfMethodCallIgnored
        mAppRootDirFile.mkdirs();

        ABaselib.initLibraryWithExternalStoragePermission(mAppTempDirFile.getAbsolutePath());
        //noinspection ResultOfMethodCallIgnored
        mAppLogDirFile.mkdirs();
    }

    public void
    initPostEssentialPermission() throws IOException {
        initAppDirectories();
    }

    public boolean
    hasEssentialPermissions() {
        // App requires few dangerous permissions.
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                AppEnv.getAppContext(),
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public String[]
    getEssentialPermissions() {
        return ESSENTIAL_PERMISSIONS;
    }

    public String
    getAppRootDirectoryPath() {
        return mAppRootDirFile.getAbsolutePath();
    }

    public File
    getErrLogFile() {
        return mAppErrLogFile;
    }

    public File
    getUsageLogFile() {
        return mAppUsageLogFile;
    }

    @SuppressWarnings("unused")
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
