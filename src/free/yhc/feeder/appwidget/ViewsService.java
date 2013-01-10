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

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.HashMap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViewsService;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ViewsService extends RemoteViewsService implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = true;
    private static final Utils.Logger P = new Utils.Logger(ViewsService.class);

    private static HashMap<Long, ViewsFactory> sViewsFactoryMap
        = new HashMap<Long, ViewsFactory>();

    public static class ListPendingIntentReceiver extends BroadcastReceiver {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            if (DBG) P.v("ListPendingIntentReceiver : onReceive.");
            if (!AppWidgetUtils.ACTION_LIST_PENDING_INTENT.equals(intent.getAction()))
                return; // unexpected intent.

            final long catid = intent.getLongExtra(AppWidgetUtils.MAP_KEY_CATEGORYID, DB.INVALID_ITEM_ID);
            if (DB.INVALID_ITEM_ID == catid) {
                if (DBG) P.w("Unexpected List Pending Intent...");
                return;
            }

            if (DBG) P.v("List Pending Intent receives");
            final int pos = intent.getIntExtra(AppWidgetUtils.MAP_KEY_POSITION, AppWidgetUtils.INVALID_POSITION);
            Utils.getUiHandler().post(new Runnable() {
                @Override
                public void
                run() {
                    ViewsFactory vf = getViewsFactory(catid);
                    if (null == vf) {
                        if (DBG) P.w("Unexpected List Pending Intent : category(" + catid + ")");
                        return;
                    }
                    vf.onItemClick(pos);
                }
            });
        }
    }

    static ViewsFactory
    getViewsFactory(long catid) {
        return sViewsFactoryMap.get(catid);
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
        eAssert(DB.INVALID_ITEM_ID != catid);
        if (DBG) P.v("onGetViewFactory : category(" + catid + ")");
        ViewsFactory vf = new ViewsFactory(catid);
        sViewsFactoryMap.put(catid, vf);
        return vf;
    }
}
