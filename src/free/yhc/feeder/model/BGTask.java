/*****************************************************************************
 *    Copyright (C) 2012, 2013 Younghyung Cho. <yhcting77@gmail.com>
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
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

public abstract class BGTask<RunParam, CancelParam> extends BaseBGTask {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(BGTask.class);

    public static final int OPT_WAKELOCK  = 0x01;
    public static final int OPT_WIFILOCK  = 0x02;

    private static final String WLTAG = "BGTask";

    private volatile Err            mResult         = Err.NO_ERR;

    private int                     mOpt            = 0;
    private RunParam                mRunParam       = null;

    private Object                  mWlmx           = new Object(); // Wakelock mutex
    private PowerManager.WakeLock   mWl             = null;
    private WifiManager.WifiLock    mWfl            = null;

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
        eAssert(ThreadEx.State.RUNNING != getState());
        mResult = Err.NO_ERR;
    }

    Err
    getResult() {
        eAssert(null != mResult);
        return mResult;
    }

    @Override
    protected void
    onPostRun(Err result) {
        eAssert(null != result);
        // See ThreadEx.bgRun()
        // result can be null in two cases
        //   - doAsyncTask() returns 'null' -- (*1)
        //   - doAsyncTask() doens't finished completely. -- (*2)
        // In case of BaseBGTask, (*1) is unexpected case and SHOULD NOT happen.
        // And (*2) is also definitely unexpected case (Runtime exception... or something...).
        // So, set to Err.UNKNOWN for those two cases.
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
