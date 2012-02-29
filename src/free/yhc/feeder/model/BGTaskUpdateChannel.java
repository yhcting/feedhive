package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

public class BGTaskUpdateChannel extends BGTask<Object, BGTaskUpdateChannel.Arg, Object> {
    private volatile NetLoader loader = null;
    private Arg                arg    = null;

    public static class Arg {
        boolean bFirstUpdate = false;
        long    cid     = -1;
        long    catid   = -1; /* cateogry id */
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
    BGTaskUpdateChannel(Object userObj) {
        super(userObj);
    }

    @Override
    protected Err
    doBGTask(Arg arg) {
        try {
            loader = new NetLoader();
            if (arg.bFirstUpdate)
                return loader.initialLoad(arg.catid, arg.url, null);
            else
                return loader.updateLoad(arg.cid, false);
        } catch (InterruptedException e) {
            if (arg.bFirstUpdate)
                logI("BGTaskUpdateChannel : Loading [" + arg.url + "] : interrupted!");
            else
                logI("BGTaskUpdateChannel : Updating [" + arg.cid + "] : interrupted!");
            return Err.Interrupted;
        }
    }

    @Override
    public boolean
    cancel(Object param) {
        // Canceling background task may corrupt DB
        //   by interrupting in the middle of transaction.
        // To avoid this case, start db transaction to cancel!.
        // TODO
        //   After Per-Channel-DB-Lock is implemented,
        //     lock/unlock should be changed to Per-Channel-Lock/Unlock.
        int retry = 5;
        try {
            DBPolicy.S().lock();
        } catch (InterruptedException e) {
            DBPolicy.S().unlock();
            eAssert(false);
            return false;
        }

        super.cancel(param); // cancel thread
        if (null != loader)
            loader.cancel();     // This is HACK for fast-interrupt.
        while (0 < retry-- && !super.cancel(param));
        DBPolicy.S().unlock();

        return (retry > 0)? true: false;
    }
}
