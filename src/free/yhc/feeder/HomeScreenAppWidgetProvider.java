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

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class HomeScreenAppWidgetProvider extends AppWidgetProvider implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = true;
    private static final Utils.Logger P = new Utils.Logger(HomeScreenAppWidgetProvider.class);

    public HomeScreenAppWidgetProvider() {
        super();
        UnexpectedExceptionHandler.get().registerModule(this);
    }

    @Override
    protected void
    finalize() throws Throwable {
        UnexpectedExceptionHandler.get().unregisterModule(this);
        super.finalize();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ HomeScreenAppWidgetProvider ]";
    }

    /* API level 16
    @Override
    public void
    onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
    }
    */

    @Override
    public void
    onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        if (DBG) P.v("onDeleted");
    }

    @Override
    public void
    onDisabled(Context context) {
        super.onDisabled(context);
        if (DBG) P.v("onDisabled");
    }

    @Override
    public void
    onEnabled(Context context) {
        super.onEnabled(context);
        if (DBG) P.v("onEnabled");
    }

    @Override
    public void
    onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (DBG) P.v("onReceive");
    }

    @Override
    public void
    onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        if (DBG) P.v("onUpdate");
        UpdateAppWidgetService.updateAppWidget(context, appWidgetIds);
    }
}
