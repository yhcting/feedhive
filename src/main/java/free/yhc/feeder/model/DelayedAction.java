/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
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
