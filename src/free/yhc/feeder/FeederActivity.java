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

import java.util.Calendar;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import free.yhc.feeder.model.Utils;

public class FeederActivity extends Activity {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(FeederActivity.class);

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Sometimes released version may have some issues regarding scheduled update.
        // Once scheduled update is executed for some reasons, next scheduled update is not
        //   set either.
        // That leads to scheduled update never happens until there are some changes at channel
        //   (adding new one or deleting existing one) even if issues are fixed at next release version
        // To avoid this case, reschedule whenever starting application.
        // This may lead to some delay for starting application(but not much) and no harmful.
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
            }
        });


        Intent intent = new Intent(this, ChannelListActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
    }
}