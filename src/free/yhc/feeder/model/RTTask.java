/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

// Singleton
// Runtime Data
//   : Data that SHOULD NOT be stored at DataBase.
//       but, need to be check in runtime.
// Should be THREAD-SAFE
public class RTTask implements
UnexpectedExceptionHandler.TrackedModule,
OnSharedPreferenceChangeListener {
    private static RTTask   instance = null;

    // Dependency on only following modules are allowed
    // - Utils
    // - UnexpectedExceptionHandler
    // - DB / DBThread
    // - UIPolicy
    // - DBPolicy
    private final DBPolicy              dbp    = DBPolicy.get();
    private final BGTaskManager         bgtm   = new BGTaskManager();;
    private final LinkedList<BGTask>    readyQ = new LinkedList<BGTask>();
    private final LinkedList<BGTask>    runQ   = new LinkedList<BGTask>();

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
    private final Object       taskQSync = new Object();
    private final KeyBasedLinkedList<OnRegisterListener> registerListenerl
                    = new KeyBasedLinkedList<OnRegisterListener>();
    private final KeyBasedLinkedList<OnTaskQueueChangedListener> taskQChangedListenerl
                    = new KeyBasedLinkedList<OnTaskQueueChangedListener>();
    private volatile int       maxConcurrent = 2; // temporally hard coding.

    public interface OnRegisterListener {
        void onRegister(BGTask task, long id, Action act);
        void onUnregister(BGTask task, long id, Action act);
    }

    public interface OnTaskQueueChangedListener {
        // Task Run Queue is changed.
        void onEnQ(BGTask task, long id, Action act);
        void onDeQ(BGTask task, long id, Action act);
    }

    public static enum TaskState {
        IDLE,
        READY, // ready to run. waiting turn!
        RUNNING,
        CANCELING,
        FAILED
    }

    public enum Action {
        UPDATE,
        DOWNLOAD;

        static Action convert(String act) {
            for (Action a : Action.values()) {
                if (a.name().equals(act))
                    return a;
            }
            return null;
        }
    }

    private class RunningBGTaskListener implements BGTask.OnEventListener {
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
                if (!readyQ.isEmpty()) {
                    t = readyQ.pop();
                    runQ.addLast(t);
                }
            }

            synchronized (bgtm) {
                if (null != t) {
                    bgtm.start(bgtm.getTaskId(t));
                }
            }

            notifyTaskQChanged(task, false);
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
        UnexpectedExceptionHandler.get().registerModule(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Utils.getAppContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, "maxnr_bgtask");
    }

    // Get singleton instance,.
    public static RTTask
    get() {
        if (null == instance)
            instance = new RTTask();
        return instance;
    }

    // ===============================
    // Privates
    // ===============================
    /**
     * Set number BG task that can be run concurrently.
     * If number of BG task is larger than this value, than
     *   only this number of tasks runs simultaneously and others are waiting.
     * After one of running task is finished, next waiting BG task start to run.
     * @param v
     */
    private void
    setMaxConcurrent(int v) {
        maxConcurrent = v;
    }

    private String
    tid(Action act, long id) {
        return act.name() + "/" + id;
    }

    private Action
    actionFromTid(String id) {
        int i = id.indexOf('/');
        return Action.convert(id.substring(0, i));
    }

    private long
    idFromTid(String id) {
        int i = id.indexOf('/');
        return Long.parseLong(id.substring(i + 1));
    }

    /**
     * Check that task is running or waiting to be run.
     * @param task
     * @return
     */
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
     * Get DB ids that are under given action.
     * @return
     */
    private long[]
    getIdsInAction(Action action) {
        String[] tids;
        LinkedList<Long> l = new LinkedList<Long>();
        synchronized (bgtm) {
            tids = bgtm.getTaskIds();
            for (String tid : tids) {
                if (action == actionFromTid(tid)) {
                    BGTask task = bgtm.peek(tid);
                    long id = idFromTid(tid);
                    if (null != task && isTaskInAction(task)) {
                        l.add(id); // all items
                    }
                }
            }
        }
        return Utils.convertArrayLongTolong(l.toArray(new Long[0]));
    }

    private void
    notifyTaskQChanged(BGTask task, boolean enQ) {
        // This is run on UI thread
        // But to increase readability... because taskQChangedListenerl assumes handled on ui thread.
        //   - not thread safe!

        eAssert(Utils.isUiThread());
        String tid;
        synchronized (bgtm) {
            tid = bgtm.getTaskId(task);
        }
        long id = idFromTid(tid);
        Action act = actionFromTid(tid);

        Iterator<OnTaskQueueChangedListener> iter = taskQChangedListenerl.iterator();
        while (iter.hasNext()) {
            if (enQ)
                iter.next().onEnQ(task, id, act);
            else
                iter.next().onDeQ(task, id, act);
        }

    }
    // ===============================
    // Package Private
    // ===============================

    // ===============================
    // Publics
    // ===============================
    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ RTTask ]";
    }

    @Override
    public void
    onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("maxnr_bgtask".equals(key)) {
            String v = prefs.getString("maxnr_bgtask", "1");
            int value = 1;
            try {
                value = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                eAssert(false);
            }
            setMaxConcurrent(value);
        }
    }

    /**
     * Listener is notified whenever a task is registered or unregistered.
     * @param key
     * @param listener
     */
    public void
    registerRegisterEventListener(Object key, OnRegisterListener listener) {
        eAssert(Utils.isUiThread());
        eAssert(null != key && null != listener);
        registerListenerl.add(key, listener);
    }

    public void
    unregisterRegisterEventListener(Object key) {
        eAssert(Utils.isUiThread());
        registerListenerl.remove(key);
    }

    public void
    registerTaskQChangedListener(Object key, OnTaskQueueChangedListener listener) {
        eAssert(Utils.isUiThread());
        eAssert(null != key && null != listener);
        taskQChangedListenerl.add(key,  listener);
    }

    public void
    unregisterTaskQChangedListener(Object key) {
        eAssert(Utils.isUiThread());
        taskQChangedListenerl.remove(key);
    }

    /**
     * Register task.
     * All BG task used SHOULD BE registered at RTTask.
     * If not, task cannot be handled anymore.
     * It should be called only in UI Thread context
     * @param id
     *   ID of task
     * @param act
     *   Action type of task.
     * @param task
     *   task to register
     * @return
     */
    public boolean
    register(long id, Action act, BGTask task) {
        eAssert(Utils.isUiThread());
        boolean r;
        synchronized (bgtm) {
            r = bgtm.register(tid(act, id), task);
        }

        if (r) {
            Iterator<OnRegisterListener> itr = registerListenerl.iterator();
            while (itr.hasNext())
                itr.next().onRegister(task, id, act);
        }
        return r;
    }


    /**
     * It should be called only in UI Thread context*
     *
     * @param
     *   id ID of task
     * @param act
     *   Action type of task.
     * @return
     *   true(success), false(fail)
     */
    public boolean
    unregister(long id, Action act) {
        eAssert(Utils.isUiThread());
        String taskId = tid(act, id);

        boolean r = false;
        BGTask task;
        synchronized (bgtm) {
            // remove from manager.
            task = bgtm.peek(taskId);
            r = bgtm.unregister(taskId);
        }

        if (!r || null == task)
            return false;

        synchronized (taskQSync) {
            if (runQ.contains(task) || readyQ.contains(task))
                return false;
        }

        Iterator<OnRegisterListener> itr = registerListenerl.iterator();
        while (itr.hasNext())
            itr.next().onUnregister(task, id, act);

        return true;
    }

    /**
     * Unbind all event listener of BGTask, whose event key matches given one.
     * @param key
     * @return
     *   number of tasks unbinded.
     */
    public int
    unbind(Object key) {
        synchronized (bgtm) {
            return bgtm.unbind(key);
        }
    }

    /**
     * Bind event listener of BGTask with key value.
     * @param id
     * @param act
     * @param key
     * @param listener
     * @return
     */
    public BGTask
    bind(long id, Action act, Object key, BGTask.OnEventListener listener) {
        synchronized (bgtm) {
            return bgtm.bind(tid(act, id), key, listener, false);
        }
    }

    /**
     * get Number of active tasks.
     * (Running task + task waiting it's turn in the ready queue.)
     * @return
     */
    public int
    getNRActiveTask() {
        synchronized (taskQSync) {
            return runQ.size() + readyQ.size();
        }
    }
    /**
     *
     * @param id
     *   ID of task
     * @param act
     *   Action type of task
     * @return
     */
    public BGTask
    getTask(long id, Action act) {
        synchronized (bgtm) {
            return bgtm.peek(tid(act, id));
        }
    }

    /**
     *
     * @param id
     * @param act
     * @return
     */
    public TaskState
    getState(long id, Action act) {
        // channel is updating???
        BGTask task;
        synchronized (bgtm) {
            task = bgtm.peek(tid(act, id));
        }

        if (null == task)
            return TaskState.IDLE;

        synchronized (taskQSync) {
            if (runQ.contains(task))
                return task.isCancelled()? TaskState.CANCELING: TaskState.RUNNING;

            if (readyQ.contains(task))
                return TaskState.READY;
        }

        if (Err.NO_ERR == task.getResult()
                   || Err.USER_CANCELLED == task.getResult()) {
            unregister(id, act);
            return TaskState.IDLE;
        }

        return TaskState.FAILED;
    }

    /**
     * Result of BG task is stored internally to refer later after BG task is terminated.
     * This function reset stored result value manually.
     * @param id
     * @param act
     */
    public void
    consumeResult(long id, Action act) {
        BGTask task;
        synchronized (bgtm) {
            task = bgtm.peek(tid(act, id));
        }
        if (null == task)
            return;

        eAssert(!isTaskInAction(task));
        task.resetResult();
    }

    /**
     * Start task.
     * @param id
     * @param act
     * @return
     */
    public boolean
    start(long id, Action act) {
        String taskId = tid(act, id);
        BGTask t = null;
        synchronized (bgtm) {
            t = bgtm.peek(taskId);
            if (null == t)
                return false;
        }
        // NOTE
        // See comments in RunningBGTaskListener.onEnd
        // DO NOT change ORDER of code line.
        boolean bStartImmediate = false;
        synchronized (taskQSync) {
            // Why remove 't' at this moment?
            // This means, "if task is already in ready state,
            //   it will move to last of the list."
            // In summary, "start request cancels previous one and newly added".
            readyQ.remove(t);

            // NOTE
            // This SHOULD be 'bindPrior' is used.
            // operation related with 'runQ' and 'readyQ' SHOULD BE DONE
            //   before normal event listener is called!
            synchronized (bgtm) {
                bgtm.bind(tid(act, id), this, new RunningBGTaskListener(), true);
            }
            // If there is no running task then start NOW!
            if (runQ.size() < maxConcurrent) {
                bStartImmediate = true;
                runQ.addLast(t);
            } else
                readyQ.addLast(t);
        }

        synchronized (bgtm) {
            if (bStartImmediate)
                bgtm.start(taskId);
        }

        notifyTaskQChanged(t, true);

        return true;
    }

    /**
     *
     * @param id
     * @param act
     * @param arg
     *   This value is passed onCancel() as argument.
     * @return
     */
    public boolean
    cancel(long id, Action act, Object arg) {
        BGTask t = null;
        synchronized (bgtm) {
            t = bgtm.peek(tid(act, id));
            if (null == t)
                return true;
        }

        synchronized (taskQSync) {
            readyQ.remove(t);
        }

        synchronized (bgtm) {
            return bgtm.cancel(tid(act, id), arg);
        }
    }

    /**
     * Get result of BG task.
     * @param id
     * @param act
     * @return
     */
    public Err
    getErr(long id, Action act) {
        synchronized (bgtm) {
            return bgtm.peek(tid(act, id)).getResult();
        }
    }

    public void
    cancelAll() {
        synchronized (bgtm) {
            bgtm.cancelAll();
        }
    }

    //===============================================
    // Complex UI Specific Requirement.
    //===============================================
    /**
     * Get item ids that are under downloading.
     * In other words, item ids that are bindded to BGTask whose action type is 'Download'
     * @return
     */
    public long[]
    getItemsDownloading() {
        return getIdsInAction(Action.DOWNLOAD);
    }

    /**
     * Get items that are under downloading and belonging to given channel.
     * In other words, item ids that are bindded to BGTask whose action type is 'Download'
     *   and item's channel id is same with given cid.
     * @param cid
     * @return
     */
    public long[]
    getItemsDownloading(long cid) {
        return getItemsDownloading(new long[] { cid });
    }

    /**
     * Get items that are under downloading and belonging to given channels.
     * In other words, item ids that are bindded to BGTask whose action type is 'Download'
     *   and item's channel id is same with one of given cids.
     * @param cids
     * @return
     */
    public long[]
    getItemsDownloading(long[] cids) {
        long[] dnids = getItemsDownloading();
        HashSet<Long> set = new HashSet<Long>();
        for (long cid : cids)
            set.add(cid);

        LinkedList<Long> l = new LinkedList<Long>();
        try {
            dbp.getDelayedChannelUpdate();
            for (long dnid : dnids)
                if (set.contains(dbp.getItemInfoLong(dnid, DB.ColumnItem.CHANNELID)))
                    l.add(dnid);
        } finally {
            dbp.putDelayedChannelUpdate();
        }
        return Utils.convertArrayLongTolong(l.toArray(new Long[0]));
    }

    /**
     * Get channel id where this download task is belonging to.
     * @param task
     * @return
     */
    public long
    getItemIdOfDownloadTask(BGTask task) {
        // Nick is task id.
        // See BGTM for details
        String tid;
        synchronized (bgtm) {
            tid = bgtm.getTaskId(task);
        }
        eAssert(Action.DOWNLOAD == actionFromTid(tid));
        return idFromTid(tid);
    }

    public long[]
    getChannelsUpdating() {
        return getIdsInAction(Action.UPDATE);
    }
}
