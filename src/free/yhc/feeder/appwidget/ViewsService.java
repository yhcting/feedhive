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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViewsService;
import free.yhc.feeder.AppWidgetCategoryChooserActivity;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ViewsService extends RemoteViewsService implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ViewsService.class);

    private static HashMap<Integer, ViewsFactory> sViewsFactoryMap
        = new HashMap<Integer, ViewsFactory>();

    public static class ListPendingIntentReceiver extends BroadcastReceiver {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            if (DBG) P.v("ListPendingIntentReceiver : onReceive.");
            if (!AppWidgetUtils.ACTION_LIST_PENDING_INTENT.equals(intent.getAction()))
                return; // unexpected intent.

            final int awid = intent.getIntExtra(AppWidgetUtils.MAP_KEY_APPWIDGETID,
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
            Utils.getUiHandler().post(new Runnable() {
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

    public static class ButtonPendingIntentReceiver extends BroadcastReceiver {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            if (DBG) P.v("ButtonPendingIntentReceiver : onReceive.");
            if (!AppWidgetUtils.ACTION_BUTTON_PENDING_INTENT.equals(intent.getAction()))
                return; // unexpected intent.

            final int awid = intent.getIntExtra(AppWidgetUtils.MAP_KEY_APPWIDGETID,
                                                AppWidgetUtils.INVALID_APPWIDGETID);
            if (DBG) P.v("onReceive pending intent : appwidget id : " + awid);
            if (AppWidgetUtils.INVALID_APPWIDGETID == awid) {
                if (DBG) P.w("Unexpected List Pending Intent...");
                return;
            }

            Intent i = new Intent(context, AppWidgetCategoryChooserActivity.class);
            i.putExtra(AppWidgetUtils.MAP_KEY_APPWIDGETID, awid);
            i.putExtra(AppWidgetCategoryChooserActivity.KEY_CANCELABLE, true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(i);
        }
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

    public static void
    instantiateViewsFactories() {
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
        int awid = intent.getIntExtra(AppWidgetUtils.MAP_KEY_APPWIDGETID, AppWidgetUtils.INVALID_APPWIDGETID);
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
