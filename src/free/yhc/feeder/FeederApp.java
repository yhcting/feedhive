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

package free.yhc.feeder;

import android.app.Application;
import android.content.res.Configuration;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.UsageReport;
import free.yhc.feeder.model.Utils;

public class FeederApp extends Application {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(FeederApp.class);

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

        // Utils.init() SHOULD be called before calling other init() functions,
        //   because Utils has application context and offer it to other modules.
        // And most modules uses application context in it's early stage - ex. constructor.
        Utils.init(getApplicationContext());

        // register default customized uncaught exception handler for error collecting.
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedExceptionHandler.get());

        DB.get().open();
        UIPolicy.get();
        DBPolicy.get();
        RTTask.get();
        UsageReport.get();
        LookAndFeel.get();
        NotiManager.get();
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
