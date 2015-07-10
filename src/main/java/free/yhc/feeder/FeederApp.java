/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

package free.yhc.feeder;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import free.yhc.feeder.appwidget.ViewsService;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.Environ;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.UsageReport;
import free.yhc.feeder.core.Utils;

public class FeederApp extends Application {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Utils.Logger P = new Utils.Logger(FeederApp.class);

    private void
    convertYesNoPreferenceToBoolean(SharedPreferences prefs,
                                    SharedPreferences.Editor prefEd,
                                    Resources res,
                                    int keyId) {
        String yesno = prefs.getString(res.getString(keyId), null);
        if (null != yesno) {
            if (res.getString(R.string.csyes).equals(yesno))
                prefEd.putBoolean(res.getString(keyId), true);
            else
                prefEd.putBoolean(res.getString(keyId), false);
        }
    }

    // Following preference is changed from list preference to switch preference.
    // - send error report
    // - send usage report
    // - new message notification
    // - use wifi only
    private void
    onUpgradeTo56(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor prefEd = prefs.edit();
        Resources res = context.getResources();
        convertYesNoPreferenceToBoolean(prefs, prefEd, res, R.string.cserr_report);
        convertYesNoPreferenceToBoolean(prefs, prefEd, res, R.string.csusage_report);
        convertYesNoPreferenceToBoolean(prefs, prefEd, res, R.string.csnewmsg_noti);
        convertYesNoPreferenceToBoolean(prefs, prefEd, res, R.string.csuse_wifi_only);
        prefEd.apply();
    }

    private void
    onAppUpgrade(Context context,
                 int from,
                 @SuppressWarnings("unused")  int to) {
        if (from < 56)
            onUpgradeTo56(context);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void
    onCreate() {
        super.onCreate();

        // NOTE
        // Order is important

        // Initialize Environment
        Environ.init(getApplicationContext(),
                     new Environ.OnAppUpgrade() {
            @Override
            public void
            onUpgrade(Context context, int from, int to) {
                onAppUpgrade(context, from, to);
            }
        });
        Environ.get();

        // Utils.init() SHOULD be called before initializing other modules.
        //   because Utils has application context and offer it to other modules.
        // And most modules uses application context in it's early stage - ex. constructor.
        Utils.init();
        Utils.cleanTempFiles();

        // register default customized uncaught exception handler for error collecting.
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedExceptionHandler.get());

        DB.get().open();
        ContentsManager.get();
        DBPolicy.get();
        RTTask.get();
        UsageReport.get();
        NotiManager.get();

        LifeSupportService.init();

        // To connect to app widgets
        ViewsService.instantiateViewsFactories();
    }

    @Override
    public void
    onLowMemory() {
        // Application is about to be killed.
        // Remove all non-sticky notification.
        NotiManager.get().removeAllNonStickyNotification();
        super.onLowMemory();
    }
}
