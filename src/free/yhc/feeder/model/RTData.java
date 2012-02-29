package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

// Singleton
// Runtime Data
//   : Data that SHOULD NOT be stored at DataBase.
//       but, need to be check in runtime.
// Should be THREAD-SAFE
public class RTData {
    private static final String idPrefix = "/";

    private static RTData  instance = null;

    private BGTaskManager  gbtm = null;

    public static enum StateChann {
        Idle,
        Updating,
        Canceling,
        UpdateFailed,
    }

    public static enum StateItem {
        Idle,
        Downloading,
        DownloadFailed,
    }

    private enum Action {
        Update,
        Download,
    }

    private class DataChann {
        BGTask task;
    }

    private class DataItem {
        BGTask task;
    }


    private RTData() {
        gbtm = new BGTaskManager();
    }

    // Get singleton instance,.
    public static RTData
    S() {
        if (null == instance)
            instance = new RTData();
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
    registerChannUpdateTask(long cid, BGTask task) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.register(Id(cid, Action.Update), task);
        }
    }


    public int
    unbindChannUpdateTask(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Id(cid, Action.Update));
        }
    }

    // Unbind all tasks whose event handler is 'onEvent'
    public int
    unbindTasks(BGTask.OnEvent onEvent) {
        eAssert(null != onEvent);
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(onEvent);
        }
    }

    // unbind tasks which have current thread as bind-owner.
    public int
    unbindTasks() {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.unbind(Thread.currentThread());
        }
    }

    public BGTask
    bindChannUpdateTask(long cid, BGTask.OnEvent onEvent) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.bind(Id(cid, Action.Update), onEvent);
        }
    }

    public BGTask
    getChannUpdateTask(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(cid, Action.Update));
        }
    }

    // 'unbind' then 'unregister'
    public boolean
    unregisterChannUpdateTask(long cid) {
        synchronized (gbtm.getSyncObj()) {
            String id = Id(cid, Action.Update);
            gbtm.unbind(id);
            return gbtm.unregister(id);
        }
    }

    // channel is updateing???
    public StateChann
    getChannState(long cid) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, Action.Update));
        }

        if (null == task)
            return StateChann.Idle;

        if (task.isAlive()) {
            return task.isInterrupted()? StateChann.Canceling: StateChann.Updating;
        } else if (Err.NoErr == task.getResult())
            return StateChann.Idle;
        else
            return StateChann.UpdateFailed;
    }

    // result information is consumed. So, back to idle if possible.
    public void
    consumeResult(long cid) {
        BGTask task;
        synchronized (gbtm.getSyncObj()) {
            task = gbtm.peek(Id(cid, Action.Update));
        }
        eAssert(!task.isAlive());
        task.resetResult();
    }

    public Err
    getChannBGTaskErr(long cid) {
        synchronized (gbtm.getSyncObj()) {
            return gbtm.peek(Id(cid, Action.Update)).getResult();
        }
    }
}
