/*****************************************************************************
 *    Copyright (C) 2012, 2013 Younghyung Cho. <yhcting77@gmail.com>
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
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;

// Singleton
// Runtime Data
//   : Data that SHOULD NOT be stored at DataBase.
//       but, need to be check in runtime.
// Should be THREAD-SAFE
public class RTTask implements
UnexpectedExceptionHandler.TrackedModule,
OnSharedPreferenceChangeListener {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(RTTask.class);

    private static RTTask   sInstance = null;

    // Dependency on only following modules are allowed
    // - Utils
    // - UnexpectedExceptionHandler
    // - DB / DBThread
    // - UIPolicy
    // - DBPolicy
    private final DBPolicy              mDbp    = DBPolicy.get();
    private final BGTaskManager         mBgtm   = new BGTaskManager();;
    private final LinkedList<BGTask>    mReadyQ = new LinkedList<BGTask>();
    private final LinkedList<BGTask>    mRunQ   = new LinkedList<BGTask>();

    // NOTE
    // Why mTaskQSync is used instead of using 'mReadyQ' and 'mRunQ' object directly as an sync object for each list?
    // This is to avoid following unexpected state
    //   "Task is not in mReadyQ and mRunQ. But is not Idle!"
    // If 'mReadyQ' and 'mRunQ' are used as synch. object for their own list,
    //   above unexpected state is unavoidable.
    // For example.
    //   synchronized (mReadyQ) {
    //       t = mReadyQ.pop();
    //   }
    //   <==== Unexpected state! ====>
    //   synchronized (mRunQ) {
    //       mRunQ.addLast(t);
    //   }
    private final Object       mTaskQSync = new Object();
    private final KeyBasedLinkedList<OnRegisterListener> mRegisterListenerl
                    = new KeyBasedLinkedList<OnRegisterListener>();
    private final KeyBasedLinkedList<OnTaskQueueChangedListener> mTaskQChangedListenerl
                    = new KeyBasedLinkedList<OnTaskQueueChangedListener>();
    private volatile int       mMaxConcurrent = 2; // temporally hard coding.

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

    private class RunningBGTaskListener extends BaseBGTask.OnEventListener {
        private void
        onEnd(BGTask task) {
            // NOTE
            // Handling race condition with 'start()' function is very important!
            // Order of these codes are deeply related with avoiding unexpected race-condition.
            // DO NOT change ORDER of code line!
            BGTask t = null;
            synchronized (mTaskQSync) {
                mRunQ.remove(task);
                if (!mReadyQ.isEmpty()) {
                    t = mReadyQ.pop();
                    mRunQ.addLast(t);
                }
            }

            synchronized (mBgtm) {
                if (null != t) {
                    mBgtm.start(mBgtm.getTaskId(t));
                }
            }

            notifyTaskQChanged(task, false);
        }

        @Override
        public void
        onCancelled(BaseBGTask task, Object param) {
            onEnd((BGTask)task);
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            onEnd((BGTask)task);
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
        if (null == sInstance)
            sInstance = new RTTask();
        return sInstance;
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
        mMaxConcurrent = v;
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
        synchronized (mTaskQSync) {
            if (mRunQ.contains(task) || mReadyQ.contains(task))
                return true;
        }
        eAssert(ThreadEx.State.RUNNING != task.getState());
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
        synchronized (mBgtm) {
            tids = mBgtm.getTaskIds();
            for (String tid : tids) {
                if (action == actionFromTid(tid)) {
                    BGTask task = mBgtm.peek(tid);
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
        // But to increase readability... because mTaskQChangedListenerl assumes handled on ui thread.
        //   - not thread safe!

        eAssert(Utils.isUiThread());
        String tid;
        synchronized (mBgtm) {
            tid = mBgtm.getTaskId(task);
        }
        long id = idFromTid(tid);
        Action act = actionFromTid(tid);

        Iterator<OnTaskQueueChangedListener> iter = mTaskQChangedListenerl.iterator();
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
        mRegisterListenerl.add(key, listener);
    }

    public void
    unregisterRegisterEventListener(Object key) {
        eAssert(Utils.isUiThread());
        mRegisterListenerl.remove(key);
    }

    public void
    registerTaskQChangedListener(Object key, OnTaskQueueChangedListener listener) {
        eAssert(Utils.isUiThread());
        eAssert(null != key && null != listener);
        mTaskQChangedListenerl.add(key,  listener);
    }

    public void
    unregisterTaskQChangedListener(Object key) {
        eAssert(Utils.isUiThread());
        mTaskQChangedListenerl.remove(key);
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
    register(final long id, final Action act, final BGTask task) {
        //eAssert(Utils.isUiThread());
        boolean r;
        synchronized (mBgtm) {
            r = mBgtm.register(tid(act, id), task);
        }

        if (r) {
            Utils.getUiHandler().post(new Runnable() {
                @Override
                public void
                run() {
                    Iterator<OnRegisterListener> itr = mRegisterListenerl.iterator();
                    while (itr.hasNext())
                        itr.next().onRegister(task, id, act);
                }
            });
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
    unregister(final long id, final Action act) {
        //eAssert(Utils.isUiThread());
        String taskId = tid(act, id);

        boolean r = false;
        final BGTask task;
        synchronized (mBgtm) {
            // remove from manager.
            task = mBgtm.peek(taskId);
            r = mBgtm.unregister(taskId);
        }

        if (!r || null == task)
            return false;

        Utils.getUiHandler().post(new Runnable() {
            @Override
            public void
            run() {
                Iterator<OnRegisterListener> itr = mRegisterListenerl.iterator();
                while (itr.hasNext())
                    itr.next().onUnregister(task, id, act);
            }
        });
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
        synchronized (mBgtm) {
            return mBgtm.unbind(key);
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
    bind(long id, Action act, Object key, BaseBGTask.OnEventListener listener) {
        synchronized (mBgtm) {
            return mBgtm.bind(tid(act, id), key, listener, false);
        }
    }

    /**
     * get Number of active tasks.
     * (Running task + task waiting it's turn in the ready queue.)
     * @return
     */
    public int
    getNRActiveTask() {
        synchronized (mTaskQSync) {
            return mRunQ.size() + mReadyQ.size();
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
        synchronized (mBgtm) {
            return mBgtm.peek(tid(act, id));
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
        synchronized (mBgtm) {
            task = mBgtm.peek(tid(act, id));
        }

        if (null == task)
            return TaskState.IDLE;

        synchronized (mTaskQSync) {
            if (mRunQ.contains(task))
                return task.isCancelled()? TaskState.CANCELING: TaskState.RUNNING;

            if (mReadyQ.contains(task))
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
        synchronized (mBgtm) {
            task = mBgtm.peek(tid(act, id));
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
        synchronized (mBgtm) {
            t = mBgtm.peek(taskId);
            if (null == t)
                return false;
        }
        // NOTE
        // See comments in RunningBGTaskListener.onEnd
        // DO NOT change ORDER of code line.
        boolean bStartImmediate = false;
        synchronized (mTaskQSync) {
            // Why remove 't' at this moment?
            // This means, "if task is already in ready state,
            //   it will move to last of the list."
            // In summary, "start request cancels previous one and newly added".
            mReadyQ.remove(t);

            // NOTE
            // This SHOULD be 'bindPrior' is used.
            // operation related with 'mRunQ' and 'mReadyQ' SHOULD BE DONE
            //   before normal event listener is called!
            synchronized (mBgtm) {
                mBgtm.bind(tid(act, id), this, new RunningBGTaskListener(), true);
            }
            // If there is no running task then start NOW!
            if (mRunQ.size() < mMaxConcurrent) {
                bStartImmediate = true;
                mRunQ.addLast(t);
            } else
                mReadyQ.addLast(t);
        }

        synchronized (mBgtm) {
            if (bStartImmediate)
                mBgtm.start(taskId);
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
        synchronized (mBgtm) {
            t = mBgtm.peek(tid(act, id));
            if (null == t)
                return true;
        }

        boolean fromReadyQ;
        synchronized (mTaskQSync) {
            fromReadyQ = mReadyQ.remove(t);
        }

        if (fromReadyQ) {
            // Not-started-task is cancelled.
            // That means "onCancelled or onPostRun is not called".
            // Therefore we should notify that task state is changed, at this moment.
            synchronized (mBgtm) {
                mBgtm.unregister(tid(act, id));
            }
            notifyTaskQChanged(t, false);
            return true;
        } else {
            synchronized (mBgtm) {
                return mBgtm.cancel(tid(act, id), arg);
            }
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
        synchronized (mBgtm) {
            return mBgtm.peek(tid(act, id)).getResult();
        }
    }

    public void
    cancelAll() {
        synchronized (mBgtm) {
            mBgtm.cancelAll();
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
            mDbp.getDelayedChannelUpdate();
            for (long dnid : dnids)
                if (set.contains(mDbp.getItemInfoLong(dnid, ColumnItem.CHANNELID)))
                    l.add(dnid);
        } finally {
            mDbp.putDelayedChannelUpdate();
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
        synchronized (mBgtm) {
            tid = mBgtm.getTaskId(task);
        }
        eAssert(Action.DOWNLOAD == actionFromTid(tid));
        return idFromTid(tid);
    }

    public long[]
    getChannelsUpdating() {
        return getIdsInAction(Action.UPDATE);
    }
}
