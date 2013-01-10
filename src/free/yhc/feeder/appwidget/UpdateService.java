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

import java.util.ArrayList;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.widget.RemoteViews;
import free.yhc.feeder.FeederActivity;
import free.yhc.feeder.R;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.ThreadEx;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class UpdateService extends Service implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = true;
    private static final Utils.Logger P = new Utils.Logger(UpdateService.class);

    private class AppWidgetUpdater extends ThreadEx<Err> {
        private final AppWidgetManager  _mAwm;
        private final int[]             _mAppWidgetIds;
        private final int               _mStartId;

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

        AppWidgetUpdater(int[] appWidgetIds, int startId) {
            _mAppWidgetIds = appWidgetIds;
            _mStartId = startId;
            _mAwm = AppWidgetManager.getInstance(getApplicationContext());
        }

        @Override
        protected void
        onPreRun() {
            if (DBG) P.v("Update onPreRun");
            for (int awid : _mAppWidgetIds) {
                RemoteViews rv = new RemoteViews(getApplicationContext().getPackageName(),
                                                 R.layout.appwidget_loading);
                if (DBG) P.v("Update widget - Loading : " + awid);
                _mAwm.updateAppWidget(awid, rv);
            }
        }

        @Override
        protected void
        onPostRun(Err result) {
            if (DBG) P.v("Update onPostRun");
            for (int awid : _mAppWidgetIds) {
                RemoteViews rv = new RemoteViews(getApplicationContext().getPackageName(),
                                                 R.layout.appwidget);
                long catid = AppWidgetUtils.getWidgetCategory(awid);
                if (DB.INVALID_ITEM_ID == catid)
                    continue;

                Intent intent = new Intent(Utils.getAppContext(),
                                           ViewsService.class);
                intent.setAction(AppWidgetUtils.ACTION_LIST_PENDING_INTENT);
                intent.setData(Uri.fromParts("content", String.valueOf(catid), null));
                intent.putExtra(AppWidgetUtils.MAP_KEY_CATEGORYID, catid);
                rv.setRemoteAdapter(R.id.list, intent);

                intent = new Intent(Utils.getAppContext(),
                                    ViewsService.ListPendingIntentReceiver.class);
                intent.setAction(AppWidgetUtils.ACTION_LIST_PENDING_INTENT);
                intent.putExtra(AppWidgetUtils.MAP_KEY_CATEGORYID, catid);
                PendingIntent pi = PendingIntent.getBroadcast(Utils.getAppContext(), 0, intent, 0);
                rv.setPendingIntentTemplate(R.id.list, pi);

                if (DBG) P.v("Update widget : " + awid);
                _mAwm.updateAppWidget(awid, rv);
            }
            stopSelf(_mStartId);
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
                //Thread.sleep(5000);
            } catch (Exception ignored) { }
            return Err.NO_ERR;
        }
    }


    public static void
    update(Context context, int[] appWidgetIds) {
        if (DBG) P.v("Update Widgets : Widget Ids : " + Utils.nrsToNString(appWidgetIds));
        // Build the intent to call the service
        Intent intent = new Intent(Utils.getAppContext(), UpdateService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        // Update the widgets via the service
        context.startService(intent);
    }

    public static void
    update(Context context, long[] cats) {
        if (DBG) P.v("Update Widgets : Category Ids : " + Utils.nrsToNString(cats));
        ArrayList<Integer> al = new ArrayList<Integer>(cats.length);
        for (long cat : cats) {
            int awid = AppWidgetUtils.getCategoryWidget(cat);
            if (AppWidgetUtils.INVALID_APPWIDGETID != awid)
                al.add(awid);
        }
        update(context, Utils.convertArrayIntegerToint(al.toArray(new Integer[0])));
    }

    public static void
    updateAll(Context context) {
        update(context, AppWidgetUtils.getAppWidgetIds());
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
    }

    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) P.v("onStartCommand : " + startId);
        // DO NOT anything for additional update request if there is already running update.
        int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        new AppWidgetUpdater(appWidgetIds, startId).run();
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
