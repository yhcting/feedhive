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

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.HashMap;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ResourceCursorAdapter;

public class CustomResourceCursorAdapter extends ResourceCursorAdapter {
    // To speed up refreshing list and dataSetChanged in case of only few-list-item are changed.
    // (usually, only one item is changed.)
    // This SHOULD NOT used when number of list item or order of list item are changed.
    private HashMap<Long, Object> unchangedMap = new HashMap<Long, Object>();
    // Why 'changedMap' is needed event if unchangedMap exists?
    // In some cases, 'notifyDataSetChanged' is called more than once before starting getting views
    //   at adapter.
    // In this case, information of 'unchangedMap' set by previous 'notifyDataSetChanged', lost.
    // To avoid this, 'changedMap' is introduced.
    // Algorithm is
    //   - row is requested to be added to 'unchangedMap',
    //   - if this row is in 'changedMap', the row isn't added to 'unchangedMap'.
    //     (because this row is changed by previous notification.)
    private HashMap<Long, Object> changedMap = new HashMap<Long, Object>();

    CustomResourceCursorAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
    }

    /**
     *
     * @param id
     *   Value of BaseColumn._ID
     */
    public void
    addUnchanged(long id) {
        //logI(">>> Unchanged : " + id);
        unchangedMap.put(id, new Object());
    }

    /**
     *
     * @param id
     *   Value of BaseColumn._ID
     */
    public void
    addChanged(long id) {
        //logI(">>> Changed : " + id);
        changedMap.put(id, new Object());
    }

    public void
    clearChangeState() {
        //logI(">>> clear change state");
        unchangedMap.clear();
        changedMap.clear();
    }

    public void
    clearChangeState(long id) {
        //logI(">>> clear change state " + id);
        unchangedMap.remove(id);
        changedMap.remove(id);
    }

    /**
     *
     * @param id
     *   Value of BaseColumn._ID
     * @return
     */
    public boolean
    isChanged(long id) {
        // Default is 'changed'
        //   => row that is not in both 'unchangedMap' and 'changedMap', is regarded as 'changed'.
        // And changedMap has priority.
        //   => row that is in both 'unchangedMap' and 'changedMap', is regarded as 'changed'.
        if (null != unchangedMap.get(id) && null == changedMap.get(id))
            return false;
        return true;
    }

    @Override
    public void
    bindView(View view, Context context, Cursor cursor) {
        eAssert(false); // this function should not be called.
    }

}
