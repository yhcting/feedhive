package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

// Singleton
// Runtime Data
//   : Data that SHOULD NOT be stored at DataBase.
//       but, need to be check in runtime.
// Should be THREAD-SAFE
public class RTTask {
    private static final String idPrefix = "/";

    private static RTTask  instance = null;

    private BGTaskManager  gbtm = null;

    public static enum StateUpdate {
        Idle,
        Updating,
        Canceling,
        UpdateFailed,
    }

    public static enum StateDownload {
        Idle,
        Downloading,
        Canceling,
        DownloadFailed,
    }

    private enum Action {
        Update,
        Download,
    }

    private RTTask() {
        gbtm = new BGTaskManager();
    }

    // Get singleton instance,.
    public static RTTask
    S() {
        if (null == instance)
            instance = new RTTask();
        return instance;
    }

    private String
    Id(long cid, Action act) {
        return idPrefix + cid + "/" + act.name();
    }

    private String
    Id(long cid, long itemid, Action act) {
        return idPrefix + cid + "/" + itemid + "/" + act.name();
    }

    public boolean
    registerUpdate(long cid, BGTask task) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.register(Id(cid, Action.Update), task);
        }
    }

    public boolean
    registerDownload(long cid, long id, BGTask task) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.register(Id(cid, id, Action.Download), task);
        }
    }


    public int
    unbindUpdate(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Id(cid, Action.Update));
        }
    }

    public int
    unbindDownload(long cid, long id) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Id(cid, id, Action.Download));
        }
    }

    // Unbind all tasks whose event handler is 'onEvent'
    public int
    unbind(BGTask.OnEvent onEvent) {
        eAssert(null != onEvent);
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(onEvent);
        }
    }

    // unbind tasks which have current thread as bind-owner.
    public int
    unbind() {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Thread.currentThread());
        }
    }

    public BGTask
    bindUpdate(long cid, BGTask.OnEvent onEvent) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.bind(Id(cid, Action.Update), onEvent);
        }
    }

    public BGTask
    bindDownload(long cid, long id, BGTask.OnEvent onEvent) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.bind(Id(cid, id, Action.Download), onEvent);
        }
    }

    public BGTask
    getUpdate(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(cid, Action.Update));
        }
    }

    public BGTask
    getDownload(long cid, long id) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(cid, id, Action.Download));
        }
    }

    // channel is updating???
    public StateUpdate
    getUpdateState(long cid) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, Action.Update));
        }

        if (null == task)
            return StateUpdate.Idle;

        if (task.isAlive()) {
            return task.isInterrupted()? StateUpdate.Canceling: StateUpdate.Updating;
        } else if (Err.NoErr == task.getResult()
                   || Err.UserCancelled == task.getResult()) {
            synchronized (gbtm.getSyncObj()) {
                // remove from manager.
                String id = Id(cid, Action.Update);
                gbtm.unbind(id);
                gbtm.unregister(id);
            }
            return StateUpdate.Idle;

        } else
            return StateUpdate.UpdateFailed;
    }

    public StateDownload
    getDownloadState(long cid, long id) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, id, Action.Download));
        }

        if (null == task)
            return StateDownload.Idle;

        if (task.isAlive()) {
            return task.isInterrupted()? StateDownload.Canceling: StateDownload.Downloading;
        } else if (Err.NoErr == task.getResult()
                || Err.UserCancelled == task.getResult()) {
            synchronized (gbtm.getSyncObj()) {
                String idstr = Id(cid, id, Action.Download);
                gbtm.unbind(idstr);
                gbtm.unregister(idstr);
            }
            return StateDownload.Idle;
        } else
            return StateDownload.DownloadFailed;
    }

    // result information is consumed. So, back to idle if possible.
    public void
    consumeUpdateResult(long cid) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, Action.Update));
        }
        eAssert(!task.isAlive());
        task.resetResult();
    }

    public void
    consumeDownloadResult(long cid, long id) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, id, Action.Download));
        }
        eAssert(!task.isAlive());
        task.resetResult();
    }

    public Err
    getUpdateErr(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(cid, Action.Update)).getResult();
        }
    }

    public Err
    getDownloadErr(long cid, long id) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(cid, id, Action.Download)).getResult();
        }
    }

    public void
    cancelAll() {
        synchronized (gbtm.getSyncObj()) {
            gbtm.cancelAll();
        }
    }
}
