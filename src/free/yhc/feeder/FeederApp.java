package free.yhc.feeder;

import android.app.Application;
import android.content.res.Configuration;
import free.yhc.feeder.model.DB;

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
        DB.newSession(getApplicationContext()).open();
        //UIPolicy.setAppRootDir(getApplicationContext().getFilesDir().getPath());
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
