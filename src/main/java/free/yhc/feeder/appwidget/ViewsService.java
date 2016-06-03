/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2016
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

import java.util.HashMap;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.widget.RemoteViewsService;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.feeder.R;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

public class ViewsService extends RemoteViewsService implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ViewsService.class, Logger.LOGLV_DEFAULT);

    private static HashMap<Integer, ViewsFactory> sViewsFactoryMap
        = new HashMap<>();
    @SuppressWarnings("FieldCanBeLocal")
    private static PreferenceChangedListener sSPCListener = null; // Shared Preference Changed Listener

    public static class ListPendingIntentReceiver extends BroadcastReceiver {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            if (DBG) P.v("Enter");
            if (!AppWidgetUtils.ACTION_LIST_PENDING_INTENT.equals(intent.getAction()))
                return; // unexpected intent.

            final int awid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                AppWidgetUtils.INVALID_APPWIDGETID);
            if (DBG) P.v("pending intent : appwidget id : " + awid);
            if (AppWidgetUtils.INVALID_APPWIDGETID == awid) {
                if (DBG) P.w("Unexpected List Pending Intent...");
                return;
            }

            if (DBG) P.v("List Pending Intent receives");
            final int pos = intent.getIntExtra(AppWidgetUtils.MAP_KEY_POSITION, AppWidgetUtils.INVALID_POSITION);
            final long id = intent.getLongExtra(AppWidgetUtils.MAP_KEY_ITEMID, DB.INVALID_ITEM_ID);
            if (DBG) P.v("pending intent : pos : " + pos + " / id : " + id);
            AppEnv.getUiHandler().post(new Runnable() {
                @Override
                public void
                run() {
                    ViewsFactory vf = getViewsFactory(awid);
                    if (null == vf) {
                        if (DBG) P.w("Unexpected List Pending Intent : appWidget(" + awid + ")");
                        return;
                    }
                    vf.onItemClick(pos, id);
                }
            });
        }
    }

    public static class MoveToTopPendingIntentReceiver extends BroadcastReceiver {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            if (DBG) P.v("Enter");
            if (!AppWidgetUtils.ACTION_MOVE_TO_TOP_PENDING_INTENT.equals(intent.getAction()))
                return; // unexpected intent.

            final int awid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                AppWidgetUtils.INVALID_APPWIDGETID);
            if (DBG) P.v("pending intent : appwidget id : " + awid);
            if (AppWidgetUtils.INVALID_APPWIDGETID == awid) {
                if (DBG) P.w("Unexpected AppWidgetId...");
                return;
            }
            sendUpdateAppWidgetRequest(context, new int[] { awid });
        }
    }

    private static class PreferenceChangedListener implements OnSharedPreferenceChangeListener {
        private Util.PrefLayout mOldLayout;
        PreferenceChangedListener() {
            mOldLayout = Util.getPrefAppWidgetButtonLayout();
        }

        @Override
        public void
        onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (!key.equals(Util.getResString(R.string.csappwidget_btn_layout)))
                return; // ignore others

            Util.PrefLayout newLayout = Util.getPrefAppWidgetButtonLayout();
            if (mOldLayout == newLayout)
                return; // not changed. ignore it.
            mOldLayout = newLayout;
            Context context = AppEnv.getAppContext();
            int[] awids = AppWidgetManager.getInstance(context)
                                          .getAppWidgetIds(new ComponentName(context, Provider.class));
            sendUpdateAppWidgetRequest(context, awids);
        }
    }

    static void
    sendUpdateAppWidgetRequest(Context context, int[] awids) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.setClass(AppEnv.getAppContext(), Provider.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, awids);
        context.sendBroadcast(intent);
    }

    static ViewsFactory
    getViewsFactory(int appWidgetId) {
        ViewsFactory vf = sViewsFactoryMap.get(appWidgetId);
        long catid = AppWidgetUtils.getWidgetCategory(appWidgetId);
        try {
            if (DB.INVALID_ITEM_ID == catid)
                throw new Exception();

            if (!DBPolicy.get().isValidCategoryId(catid)) {
                AppWidgetUtils.deleteWidgetToCategoryMap(appWidgetId);
                throw new Exception();
            }
        } catch (Exception e) {
            if (null != vf)
                sViewsFactoryMap.remove(appWidgetId);
            return null;
        }
        if (null == vf)
            vf = new ViewsFactory(catid, appWidgetId);
        else
            vf.setCategory(catid);
        sViewsFactoryMap.put(appWidgetId, vf);
        return vf;
    }

    static void
    updateViewsFactory(int appWidgetId) {
        // Calling getViewsFactory is enough to update ViewsFactory.
        getViewsFactory(appWidgetId);
    }

    /**
     * Called only once at FeederApp.onCreate
     */
    public static void
    instantiateViewsFactories() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(AppEnv.getAppContext());
        sSPCListener = new PreferenceChangedListener();
        // NOTE
        // SharedPreference uses 'WeekHashMap'.
        // So, listener SHOULD have EXPLICIT variable keeping it's reference to prevent it from GC(Garbage Collection).
        prefs.registerOnSharedPreferenceChangeListener(sSPCListener);

        for (int awid : AppWidgetUtils.getAppWidgetIds())
            getViewsFactory(awid);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .appwidget.ViewsService ]";
    }

    @Override
    public RemoteViewsFactory
    onGetViewFactory(Intent intent) {
        long catid = intent.getLongExtra(AppWidgetUtils.MAP_KEY_CATEGORYID, DB.INVALID_ITEM_ID);
        int awid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetUtils.INVALID_APPWIDGETID);
        P.bug(DB.INVALID_ITEM_ID != catid
              && AppWidgetUtils.INVALID_APPWIDGETID != awid);
        if (DBG) P.v("category(" + catid + ")" + " appwidget(" + awid + ")");
        ViewsFactory vf = getViewsFactory(awid);
        if (null == vf) {
            vf = new ViewsFactory(catid, awid);
            sViewsFactoryMap.put(awid, vf);
        }
        return vf;
    }
}
