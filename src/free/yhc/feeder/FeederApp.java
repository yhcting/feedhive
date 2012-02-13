package free.yhc.feeder;

import android.app.Application;
import android.content.res.Configuration;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.UIPolicy;

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
        UIPolicy.makeAppRootDir();
        DB.newSession(getApplicationContext()).open();
    }

    @Override
    public void
    onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void
    onTerminate() {
        DB.db().close();

        super.onTerminate();
    }

}
