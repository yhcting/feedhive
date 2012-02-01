package free.yhc.feeder.model;

import android.util.Log;

public class Utils {
    static final boolean DBG = true;
    static final String TAG = "[Feeder]";

    public static void
    eAssert(boolean cond) {
        if (!DBG)
            return;

        if (!cond) {
            Thread.dumpStack();
            throw new AssertionError();
        }
    }

    public static void
    logI(String msg) {
        if (!DBG)
            return;

        Log.i(TAG, msg);
    }

    public static void
    logW(String msg) {
        if (!DBG)
            return;

        Log.w(TAG, msg);
    }

    public static void
    logE(String msg) {
        if (!DBG)
            return;

        Log.e(TAG, msg);
    }
}
