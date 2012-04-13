package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.Iterator;
import java.util.LinkedList;

// Singleton
// Runtime Data
//   : Data that SHOULD NOT be stored at DataBase.
//       but, need to be check in runtime.
// Should be THREAD-SAFE
public class RTTask implements
UnexpectedExceptionHandler.TrackedModule {
    private static RTTask           instance = null;

    private BGTaskManager           gbtm = null;
    private LinkedList<BGTask>      readyQ = new LinkedList<BGTask>();
    private LinkedList<BGTask>      runQ = new LinkedList<BGTask>();

    // NOTE
    // Why taskQSync is used instead of using 'readyQ' and 'runQ' object directly as an sync object for each list?
    // This is to avoid following unexpected state
    //   "Task is not in readyQ and runQ. But is not Idle!"
    // If 'readyQ' and 'runQ' are used as synch. object for their own list,
    //   above unexpected state is unavoidable.
    // For example.
    //   synchronized (readyQ) {
    //       t = readyQ.pop();
    //   }
    //   <==== Unexpected state! ====>
    //   synchronized (runQ) {
    //       runQ.addLast(t);
    //   }
    private Object             taskQSync = new Object();

    private volatile int       max_concurrent = 3; // temporally hard coding.

    private LinkedList<ManagerEventListener> eventListenerl = new LinkedList<ManagerEventListener>();

    public interface OnRTTaskManagerEvent {
        void onBGTaskRegister(long id, BGTask task, Action act);
        void onBGTaskUnregister(long id, BGTask task, Action act);
    }

    public static enum TaskState {
        Idle,
        Ready, // ready to run. waiting turn!
        Running,
        Canceling,
        Failed
    }

    public enum Action {
        Update,
        Download;

        static Action convert(String act) {
            for (Action a : Action.values()) {
                if (a.name().equals(act))
                    return a;
            }
            return null;
        }
    }

    private class ManagerEventListener {
        Object               key;
        OnRTTaskManagerEvent listener;
        ManagerEventListener(Object key, OnRTTaskManagerEvent listener) {
            this.key = key;
            this.listener = listener;
        }
    }

    private class TaskInReady {
        BGTask  task;
        Object  arg;
        TaskInReady(BGTask t, Object a) {
            task = t;
            arg = a;
        }
    }


    private class RunningBGTaskOnEvent implements BGTask.OnEvent {
        private void
        onEnd(BGTask task) {
            eAssert(!task.isAlive());
            // NOTE
            // Handling race condition with 'start()' function is very important!
            // Order of these codes are deeply related with avoiding unexpected race-condition.
            // DO NOT change ORDER of code line!
            BGTask t = null;
            synchronized (taskQSync) {
                runQ.remove(task);
                if (readyQ.isEmpty())
                    return;
                t = readyQ.pop();
                runQ.addLast(t);
            }

            synchronized (gbtm) {
                gbtm.start(t.getNick());
            }
        }

        @Override
        public void
        onProgress(BGTask task, long progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            onEnd(task);
        }

        @Override
        public void
        onPreRun(BGTask task) {
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            onEnd(task);
        }
    }


    private RTTask() {
        gbtm = new BGTaskManager();
    }

    // Get singleton instance,.
    public static RTTask
    S() {
        if (null == instance) {
            instance = new RTTask();
            UnexpectedExceptionHandler.S().registerModule(instance);
        }
        return instance;
    }


    // ===============================
    // Privates
    // ===============================

    private String
    Id(Action act, long id) {
        return act.name() + "/" + id;
    }

    private Action
    actionFromId(String id) {
        int i = id.indexOf('/');
        return Action.convert(id.substring(0, i));
    }

    private long
    idFromId(String id) {
        int i = id.indexOf('/');
        return Long.parseLong(id.substring(i + 1));
    }

    private boolean
    isTaskInAction(BGTask task) {
        synchronized (taskQSync) {
            if (runQ.contains(task) || readyQ.contains(task))
                return true;
        }
        eAssert(!task.isAlive());
        return false;
    }

    /**
     * Get items that are under downloading.
     *
     * @param cid : < 0 : all items / >= 0 : items belongs channel 'cid'
     * @return
     */
    private long[]
    itemsDownloading(long cid) {
        String[] tids;
        LinkedList<Long> l = new LinkedList<Long>();
        synchronized (gbtm) {
            tids = gbtm.getTaskIds();
            for (String tid : tids) {
                if (Action.Download == actionFromId(tid)) {
                    BGTask task = gbtm.peek(tid);
                    long id = idFromId(tid);
                    if (null != task && isTaskInAction(task)) {
                        if (cid < 0)
                            l.add(id);
                        else if (cid == DBPolicy.S().getItemInfoLong(id, DB.ColumnItem.CHANNELID))
                            l.add(id);
                    }
                }
            }
        }
        return Utils.arrayLongTolong(l.toArray(new Long[0]));
    }
    // ===============================
    // Package Private
    // ===============================
    void
    setMaxConcurrent(int v) {
        max_concurrent = v;
    }

    // ===============================
    // Publics
    // ===============================
    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ RTTask ]";
    }

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
    register(long id, Action act, BGTask task) {
        synchronized (gbtm) {
            boolean r = gbtm.register(Id(act, id), task);
            if (r) {
                for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
                    el.listener.onBGTaskRegister(id, task, act);
            }
            return r;
        }
    }


    // @return : true(success), false(fail)
    public boolean
    unregister(long id, Action act) {
        String taskId = Id(act, id);

        boolean r = false;
        BGTask task;
        synchronized (gbtm) {
            // remove from manager.
            task = gbtm.peek(taskId);
            r = gbtm.unregister(taskId);
        }

        if (!r || null == task)
            return false;

        synchronized (taskQSync) {
            if (runQ.contains(task) || readyQ.contains(task))
                return false;
        }

        for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
            el.listener.onBGTaskUnregister(id, task, act);

        return true;
    }


    public int
    unbind(long id, Action act) {
        synchronized (gbtm) {
            return gbtm.unbind(Thread.currentThread(), Id(act, id));
        }
    }

    public int
    unbind(Object onEventKey) {
        synchronized (gbtm) {
            return gbtm.unbind(Thread.currentThread(), onEventKey);
        }
    }

    public BGTask
    bind(long id, Action act, Object onEventKey, BGTask.OnEvent onEvent) {
        synchronized (gbtm) {
            return gbtm.bind(Id(act, id), onEventKey, onEvent);
        }
    }

    public BGTask
    getTask(long id, Action act) {
        synchronized (gbtm) {
            return gbtm.peek(Id(act, id));
        }
    }

    // channel is updating???
    public TaskState
    getState(long id, Action act) {
        // channel is updating???
        BGTask task;
        synchronized (gbtm) {
            task = gbtm.peek(Id(act, id));
        }

        if (null == task)
            return TaskState.Idle;

        synchronized (taskQSync) {
            if (runQ.contains(task))
                return task.isInterrupted()? TaskState.Canceling: TaskState.Running;

            if (readyQ.contains(task))
                return TaskState.Ready;
        }

        if (Err.NoErr == task.getResult()
                   || Err.UserCancelled == task.getResult()) {
            unregister(id, act);
            return TaskState.Idle;
        }

        return TaskState.Failed;
    }

    // result information is consumed. So, back to idle if possible.
    public void
    consumeResult(long id, Action act) {
        BGTask task;
        synchronized (gbtm) {
            task = gbtm.peek(Id(act, id));
        }
        if (null == task)
            return;

        eAssert(!isTaskInAction(task));
        task.resetResult();
    }

    public boolean
    start(long id, Action act) {
        String taskId = Id(act, id);
        BGTask t = null;
        synchronized (gbtm) {
            t = gbtm.peek(taskId);
            if (null == t)
                return false;
        }
        // NOTE
        // See comments in RunningBGTaskOnEvent.onEnd
        // DO NOT change ORDER of code line.
        boolean bStartImmediate = false;
        synchronized (taskQSync) {
            readyQ.remove(t);

            // NOTE
            // This SHOULD be 'bindPrior' is used.
            // operation related with 'runQ' and 'readyQ' SHOULD BE DONE
            //   before normal event listener is called!
            synchronized (gbtm) {
                gbtm.bindPrior(Id(act, id), null, new RunningBGTaskOnEvent());
            }
            // If there is no running task then start NOW!
            if (runQ.size() < max_concurrent) {
                bStartImmediate = true;
                runQ.addLast(t);
            } else
                readyQ.addLast(t);
        }

        synchronized (gbtm) {
            if (bStartImmediate)
                gbtm.start(taskId);
        }

        return true;
    }

    public boolean
    cancel(long id, Action act, Object arg) {
        BGTask t = null;
        synchronized (gbtm) {
            t = gbtm.peek(Id(act, id));
            if (null == t)
                return true;
        }

        synchronized (taskQSync) {
            readyQ.remove(t);
        }

        synchronized (gbtm) {
            return gbtm.cancel(Id(act, id), arg);
        }
    }

    public Err
    getErr(long id, Action act) {
        synchronized (gbtm) {
            return gbtm.peek(Id(act, id)).getResult();
        }
    }

    public void
    cancelAll() {
        synchronized (gbtm) {
            gbtm.cancelAll();
        }
    }

    //===============================================
    // Complex UI Specific Requirement.
    //===============================================

    public long[]
    getItemsDownloading() {
        return itemsDownloading(-1);
    }

    /**
     * Get items that are under downloading.
     *
     * @param cid
     * @return
     */
    public long[]
    getItemsDownloading(long cid) {
        return itemsDownloading(cid);
    }
}
