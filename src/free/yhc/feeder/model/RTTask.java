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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
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

    public void
    init(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, "maxnr_bgtask");
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
        max_concurrent = v;
    }

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
     * Get items that are under downloading.
     * @param cids
     *   null : all downloading items
     *   otherwise : downloading items belonging to given channels.
     * @return
     */
    private long[]
    itemsDownloading(long[] cids) {
        String[] tids;
        // Create cids map for fast searching.
        HashMap<Long, Object> m = null;
        if (null != cids) {
            m = new HashMap<Long, Object>();
            for (long cid : cids)
                m.put(cid, new Object());
        }

        LinkedList<Long> l = new LinkedList<Long>();
        synchronized (gbtm) {
            tids = gbtm.getTaskIds();
            for (String tid : tids) {
                if (Action.Download == actionFromId(tid)) {
                    BGTask task = gbtm.peek(tid);
                    long id = idFromId(tid);
                    if (null != task && isTaskInAction(task)) {
                        if (null == m)
                            l.add(id); // all items
                        else if (null != m.get(DBPolicy.S().getItemInfoLong(id, DB.ColumnItem.CHANNELID)))
                            l.add(id);
                    }
                }
            }
        }
        return Utils.convertArrayLongTolong(l.toArray(new Long[0]));
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

    /**
     * Register task.
     * All BG task used SHOULD BE registered at RTTask.
     * If not, task cannot be handled anymore.
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
        synchronized (gbtm) {
            boolean r = gbtm.register(Id(act, id), task);
            if (r) {
                for (ManagerEventListener el : eventListenerl.toArray(new ManagerEventListener[0]))
                    el.listener.onBGTaskRegister(id, task, act);
            }
            return r;
        }
    }


    /**
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

    /**
     * Unbind given task.
     * @param id
     * @param act
     * @return
     *   number of tasks unbinded (0 or 1)
     */
    public int
    unbind(long id, Action act) {
        synchronized (gbtm) {
            return gbtm.unbind(Thread.currentThread(), Id(act, id));
        }
    }

    /**
     * Unbind all event listener of BGTask, whose event key matches given one.
     * @param onEventKey
     * @return
     *   number of tasks unbinded.
     */
    public int
    unbind(Object onEventKey) {
        synchronized (gbtm) {
            return gbtm.unbind(Thread.currentThread(), onEventKey);
        }
    }

    /**
     * Bind event listener of BGTask with key value.
     * @param id
     * @param act
     * @param onEventKey
     * @param onEvent
     * @return
     */
    public BGTask
    bind(long id, Action act, Object onEventKey, BGTask.OnEvent onEvent) {
        synchronized (gbtm) {
            return gbtm.bind(Id(act, id), onEventKey, onEvent);
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
        synchronized (gbtm) {
            return gbtm.peek(Id(act, id));
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
        synchronized (gbtm) {
            task = gbtm.peek(Id(act, id));
        }

        if (null == task)
            return TaskState.Idle;

        synchronized (taskQSync) {
            if (runQ.contains(task))
                return task.isCancelled()? TaskState.Canceling: TaskState.Running;

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

    /**
     * Result of BG task is stored internally to refer later after BG task is terminated.
     * This function reset stored result value manually.
     * @param id
     * @param act
     */
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

    /**
     * Start task.
     * @param id
     * @param act
     * @return
     */
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

    /**
     * Get result of BG task.
     * @param id
     * @param act
     * @return
     */
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
    /**
     * Get item ids that are under downloading.
     * In other words, item ids that are bindded to BGTask whose action type is 'Download'
     * @return
     */
    public long[]
    getItemsDownloading() {
        return itemsDownloading(null);
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
        return itemsDownloading(new long[] { cid });
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
        return itemsDownloading(cids);
    }
}
