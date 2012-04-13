package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logW;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class FeederActivity extends Activity {
    private static volatile boolean bInitialized = false;
    private static Object           initSync = new Object();

    public class InitAsync extends AsyncTask<Integer, Integer, Integer> {
        private ProgressDialog dialog = null;

        @Override
        protected void
        onPreExecute() {
            dialog = new ProgressDialog(FeederActivity.this);
            dialog.setMessage(FeederActivity.this.getResources().getText(R.string.plz_wait));
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected void
        onPostExecute(Integer result) {
            Intent intent = new Intent(FeederActivity.this, ChannelListActivity.class);
            startActivity(intent);
            FeederActivity.this.finish();
            dialog.dismiss();
        }

        // return :
        @Override
        protected Integer
        doInBackground(Integer... params) {
            synchronized (initSync) {
                if (!bInitialized) {
                    bInitialized = true;
                    initialize();
                }
            }
            return 0;
        }
    }

    // NOTE
    // This should be called only once!!!
    private void
    initialize() {
        DB.newSession(getApplicationContext()).open();
        UIPolicy.initialise();
        UIPolicy.cleanTempFiles();
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

        UIPolicy.applyAppPreference(FeederActivity.this);
        UnexpectedExceptionHandler.S().setEnvironmentInfo(FeederActivity.this);
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new InitAsync().execute(0);
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