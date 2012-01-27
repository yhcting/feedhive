package free.yhc.feeder;

import android.util.Log;

class Utils {
    static final boolean DBG = true;
    static final String TAG = "[Feeder]";

    static void
    eAssert(boolean cond) {
        if (!DBG)
            return;

        if (!cond) {
            Thread.dumpStack();
            throw new AssertionError();
        }
    }

    static void
    logI(String msg) {
        if (!DBG)
            return;

        Log.i(TAG, msg);
    }

    static void
    logW(String msg) {
        if (!DBG)
            return;

        Log.w(TAG, msg);
    }

    static void
    logE(String msg) {
        if (!DBG)
            return;

        Log.e(TAG, msg);
    }
}
