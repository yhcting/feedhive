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

        // Check predefined files
        File f = new File(UIPolicy.getPredefinedChannelsFilePath());
        if (!f.exists()) {
            // Copy default predefined channels file from Assert!
            Context context = getApplicationContext();
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
