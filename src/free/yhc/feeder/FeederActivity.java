package free.yhc.feeder;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;

public class FeederActivity extends Activity {
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
            FeederApp.initialize(FeederActivity.this);
            return 0;
        }
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