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
import free.yhc.feeder.AppWidgetCategoryChooserActivity;
import free.yhc.feeder.AppWidgetMenuActivity;
import free.yhc.feeder.AppWidgetUpdateCategoryActivity;
import free.yhc.feeder.R;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.core.Environ;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.ThreadEx;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Utils;

public class UpdateService extends Service implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(UpdateService.class);

    private class AppWidgetUpdater extends ThreadEx<Err> {
        private final AppWidgetManager _mAwm;
        private final int[] _mAppWidgetIds;
        private final int _mStartId;

        private Intent
        createBaseIntent(Class<?> rcvrCls, long catid, int awid,
                         String action, boolean newTask) {
            Intent i = new Intent(Environ.getAppContext(), rcvrCls);
            // To tell "This is different intent from previous one!"
            i.setData(Uri.fromParts("content", String.valueOf(awid), null));
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, awid);
            i.putExtra(AppWidgetUtils.MAP_KEY_CATEGORYID, catid);
            if (newTask) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }

            if (null != action)
                i.setAction(action);
            return i;
        }

        AppWidgetUpdater(int[] appWidgetIds, int startId) {
            _mAppWidgetIds = appWidgetIds;
            _mStartId = startId;
            _mAwm = AppWidgetManager.getInstance(getApplicationContext());
        }

        @Override
        protected void
        onPreRun() {
            if (DBG) P.v("Enter");
            for (int awid : _mAppWidgetIds) {
                RemoteViews rv = new RemoteViews(getApplicationContext().getPackageName(),
                                                 R.layout.appwidget_loading);
                if (DBG) P.v("Loading : " + awid);
                _mAwm.updateAppWidget(awid, rv);
            }
        }

        @Override
        protected void
        onPostRun(Err result) {
            if (DBG) P.v("Enter");
            for (int awid : _mAppWidgetIds) {
                RemoteViews rv = null;
                switch (Utils.getPrefAppWidgetButtonLayout()) {
                case RIGHT:
                    rv = new RemoteViews(getApplicationContext().getPackageName(),
                                         R.layout.appwidget_right);
                    break;
                case LEFT:
                    rv = new RemoteViews(getApplicationContext().getPackageName(),
                                         R.layout.appwidget_left);
                    break;
                default:
                    Utils.eAssert(false);
                }

                long catid = AppWidgetUtils.getWidgetCategory(awid);
                if (DB.INVALID_ITEM_ID == catid)
                    continue;

                // List action pending intent
                // ==========================
                Intent intent = createBaseIntent(ViewsService.class,
                                                 catid, awid,
                                                 null, false);
                rv.setRemoteAdapter(R.id.list, intent);

                intent = createBaseIntent(ViewsService.ListPendingIntentReceiver.class,
                                          catid, awid,
                                          AppWidgetUtils.ACTION_LIST_PENDING_INTENT,
                                          false);
                PendingIntent pi = PendingIntent.getBroadcast(Environ.getAppContext(), 0, intent, 0);
                rv.setPendingIntentTemplate(R.id.list, pi);

                // Change category button action pending intent
                // ============================================
                intent = createBaseIntent(AppWidgetCategoryChooserActivity.class,
                                          catid, awid,
                                          AppWidgetUtils.ACTION_CHANGE_CATEGORY_PENDING_INTENT,
                                          true);
                intent.putExtra(AppWidgetCategoryChooserActivity.KEY_CANCELABLE, true);
                pi = PendingIntent.getActivity(Environ.getAppContext(), 0, intent, 0);
                rv.setOnClickPendingIntent(R.id.changecat, pi);

                // Move-to-top button action pending intent
                // ============================================
                intent = createBaseIntent(ViewsService.MoveToTopPendingIntentReceiver.class,
                                          catid, awid,
                                          AppWidgetUtils.ACTION_MOVE_TO_TOP_PENDING_INTENT,
                                          true);
                pi = PendingIntent.getBroadcast(Environ.getAppContext(), 0, intent, 0);
                rv.setOnClickPendingIntent(R.id.move_to_top, pi);

                // update button action pending intent
                // ============================================
                intent = createBaseIntent(AppWidgetUpdateCategoryActivity.class,
                                          catid, awid,
                                          AppWidgetUtils.ACTION_UPDATE_CATEGORY_PENDING_INTENT,
                                          true);
                pi = PendingIntent.getActivity(Environ.getAppContext(), 0, intent, 0);
                rv.setOnClickPendingIntent(R.id.update, pi);

                // more menu button action pending intent
                // ============================================
                intent = createBaseIntent(AppWidgetMenuActivity.class,
                                          catid, awid,
                                          AppWidgetUtils.ACTION_MORE_MENU_PENDING_INTENT,
                                          true);
                pi = PendingIntent.getActivity(Environ.getAppContext(), 0, intent, 0);
                rv.setOnClickPendingIntent(R.id.more_menu, pi);

                if (DBG) P.v("widget : " + awid);
                _mAwm.updateAppWidget(awid, rv);
                // Update ViewsFactory manually!
                ViewsService.updateViewsFactory(awid);
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
            if (DBG) P.v("Enter");
            stopSelf();
        }

        @Override
        protected Err
        doAsyncTask() {
            if (DBG) P.v("Enter");
            /*
            try {
                //Thread.sleep(5000);
            } catch (Exception ignored) { }
            */
            return Err.NO_ERR;
        }
    }


    public static void
    update(Context context, int[] appWidgetIds) {
        if (DBG) P.v("Widget Ids : " + Utils.nrsToNString(appWidgetIds));
        // Build the intent to call the service
        Intent intent = new Intent(Environ.getAppContext(), UpdateService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        // Update the widgets via the service
        context.startService(intent);
    }

    public static void
    update(Context context, long[] cats) {
        if (DBG) P.v("Category Ids : " + Utils.nrsToNString(cats));
        ArrayList<Integer> al = new ArrayList<>(cats.length);
        for (long cat : cats) {
            int[] awids = AppWidgetUtils.getCategoryWidget(cat);
            for (int awid : awids)
                al.add(awid);
        }

        if (al.size() > 0)
            update(context, Utils.convertArrayIntegerToint(al.toArray(new Integer[al.size()])));
    }

    @SuppressWarnings("unused")
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
        if (DBG) P.v("startId : " + startId);
        // DO NOT anything for additional update request if there is already running update.
        int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        new AppWidgetUpdater(appWidgetIds, startId).run();
        return START_NOT_STICKY;
    }

    @Override
    public void
    onDestroy() {
        if (DBG) P.v("Enter");
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
