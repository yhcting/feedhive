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
        BGTask<?, ?, ?> task = null;
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
            eAssert(false);
            logW("onPreRun at BGTaskManager SHOULD NOT be called!");
        }

        @Override
        public void
        onPostRun(BGTask task, Object user, Err result) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param, Object user) {
            eAssert(false);
            logW("onCancel at BGTaskManager SHOULD NOT be called!");
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

    void
    register(String taskId, BGTask<?, ?, ?> task) {
        logI("BGTM : register :" + taskId);
        synchronized (map) {
            eAssert(null == map.get(taskId));
            map.put(taskId, new TaskMapV(task));
        }
    }

    void
    unbind(String taskId) {
        logI("BGTM : unbind :" + taskId);
        synchronized (map) {
            TaskMapV v = map.get(taskId);
            eAssert(null != v);
            synchronized (v) {
                v.task.attach(looper.getEventLoopHandler());
                v.task.setOnEventListener(eventListener);
            }
        }
    }

    BGTask
    peek(String taskId) {
        //logI("BGTM : peek [" + taskId);
        TaskMapV v;
        synchronized (map) {
            v = map.get(taskId);
        }
        return (null == v)? null: v.task;
    }

    BGTask
    bind(String taskId, BGTask.OnEvent onEvent) {
        logI("BGTM : bind :" + taskId);
        TaskMapV v;
        synchronized (map) {
            v = map.get(taskId);
            if (null == v)
                return null;
        }
        synchronized (v.task) {
            v.task.attach();
            v.task.setOnEventListener(onEvent);
        }
        return v.task;
    }

    void
    unregister(String taskId) {
        logI("BGTM : unregister :" + taskId);
        synchronized (map) {
            eAssert(null != map.get(taskId));
            map.remove(taskId);
        }
    }
}
