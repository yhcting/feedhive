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

import java.util.ArrayList;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.model.Utils;
import free.yhc.feeder.model.Utils.Logger;

public class AppWidgetUtils {
    private static final boolean DBG = false;
    private static final Logger P = new Logger(AppWidgetUtils.class);

    public static final int    INVALID_APPWIDGETID = -1;
    public static final String MAP_KEY_APPWIDGETID = "appwidgetid";

    static final String ACTION_LIST_PENDING_INTENT = "feeder.intent.action.LIST_PENDING_INTENT";

    static final int    INVALID_POSITION    = -1;

    static final String MAP_KEY_CATEGORYID  = "categoryid";
    static final String MAP_KEY_ITEMID      = "itemid";
    static final String MAP_KEY_POSITION    = "position";

    private static final String APPWIDGET_PREF_FILE = "appWidgetPref";

    // AppWidget to Category Map.
    private static final SharedPreferences sMapPref
        = Utils.getAppContext().getSharedPreferences(APPWIDGET_PREF_FILE, Context.MODE_PRIVATE);
    private static final SharedPreferences.Editor sMapPrefEditor
        = sMapPref.edit();;

    static int[]
    getAppWidgetIds() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(Utils.getAppContext());
        ComponentName widget = new ComponentName(Utils.getAppContext(), Provider.class);
        return appWidgetManager.getAppWidgetIds(widget);
    }

    static void
    deleteWidgetToCategoryMap(int appWidgetId) {
        if (DBG) P.v("<DELETE> Widget->Category Map : widget:" + appWidgetId);
        sMapPrefEditor.remove(String.valueOf(appWidgetId));
        sMapPrefEditor.apply();
    }

    static long
    getWidgetCategory(int appWidgetId) {
        return sMapPref.getLong(String.valueOf(appWidgetId), DB.INVALID_ITEM_ID);
    }

    static int[]
    getCategoryWidget(long categoryid) {
        ArrayList<Integer> al = new ArrayList<Integer>();
        int[] awids = AppWidgetUtils.getAppWidgetIds();
        for (int id : awids) {
            long catid = sMapPref.getLong(String.valueOf(id), DB.INVALID_ITEM_ID);
            if (categoryid == catid)
                al.add(id);
        }
        int[] rets = Utils.convertArrayIntegerToint(al.toArray(new Integer[0]));
        if (DBG) P.v("<GET> Category->Widget Map : category:" + categoryid +
                     " -> widget:" + Utils.nrsToNString(rets));
        return rets;
    }

    public static void
    putWidgetToCategoryMap(int appWidgetId, long categoryid) {
        if (DBG) P.v("<PUT> Widget->Category Map : widget:" + appWidgetId + " -> category:" + categoryid);
        sMapPrefEditor.putLong(String.valueOf(appWidgetId), categoryid);
        sMapPrefEditor.apply();
    }


}
