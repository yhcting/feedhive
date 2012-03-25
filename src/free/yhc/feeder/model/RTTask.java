package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.Iterator;
import java.util.LinkedList;

// Singleton
// Runtime Data
//   : Data that SHOULD NOT be stored at DataBase.
//       but, need to be check in runtime.
// Should be THREAD-SAFE
public class RTTask {
    private static RTTask  instance = null;

    private BGTaskManager  gbtm = null;
    private LinkedList<ManagerEventListener> eventListenerl = new LinkedList<ManagerEventListener>();

    public interface OnRTTaskManagerEvent {
        void onUpdateBGTaskRegister(long cid, BGTask task);
        void onUpdateBGTaskUnregister(long cid, BGTask task);
        void onDownloadBGTaskRegster(long cid, long id, BGTask task);
        void onDownloadBGTaskUnegster(long cid, long id, BGTask task);
    }

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

    private class ManagerEventListener {
        Object               key;
        OnRTTaskManagerEvent listener;
        ManagerEventListener(Object key, OnRTTaskManagerEvent listener) {
            this.key = key;
            this.listener = listener;
        }
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
    Id(Action act, long cid) {
        return act.name() + "/" + cid;
    }

    private String
    Id(Action act, long cid, long itemid) {
        return Id(act, cid) + "/" + itemid;
    }

    private boolean
    unregister(String id) {
        synchronized (gbtm.getSyncObj()) {
            // remove from manager.
            gbtm.clear(id);
            return gbtm.unregister(id);
        }
    }

    // ===============================
    // Publics
    // ===============================
    public void
    registerManagerEventListener(Object key, OnRTTaskManagerEvent listener) {
        eAssert(null != key && null != listener);
        eventListenerl.add(new ManagerEventListener(key, listener));
    }

    public long
    unregisterManagerEventListener(Object key) {
        Iterator<ManagerEventListener> itr = eventListenerl.iterator();
        long ret = 0;
        while (itr.hasNext()) {
            ManagerEventListener el = itr.next();
            if (key == el.key) {
                itr.remove();
                ret++;
            }
        }
        return ret;
    }

    public boolean
    registerUpdate(long cid, BGTask task) {
        synchronized (gbtm.getSyncObj()) {
            boolean r = gbtm.register(Id(Action.Update, cid), task);
            if (r) {
                for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
                    el.listener.onUpdateBGTaskRegister(cid, task);
            }
            return r;
        }
    }

    public boolean
    registerDownload(long cid, long id, BGTask task) {
        synchronized (gbtm.getSyncObj()) {
            boolean r = gbtm.register(Id(Action.Download, cid, id), task);
            if (r) {
                for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
                    el.listener.onDownloadBGTaskRegster(cid, id, task);
            }
            return r;
        }
    }

    public int
    unbindUpdate(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Thread.currentThread(), Id(Action.Update, cid));
        }
    }

    public int
    unbindDownload(long cid, long id) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Thread.currentThread(), Id(Action.Download, cid, id));
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
            return gbtm.bind(Id(Action.Update, cid), onEventKey, onEvent);
        }
    }

    public BGTask
    bindUpdate(long cid, BGTask.OnEvent onEvent) {
        return bindUpdate(cid, null, onEvent);
    }

    public BGTask
    bindDownload(long cid, long id, Object onEventKey, BGTask.OnEvent onEvent) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.bind(Id(Action.Download, cid, id), onEventKey, onEvent);
        }
    }

    public BGTask
    bindDownload(long cid, long id, BGTask.OnEvent onEvent) {
        return bindDownload(cid, id, null, onEvent);
    }

    public BGTask
    getUpdate(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(Action.Update, cid));
        }
    }

    public BGTask
    getDownload(long cid, long id) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(Action.Download, cid, id));
        }
    }

    // channel is updating???
    public StateUpdate
    getUpdateState(long cid) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(Action.Update, cid));
        }

        if (null == task)
            return StateUpdate.Idle;

        if (task.isAlive()) {
            return task.isInterrupted()? StateUpdate.Canceling: StateUpdate.Updating;
        } else if (Err.NoErr == task.getResult()
                   || Err.UserCancelled == task.getResult()) {
            boolean r = unregister(Id(Action.Update, cid));
            if (r) {
                for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
                    el.listener.onUpdateBGTaskUnregister(cid, task);
            }
            return StateUpdate.Idle;

        } else
            return StateUpdate.UpdateFailed;
    }

    public StateDownload
    getDownloadState(long cid, long id) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(Action.Download, cid, id));
        }

        if (null == task)
            return StateDownload.Idle;

        if (task.isAlive()) {
            return task.isInterrupted()? StateDownload.Canceling: StateDownload.Downloading;
        } else if (Err.NoErr == task.getResult()
                || Err.UserCancelled == task.getResult()) {
            boolean r = unregister(Id(Action.Download, cid, id));
            if (r) {
                for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
                    el.listener.onDownloadBGTaskUnegster(cid, id, task);
            }
            return StateDownload.Idle;
        } else
            return StateDownload.DownloadFailed;
    }

    /**
     * Is download task is running state? (Canceling or Downloading)
     * @cid channel id
     * @id  item id
     */
    public boolean
    isDownloadRunning(long cid, long id) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(Action.Download, cid, id));
        }

        if (null == task)
            return false;

        return task.isAlive();
    }

    /**
     * Is there any downloading task belongs to this channel?
     * @cid channel id
     */
    public boolean
    isDownloadRunning(long cid) {
        String[] tids;
        synchronized (gbtm.getSyncObj()) {
            tids = gbtm.getTaskIds();
            for (String tid : tids) {
                if (tid.startsWith(Id(Action.Download, cid))) {
                    BGTask task = gbtm.peek(tid);
                    if (null != task && task.isAlive())
                        return true;
                }
            }
            return false;
        }
    }

    // result information is consumed. So, back to idle if possible.
    public void
    consumeUpdateResult(long cid) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(Action.Update, cid));
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
            task = gbtm.peek(Id(Action.Download, cid, id));
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
            task = gbtm.peek(Id(Action.Update, cid));
        }

        if (null == task)
            return true; // nothing to do - already unregistered.

        if (task.isAlive())
            return false;

        boolean r = unregister(Id(Action.Update, cid));
        if (r) {
            for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
                el.listener.onUpdateBGTaskUnregister(cid, task);
        }
        return r;
    }

    public Err
    getUpdateErr(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(Action.Update, cid)).getResult();
        }
    }

    /**
     * Get download error of latest download bg task.
     */
    public Err
    getDownloadErr(long cid, long id) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(Action.Download, cid, id)).getResult();
        }
    }

    public void
    cancelAll() {
        synchronized (gbtm.getSyncObj()) {
            gbtm.cancelAll();
        }
    }
}
