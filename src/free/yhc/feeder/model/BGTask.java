package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.util.Iterator;
import java.util.LinkedList;

import android.os.Handler;

public class BGTask<RunParam, CancelParam> extends Thread {
    private Handler          ownerHandler = new Handler();
    private LinkedList<EventListener> listenerList = new LinkedList<EventListener>();
    private volatile Err     result  = Err.NoErr;
    private volatile boolean cancelled = false;

    private RunParam         runParam    = null;
    private CancelParam      cancelParam = null;

    public interface OnEvent<RunParam, CancelParam> {
        // return : false (DO NOT run this task)
        void onPreRun  (BGTask<RunParam, CancelParam> task);
        void onPostRun (BGTask<RunParam, CancelParam> task, Err result);
        void onCancel  (BGTask<RunParam, CancelParam> task, CancelParam param);
        void onProgress(BGTask<RunParam, CancelParam> task, long progress);
    }

    public static class EventListener {
        private Object      key;
        private Handler     handler;
        private OnEvent     onEvent;

        EventListener(Object key, Handler handler, OnEvent onEvent) {
            this.key = key;
            this.handler = handler;
            this.onEvent = onEvent;
        }

        Object getKey() {
            return key;
        }

        Handler getHandler() {
            return handler;
        }

        OnEvent getOnEvent() {
            return onEvent;
        }
    }

    private class BGTaskPost implements Runnable {
        private boolean bCancelled;
        BGTaskPost(boolean bCancelled) {
            this.bCancelled = bCancelled;
        }

        @Override
        public void run() {
            if (BGTask.this.isAlive()) {
                logI("BGTask : REPOST : wait for task is Done");
                ownerHandler.post(new BGTaskPost(bCancelled));
                return;
            }

            logI("BGTask : REAL POST!!! cancelled(" + bCancelled + ") NR Listener (" + getListeners().length);
            if (bCancelled) {
                BGTask.this.onCancel(cancelParam);
                for (EventListener listener : getListeners()) {
                    final OnEvent onEvent = listener.getOnEvent();
                    logI("BGTask : Post(Cancel) to onEvent : " + onEvent.toString());
                    listener.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            onEvent.onCancel(BGTask.this, cancelParam);
                        }
                    });
                }
            } else {
                BGTask.this.onPostRun(result);
                for (EventListener listener : getListeners()) {
                    final OnEvent onEvent = listener.getOnEvent();
                    logI("BGTask : Post(Post) to onEvent : " + onEvent.toString());
                    listener.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            logI("+++ BGTask : postRun : " + onEvent.toString());
                            onEvent.onPostRun(BGTask.this, result);
                        }
                    });
                }
            }
        }
    }

    public BGTask() {
        super();
    }

    public BGTask(Object onEventKey, OnEvent onEvent) {
        super();
        synchronized (listenerList) {
            EventListener listener = new EventListener(onEventKey, new Handler(), onEvent);
            listenerList.addLast(listener);
        }
    }

    // ==========================================
    // Package Private
    // ==========================================
    private EventListener[]
    getListeners() {
        synchronized (listenerList) {
            return listenerList.toArray(new EventListener[0]);
        }
    }
    // ==========================================
    // Package Private
    // ==========================================
    void
    resetResult() {
        eAssert(!isAlive());
        result = Err.NoErr;
    }

    // ==========================================
    // Open Interface
    // ==========================================
    public Err
    getResult() {
        eAssert(null != result);
        return result;
    }

    public void
    registerEventListener(Object key, OnEvent onEvent) {
        // This is atomic expression
        synchronized (listenerList) {
            logI("BGTask : registerEventListener : key(" + key + ") onEvent(" + onEvent + ")");
            EventListener listener = new EventListener(key, new Handler(), onEvent);
            listenerList.addLast(listener);
        }
    }

    public void
    unregisterEventListener(Thread owner) {
        synchronized (listenerList) {
            Iterator<EventListener> iter = listenerList.iterator();
            while (iter.hasNext()) {
                EventListener listener = iter.next();
                if (listener.getHandler().getLooper().getThread() == owner) {
                    logI("BGTask : unregisterEventListener : (" + listener.key + ") onEvent(" + listener.onEvent + ")");
                    iter.remove();
                }
            }
        }
    }

    public void
    unregisterEventListener(Thread owner, Object onEventKey) {
        synchronized (listenerList) {
            Iterator<EventListener> iter = listenerList.iterator();
            while (iter.hasNext()) {
                EventListener listener = iter.next();
                if (listener.getHandler().getLooper().getThread() == owner
                    && listener.getKey() == onEventKey) {
                    logI("BGTask : unregisterEventListener : (" + listener.key + ") onEvent(" + listener.onEvent + ")");
                    iter.remove();
                }
            }
        }
    }

    public void
    clearEventListener() {
        synchronized (listenerList) {
            logI("BGTask : clearEventListener");
            listenerList.clear();
        }
    }

    // main background task to do.
    protected Err
    doBGTask(RunParam runParam)
        throws FeederException {
        return Err.NoErr;
    }

    protected void
    onPreRun() {}

    protected void
    onPostRun (Err result) {}

    protected void
    onCancel(CancelParam param) {}

    protected void
    onProgress(long progress) {}

    @Override
    public final void
    run() {
        logI("BGTask : BGJobs started");
        boolean bInterrupted = false;
        try {
            result =  doBGTask(runParam);
            eAssert(null != result);
        } catch (FeederException e) {
            if (Err.Interrupted == e.getError())
                bInterrupted = true;
        }

        if (!bInterrupted)
            bInterrupted = Thread.currentThread().isInterrupted();

        if (cancelled && Err.NoErr != result)
            result = Err.UserCancelled;

        logI("BGTask : BGJobs done - try to post! : " + result.name() + " interrupted(" + bInterrupted + ")");

        if (bInterrupted
            || Err.Interrupted == result
            || Err.UserCancelled == result)
            ownerHandler.post(new BGTaskPost(true));
        else
            ownerHandler.post(new BGTaskPost(false));
    }

    public void
    publishProgress(final long progress) {
        onProgress(progress);
        for (EventListener listener : getListeners()) {
            final OnEvent onEvent = listener.getOnEvent();
            listener.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    onEvent.onProgress(BGTask.this, progress);
                }
            });
        }
    }

    public final void
    start(RunParam runParam) {
        onPreRun();
        for (EventListener listener : getListeners()) {
            final OnEvent onEvent = listener.getOnEvent();
            listener.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    onEvent.onPreRun(BGTask.this);
                }
            });
        }
        this.runParam = runParam;
        super.start();
    }

    public boolean
    cancel(CancelParam param) {
        cancelled = true;
        result = Err.UserCancelled;
        cancelParam = param;
        logI("BGTask : cancel()");
        interrupt();
        return true; // always success.
    }
}
