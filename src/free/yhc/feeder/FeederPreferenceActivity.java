package free.yhc.feeder;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class FeederPreferenceActivity extends PreferenceActivity implements
UnexpectedExceptionHandler.TrackedModule {
    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ChannelSettingActivity ]";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.S().registerModule(this);
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            UIPolicy.applyAppPreference(this);

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }
}
