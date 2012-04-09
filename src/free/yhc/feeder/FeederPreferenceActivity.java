package free.yhc.feeder;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;
import free.yhc.feeder.model.UIPolicy;

public class FeederPreferenceActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            UIPolicy.applyAppPreference(this);

        return super.onKeyDown(keyCode, event);
    }
}
