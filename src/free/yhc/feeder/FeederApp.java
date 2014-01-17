/******************************************************************************
 *    Copyright (C) 2012, 2013, 2014 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of FeedHive
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
 *    along with this program.	If not, see <http://www.gnu.org/licenses/>.
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
import free.yhc.feeder.model.ContentsManager;
import free.yhc.feeder.model.Environ;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.UsageReport;
import free.yhc.feeder.model.Utils;

public class FeederApp extends Application {
    private static final boolean DBG = false;
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
    onAppUpgrade(Context context, int from, int to) {
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
