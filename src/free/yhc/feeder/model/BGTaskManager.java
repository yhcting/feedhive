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
import static free.yhc.feeder.model.Utils.logI;

import java.util.HashMap;



// Used only at 'RTData'
// This should be THREAD-SAFE
class BGTaskManager implements
UnexpectedExceptionHandler.TrackedModule {
    private final HashMap<String, TaskMapV> map = new HashMap<String, TaskMapV>();

    // TaskMap Value
    private class TaskMapV {
        Thread          owner  = null;
        BGTask          task   = null;
        TaskMapV(BGTask task, String taskId) {
            task.setNick(taskId);
            this.task = task;
        }
    }

    BGTaskManager() {
        UnexpectedExceptionHandler.S().registerModule(this);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ BGTaskManager ]";
    }

    /**
     * Register task with given taskId.
     * Registering task whose Id is already used, is NOT ALLOWED (assert will be issued.)
     * @param taskId
     * @param task
     * @return
     *   false if there is already same-id-task (this SHOULD NOT happen).
     */
    boolean
    register(String taskId, BGTask task) {
        logI("BGTM : register :" + taskId);
        if (null != map.get(taskId)) {
            eAssert(false);
            return false;
        }
        map.put(taskId, new TaskMapV(task, taskId));
        return true;
    }

    /**
     * Unbind task that id and owner match.
     * @param owner
     * @param taskId
     * @return
     *   number of tasks unbinded (0 or 1)
     */
    int
    unbind(Thread owner, String taskId) {
        logI("BGTM : unbind :" + taskId);
        TaskMapV v = map.get(taskId);
        if (null == v || v.owner != owner)
            return 0;
        logI("BGTM : unbind :" + v.task.getNick());
        v.task.unregisterEventListener(owner);
        return 1;
    }

    /**
     * Unbind tasks whose owner matches.
     * @param owner
     * @return
     *   number of tasks unbinded.
     */
    int
    unbind(Thread owner) {
        int        ret = 0;
        TaskMapV[] vs = map.values().toArray(new TaskMapV[0]);
        for (TaskMapV v : vs)
            if (v.owner == owner) {
                v.task.unregisterEventListener(owner);
                logI("BGTM : unbind (owner):" + v.task.getNick());
                ret++;
            }
        return ret;
    }

    /**
     * Unbind tasks that owner and onEventKey match.
     * @param owner
     * @param onEventKey
     * @return
     *   number of tasks unbinded.
     */
    int
    unbind(Thread owner, Object onEventKey) {
        int        ret = 0;
        TaskMapV[] vs = map.values().toArray(new TaskMapV[0]);
        for (TaskMapV v : vs)
            if (v.owner == owner) {
                v.task.unregisterEventListener(owner, onEventKey);
                logI("BGTM : unbind (onEventKey) :" + v.task.getNick());
                ret++;
            }
        return ret;
    }

    /**
     * Getting BGTask.
     * This doesn't change any state.
     * @param taskId
     * @return
     */
    BGTask
    peek(String taskId) {
        //logI("BGTM : peek [" + taskId);
        TaskMapV v = map.get(taskId);
        return (null == v)? null: v.task;
    }

    /**
     * Newly bound event will be registered to the last of listener list.
     * @param taskId
     * @param onEventKey
     *   this can be associated with several onEvent.
     * @param onEvent
     * @return
     */
    BGTask
    bind(String taskId, Object onEventKey, BGTask.OnEvent onEvent) {
        logI("BGTM : bind : " + taskId + " : " + onEvent.toString());
        TaskMapV v = map.get(taskId);
        if (null == v)
            return null;
        v.owner = Thread.currentThread();
        v.task.registerEventListener(onEventKey, onEvent);
        return v.task;
    }

    /**
     * This is same with 'bind' except for that newly bound event will be
     *   registered to the first of listener list.
     * This SHOULD be used CAREFULLY.
     * @param taskId
     * @param onEventKey
     * @param onEvent
     * @return
     */
    BGTask
    bindPrior(String taskId, Object onEventKey, BGTask.OnEvent onEvent) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return null;
        v.owner = Thread.currentThread();
        v.task.registerPriorEventListener(onEventKey, onEvent);
        return v.task;
    }

    /**
     * Clear event listener of given BGTask.
     * @param taskId
     * @return
     */
    int
    clear(String taskId) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return 0;
        logI("BGTM : clear :" + v.task.getNick());
        v.task.clearEventListener();
        return 1;
    }

    /**
     * Unregister task.
     * This doens't interrupt(or cancel) running background task.
     * @param taskId
     * @return
     */
    boolean
    unregister(String taskId) {
        logI("BGTM : unregister :" + taskId);
        if (null == map.get(taskId))
            return false;
        map.remove(taskId);
        return true;
    }

    /**
     * THIS SHOULD BE CALLED ONLY BY 'RTTask'
     * Start task
     * @param taskId
     * @return
     *   true (success) / false (fail to find task)
     */
    boolean
    start(String taskId) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return false;

        v.task.start();
        return true;
    }

    /**
     * THIS SHOULD BE CALLED ONLY BY 'RTTask'
     * Cancel background task.
     * given 'arg' is passed to onCancel() of each listener.
     * @param taskId
     * @param arg
     * @return
     */
    boolean
    cancel(String taskId, Object arg) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return false;

        return v.task.cancel(arg);
    }

    /**
     * Get all task Ids registered.
     * @return
     */
    String[]
    getTaskIds() {
        return map.keySet().toArray(new String[0]);
    }

    /**
     * Get all BGTasks registered.
     * @return
     */
    BGTask[]
    getTasks() {
        TaskMapV[] mv = map.values().toArray(new TaskMapV[0]);
        BGTask[] ts = new BGTask[mv.length];
        for (int i = 0; i < ts.length; i++)
            ts[i] = mv[i].task;
        return ts;
    }

    /**
     * Cancel all registred tasks.
     */
    void
    cancelAll() {
        TaskMapV[] vs = map.values().toArray(new TaskMapV[0]);
        for (TaskMapV v : vs) {
            v.task.clearEventListener();
            v.task.cancel(null);
        }
    }
}
