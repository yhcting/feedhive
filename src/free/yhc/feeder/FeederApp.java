package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logW;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class FeederApp extends Application {
    private static volatile boolean bInitialized = false;
    private static Object           initLock = new Object();

    // NOTE 1
    // This should be called only once!!!
    //
    // NOTE 2
    // Here is interesting observation.
    // Some functions require 'context'.
    // But, in some cases, application context returned by 'getApplicationContext()'
    //   issues unexpected exception.
    // Interesting point is, context from 'Activity' instance doens't have above issue.
    // So, even if this 'initialize' function is member of FeederApp,
    //   this function should be called with 'Activity' context.
    public static void
    initialize(Context context) {
        synchronized (initLock) {
            if (bInitialized)
                return;
            bInitialized = true;
        }

        DB.newSession(context).open();
        UIPolicy.initialise();
        UIPolicy.cleanTempFiles();
        RTTask.S(); // create instance

        // Check predefined files
        File f = new File(UIPolicy.getPredefinedChannelsFilePath());
        if (!f.exists()) {
            // Copy default predefined channels file from Assert!
            AssetManager am = context.getAssets();
            InputStream is = null;
            try {
                is = am.open("channels.xml");
                OutputStream out = new FileOutputStream(f);
                byte buf[]=new byte[1024 * 16];
                int len;
                while((len = is.read(buf)) > 0)
                    out.write(buf, 0, len);
                out.close();
                is.close();
            }
            catch (IOException e) {
                logW("FeederApp Critical Error!");
                eAssert(false);
                return;
            }
        }

        UIPolicy.applyAppPreference(context);
        UnexpectedExceptionHandler.S().setEnvironmentInfo(context);
    }

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
