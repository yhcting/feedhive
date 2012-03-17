package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.logI;
import android.content.Context;
import android.os.PowerManager;

public class BGTaskUpdateChannel extends BGTask<BGTaskUpdateChannel.Arg, Object> {
    private static final String WLTag = "free.yhc.feeder.BGTaskUpdateChannel";

    private Context            context;
    private PowerManager.WakeLock wl;
    private volatile NetLoader loader = null;
    private Arg                arg    = null;

    public static class Arg {
        boolean bFirstUpdate = false;
        long    cid     = -1;
        long    catid   = -1; // category id
        String  url     = "";

        // For update
        public Arg(long cid) {
            this.cid = cid;
        }

        // For initial load
        public Arg(long catid, String url) {
            bFirstUpdate = true;
            this.catid = catid;
            this.url = url;
        }
    }

    public
    BGTaskUpdateChannel(Context context) {
        super();
        this.context = context;
        wl = ((PowerManager)context.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTag);
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
        this.arg = arg;
        try {
            loader = new NetLoader();
            if (arg.bFirstUpdate)
                return loader.initialLoad(arg.catid, arg.url, null);
            else
                return loader.updateLoad(arg.cid, false);
        } catch (FeederException e) {
            if (arg.bFirstUpdate)
                logI("BGTaskUpdateChannel : Loading [" + arg.url + "] : interrupted!");
            else
                logI("BGTaskUpdateChannel : Updating [" + arg.cid + "] : interrupted!");
            return e.getError();
        }
    }

    @Override
    public boolean
    cancel(Object param) {
        // I may misunderstand that canceling background task may corrupt DB
        //   by interrupting in the middle of transaction.
        // But java thread doesn't interrupt it's executing.
        // So, I don't worry about this (different from C.)
        super.cancel(param); // cancel thread
        if (null != loader)
            loader.cancel();     // This is HACK for fast-interrupt.
        return true;
    }
}
