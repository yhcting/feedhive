package free.yhc.feeder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class FeederActivity extends Activity {
    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, ChannelListActivity.class);
        startActivity(intent);
        finish();
    }


    @Override
    protected void
    onDestroy() {
        super.onDestroy();
    }
}