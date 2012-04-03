package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.logI;

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
    private volatile long           progress = 0;

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

        Err result = Err.NoErr;
        try {
            loader.downloadToFile(arg.url,
                    arg.tempFile,
                    arg.toFile,
                    new NetLoader.OnProgress() {
                @Override
                public void onProgress(NetLoader loader, long prog) {
                    // TODO Auto-generated method stub
                    progress = prog;
                    publishProgress(prog);
                }
            });
        } catch (FeederException e) {
            result = e.getError();
        }

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
