package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logW;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;

public class FeederActivity extends Activity {
    private Handler handler = new Handler();

    private class InitRun implements Runnable {
        @Override
        public void run() {
            UIPolicy.initialise();
            UIPolicy.cleanTempFiles();
            DB.newSession(getApplicationContext()).open();
            RTTask.S(); // create instance

            // Check predefined files
            File f = new File(UIPolicy.getPredefinedChannelsFilePath());
            if (!f.exists()) {
                // Copy default predefined channels file from Assert!
                Context context = getApplicationContext();
                AssetManager am = context.getAssets();
                InputStream is = null;
                try {
                    is = am.open("channels.xml");
                    OutputStream out = new FileOutputStream(f);
                    byte buf[]=new byte[1024 * 16];
                    int len;
                    while((len = is.read(buf)) > 0)
                        out.write(buf, 0, len);
                    out.close();
                    is.close();
                }
                catch (IOException e) {
                    logW("FeederApp Critical Error!");
                    eAssert(false);
                    return;
                }
            }
            Intent intent = new Intent(FeederActivity.this, ChannelListActivity.class);
            startActivity(intent);
            FeederActivity.this.finish();
        }
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.greeting);
    }

    @Override
    protected void
    onResume() {
        super.onResume();
        handler.post(new InitRun());
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