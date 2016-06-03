/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

package free.yhc.feeder;

import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.AssetSQLiteHelper;
import free.yhc.feeder.core.UnexpectedExceptionHandler;


public class PredefinedChannelActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(PredefinedChannelActivity.class, Logger.LOGLV_DEFAULT);

    public static final String KEY_URLS = "urls";
    public static final String KEY_ICONURLS = "iconurls";

    // ========================================================================
    //
    // Constants for DB
    // These values SHOULD MATCH asset DB.
    //
    // ========================================================================
    private static final int DB_VERSION = 13;

    private static final String DB_NAME = "predefined_channels.db";
    private static final String DB_ASSET = "channels.db";

    private static final String DB_TABLE = "channels";
    private static final String DB_COL_ID = "_id";
    private static final String DB_COL_TITLE = "title";
    private static final String DB_COL_DESC = "description";
    private static final String DB_COL_URL = "url";
    private static final String DB_COL_ICONURL = "iconurl";
    // country code value defined by "ISO 3166-1 alpha-3"
    @SuppressWarnings("unused")
    private static final String DB_COL_CCODE = "countrycode";
    private static final String DB_COL_STATE = "state";
    private static final String[] sDbColCategories = new String[] {"category0",
                                                                   "category1",
                                                                   "category2",
                                                                   "category3",
                                                                   "category4"};

    private static final String[] sListCursorProj = new String[] { DB_COL_ID,
                                                                   DB_COL_TITLE,
                                                                   DB_COL_DESC,
                                                                   DB_COL_URL,
                                                                   DB_COL_ICONURL,
                                                                   sDbColCategories[0],
                                                                   sDbColCategories[1],
                                                                   sDbColCategories[2],
                                                                   sDbColCategories[3],
                                                                   sDbColCategories[4],
                                                                   DB_COL_STATE };
    // ========================================================================
    // Members
    // ========================================================================
    private final DBPolicy mDbp = DBPolicy.get();

    // Variables set only once.
    private AssetSQLiteHelper mAssetDB = null;
    private HashMap<Long, ChanInfo> mChanMap = new HashMap<>();

    // Runtime variable
    private String prevCategory = "";
    private String prevSearch = "";

    public static class ListRow extends LinearLayout {
        ChanInfo chaninfo = new ChanInfo();
        public ListRow(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    static class ChanInfo {
        String url = null;
        String iconurl = null;
        ChanInfo() { }
        ChanInfo(ChanInfo other) {
            url = other.url;
            iconurl = other.iconurl;
        }
    }

    private class ListAdapter extends ResourceCursorAdapter {
        public
        ListAdapter(Context context, int layout, Cursor c) {
            super(context, layout, c, true);
        }

        @Override
        public void
        bindView(View view, final Context context, final Cursor c) {
            ListRow row = (ListRow)view;

            TextView titlev = (TextView)view.findViewById(R.id.title);
            CheckBox checkv = (CheckBox)view.findViewById(R.id.checkbtn);

            titlev.setText(c.getString(c.getColumnIndex(DB_COL_TITLE)));
            row.chaninfo.url = c.getString(c.getColumnIndex(DB_COL_URL));
            row.chaninfo.iconurl = c.getString(c.getColumnIndex(DB_COL_ICONURL));


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
            if (mDbp.isDuplicatedChannelUrl(row.chaninfo.url)) {
                checkv.setVisibility(View.GONE);
                titlev.setTextColor(context.getResources().getColor(R.color.title_color_opened));
                titlev.setFocusable(true);
            } else {
                checkv.setVisibility(View.VISIBLE);
                long id = c.getLong(c.getColumnIndex(DB_COL_ID));
                checkv.setChecked(mChanMap.containsKey(id));

                titlev.setTextColor(context.getResources().getColor(R.color.title_color_new));
                titlev.setFocusable(false);
            }
        }
    }

    // ========================================================================
    // DB Operations
    // ========================================================================
    private String[]
    getCategories() {
        SortedSet<String> ss = new TreeSet<>();
        for (String col : sDbColCategories) {
            Cursor c = mAssetDB.sqlite().query(true,
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
        System.arraycopy(ss.toArray(new String[ss.size()]), 0, cats, 1, ss.size());
        return cats;
    }

    private Cursor
    query(String category, String search) {
        String   where = "";
        if (!category.isEmpty()) {
            int i = 0;
            while (i < sDbColCategories.length) {
                where += sDbColCategories[i] + " = " + DatabaseUtils.sqlEscapeString(category);
                if (++i < sDbColCategories.length)
                    where += " OR ";
            }
        }

        if (!search.isEmpty()) {
            if (!where.isEmpty())
                where = "(" + where + ") AND ";
            where += DB.buildSQLWhere(DB_COL_TITLE, search);
        }

        // implement this.
        return mAssetDB.sqlite().query(DB_TABLE,
                                       sListCursorProj,
                                       where, null,
                                       null, null,
                                       DB_COL_TITLE);
    }

    /**
     *
     * @param category empty : for all categories
     * @param search empty : all channels.
     */
    private void
    refreshList(String category, String search) {
        getAdapter().changeCursor(query(category, search));
    }

    // ========================================================================
    //
    // ========================================================================
    private void
    requestRefreshList() {
        AppEnv.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                boolean needRefresh = false;
                String category = "";
                String search;
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

    private void
    onCheck(long id, ChanInfo chaninfo, boolean isChecked) {
        if (isChecked)
            // NOTE
            // passed 'chaninfo' is reference of ChanInfo located at ListRow.
            // So, as list is scrolled, value is changed accordingly.
            // This is definitely NOT expected.
            // We need to fixed ChanInfo value here.
            // So, put cloned(deep-copied) value to hashmap.
            mChanMap.put(id, new ChanInfo(chaninfo));
        else
            mChanMap.remove(id);
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
            = new ArrayAdapter<>(this,
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
            public void
            beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void
            onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void
    setListView(final ListView lv) {
        // closing cursor is Adapter's responsibility.
        @SuppressLint("Recycle")
        Cursor c = mAssetDB.sqlite().query(DB_TABLE,
                                           sListCursorProj,
                                           null, null,
                                           null, null,
                                           DB_COL_TITLE);
        lv.setAdapter(new ListAdapter(this, R.layout.predefined_channel_row, c));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListRow row = (ListRow) view;
                CheckBox cb = (CheckBox) view.findViewById(R.id.checkbtn);
                cb.setChecked(!cb.isChecked());
                onCheck(id, row.chaninfo, cb.isChecked());
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
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);

        mAssetDB = new AssetSQLiteHelper(DB_NAME, DB_ASSET, DB_VERSION);
        mAssetDB.open();

        setContentView(R.layout.predefined_channel);

        setCategorySpinner((Spinner)findViewById(R.id.sp_category));
        setSearchEdit((EditText)findViewById(R.id.editbox));
        setListView((ListView)findViewById(R.id.list));

        (findViewById(R.id.append)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                Intent intent = new Intent();
                //noinspection ToArrayCallWithZeroLengthArrayArgument
                ChanInfo[] cis = mChanMap.values().toArray(new ChanInfo[0]);
                String[] urls = new String[cis.length];
                String[] iconurls = new String[cis.length];
                for (int i = 0 ; i < cis.length; i++) {
                    urls[i] = cis[i].url;
                    iconurls[i] = cis[i].iconurl;
                }
                intent.putExtra(KEY_URLS, urls);
                intent.putExtra(KEY_ICONURLS, iconurls);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
        (findViewById(R.id.cancel)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
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
        mAssetDB.close();
        super.onDestroy();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }
}
