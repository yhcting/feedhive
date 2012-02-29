package free.yhc.feeder;

import android.app.Application;
import android.content.res.Configuration;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.RTTask;
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
        RTTask.S(); // create instance
    }

    @Override
    public void
    onLowMemory() {
        RTTask.S().cancelAll();
        super.onLowMemory();
    }

    @Override
    public void
    onTerminate() {
        RTTask.S().cancelAll();
        DB.db().close();
        super.onTerminate();
    }

}
