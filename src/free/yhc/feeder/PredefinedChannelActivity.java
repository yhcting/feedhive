package free.yhc.feeder;
import static free.yhc.feeder.model.Utils.eAssert;
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
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.FeederException;
import free.yhc.feeder.model.Utils;


public class PredefinedChannelActivity extends Activity {
    private ListView    list;

    class SpinAsyncEventHandler implements SpinAsyncTask.OnEvent {
        @Override
        public Err
        onDoWork(SpinAsyncTask task, Object... objs) {
            Err err = Err.NoErr;
            String imageref = (String)objs[2];
            long[] cid = new long[1];
            try {
                err = task.initialLoad(cid, objs[0], objs[1]);
            } catch (InterruptedException e) {
                return Err.Interrupted;
            }

            if (Err.NoErr != err)
                return err;

            if (imageref.isEmpty())
                return Err.NoErr; // Ignore icon
            // TODO
            //   do something to update imageblob from imageref.
            // Make url string from file path
            byte[] imageData = null;
            try {
                imageData = Utils.getDecodedImageData(imageref);
            } catch (FeederException e) {
                return e.getError();
            }

            if (null == imageData) {
                return Err.CodecDecode;
            }

            try {
                DBPolicy.get().updateChannel_image(cid[0], imageData);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return Err.DBUnknown;
            }
            return err;
        }

        @Override
        public void
        onPostExecute(SpinAsyncTask task, Err result) {
            if (Err.NoErr != result)
                LookAndFeel.showTextToast(PredefinedChannelActivity.this, result.getMsgId());
        }
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
                new SpinAsyncTask(PredefinedChannelActivity.this, new SpinAsyncEventHandler(), R.string.load_progress)
                    .execute(DBPolicy.get().getDefaultCategoryId(),
                            ch.url, ch.imageref);
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
        // FIXME : '0' is temporal value.
        setResult(0);
    }
}
