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

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logW;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
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
    public static void
    initialize(Context context) {
        DB.newSession(context).open();
        UIPolicy.initialise();
        UIPolicy.cleanTempFiles();
        RTTask.S(); // create instance

        // Check predefined files
        File f = new File(UIPolicy.getPredefinedChannelsFilePath());
        if (!f.exists()) {
            // Copy default predefined channels file from Assert!
            AssetManager am = context.getAssets();
            InputStream is = null;
            try {
                is = am.open("channels.xml");
                OutputStream out = new FileOutputStream(f);
                byte buf[]=new byte[1024 * 16];
                int len;
                while((len = is.read(buf)) > 0)
                    out.write(buf, 0, len);
                out.close();
                is.close();
            }
            catch (IOException e) {
                logW("FeederApp Critical Error!");
                eAssert(false);
                return;
            }
        }

        UIPolicy.applyAppPreference(context);
        UnexpectedExceptionHandler.S().setEnvironmentInfo(context);
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
