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

package free.yhc.feeder.appwidget;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.widget.RemoteViews;
import free.yhc.feeder.FeederActivity;
import free.yhc.feeder.R;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.ThreadEx;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class UpdateService extends Service implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = true;
    private static final Utils.Logger P = new Utils.Logger(UpdateService.class);

    private static final String APPWIDGET_PREF_FILE = "appWidgetPref";

    private boolean mUpdateRunning = false;
    private SharedPreferences mWidgetCatMapPref = null;

    private class AppWidgetUpdater extends ThreadEx<Err> {
        private final AppWidgetManager mAwm;
        private final int[] mAppWidgetIds;

        private PendingIntent
        getAppLauncherPendingIntent() {
            // Register an onClickListener
            Intent intent = new Intent(Utils.getAppContext(), FeederActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pIntent = PendingIntent.getActivity(Utils.getAppContext(),
                                                              0,
                                                              intent,
                                                              PendingIntent.FLAG_UPDATE_CURRENT);
            return pIntent;
        }

        AppWidgetUpdater(int[] appWidgetIds) {
            mAppWidgetIds = appWidgetIds;
            mAwm = AppWidgetManager.getInstance(getApplicationContext());
        }

        @Override
        protected void
        onPreRun() {
            if (DBG) P.v("Update onPreRun");
            for (int awid : mAppWidgetIds) {
                RemoteViews rv = new RemoteViews(getApplicationContext().getPackageName(),
                                                 R.layout.appwidget_loading);
                mAwm.updateAppWidget(awid, rv);
            }
        }

        @Override
        protected void
        onPostRun(Err result) {
            if (DBG) P.v("Update onPostRun");
            for (int awid : mAppWidgetIds) {
                RemoteViews rv = new RemoteViews(getApplicationContext().getPackageName(),
                                                 R.layout.appwidget_loading);
                rv.setImageViewResource(R.id.image, R.drawable.search_up);
                /*
                remoteViews.setOnClickPendingIntent(R.id.list, pIntent);
                appWidgetManager.updateAppWidget(widgetId, rv);
                */
                mAwm.updateAppWidget(awid, rv);
            }
            stopSelf();
        }

        @Override
        protected void
        onCancel() {
        }

        @Override
        protected void
        onCancelled() {
            if (DBG) P.v("Update onCancelled");
            stopSelf();
        }

        @Override
        protected Err
        doAsyncTask() {
            if (DBG) P.v("Update AsyncTask");
            try {
                Thread.sleep(5000);
            } catch (Exception ignored) { }
            return Err.NO_ERR;
        }
    }


    public static void
    update(Context context, int[] appWidgetIds) {
        // Build the intent to call the service
        Intent intent = new Intent(Utils.getAppContext(), UpdateService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        // Update the widgets via the service
        context.startService(intent);
    }

    public static void
    updateAll(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(Utils.getAppContext());
        ComponentName widget = new ComponentName(Utils.getAppContext(), Provider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widget);
        update(context, appWidgetIds);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .appwidget.UpdateService ]";
    }

    @Override
    public void
    onCreate() {
        super.onCreate();
        UnexpectedExceptionHandler.get().registerModule(this);
        mWidgetCatMapPref = getSharedPreferences(APPWIDGET_PREF_FILE, MODE_PRIVATE);
    }

    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) P.v("onStartCommand : " + startId);
        // DO NOT anything for additional update request if there is already running update.
        if (mUpdateRunning)
            return START_NOT_STICKY;
        mUpdateRunning = true;

        int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        new AppWidgetUpdater(appWidgetIds).run();
        return START_NOT_STICKY;
    }

    @Override
    public void
    onDestroy() {
        if (DBG) P.v("onDestroy");
        UnexpectedExceptionHandler.get().unregisterModule(this);
        //logI("ScheduledUpdateService : onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder
    onBind(Intent intent) {
        return null;
    }

}
