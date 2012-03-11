package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.util.HashMap;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;



// Used only at 'RTData'
// This should be THREAD-SAFE
class BGTaskManager {
    private EventMessageLooper        looper = new EventMessageLooper();
    private HashMap<String, TaskMapV> map = new HashMap<String, TaskMapV>();

    // TaskMap Value
    private class TaskMapV {
        Thread          owner  = null;
        String          taskId = null;
        BGTask          task   = null;
        TaskMapV(BGTask task, String taskId) {
            this.task = task;
            this.taskId = taskId;
        }
    }

    private class EventMessageLooper extends Thread {
        private Handler handler = null;

        Handler
        getEventLoopHandler() {
            return handler;
        }

        @Override
        public void run() {
            logI("BGTaskManager EventLoop started!");
            Looper.prepare();
            handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                }
            };
            Looper.loop();
        }
    }

    BGTaskManager() {
        looper.start();
        while (true) {
            if (null != looper.getEventLoopHandler())
                break;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                logW("BGTaskManager Initialization is interrupted.")
                ;
            }
        }
    }

    Object
    getSyncObj() {
        return map;
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
        logI("BGTM : bind :" + taskId);
        TaskMapV v = map.get(taskId);
        if (null == v || v.owner != owner)
            return 0;

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
    bind(String taskId, BGTask.OnEvent onEvent) {
        logI("BGTM : bind :" + taskId);
        TaskMapV v = map.get(taskId);
        if (null == v)
            return null;
        v.owner = Thread.currentThread();
        v.task.registerEventListener(onEvent);
        return v.task;
    }

    int
    clear(String taskId) {
        TaskMapV v = map.get(taskId);
        if (null == v)
            return 0;
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
