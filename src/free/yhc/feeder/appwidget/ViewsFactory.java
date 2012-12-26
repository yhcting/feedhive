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

import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ViewsFactory implements
RemoteViewsService.RemoteViewsFactory,
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = true;
    private static final Utils.Logger P = new Utils.Logger(ViewsFactory.class);

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .appwidget.ViewsFactory ]";
    }

    @Override
    public int
    getCount() {

    }

    public long
    getItemId() {

    }

    @Override
    public RemoteViews
    getLoadingView() {

    }

    @Override
    public RemoteViews
    getViewAt(int position) {

    }

    @Override
    public int
    getViewTypeCount() {
        // See http://developer.android.com/reference/android/widget/Adapter.html
        return 1;
    }

    @Override
    public boolean
    hasStableIds() {
        return true;
    }

    @Override
    public void
    onCreate() {

    }

    @Override
    public void
    onDataSetChanged() {

    }

    @Override
    public void
    onDestroy() {

    }
}
