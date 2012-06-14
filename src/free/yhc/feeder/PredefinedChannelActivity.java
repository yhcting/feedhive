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

import java.util.Calendar;
import java.util.SortedSet;
import java.util.TreeSet;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import free.yhc.feeder.model.AssetSQLiteHelper;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.FeederException;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;


public class PredefinedChannelActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    // ========================================================================
    //
    // Constants for DB
    // These values SHOULD MATCH asset DB.
    //
    // ========================================================================
    private static final int DB_VERSION     = 3;

    private static final String DB_NAME     = "predefined_channels.db";
    private static final String DB_ASSET    = "channels.db";

    private static final String DB_TABLE = "channels";
    private static final String DB_COL_ID       = "_id";
    private static final String DB_COL_TITLE    = "title";
    private static final String DB_COL_DESC     = "description";
    private static final String DB_COL_URL      = "url";
    private static final String DB_COL_ICONURL  = "iconurl";
    // country code value defined by "ISO 3166-1 alpha-3"
    private static final String DB_COL_CCODE    = "countrycode";
    private static final String DB_COL_STATE    = "state";
    private static final String[] DB_COL_CATEGORIES = new String[] {"category0",
                                                                    "category1",
                                                                    "category2",
                                                                    "category3",
                                                                    "category4"};

    private static final String[] listCursorProj = new String[] { DB_COL_ID,
                                                                  DB_COL_TITLE,
                                                                  DB_COL_DESC,
                                                                  DB_COL_URL,
                                                                  DB_COL_ICONURL,
                                                                  DB_COL_CATEGORIES[0],
                                                                  DB_COL_CATEGORIES[1],
                                                                  DB_COL_CATEGORIES[2],
                                                                  DB_COL_CATEGORIES[3],
                                                                  DB_COL_CATEGORIES[4],
                                                                  DB_COL_STATE };
    // ========================================================================
    // Members
    // ========================================================================
    private long                categoryid = -1;
    private Handler             handler = new Handler();
    private AssetSQLiteHelper   db = null;

    // Runtime variable
    private String prevCategory = "";
    private String prevSearch   = "";

    public static class ListRow extends LinearLayout {
        String url;
        String iconurl;

        public
        ListRow(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    private class ListAdapter extends ResourceCursorAdapter {
        public
        ListAdapter(Context context, int layout, Cursor c) {
            super(context, layout, c);
        }

        @Override
        public void
        bindView(View view, final Context context, final Cursor c) {
            ListRow row = (ListRow)view;

            TextView titlev = (TextView)view.findViewById(R.id.title);
            titlev.setText(c.getString(c.getColumnIndex(DB_COL_TITLE)));
            row.url = c.getString(c.getColumnIndex(DB_COL_URL));
            row.iconurl = c.getString(c.getColumnIndex(DB_COL_ICONURL));

            // TODO
            // It's extremely weird !!!
            // "titlev.setFocusable(true)" / "titlev.setFocusable(false)" works exactly opposite
            //   way against the way it should work!!!
            //
            // Current Status
            //   "titlev.setFocusable(false)" => touch works for item.
            //   "titlev.setFocusable(true)" => touch doesn't work for item.
            //
            // What happened to this!!!
            // Need to check this!!!
            // (I think this is definitely BUG of ANDROID FRAMEWORK!)
            // => This case is same with below "else" case too.
            if (DBPolicy.S().isChannelUrlUsed(row.url)) {
                titlev.setTextColor(context.getResources().getColor(R.color.title_color_opened));
                titlev.setFocusable(true);
            } else {
                titlev.setTextColor(context.getResources().getColor(R.color.title_color_new));
                titlev.setFocusable(false);
            }
        }
    }

    private void
    addChannel(String url, String imageref) {
        eAssert(Utils.isValidValue(url));

        long cid = -1;
        try {
            cid = DBPolicy.S().insertNewChannel(categoryid, url);
        } catch (FeederException e) {
            LookAndFeel.showTextToast(this, e.getError().getMsgId());
            return;
        }

        // full update for this newly inserted channel
        BGTaskUpdateChannel task;
        if (imageref.isEmpty())
            task = new BGTaskUpdateChannel(this, new BGTaskUpdateChannel.Arg(cid));
        else
            task = new BGTaskUpdateChannel(this, new BGTaskUpdateChannel.Arg(cid, imageref));
        RTTask.S().register(cid, RTTask.Action.Update, task);
        RTTask.S().start(cid, RTTask.Action.Update);
        ScheduledUpdater.scheduleNextUpdate(this, Calendar.getInstance());
        getAdapter().notifyDataSetChanged();
    }


    // ========================================================================
    // DB Operations
    // ========================================================================
    private String[]
    getCategories() {
        SortedSet<String> ss = new TreeSet<String>();
        for (String col : DB_COL_CATEGORIES) {
            Cursor c = db.sqlite().query(true,
                                         DB_TABLE,
                                         new String[] { col },
                                         null, null,
                                         null, null,
                                         null, null);
            if (c.moveToFirst())
                do {
                    String cat = c.getString(0);
                    if (!cat.isEmpty())
                        ss.add(cat);
                } while (c.moveToNext());
            c.close();
        }

        String[] cats = new String[ss.size() + 1];
        // cats[0] is for 'all'
        cats[0] = getResources().getString(R.string.all);
        System.arraycopy(ss.toArray(new String[0]), 0, cats, 1, ss.size());
        return cats;
    }

    /**
     *
     * @param category
     *   empty : for all categories
     * @param search
     *   empty : all channels.
     */
    private void
    refreshList(String category, String search) {
        String   where = "";
        if (!category.isEmpty()) {
            int i = 0;
            while (i < DB_COL_CATEGORIES.length) {
                where += DB_COL_CATEGORIES[i] + " = " + DatabaseUtils.sqlEscapeString(category);
                if (++i < DB_COL_CATEGORIES.length)
                    where += " OR ";
            }
        }

        if (!search.isEmpty()) {
            if (!where.isEmpty())
                where = "(" + where + ") AND ";
            where += DB.buildSQLWhere(DB_COL_TITLE, search);
        }

        // implement this.
        Cursor c = db.sqlite().query(DB_TABLE,
                                     listCursorProj,
                                     where, null,
                                     null, null,
                                     DB_COL_TITLE);
        getAdapter().changeCursor(c);
    }

    private void
    requestRefreshList() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                boolean needRefresh = false;
                String category = "";
                String search = "";
                Spinner sp = (Spinner)findViewById(R.id.sp_category);
                EditText et = (EditText)findViewById(R.id.editbox);
                if (0 != sp.getSelectedItemPosition())
                    category = (String)sp.getSelectedItem();
                search = et.getText().toString();

                if (!prevCategory.equals(category)) {
                    prevCategory = category;
                    needRefresh = true;
                }
                if (!prevSearch.equals(search)) {
                    prevSearch = search;
                    needRefresh = true;
                }

                if (needRefresh)
                    refreshList(category, search);
            }
        });
    }

    // ========================================================================
    // For UI.
    // ========================================================================
    private ListAdapter
    getAdapter() {
        return (ListAdapter)((ListView)findViewById(R.id.list)).getAdapter();
    }

    private void
    setCategorySpinner(final Spinner sp) {
        final ArrayAdapter<String> adapter
            = new ArrayAdapter<String>(this,
                                       android.R.layout.simple_spinner_item,
                                       getCategories());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setSelection(0); // default is 'all'
        sp.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void
            onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                requestRefreshList();
            }
            @Override
            public void
            onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void
    setSearchEdit(final EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void
            afterTextChanged(Editable s) {
                requestRefreshList();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void
    setListView(final ListView lv) {
        Cursor c = db.sqlite().query(DB_TABLE,
                                     listCursorProj,
                                     null, null,
                                     null, null,
                                     DB_COL_TITLE);
        lv.setAdapter(new ListAdapter(this, R.layout.predefined_channel_row, c));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListRow row = (ListRow)view;
                addChannel(row.url, row.iconurl);
                setResult(RESULT_OK, null);
                finish();
            }
        });
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ PredefinedChannelActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        categoryid = this.getIntent().getLongExtra("category", -1);
        eAssert(categoryid >= 0);

        db = new AssetSQLiteHelper(this, DB_NAME, DB_ASSET, DB_VERSION);
        db.open();

        setContentView(R.layout.predefined_channel);

        setCategorySpinner((Spinner)findViewById(R.id.sp_category));
        setSearchEdit((EditText)findViewById(R.id.editbox));
        setListView((ListView)findViewById(R.id.list));
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    protected void
    onDestroy() {
        db.close();
        super.onDestroy();
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }
}
