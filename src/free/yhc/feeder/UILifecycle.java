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

package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.app.Activity;
import android.os.Handler;
import android.view.View;

// NOTE
// At some activity, UI thread has lots of jobs to do.
// And it may take quite long time.
// Because it should be run at UI thread, AsyncTask + INDETERMINATE PROGRESS cannot be a solution.
// So, below UILifecycle class is newly introduced to do time-consuming-UI-jobs.
// Flow is like follows.
// - As soon as activity is started, one single screen - sign of life - is shown to user.
// - After that, all other UI jobs are executed (jobs that takes time.)
//
// NOTE [ASSUMPTION]
// This class is created at UI thread!
//
// NOTE [IMPORTANT]
// This should match Android Activity Lifecycle!
// Ex.
//   onResume can be called ONLY after 'onStart' or 'onPause'.
//   (See Activity's Lifecycle document!)
public class UILifecycle {
    private String  name;
    private State   sm      = State.INVALID; // State Machine
    private State   next    = State.INVALID;
    private Activity act;
    private OnEvent cb; // callback listener
    private View    contentv;
    private View    waitv;
    private Handler handler = new Handler();

    interface OnEvent {
        void onUICreate(View contentv);
        void onUIStart(View contentv);
        void onUIResume(View contentv);
        void onUIPause(View contentv);
        void onUIStop(View contentv);
        void onUIDestroy(View contentv);
    }

    enum State {
        INVALID,    // invalid state
        TRIGGERED,  // after delayed start is triggered.
        UICREATE,   // onUICreate
        CREATED,    // after onCreate
        UISTART,    // onUIStart
        STARTED,    // after onStart
        UIRESUME,   // onUIResume
        RESUMED,    // after onResume
        PAUSED,     // after onPause
        STOPPED,    // after onStop
        DESTROIED,  // after onDestroy
    }

    private void
    cbOnUICreate() {
        logI(name + ": UI Lifecycle : onUICreate");
        cb.onUICreate(contentv);
    }

    private void
    cbOnUIStart() {
        logI(name + ": UI Lifecycle : onUIStart");
        cb.onUIStart(contentv);
    }

    private void
    cbOnUIResume() {
        logI(name + ": UI Lifecycle : onUIResume");
        cb.onUIResume(contentv);
    }

    private void
    cbOnUIPause() {
        logI(name + ": UI Lifecycle : onUIPause");
        cb.onUIPause(contentv);
    }

    private void
    cbOnUIStop() {
        logI(name + ": UI Lifecycle : onUIStop");
        cb.onUIStop(contentv);
    }

    private void
    cbOnUIDestroy() {
        logI(name + ": UI Lifecycle : onUIDestroy");
        cb.onUIDestroy(contentv);
    }

    private void
    setWaitView() {
        act.getActionBar().hide();
        act.setContentView(waitv);
    }

    private void
    setContentView() {
        act.getActionBar().show();
        act.setContentView(contentv);
    }

    /**
     * This function SHOULD be called AT LEAST AFTER Actvity.onResume.
     * Calling after first screen of activity is drawn, is expected!
     */
    private void
    delayedCreate() {
        sm = State.TRIGGERED;
        // real-activity-initialization should be done.
        handler.post(new Runnable() {
            @Override
            public void run() {
                setContentView();
                cbOnUICreate();
                cbOnUIStart();
                cbOnUIResume();
                sm = State.RESUMED;
            }
        });
    }

    private void
    delayedStart() {
        sm = State.TRIGGERED;
        // real-activity-initialization should be done.
        handler.post(new Runnable() {
            @Override
            public void run() {
                setContentView();
                cbOnUIStart();
                cbOnUIResume();
                sm = State.RESUMED;
            }
        });
    }

    private void
    delayedResume() {
        sm = State.TRIGGERED;
        // real-activity-initialization should be done.
        handler.post(new Runnable() {
            @Override
            public void run() {
                setContentView();
                cbOnUIResume();
                sm = State.RESUMED;
            }
        });
    }

    /**
     *
     * @param name
     *   name of instance (for debugging, logging and identifying)
     * @param act
     * @param listener
     * @param wait_layout
     */
    UILifecycle(String name,
                Activity act, OnEvent listener,
                View contentv, View waitv) {
        this.name = name;
        this.act = act;
        cb = listener;
        this.contentv = contentv;
        this.waitv = waitv;
    }

    Handler getHandler() {
        return handler;
    }

    State state() {
        return sm;
    }

    void reset() {
        sm = State.INVALID;
    }

    void setDelayedNextState(State state) {
        next = state;
    }

    void triggerDelayedNextState() {
        if (State.INVALID == next)
            return; // nothing to do

        logI(name + ": UI Lifecycle : triggerDelayedNextState : " + next.name());

        if (State.UICREATE == next)
            delayedCreate();
        else if (State.UISTART == next)
            delayedStart();
        else if (State.UIRESUME == next)
            delayedResume();
        else
            eAssert(false); // unexpected

        next = State.INVALID;
    }

    /**
     * SHOULD be called at Activity.onCreate
     * This function do nothing but reset state of UI lifecycle.
     * Why?
     * This class is for delayed-start.
     * So, state will be proceeded to next when delayed start is triggered.
     */
    void onCreate() {
        logI(name + ": UI Lifecycle : onCreate : " + sm.name() + " => " + next.name());
        // reset State
        setDelayedNextState(State.UICREATE);
        // NOTE
        // cb.onUICreate Should NOT be called here.
        // It should be delayed-start.
        setWaitView();
        sm = State.CREATED;
    }

    /**
     * SHOULD be called at Activity.onStart
     */
    void onStart() {
        logI(name + ": UI Lifecycle : onStart : " + sm.name() + " => " + next.name());
        if (State.STOPPED == sm) {
            setWaitView();
            // State SHOULD NOT jump to UISTART with skipping UICREATE
            if (State.UICREATE != next)
                setDelayedNextState(State.UISTART);
        }
        sm = State.STARTED;
    }

    /**
     * SHOULD be called at Activity.onResume
     */
    void onResume() {
        logI(name + ": UI Lifecycle : onResume : " + sm.name() + " => " + next.name());
        if (State.PAUSED == sm) {
            setWaitView();
            // State SHOULD NOT jump to RESUME with skipping UISTART and UICREATE
            if (State.UICREATE != next && State.UISTART != next)
                setDelayedNextState(State.UIRESUME);
        }
        sm = State.RESUMED;
    }

    /**
     * SHOULD be called at Activity.onPause
     */
    void onPause() {
        logI(name + ": UI Lifecycle : onPause : " + sm.name() + " => " + next.name());
        if (State.RESUMED != sm)
            return;

        cbOnUIPause();
        sm = State.PAUSED;
    }

    /**
     * SHOULD be called at Activity.onStop
     */
    void onStop() {
        logI(name + ": UI Lifecycle : onStop : " + sm.name() + " => " + next.name());
        if (State.PAUSED != sm && State.STARTED != sm)
            return;

        cbOnUIStop();
        sm = State.STOPPED;
    }

    /**
     * SHOULD be called at Activity.onDestroy
     */
    void onDestroy() {
        logI(name + ": UI Lifecycle : onDestroy : " + sm.name() + " => " + next.name());
        if (State.STOPPED != sm && State.CREATED != sm)
            return;

        cbOnUIDestroy();
        sm = State.DESTROIED;
    }
}
