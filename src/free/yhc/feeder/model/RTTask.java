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


    // ===============================
    // Privates
    // ===============================

    private String
    Id(long cid, Action act) {
        return idPrefix + cid + "/" + act.name();
    }

    private String
    Id(long cid, long itemid, Action act) {
        return idPrefix + cid + "/" + itemid + "/" + act.name();
    }

    private void
    unregister(String id) {
        synchronized (gbtm.getSyncObj()) {
            // remove from manager.
            gbtm.clear(id);
            gbtm.unregister(id);
        }
    }

    // ===============================
    // Publics
    // ===============================

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
            return gbtm.unbind(Thread.currentThread(), Id(cid, Action.Update));
        }
    }

    public int
    unbindDownload(long cid, long id) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Thread.currentThread(), Id(cid, id, Action.Download));
        }
    }

    public int
    unbind(Object onEventKey) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Thread.currentThread(), onEventKey);
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
    bindUpdate(long cid, Object onEventKey, BGTask.OnEvent onEvent) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.bind(Id(cid, Action.Update), onEventKey, onEvent);
        }
    }

    public BGTask
    bindUpdate(long cid, BGTask.OnEvent onEvent) {
        return bindUpdate(cid, null, onEvent);
    }

    public BGTask
    bindDownload(long cid, long id, Object onEventKey, BGTask.OnEvent onEvent) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.bind(Id(cid, id, Action.Download), onEventKey, onEvent);
        }
    }

    public BGTask
    bindDownload(long cid, long id, BGTask.OnEvent onEvent) {
        return bindDownload(cid, id, null, onEvent);
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
            unregister(Id(cid, Action.Update));
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
            unregister(Id(cid, id, Action.Download));
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
        if (null == task)
            return;

        eAssert(!task.isAlive());
        task.resetResult();
    }

    public void
    consumeDownloadResult(long cid, long id) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, id, Action.Download));
        }
        if (null == task)
            return;

        eAssert(!task.isAlive());
        task.resetResult();
    }

    // @return : true(success), false(fail)
    public boolean
    unregisterUpdate(long cid) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, Action.Update));
        }

        if (null == task)
            return true; // nothing to do - already unregistered.

        if (task.isAlive())
            return false;

        unregister(Id(cid, Action.Update));
        return true;
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
