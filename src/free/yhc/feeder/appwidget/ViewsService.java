/*****************************************************************************
 *    Copyright (C) 2012, 2013 Younghyung Cho. <yhcting77@gmail.com>
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

import static free.yhc.feeder.model.Utils.eAssert;

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
import free.yhc.feeder.R;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.Environ;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ViewsService extends RemoteViewsService implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ViewsService.class);

    private static HashMap<Integer, ViewsFactory> sViewsFactoryMap
        = new HashMap<Integer, ViewsFactory>();
    private static PreferenceChangedListener sSPCListener = null; // Shared Preference Changed Listener

    public static class ListPendingIntentReceiver extends BroadcastReceiver {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            if (DBG) P.v("ListPendingIntentReceiver : onReceive.");
            if (!AppWidgetUtils.ACTION_LIST_PENDING_INTENT.equals(intent.getAction()))
                return; // unexpected intent.

            final int awid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                AppWidgetUtils.INVALID_APPWIDGETID);
            if (DBG) P.v("onReceive pending intent : appwidget id : " + awid);
            if (AppWidgetUtils.INVALID_APPWIDGETID == awid) {
                if (DBG) P.w("Unexpected List Pending Intent...");
                return;
            }

            if (DBG) P.v("List Pending Intent receives");
            final int pos = intent.getIntExtra(AppWidgetUtils.MAP_KEY_POSITION, AppWidgetUtils.INVALID_POSITION);
            final long id = intent.getLongExtra(AppWidgetUtils.MAP_KEY_ITEMID, DB.INVALID_ITEM_ID);
            if (DBG) P.v("onReceive pending intent : pos : " + pos + " / id : " + id);
            Environ.getUiHandler().post(new Runnable() {
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
            if (DBG) P.v("MoveToTopPendingIntentReceiver : onReceive.");
            if (!AppWidgetUtils.ACTION_MOVE_TO_TOP_PENDING_INTENT.equals(intent.getAction()))
                return; // unexpected intent.

            final int awid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                AppWidgetUtils.INVALID_APPWIDGETID);
            if (DBG) P.v("onReceive pending intent : appwidget id : " + awid);
            if (AppWidgetUtils.INVALID_APPWIDGETID == awid) {
                if (DBG) P.w("Unexpected AppWidgetId...");
                return;
            }
            sendUpdateAppWidgetRequest(context, new int[] { awid });
        }
    }

    private static class PreferenceChangedListener implements OnSharedPreferenceChangeListener {
        private Utils.PrefLayout mOldLayout;
        PreferenceChangedListener() {
            mOldLayout = Utils.getPrefAppWidgetButtonLayout();
        }

        @Override
        public void
        onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (!key.equals(Utils.getResText(R.string.csappwidget_btn_layout)))
                return; // ignore others

            Utils.PrefLayout newLayout = Utils.getPrefAppWidgetButtonLayout();
            if (mOldLayout == newLayout)
                return; // not changed. ignore it.
            mOldLayout = newLayout;
            Context context = Environ.getAppContext();
            int[] awids = AppWidgetManager.getInstance(context)
                                          .getAppWidgetIds(new ComponentName(context, Provider.class));
            sendUpdateAppWidgetRequest(context, awids);
        }
    }

    static void
    sendUpdateAppWidgetRequest(Context context, int[] awids) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.setClass(Environ.getAppContext(), Provider.class);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Environ.getAppContext());
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
        eAssert(DB.INVALID_ITEM_ID != catid
                && AppWidgetUtils.INVALID_APPWIDGETID != awid);
        if (DBG) P.v("onGetViewFactory : category(" + catid + ")" + " appwidget(" + awid + ")");
        ViewsFactory vf = getViewsFactory(awid);
        if (null == vf) {
            vf = new ViewsFactory(catid, awid);
            sViewsFactoryMap.put(awid, vf);
        }
        return vf;
    }
}
