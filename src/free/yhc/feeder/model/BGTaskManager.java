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
    private OnEventListener           eventListener = new OnEventListener();

    // TaskMap Value
    private class TaskMapV {
        Thread          owner = null;
        BGTask<?, ?, ?> task  = null;
        TaskMapV(BGTask<?, ?, ?> task) {
            this.task = task;
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

    private class OnEventListener implements BGTask.OnEvent<Object, Object, Object> {
        @Override
        public void
        onPreRun(BGTask task, Object user) {
            logW("onPreRun at BGTaskManager will be IGNORED!");
        }

        @Override
        public void
        onPostRun(BGTask task, Object user, Err result) {
            logW("onPostRun at BGTaskManager will be IGNORED!");
        }

        @Override
        public void
        onCancel(BGTask task, Object param, Object user) {
            logW("onCancel at BGTaskManager will be IGNORED");
        }

        @Override
        public void
        onProgress(BGTask task, Object user, int progress) {
            // nothing to do.
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
    register(String taskId, BGTask<?, ?, ?> task) {
        logI("BGTM : register :" + taskId);
        if (null != map.get(taskId)) {
            eAssert(false);
            return false;
        }
        map.put(taskId, new TaskMapV(task));
        return true;
    }

    private void
    _unbind(TaskMapV v) {
        v.owner = looper;
        v.task.attach(looper.getEventLoopHandler());
        v.task.setOnEventListener(eventListener);
    }

    int
    unbind(String taskId) {
        logI("BGTM : unbind :" + taskId);
        TaskMapV v = map.get(taskId);
        if (null == v)
            return 0;
        _unbind(v);
        return 1;
    }

    int
    unbind(BGTask.OnEvent onEvent) {
        int        ret = 0;
        TaskMapV[] vs = map.values().toArray(new TaskMapV[0]);
        for (TaskMapV v : vs)
            if (v.task.getOnEvent().equals(onEvent)) {
                _unbind(v);
                ret++;
            }
        return ret;
    }

    int
    unbind(Thread owner) {
        int        ret = 0;
        TaskMapV[] vs = map.values().toArray(new TaskMapV[0]);
        for (TaskMapV v : vs)
            if (v.owner.equals(owner)) {
                _unbind(v);
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
        v.task.attach();
        v.task.setOnEventListener(onEvent);
        return v.task;
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
}
