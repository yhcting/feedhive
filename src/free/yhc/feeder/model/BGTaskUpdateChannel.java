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
        long    cid        = -1;
        int     flag       = NetLoader.UPD_DEFAULT;
        String  customIconref = null;

        public Arg(long cid) {
            this.cid = cid;
        }

        public Arg(long cid, int flag) {
            this.cid = cid;
            this.flag = flag;
        }

        public Arg(long cid, int flag, String customIconref) {
            this.cid = cid;
            this.flag = flag;
            this.customIconref = customIconref;
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
            if (null == arg.customIconref)
                loader.updateLoad(arg.cid, arg.flag);
            else
                loader.updateLoad(arg.cid, arg.flag, arg.customIconref);
        } catch (FeederException e) {
            logI("BGTaskUpdateChannel : Updating [" + arg.cid + "] : interrupted!");
            return e.getError();
        }
        return Err.NoErr;
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
