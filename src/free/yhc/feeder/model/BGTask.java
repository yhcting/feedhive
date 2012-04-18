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

import java.util.Iterator;
import java.util.LinkedList;

import android.os.Handler;

public class BGTask<RunParam, CancelParam> extends Thread {
    private String           nick = null; // can be used several purpose.
    private Handler          ownerHandler = new Handler();
    private LinkedList<EventListener> listenerList = new LinkedList<EventListener>();
    private volatile Err     result  = Err.NoErr;

    // Flags
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

    public BGTask(RunParam arg) {
        super();
        runParam = arg;
    }

    public BGTask(Object onEventKey, OnEvent onEvent) {
        super();
        synchronized (listenerList) {
            EventListener listener = new EventListener(onEventKey, new Handler(), onEvent);
            listenerList.addLast(listener);
        }
    }

    // ==========================================
    // Private
    // ==========================================
    private EventListener[]
    getListeners() {
        synchronized (listenerList) {
            return listenerList.toArray(new EventListener[0]);
        }
    }
    // ==========================================
    // Protected
    // ==========================================

    // ==========================================
    // Package Private
    // ==========================================
    void
    resetResult() {
        eAssert(!isAlive());
        result = Err.NoErr;
    }

    void
    setNick(String nick) {
        this.nick = nick;
    }

    // ==========================================
    // Open Interface
    // ==========================================
    public String
    getNick() {
        return nick;
    }

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
    registerPriorEventListener(Object key, OnEvent onEvent) {
        synchronized (listenerList) {
            EventListener listener = new EventListener(key, new Handler(), onEvent);
            listenerList.addFirst(listener);
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

    // This SHOULD NOT BE CALLED DIRECTLY!!!
    // 'start' SHOULD be called only at BGTaskManager
    @Override
    public final void
    start() {
        // Current algorithm for managing BGTask, doesn't allow 'null' nick.
        // See BGTaskManager for details.
        //
        // Trying to running BGTask that has null nick value, means
        //   "try to start BGTask that is not managed".
        // And this is out of expectation.
        eAssert(null != nick);
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
        super.start();
    }

    @Override
    public final void
    run() {
        // Task may be cancelled before real-running.
        if (cancelled)
            return;

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

    boolean
    cancel(CancelParam param) {
        if (cancelled)
            return true; // nothing to do.

        cancelled = true;
        result = Err.UserCancelled;
        cancelParam = param;
        logI("BGTask : cancel()");

        if (Thread.State.NEW == getState()) {
            // Task is not even started.
            // We can cancel it!.
            interrupt();
            ownerHandler.post(new BGTaskPost(true));
        } else if (Thread.State.TERMINATED == getState())
            // Task is already finished.
            // So, we cannot cancel it!
            return false;
        else
            // Thread is alive!
            interrupt();

        return true;
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
}
