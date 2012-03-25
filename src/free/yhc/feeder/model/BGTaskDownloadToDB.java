package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.ByteArrayOutputStream;

import android.content.Context;
import android.os.PowerManager;

public class BGTaskDownloadToDB extends BGTask<BGTaskDownloadToDB.Arg, Object> {
    private static final String WLTag = "free.yhc.feeder.BGTaskDownloadToDB";

    private Context                 context;
    private PowerManager.WakeLock   wl;
    private volatile NetLoader      loader = null;
    private Arg                     arg      = null;
    private volatile int            progress = 0;

    public static class Arg {
        String      url  = null;
        long        cid  = -1;
        long        id   = -1;
        DB.ColumnItem column = null;

        public Arg(String url, long cid, long id, DB.ColumnItem column) {
            this.url = url;
            this.cid = cid;
            this.id = id;
            this.column = column;
        }
    }

    private boolean
    cleanupStream() {
        return true;
    }

    public
    BGTaskDownloadToDB(Context context) {
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

        ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        Err result = loader.download(ostream, arg.url,
                new NetLoader.OnProgress() {
            @Override
            public void onProgress(int prog) {
                // TODO Auto-generated method stub
                progress = prog;
                publishProgress(prog);
            }
        });

        if (Err.NoErr == result) {
            // only 'RAWDATA' is supported now.
            eAssert(DB.ColumnItem.RAWDATA == arg.column);
            if (0 > DBPolicy.S().updateItem_data(arg.cid, arg.id, ostream.toByteArray()))
                result = Err.DBUnknown;
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
