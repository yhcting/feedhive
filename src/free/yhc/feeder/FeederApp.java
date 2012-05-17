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
import android.content.Context;
import android.content.res.Configuration;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class FeederApp extends Application {
    // NOTE 1
    // This should be called only once!!!
    //
    // NOTE 2
    // Here is interesting observation.
    // Some functions require 'context'.
    // But, in some cases, application context returned by 'getApplicationContext()'
    //   issues unexpected exception.
    // Interesting point is, context from 'Activity' instance doens't have above issue.
    // So, even if this 'initialize' function is member of FeederApp,
    //   this function should be called with 'Activity' context.
    private void
    initialize(Context context) {
        // Create singleton instances
        DB.newSession(context).open();
        RTTask.S();

        UnexpectedExceptionHandler.S().init(context);
        // Initialize modules
        UIPolicy.init(context);
        RTTask.S().init(context);
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
        // register default customized uncaughted exception handler for error collecting.
        UnexpectedExceptionHandler.S();
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedExceptionHandler.S());

        initialize(getApplicationContext());
    }

    @Override
    public void
    onLowMemory() {
        super.onLowMemory();
    }
}
