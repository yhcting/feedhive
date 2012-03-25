package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.Calendar;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;

public class ChannelSettingActivity extends Activity {
    private static final long hourInSecs = 60 * 60;

    private long   cid = -1;
    private long   oldSchedUpdateHour = 0;

    private void
    updateSetting() {
        Spinner spinner = (Spinner)findViewById(R.id.spinner);
        long oclock = Long.parseLong(spinner.getSelectedItem().toString());
        eAssert(0 <= oclock && oclock <= 23);
        if (oldSchedUpdateHour != oclock) {
            DBPolicy.S().updateChannel_schedUpdate(cid, oclock * hourInSecs);
            ScheduledUpdater.scheduleNextUpdate(this, Calendar.getInstance());
        }
    }

    private void
    addSchedUpdateRow(final ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
            = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, hours);
        sp.setAdapter(spinnerArrayAdapter);
        parent.addView(itemv);
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cid = getIntent().getLongExtra("cid", -1);
        eAssert(cid >= 0);

        ActionBar ab = getActionBar();
        setTitle(DBPolicy.S().getChannelInfoString(cid, DB.ColumnChannel.TITLE));
        ab.setDisplayShowHomeEnabled(false);

        setContentView(R.layout.channel_setting);


        final LinearLayout schedlo = (LinearLayout)findViewById(R.id.sched_layout);
        ImageView ivAddSched = (ImageView)findViewById(R.id.add_sched);
        ivAddSched.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSchedUpdateRow(schedlo);
            }
        });

        /*
        Spinner spinner = (Spinner)findViewById(R.id.time_choose_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.time_spinner_array, R.layout.spinner_text_item);
        adapter.setDropDownViewResource(R.layout.spinner_text_item);
        spinner.setAdapter(adapter);

        // Set as stored value.
        long secs = DBPolicy.S().getChannelInfoLong(cid, DB.ColumnChannel.SCHEDUPDATETIME);
        oldSchedUpdateHour = secs / hourInSecs;
        // In current algorithm, only hour-based-number is used.
        eAssert(0 == (secs % hourInSecs));
        int pos = adapter.getPosition("" + oldSchedUpdateHour);
        spinner.setSelection(pos);
        */
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
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
    }
}
