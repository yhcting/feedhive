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
import static free.yhc.feeder.model.Utils.logW;

import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.PowerManager;

public class BGTask<RunParam, CancelParam> extends Thread {
    public static final int OPT_WAKELOCK  = 0x01;
    public static final int OPT_WIFILOCK  = 0x02;

    private static final String WLTAG = "BGTask";

    private String                  mNick           = null; // can be used several purpose.
    private final Handler           mOwnerHandler   = new Handler();
    private final Object            mStateLock      = new Object();
    private volatile State          mState          = State.READY;

    // Event if this is KeyBasedLinkedList, DO NOT USE KeyBasedLinkedList.
    // Using KeyBasedLinkedList here just increases code complexity.
    private LinkedList<EventLLElem> mEventListenerl = new LinkedList<EventLLElem>();
    private volatile Err            mResult         = Err.NO_ERR;

    private int                     mOpt            = 0;
    private RunParam                mRunParam       = null;
    private CancelParam             mCancelParam    = null;

    private Object                  mWlmx           = new Object(); // Wakelock mutex
    private PowerManager.WakeLock   mWl             = null;
    private WifiManager.WifiLock    mWfl            = null;

    public interface OnEventListener<RunParam, CancelParam> {
        // return : false (DO NOT run this task)
        void onPreRun  (BGTask<RunParam, CancelParam> task);
        void onPostRun (BGTask<RunParam, CancelParam> task, Err result);
        void onCancel  (BGTask<RunParam, CancelParam> task, CancelParam param);
        void onProgress(BGTask<RunParam, CancelParam> task, long progress);
    }

    private static enum State {
        READY,
        RUNNING,
        CANCELLED,
        DONE,
    }

    // LLElem : Listener List ELEMent
    private static class EventLLElem {
        private final Object            key;
        private final Handler           handler;
        private final OnEventListener   listener;

        EventLLElem(Object aKey, Handler aHandler, OnEventListener aListener) {
            key         = aKey;
            handler     = aHandler;
            listener    = aListener;
        }
    }

    private class BGTaskPost implements Runnable {
        private boolean _mCancelled;
        private Err     _mResult;
        BGTaskPost(boolean cancelled, Err result) {
            _mCancelled = cancelled;
            _mResult = result;
        }

