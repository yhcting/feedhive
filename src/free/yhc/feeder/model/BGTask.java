package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.Iterator;
import java.util.LinkedList;

import android.os.Handler;

public class BGTask<RunParam, CancelParam> extends Thread {
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
        void onProgress(BGTask<RunParam, CancelParam> task, int progress);
    }

    public static class EventListener {
        private Handler     handler;
        private OnEvent     onEvent;

        EventListener(Handler handler, OnEvent onEvent) {
            this.handler = handler;
            this.onEvent = onEvent;
        }

        Handler getHandler() {
            return handler;
        }

        OnEvent getOnEvent() {
            return onEvent;
        }
    }

    public BGTask() {
        super();
    }

    public BGTask(OnEvent onEvent) {
        super();
        synchronized (listenerList) {
            EventListener listener = new EventListener(new Handler(), onEvent);
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
    registerEventListener(OnEvent onEvent) {
        // This is atomic expression
        synchronized (listenerList) {
            EventListener listener = new EventListener(new Handler(), onEvent);
            listenerList.addLast(listener);
        }
    }

    public void
    unregisterEventListener(Thread owner) {
        synchronized (listenerList) {
            Iterator<EventListener> iter = listenerList.iterator();
            while (iter.hasNext()) {
                EventListener listener = iter.next();
                if (listener.getHandler().getLooper().getThread() == owner)
                    iter.remove();
            }
        }
    }

    public void
    clearEventListener() {
        synchronized (listenerList) {
            listenerList.clear();
        }
    }

    // main background task to do.
    protected Err
    doBGTask(RunParam runParam)
        throws FeederException {
        return Err.NoErr;
    }

    @Override
    public final void
    run() {
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

        if (bInterrupted
            || Err.Interrupted == result
            || Err.UserCancelled == result) {
            for (EventListener listener : getListeners()) {
                final OnEvent onEvent = listener.getOnEvent();
                listener.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onEvent.onCancel(BGTask.this, cancelParam);
                    }
                });
            }
        } else {
            for (EventListener listener : getListeners()) {
                final OnEvent onEvent = listener.getOnEvent();
                listener.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onEvent.onPostRun(BGTask.this, result);
                    }
                });
            }
        }
    }

    public void
    publishProgress(final int progress) {
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
        interrupt();
        return true; // always success.
    }
}
