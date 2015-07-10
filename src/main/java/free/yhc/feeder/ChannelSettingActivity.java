/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

import static free.yhc.feeder.core.Utils.eAssert;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.Feed;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Utils;

public class ChannelSettingActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Utils.Logger P = new Utils.Logger(ChannelSettingActivity.class);

    // match string-array 'strarr_updatemode_setting'
    private static final int SPPOS_UPDATEMODE_NORMAL = 0;
    private static final int SPPOS_UPDATEMODE_DOWNLOAD = 1;

    // match string-array 'strarr_browser_setting'
    private static final int SPPOS_BROWSER_IN = 0;
    private static final int SPPOS_BROWSER_EX = 1;

    private final DBPolicy mDbp = DBPolicy.get();
    private long mCid = -1;

    private void
    updateSchedUpdateSetting() {
        String oldSchedUpdate = mDbp.getChannelInfoString(mCid, ColumnChannel.SCHEDUPDATETIME);

        LinearLayout schedlo = (LinearLayout)findViewById(R.id.sched_layout);
        // sodhs : Seconds Of Day HashSet
        HashSet<Long> sodhs = new HashSet<>();
        int i = 0;
        while (i < schedlo.getChildCount()) {
            View v = schedlo.getChildAt(i);
            Spinner sp = (Spinner)v.findViewById(R.id.spinner);
            // hod : Hour Of Day
            long hod = Long.parseLong((String)sp.getSelectedItem());
            sodhs.add(hod * 60 * 60);
            i++;
        }

        long[] sods = Utils.convertArrayLongTolong(sodhs.toArray(new Long[sodhs.size()]));
        Arrays.sort(sods);
        if (!Utils.nrsToNString(sods).equals(oldSchedUpdate)) {
            mDbp.updateChannel_schedUpdate(mCid, sods);
            ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
        }
    }

    private void
    updateUpdateModeSetting() {
        Spinner sp = (Spinner)findViewById(R.id.sp_updatemode);
        long old_updmode = mDbp.getChannelInfoLong(mCid, ColumnChannel.UPDATEMODE);
        long updmode = old_updmode;
        switch (sp.getSelectedItemPosition()) {
        case SPPOS_UPDATEMODE_NORMAL:
            updmode = Utils.bitSet(updmode, Feed.Channel.FUPD_LINK, Feed.Channel.MUPD);
            break;
        case SPPOS_UPDATEMODE_DOWNLOAD:
            updmode = Utils.bitSet(updmode, Feed.Channel.FUPD_DN, Feed.Channel.MUPD);
            break;
        default:
            eAssert(false);
        }

        if (old_updmode != updmode)
            mDbp.updateChannel(mCid, ColumnChannel.UPDATEMODE, updmode);
    }

    private void
    updateBrowserSetting() {
        long old_action = mDbp.getChannelInfoLong(mCid, ColumnChannel.ACTION);
        long action = old_action;

        if (Feed.Channel.FACT_TYPE_DYNAMIC != Feed.Channel.getActType(action))
            // In this case, spinner itself is 'GONE'. So, nothing to do.
            return; // nothing to do.

        Spinner sp = (Spinner)findViewById(R.id.sp_browser);
        // clear bit for action program.
        switch (sp.getSelectedItemPosition()) {
        case SPPOS_BROWSER_IN:
            action = Utils.bitSet(action, Feed.Channel.FACT_PROG_IN, Feed.Channel.MACT_PROG);
            break;
        case SPPOS_BROWSER_EX:
            action = Utils.bitSet(action, Feed.Channel.FACT_PROG_EX, Feed.Channel.MACT_PROG);
            break;
        default:
            eAssert(false);
        }

        if (old_action != action)
            mDbp.updateChannel(mCid, ColumnChannel.ACTION, action);
    }

    private void
    updateSetting() {
        updateSchedUpdateSetting();
        updateUpdateModeSetting();
        updateBrowserSetting();
    }

    private void
    addSchedUpdateRow(final ViewGroup parent, int hourOfDay) {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams")
        final View itemv = inflater.inflate(R.layout.channel_setting_sched, null);
        ImageView ivClose = (ImageView)itemv.findViewById(R.id.imgbtn_close);
        ivClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parent.removeView(itemv);
            }
        });
        Spinner sp = (Spinner)itemv.findViewById(R.id.spinner);
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++)
            hours[i] = "" + i;

        ArrayAdapter<String> spinnerArrayAdapter
            = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, hours);
        sp.setAdapter(spinnerArrayAdapter);
        // hourOfDay is same with position at spinner
        sp.setSelection(hourOfDay);
        parent.addView(itemv);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ChannelSettingActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);

        mCid = getIntent().getLongExtra("cid", -1);
        eAssert(mCid >= 0);

        ActionBar ab = getActionBar();
        eAssert(null != ab);
        setTitle(mDbp.getChannelInfoString(mCid, ColumnChannel.TITLE));
        ab.setDisplayShowHomeEnabled(false);

        setContentView(R.layout.channel_setting);

        // Setup "Scheduled Update"
        final LinearLayout schedlo = (LinearLayout)findViewById(R.id.sched_layout);
        ImageView ivAddSched = (ImageView)findViewById(R.id.add_sched);
        ivAddSched.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSchedUpdateRow(schedlo, 0);
            }
        });

        String schedtime = mDbp.getChannelInfoString(mCid, ColumnChannel.SCHEDUPDATETIME);
        long[] secs = Utils.nStringToNrs(schedtime);
        for (long s : secs) {
            eAssert(0 <= s && s < Utils.DAY_IN_SEC);
            addSchedUpdateRow(schedlo, (int)(s / Utils.HOUR_IN_SEC));
        }

        // Setup "Update Type"
        Spinner sp = (Spinner)findViewById(R.id.sp_updatemode);
        ArrayAdapter<CharSequence> spadapter = ArrayAdapter.createFromResource(
                    this, R.array.strarr_updatemode_setting, android.R.layout.simple_spinner_item);
        spadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(spadapter);

        long uptype = mDbp.getChannelInfoLong(mCid, ColumnChannel.UPDATEMODE);
        if (Feed.Channel.isUpdLink(uptype))
            sp.setSelection(SPPOS_UPDATEMODE_NORMAL); // 'Normal' is position 0
        else if (Feed.Channel.isUpdDn(uptype))
            sp.setSelection(SPPOS_UPDATEMODE_DOWNLOAD); // 'Download' is position 1
        else
            eAssert(false);

        // Setup Browser
        long action = mDbp.getChannelInfoLong(mCid, ColumnChannel.ACTION);
        long actionType = Feed.Channel.getActType(action);
        if (actionType == Feed.Channel.FACT_TYPE_DYNAMIC) {
            sp = (Spinner)findViewById(R.id.sp_browser);
            spadapter = ArrayAdapter.createFromResource(
                        this, R.array.strarr_browser_setting, android.R.layout.simple_spinner_item);
            spadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sp.setAdapter(spadapter);

            if (Feed.Channel.isActProgIn(action))
                sp.setSelection(SPPOS_BROWSER_IN); // 'internal browser' is position 0
            else if (Feed.Channel.isActProgEx(action))
                sp.setSelection(SPPOS_BROWSER_EX); // 'external browser' is position 1
            else
                eAssert(false);
        } else if (Feed.Channel.FACT_TYPE_EMBEDDED_MEDIA == actionType) {
            findViewById(R.id.browser_layout).setVisibility(View.GONE);
        } else {
            // Action is unknown.
            // This case can be reached when there is no items in the channel. (Empty channel)
            // (Default action is Feed.FINVALID)
            // In this case, show minimal setting options only
            findViewById(R.id.browser_layout).setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode,
                             @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            updateSetting();

        return super.onKeyDown(keyCode, event);
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
        super.onDestroy();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }
}
