package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.content.Context;
import android.os.PowerManager;

public class BGTaskDownloadToFile extends BGTask<BGTaskDownloadToFile.Arg, Object> {
    private static final String WLTag = "free.yhc.feeder.BGTaskDownloadToFile";

    private Context                 context;
    private PowerManager.WakeLock   wl;
    private volatile OutputStream   ostream  = null;
    private volatile NetLoader      loader = null;
    private Arg                     arg      = null;
    private volatile int            progress = 0;

    public static class Arg {
        String      url         = null;
        String      toFile      = null;
        String      tempFile    = null;

        public Arg(String url, String toFile, String tempFile) {
            this.url = url;
            this.toFile = toFile;
            this.tempFile = tempFile;
        }
    }

    private boolean
    cleanupStream() {
        try {
            if (null != ostream) {
                ostream.close();
                if (null != arg)
                    if (!new File(arg.tempFile).delete()) {
                        logI("**** Fail to delete temporal downloaded file!!! [" + arg.tempFile +"]\n");
                        return false;
                    }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logW(e.getMessage());
            return false;
        }
        return true;
    }

    public
    BGTaskDownloadToFile(Context context) {
        super();
        this.context = context;
        wl = ((PowerManager)context.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTag);

    }

    @Override
    public void
    registerEventListener(Object key, OnEvent onEvent) {
        super.registerEventListener(key, onEvent);
        publishProgress(progress);
    }

    @Override
    protected void
    onPreRun() {
        wl.acquire();
    }

    @Override
    protected void
    onPostRun (Err result) {
        wl.release();
    }

    @Override
    protected void
    onCancel(Object param) {
        wl.release();
    }

    @Override
    protected Err
    doBGTask(Arg arg) {
        logI("* Start background Job : DownloadToFileTask\n" +
             "    Url : " + arg.url);
        this.arg = arg;

        loader = new NetLoader();

        try {
            ostream = new FileOutputStream(arg.tempFile);
        } catch (FileNotFoundException e) {
            eAssert(false);
        }

        Err result = Err.NoErr;
        try {
            loader.download(ostream, arg.url,
                    new NetLoader.OnProgress() {
                @Override
                public void onProgress(int prog) {
                    // TODO Auto-generated method stub
                    progress = prog;
                    publishProgress(prog);
                }
            });
        } catch (FeederException e) {
            result = e.getError();
        }

        if (Err.NoErr == result)
            if (!new File(arg.tempFile).renameTo(new File(arg.toFile)))
                result = Err.IOFile;

        return result;
    }

    @Override
    public boolean
    cancel(Object param) {
        // HACK for fast-interrupt
        // Raise IOException in force
        super.cancel(param);
        if (null != loader)
            loader.cancel();
        cleanupStream();
        return true;
    }
}
