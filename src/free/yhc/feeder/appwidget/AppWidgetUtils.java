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

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.model.Environ;
import free.yhc.feeder.model.Utils;
import free.yhc.feeder.model.Utils.Logger;

public class AppWidgetUtils {
    private static final boolean DBG = false;
    private static final Logger P = new Logger(AppWidgetUtils.class);

    public static final int    INVALID_APPWIDGETID = -1;
    public static final String MAP_KEY_CATEGORYID  = "categoryid";

    static final String ACTION_LIST_PENDING_INTENT            = "feeder.intent.action.LIST_PENDING_INTENT";
    static final String ACTION_CHANGE_CATEGORY_PENDING_INTENT = "feeder.intent.action.CHANGE_CATEGORY_PENDING_INTENT";
    static final String ACTION_MOVE_TO_TOP_PENDING_INTENT     = "feeder.intent.action.MOVE_TO_TOP_PENDING_INTENT";
    static final String ACTION_UPDATE_CATEGORY_PENDING_INTENT = "feeder.intent.action.UPDATE_CATEGORY_PENDING_INTENT";
    static final String ACTION_MORE_MENU_PENDING_INTENT       = "feeder.intent.action.MORE_MENU_PENDING_INTENT";

    static final int    INVALID_POSITION    = -1;

    static final String MAP_KEY_ITEMID      = "itemid";
    static final String MAP_KEY_POSITION    = "position";

    private static final String APPWIDGET_PREF_FILE = "appWidgetPref";

    // AppWidget to Category Map.
    private static final SharedPreferences sMapPref
        = Environ.getAppContext().getSharedPreferences(APPWIDGET_PREF_FILE, Context.MODE_PRIVATE);
    private static final SharedPreferences.Editor sMapPrefEditor
        = sMapPref.edit();;

    static int[]
    getAppWidgetIds() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(Environ.getAppContext());
        ComponentName widget = new ComponentName(Environ.getAppContext(), Provider.class);
        return appWidgetManager.getAppWidgetIds(widget);
    }

    static void
    deleteWidgetToCategoryMap(int appWidgetId) {
        if (DBG) P.v("widget : " + appWidgetId);
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
        if (DBG) P.v("category:" + categoryid +
                     " -> widget:" + Utils.nrsToNString(rets));
        return rets;
    }

    public static void
    putWidgetToCategoryMap(int appWidgetId, long categoryid) {
        if (DBG) P.v("widget:" + appWidgetId + " -> category:" + categoryid);
        sMapPrefEditor.putLong(String.valueOf(appWidgetId), categoryid);
        sMapPrefEditor.apply();
    }
}
