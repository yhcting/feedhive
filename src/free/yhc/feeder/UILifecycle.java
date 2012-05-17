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
import android.os.Handler;

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
    private State   sm      = State.INIT; // State Machine
    private OnEvent cb; // callback listener
    private Handler handler = new Handler();

    interface OnEvent {
        void onUICreate();
        void onUIStart();
        void onUIResume();
        void onUIPause();
        void onUIStop();
        void onUIDestroy();
    }

    enum State {
        INIT,       // default or after onDestroy
        TRIGGERED,  // after delayed start is triggered.
        CREATED,    // after onCreate
        STARTED,    // after onStart
        RESUMED,    // after onResume
        PAUSED,     // after onPause
        STOPPED,    // after onStop
        DESTROIED,  // after onDestroy
    }

    UILifecycle(OnEvent listener) {
        cb = listener;
    }

    State state() {
        return sm;
    }

    void reset() {
        sm = State.INIT;
    }

    boolean isStarted() {
        return State.INIT != sm;
    }
    /**
     * This function SHOULD be called AT LEAST AFTER Actvity.onResume.
     * Calling after first screen of activity is drawn, is expected!
     */
    void triggerDelayedStart() {
        eAssert(State.INIT == sm);
        if (State.INIT != sm)
            return;

        sm = State.TRIGGERED;
        // real-activity-initialization should be done.
        handler.post(new Runnable() {
            @Override
            public void run() {
                cb.onUICreate();
                sm = State.CREATED;
                onStart();
                onResume();
            }
        });
    }

    /**
     * SHOULD be called at Activity.onCreate
     * This function do nothing but reset state of UI lifecycle.
     * Why?
     * This class is for delayed-start.
     * So, state will be proceeded to next when delayed start is triggered.
     */
    void onCreate() {
        // reset State
        sm = State.INIT;
        // NOTE
        // cb.onUICreate Should NOT be called here.
        // It should be delayed-start.
    }

    /**
     * SHOULD be called at Activity.onStart
     */
    void onStart() {
        if (State.CREATED != sm && State.STOPPED != sm)
            return;

        cb.onUIStart();

        sm = State.STARTED;
    }

    /**
     * SHOULD be called at Activity.onResume
     */
    void onResume() {
        if (State.STARTED != sm && State.PAUSED != sm)
            return;

        cb.onUIResume();

        sm = State.RESUMED;
    }

    /**
     * SHOULD be called at Activity.onPause
     */
    void onPause() {
        if (State.RESUMED != sm)
            return;

        cb.onUIPause();

        sm = State.PAUSED;
    }

    /**
     * SHOULD be called at Activity.onStop
     */
    void onStop() {
        if (State.PAUSED != sm && State.STARTED != sm)
            return;

        cb.onUIStop();

        sm = State.STOPPED;
    }

    /**
     * SHOULD be called at Activity.onDestroy
     */
    void onDestroy() {
        if (State.STOPPED != sm && State.CREATED != sm)
            return;

        cb.onUIDestroy();

        sm = State.DESTROIED;
    }
}
