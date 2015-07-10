/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

package free.yhc.feeder.core;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

// CancelParam is not used. But it is preserved for future use.
public abstract class BGTask<RunParam, CancelParam> extends BaseBGTask {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Utils.Logger P = new Utils.Logger(BGTask.class);

    public static final int OPT_WAKELOCK = 0x01;
    public static final int OPT_WIFILOCK = 0x02;

    private static final String WLTAG = "BGTask";

    private volatile Err mResult = Err.NO_ERR;

    private int mOpt = 0;
    private RunParam mRunParam = null;

    private final Object mWlmx = new Object(); // Wakelock mutex
    private PowerManager.WakeLock mWl = null;
    private WifiManager.WifiLock mWfl = null;

    public BGTask(RunParam arg, int option) {
        super();
        // NOTE
        // Even if, BGTask is designed considering multi-threaded environment,
        //   to help understanding code structure, only UI Thread can create BG Task.
        // (Actually, this is NOT constraints for BGTask, but for Feeder app.
        Utils.eAssert(Utils.isUiThread());
        mRunParam = arg;
        mOpt = option;
    }

    // ==========================================
    // Private
    // ==========================================
    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    private void
    aquireWLock() {
        synchronized (mWlmx) {
            // acquire wakelock/wifilock if needed
            if (0 != (OPT_WAKELOCK & mOpt)) {
                //logI("< WakeLock >" + WLTAG + mNick + " <<<<<< acquire");
                mWl = ((PowerManager)Environ.getAppContext().getSystemService(Context.POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG + getName());
                mWl.acquire();
            }

            if (0 != (OPT_WIFILOCK & mOpt)) {
                mWfl = ((WifiManager)Environ.getAppContext().getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, WLTAG + getName());
                mWfl.acquire();
            }
        }
    }

    // ==========================================
    // Package Private
    // ==========================================
    /**
     * Set result of this BGTask to 'Err.NoErr'.
     */
    void
    resetResult() {
        Utils.eAssert(ThreadEx.State.RUNNING != getState());
        mResult = Err.NO_ERR;
    }

    Err
    getResult() {
        Utils.eAssert(null != mResult);
        return mResult;
    }

    @Override
    protected void
    onPostRun(Err result) {
        Utils.eAssert(null != result);
        // See ThreadEx.bgRun()
        // result can be null in two cases
        //   - doAsyncTask() returns 'null' -- (*1)
        //   - doAsyncTask() doens't finished completely. -- (*2)
        // In case of BaseBGTask, (*1) is unexpected case and SHOULD NOT happen.
        // And (*2) is also definitely unexpected case (Runtime exception... or something...).
        // So, set to Err.UNKNOWN for those two cases.
        //noinspection ConstantConditions
        if (null == result)
            result = Err.UNKNOWN;

        mResult = result;
        super.onPostRun(result);
    }

    @Override
    final protected Err
    doAsyncTask() {
        try {
            return doBgTask(mRunParam);
        } catch (FeederException e) {
            return e.getError();
        } catch (Throwable e) {
            return Err.UNKNOWN;
        }
    }

    protected abstract Err
    doBgTask(RunParam param) throws FeederException ;
}
