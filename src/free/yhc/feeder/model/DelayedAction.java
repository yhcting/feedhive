/******************************************************************************
 *    Copyright (C) 2012, 2013, 2014 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of FeedHive
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
 *    along with this program.	If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder.model;

import android.os.Handler;

public class DelayedAction implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(DelayedAction.class);

    private final Handler   mHandler;
    private final Runnable  mAction;
    // action can be delayed at most 'mDelayLimist' time.
    private final int       mDelayLimit; // ms
    private final int       mDelay; // ms
    private final Object    mDelaySumLock = new Object();
    private int mDelaySum = 0;

    private final Runnable mActionTrigger = new Runnable() {
        @Override
        public void
        run() {
            synchronized (mDelaySumLock) {
                mDelaySum = 0; // action is done. So, initialize it.
            }
            if (null != mAction)
                mAction.run();
        }
    };

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ DelayedAction ]";
    }

    public DelayedAction(Handler actionContextHandler,
                         Runnable action,
                         int delayLimit,
                         int delay) {
        mHandler = actionContextHandler;
        if (delayLimit < delay)
            delayLimit = delay;
        mAction = action;
        mDelayLimit = delayLimit;
        mDelay = delay;
    }

    public void
    triggerAction() {
        boolean delayable = true;
        synchronized (mDelaySumLock) {
            mDelaySum += mDelay;
            if (mDelaySum > mDelayLimit)
                delayable = false;
        }
        if (delayable) {
            mHandler.removeCallbacks(mActionTrigger);
            mHandler.postDelayed(mActionTrigger, mDelay);
        }
    }
}
