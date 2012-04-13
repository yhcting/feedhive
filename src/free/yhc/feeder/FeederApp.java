package free.yhc.feeder;

import android.app.Application;
import android.content.res.Configuration;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

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
        // register default customized uncaughted exception handler for error collecting.
        UnexpectedExceptionHandler.S();
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedExceptionHandler.S());
    }

    @Override
    public void
    onLowMemory() {
        super.onLowMemory();
    }
}