        @Override
        public void run() {
            if (BGTask.this.isAlive()) {
                //logI("BGTask : REPOST : wait for task is Done");
                // post message continuously may drop responsibility for user, in case that mOwnerHandler is attached to UI thread.
                // To increase responsibility, post message with some delay.
                mOwnerHandler.postDelayed(this, 10);
                return;
            }

            //logI("BGTask : REAL POST!!! cancelled: " + bCancelled + ", NR Listener: " + getEventLLElems().length);
            if (_mCancelled) {
                onEarlyCancel(mCancelParam);
                for (EventLLElem e : getEventLLElems()) {
                    final OnEventListener listener = e.listener;
                    //logI("BGTask : Post(Cancel) to onEvent : " + onEvent.toString());
                    e.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onCancel(BGTask.this, mCancelParam);
                        }
                    });
                }
                mOwnerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onLateCancel(mCancelParam);
                    }
                });
            } else {
                onEarlyPostRun(mResult);
                for (EventLLElem e : getEventLLElems()) {
                    final OnEventListener listener = e.listener;
                    //logI("BGTask : Post(Post) to onEvent : " + onEvent.toString());
                    e.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            //logI("+++ BGTask : postRun : " + onEvent.toString());
                            listener.onPostRun(BGTask.this, mResult);
                        }
                    });
                }
                mOwnerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onLatePostRun(mResult);
                    }
                });
            }
        }
    }

    public BGTask(RunParam arg, int option) {
        super();
        // NOTE
        // Even if, BGTask is designed considering multi-threaded environment,
        //   to help understanding code structure, only UI Thread can create BG Task.
        // (Actually, this is NOT constraints for BGTask, but for Feeder app.
        eAssert(Utils.isUiThread());
        mRunParam = arg;
        mOpt = option;
    }

    // ==========================================
    // Private
    // ==========================================
    private void
    releaseWLock() {
        synchronized (mWlmx) {
            if (null != mWl) {
                //logI("< WakeLock >" + WLTAG + mNick + " >>>>>> release");
                mWl.release();
                mWl = null;
            }

            if (null != mWfl) {
                mWfl.release();
                mWfl = null;
            }
        }
    }

    private void
    aquireWLock() {
        synchronized (mWlmx) {
            // acquire wakelock/wifilock if needed
            if (0 != (OPT_WAKELOCK & mOpt)) {
                //logI("< WakeLock >" + WLTAG + mNick + " <<<<<< acquire");
                mWl = ((PowerManager)Utils.getAppContext().getSystemService(Context.POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG + mNick);
                mWl.acquire();
            }

            if (0 != (OPT_WIFILOCK & mOpt)) {
                mWfl = ((WifiManager)Utils.getAppContext().getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, WLTAG + mNick);
                mWfl.acquire();
            }
        }
    }

    private EventLLElem[]
    getEventLLElems() {
        synchronized (mEventListenerl) {
            return mEventListenerl.toArray(new EventLLElem[0]);
        }
    }
    // ==========================================
    // Protected
    // ==========================================

    // ==========================================
    // Package Private
    // ==========================================
    /**
     * Set result of this BGTask to 'Err.NoErr'.
     */
    void
    resetResult() {
        eAssert(!isAlive());
        mResult = Err.NO_ERR;
    }

    void
    setNick(String nick) {
        mNick = nick;
    }

    // ==========================================
    // Open Interface
    // ==========================================
    String
    getNick() {
        return mNick;
    }

    /**
     * Originally, isInterupted() is used.
     * But, based on my test experience, isInterrupted seems to reflect only BG Thread state
     *   (Not Thread object's state).
     * So, with some sensitive timing condition, calling Thread.isInterrupted() returns false,
     *   if it is called right after Thread.interrupt() is called.
     * (I didn't test and verify it strictly yet.)
     * To avoid this, isCancelled() is newly introduced and used.
     * @return
     */
    boolean
    isCancelled() {
        // mStateLock don't needed to be used here because state is volatile.
        return State.CANCELLED == mState;
    }

    Err
    getResult() {
        eAssert(null != mResult);
        return mResult;
    }

    /**
     * Register event listener with it's key value.
     * Newly added event listener will be added to the last of listener list.
     * (event listener will be notified in order of listener list.)
     *
     * Owner of given listener will be set into current Thread automatically.
     *
     * Key value is used to find event listener (onEvent).
     * Several event listener may share one key value.
     * Event callback will be called on caller's thread message loop.
     * @param key
     * @param listener
     * @param hasPriority
     *   true if this event listener
     */
    void
    registerEventListener(Object key, OnEventListener listener, boolean hasPriority) {
        // See comments regarding 'eAssert' at BGTask constructor.
        eAssert(Utils.isUiThread());
        eAssert(null != key && null != listener);

        if (mOwnerHandler.getLooper().getThread() != Thread.currentThread())
            logW("BGTask IMPORTANT : owner thread is different with event listener thread");

        EventLLElem e = new EventLLElem(key, new Handler(), listener);
        synchronized (mEventListenerl) {
            //logI("BGTask : registerEventListener : key(" + key + ") onEvent(" + onEvent + ")");
            if (hasPriority)
                mEventListenerl.addFirst(e);
            else
                mEventListenerl.addLast(e);
        }
    }

    /**
     * Unregister event listener whose key and listener match.
     * one of 'key' and 'listener' can be null, but NOT both.
     * IMPORTANT
     *   unregister SHOULD be called at the same thread context on which corresponding 'registerEventListener' is called.
     * @param key
     *   'null' means ignore key value.
     *   otherwise listeners having matching key value, are unregistered.
     * @param listener
     *   'null' means unregister all listeners whose key value matches.
     *   otherwise, unregister listener whose key and listener both match.
     */
    void
    unregisterEventListener(Object key, OnEventListener listener) {
        // See comments regarding 'eAssert' at BGTask constructor.
        eAssert(Utils.isUiThread());
        eAssert(null != key || null != listener);

        synchronized (mEventListenerl) {
            Iterator<EventLLElem> iter = mEventListenerl.iterator();
            while (iter.hasNext()) {
                EventLLElem e = iter.next();
                if ((null == key || e.key == key)
                    && (null == listener || listener == e.listener)) {
                    eAssert(e.handler.getLooper().getThread() == Thread.currentThread());
                    //logI("BGTask : unregisterEventListener : (" + listener.key + ") onEvent(" + listener.onEvent + ")");
                    iter.remove();
                }
            }
        }
    }

    /**
     * DANGEROUS FUNCTION
     * DO NOT USE if you are not sure what your are doing!
     * Make event listener list empty.
     * (Same as unregistering all listeners.)
     */
    void
    clearEventListener() {
        // See comments regarding 'eAssert' at BGTask constructor.
        eAssert(Utils.isUiThread());
        synchronized (mEventListenerl) {
            //logI("BGTask : clearEventListener");
            mEventListenerl.clear();
        }
    }

    // main background task to do.
    protected Err
    doBGTask(RunParam runParam)
        throws FeederException {
        return Err.NO_ERR;
    }

    /**
     * This will be called prior to all other registered listeners whose owner thread
     *   is same with this Thread's owner (thread context in which this Thread object
     *   is created (owner thread)).
     * If other listener's owner thread is different with the owner of this Thread object,
     *   than order of listener's execution is unknown (it's dependent on thread scheduling.)
     */
    protected void
    onEarlyPreRun() {}

    /**
     * This will be called after all other registered listeners whose owner thread
     *   is same with this Thread's owner (thread context in which this Thread object
     *   is created (owner thread)) is executed.
     * If other listener's owner thread is different with the owner of this Thread object,
     *   than order of listener's execution is unknown (it's dependent on thread scheduling.)
     */
    protected void
    onLatePreRun() {}

    /**
     * This will be called prior to all other registered listeners whose owner thread
     *   is same with this Thread's owner (thread context in which this Thread object
     *   is created (owner thread)).
     * If other listener's owner thread is different with the owner of this Thread object,
     *   than order of listener's execution is unknown (it's dependent on thread scheduling.)
     */
    protected void
    onEarlyPostRun (Err result) {}

    /**
     * This will be called after all other registered listeners whose owner thread
     *   is same with this Thread's owner (thread context in which this Thread object
     *   is created (owner thread)) is executed.
     * If other listener's owner thread is different with the owner of this Thread object,
     *   than order of listener's execution is unknown (it's dependent on thread scheduling.)
     */
    protected void
    onLatePostRun (Err result) {
        releaseWLock();
    }

    /**
     * This will be called prior to all other registered listeners whose owner thread
     *   is same with this Thread's owner (thread context in which this Thread object
     *   is created (owner thread)).
     * If other listener's owner thread is different with the owner of this Thread object,
     *   than order of listener's execution is unknown (it's dependent on thread scheduling.)
     */
    protected void
    onEarlyCancel(CancelParam param) {}

    /**
     * This will be called after all other registered listeners whose owner thread
     *   is same with this Thread's owner (thread context in which this Thread object
     *   is created (owner thread)) is executed.
     * If other listener's owner thread is different with the owner of this Thread object,
     *   than order of listener's execution is unknown (it's dependent on thread scheduling.)
     */
    protected void
    onLateCancel(CancelParam param) {
        releaseWLock();
    }

    /**
     * This will be called prior to all other registered listeners whose owner thread
     *   is same with this Thread's owner (thread context in which this Thread object
     *   is created (owner thread)).
     * If other listener's owner thread is different with the owner of this Thread object,
     *   than order of listener's execution is unknown (it's dependent on thread scheduling.)
     */
    protected void
    onProgress(long progress) {}

    // This SHOULD NOT BE CALLED DIRECTLY!!!
    // 'start' SHOULD be called only at BGTaskManager
    /**
     * THIS SHOULD BE CALLED ONLY BY 'BGTaskManager'.
     * Start background task
     */
    @Override
    public final void
    start() {
        // Current algorithm for managing BGTask, doesn't allow 'null' nick.
        // See BGTaskManager for details.
        //
        // Trying to running BGTask that has null nick value, means
        //   "try to start BGTask that is not managed".
        // And this is out of expectation.
        eAssert(null != mNick);
        aquireWLock();
        mOwnerHandler.post(new Runnable() {
            @Override
            public void run() {
                onEarlyPreRun();
                for (EventLLElem e : getEventLLElems()) {
                    final OnEventListener listener = e.listener;
                    e.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onPreRun(BGTask.this);
                        }
                    });
                }
                mOwnerHandler.post(new Runnable() {
                   @Override
                   public void run() {
                       onLatePreRun();
                   }
                });
                BGTask.super.start();
            }
        });
    }

    /**
     * DO NOT USE CALL THIS FUNCTION OUTSIDE
     *   even if it's visibility is 'public'
     * (visibility is 'public' only because this is overridden.)
     */
    @Override
    public final void
    run() {
        // Task may be cancelled before real-running.
        // 'mStateLock' don't needed to be used here because state is volatile.
        synchronized (mStateLock) {
            if (State.READY != mState) {
                eAssert(false);
                return;
            }
            mState = State.RUNNING;
        }

        //logI("BGTask : BGJobs started");
        boolean bInterrupted = false;
        try {
            mResult = doBGTask(mRunParam);
            eAssert(null != mResult);
        } catch (FeederException e) {
            if (Err.INTERRUPTED == e.getError())
                bInterrupted = true;
        }

        if (!bInterrupted)
            bInterrupted = Thread.currentThread().isInterrupted();

        synchronized (mStateLock) {
            if (State.CANCELLED == mState)
                mResult = Err.USER_CANCELLED;
            else
                mState = State.DONE;
        }

        //logI("BGTask : BGJobs done - try to post! : " + mResult.name() + " interrupted(" + bInterrupted + ")");

        if (bInterrupted
            || Err.INTERRUPTED == mResult
            || Err.USER_CANCELLED == mResult)
            mOwnerHandler.post(new BGTaskPost(true, mResult));
        else
            mOwnerHandler.post(new BGTaskPost(false, mResult));
    }

    /**
     * THIS SHOULD BE CALLED ONLY BY 'BGTaskManager'.
     * Cancel background task.
     * This will interrupt background task.
     * @param param
     * @return
     */
    boolean
    cancel(CancelParam param) {
        synchronized (mStateLock) {
            switch(mState) {
            case CANCELLED:
                return true;
            case DONE:
                return false;
            }
            mState = State.CANCELLED;
        }

        mResult = Err.USER_CANCELLED;
        mCancelParam = param;
        //logI("BGTask : cancel()");

        if (Thread.State.NEW == getState()) {
            // Task is not even started.
            // We can cancel it!.
            interrupt();
            mOwnerHandler.post(new BGTaskPost(true, mResult));
        } else if (Thread.State.TERMINATED == getState())
            // Task is already finished.
            // So, we cannot cancel it!
            return false;
        else
            // Thread is alive!
            interrupt();

        return true;
    }

    /**
     * This will trigger onProgress listener.
     * onProgress listener will be called in their owner's thread context.
     */
    void
    publishProgress(final long progress) {
        mOwnerHandler.post(new Runnable() {
            @Override
            public void run() {
                onProgress(progress);
            }
        });
        for (EventLLElem e : getEventLLElems()) {
            final OnEventListener listener = e.listener;
            e.handler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onProgress(BGTask.this, progress);
                }
            });
        }
    }
}
