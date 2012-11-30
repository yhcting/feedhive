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



// Used only at 'RTData'
// This should be THREAD-SAFE
class BGTaskManager implements
UnexpectedExceptionHandler.TrackedModule {
    private final HashMap<String, TaskMapElem> mMap = new HashMap<String, TaskMapElem>();

    // TaskMap Value
    private class TaskMapElem {
        BGTask          task   = null;
        TaskMapElem(BGTask aTask, String taskId) {
            task = aTask;
            task.setName(taskId);
        }
    }

    BGTaskManager() {
        UnexpectedExceptionHandler.get().registerModule(this);
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
        //logI("BGTM : register :" + taskId);
        if (null != mMap.get(taskId)) {
            eAssert(false);
            return false;
        }
        mMap.put(taskId, new TaskMapElem(task, taskId));
        return true;
    }

    /**
     * Unbind tasks that owner and onEventKey match.
     * @param key
     * @return
     *   number of tasks unbinded.
     */
    int
    unbind(Object key) {
        int        ret = 0;
        TaskMapElem[] vs = mMap.values().toArray(new TaskMapElem[0]);
        for (TaskMapElem v : vs) {
            v.task.unregisterEventListener(key, null);
            //logI("BGTM : unbind (onEventKey) :" + v.task.getNick());
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
        TaskMapElem v = mMap.get(taskId);
        return (null == v)? null: v.task;
    }

    /**
     * Newly bound event will be registered to the last of listener list.
     * @param taskId
     * @param key
     *   this can be associated with several onEvent.
     * @param listener
     * @param hasPriority
     *   true if listener SHOULD receive event prior to other existing listeners.
     * @return
     */
    BGTask
    bind(String taskId, Object key, BaseBGTask.OnEventListener listener, boolean hasPriority) {
        //logI("BGTM : bind : " + taskId + " : " + onEvent.toString());
        TaskMapElem v = mMap.get(taskId);
        if (null == v)
            return null;
        v.task.registerEventListener(key, listener, hasPriority);
        return v.task;
    }

    /**
     * Clear event listener of given BGTask.
     * @param taskId
     * @return
     */
    int
    clear(String taskId) {
        TaskMapElem v = mMap.get(taskId);
        if (null == v)
            return 0;
        //logI("BGTM : clear :" + v.task.getNick());
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
        //logI("BGTM : unregister :" + taskId);
        if (null == mMap.get(taskId))
            return false;
        mMap.remove(taskId);
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
        TaskMapElem v = mMap.get(taskId);
        if (null == v)
            return false;

        v.task.run();
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
        TaskMapElem v = mMap.get(taskId);
        if (null == v)
            return false;

        return v.task.cancel(arg);
    }

    String
    getTaskId(BGTask task) {
        return task.getName();
    }

    /**
     * Get all task Ids registered.
     * @return
     */
    String[]
    getTaskIds() {
        return mMap.keySet().toArray(new String[0]);
    }

    /**
     * Get all BGTasks registered.
     * @return
     */
    BGTask[]
    getTasks() {
        TaskMapElem[] mv = mMap.values().toArray(new TaskMapElem[0]);
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
        TaskMapElem[] vs = mMap.values().toArray(new TaskMapElem[0]);
        for (TaskMapElem v : vs) {
            v.task.clearEventListener();
            v.task.cancel(null);
        }
    }
}
