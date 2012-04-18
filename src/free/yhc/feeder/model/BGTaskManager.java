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
    private HashMap<String, TaskMapV> map = new HashMap<String, TaskMapV>();

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

    BGTask
    peek(String taskId) {
        //logI("BGTM : peek [" + taskId);
        TaskMapV v = map.get(taskId);
        return (null == v)? null: v.task;
    }

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

    BGTask
    bindPrior(String taskId, Object onEventKey, BGTask.OnEvent onEvent) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return null;
        v.owner = Thread.currentThread();
        v.task.registerPriorEventListener(onEventKey, onEvent);
        return v.task;
    }

    int
    clear(String taskId) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return 0;
        logI("BGTM : clear :" + v.task.getNick());
        v.task.clearEventListener();
        return 1;
    }

    boolean
    unregister(String taskId) {
        logI("BGTM : unregister :" + taskId);
        if (null == map.get(taskId))
            return false;
        map.remove(taskId);
        return true;
    }

    boolean
    start(String taskId) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return false;

        v.task.start();
        return true;
    }

    boolean
    cancel(String taskId, Object arg) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return false;

        return v.task.cancel(arg);
    }

    String[]
    getTaskIds() {
        return map.keySet().toArray(new String[0]);
    }

    BGTask[]
    getTasks() {
        TaskMapV[] mv = map.values().toArray(new TaskMapV[0]);
        BGTask[] ts = new BGTask[mv.length];
        for (int i = 0; i < ts.length; i++)
            ts[i] = mv[i].task;
        return ts;
    }

    void
    cancelAll() {
        TaskMapV[] vs = map.values().toArray(new TaskMapV[0]);
        for (TaskMapV v : vs) {
            v.task.clearEventListener();
            v.task.cancel(null);
        }
    }
}
