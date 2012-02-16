package free.yhc.feeder;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

public class FeederActivity extends Activity {
    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.greeting);
        Intent intent = new Intent(this, ChannelListActivity.class);
        startActivityForResult(intent, 0);
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        finish();
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