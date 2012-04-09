package free.yhc.feeder;

import android.app.Application;
import android.content.res.Configuration;

public class FeederApp extends Application {
    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void
    onCreate() {
        super.onCreate();
    }

    @Override
    public void
    onLowMemory() {
        super.onLowMemory();
    }
}
