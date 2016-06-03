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

import java.util.ArrayList;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import free.yhc.baselib.Logger;

import free.yhc.feeder.db.DB;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Util;

import static free.yhc.baselib.util.Util.convertArrayIntegerToint;

public class Provider extends AppWidgetProvider implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(Provider.class, Logger.LOGLV_DEFAULT);


    public Provider() {
        super();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .appwidget.Provider ]";
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
        for (int id : appWidgetIds)
            AppWidgetUtils.deleteWidgetToCategoryMap(id);
        if (DBG) P.v("Exit");

    }

    @Override
    public void
    onDisabled(Context context) {
        super.onDisabled(context);
        if (DBG) P.v("Exit");
    }

    @Override
    public void
    onEnabled(Context context) {
        super.onEnabled(context);
        if (DBG) P.v("Exit");
    }

    @Override
    public void
    onReceive(@NonNull Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);
        if (DBG) P.v("Exit");
    }

    @Override
    public void
    onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        if (DBG) P.v("widget ids : " + Util.nrsToNString(appWidgetIds));

        ArrayList<Integer> wl = new ArrayList<>(appWidgetIds.length);
        for (int awid : appWidgetIds) {
            long catid = AppWidgetUtils.getWidgetCategory(awid);
            if (DB.INVALID_ITEM_ID != catid)
                wl.add(awid);
        }
        UpdateService.update(context,
                             convertArrayIntegerToint(wl.toArray(new Integer[wl.size()])));
    }
}
