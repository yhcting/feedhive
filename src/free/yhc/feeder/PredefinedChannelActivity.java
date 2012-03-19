package free.yhc.feeder;
import static free.yhc.feeder.model.Utils.eAssert;

import java.util.Calendar;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.RTTask;


public class PredefinedChannelActivity extends Activity {
    private ListView    list;

    private void
    addChannel(String url, String imageref) {
        eAssert(url != null);
        long cid = DBPolicy.S().insertNewChannel(DBPolicy.S().getDefaultCategoryId(), url);
        if (cid < 0) {
            LookAndFeel.showTextToast(this, R.string.warn_add_channel);
            return;
        }
        // full update for this newly inserted channel
        BGTaskUpdateChannel task = new BGTaskUpdateChannel(this);
        RTTask.S().registerUpdate(cid, task);
        if (imageref.isEmpty())
            task.start(new BGTaskUpdateChannel.Arg(cid, true));
        else
            task.start(new BGTaskUpdateChannel.Arg(cid, imageref));
        ScheduledUpdater.scheduleNextUpdate(this, Calendar.getInstance());
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.predefined_channel);
        list = ((ListView) findViewById(R.id.list));
        eAssert(null != list);
        PredefinedValues.Category[] cats = PredefinedValues.getPredefinedChannels();
        int count = 0;
        for (PredefinedValues.Category cat : cats)
            count += cat.channels.length;

        PredefinedValues.Channel[] chs = new PredefinedValues.Channel[count];
        count = 0;
        for (PredefinedValues.Category cat : cats)
            for (PredefinedValues.Channel ch : cat.channels)
                chs[count++] = ch;

        final LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        list.setAdapter(new ArrayAdapter<PredefinedValues.Channel>(this, R.id.text, chs) {
            @Override
            public View
            getView(int position, View convertView, ViewGroup parent) {
                View row;

                if (null == convertView)
                    row = inflater.inflate(R.layout.predefined_channel_row, null);
                else
                    row = convertView;

                TextView tv = (TextView)row.findViewById(R.id.name);
                tv.setText(getItem(position).name);
                return row;
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PredefinedValues.Channel ch = (PredefinedValues.Channel)list.getAdapter().getItem(position);
                addChannel(ch.url, ch.imageref);
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
        super.onDestroy();
    }
}
