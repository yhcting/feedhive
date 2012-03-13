package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;

// There is no way to notify result of scheduled-update to user.
// So, even if scheduled update may fail, there is no explicit notification.
// But user can know whether scheduled-update successes or fails by checking age since last successful update.
// (See channelListAdapter for 'age' time)
public class ScheduledUpdater extends Service {
    static final long dayInMs = 24 * 60 * 60 * 1000;

    static final int ReqCUpdate     = 0;

    static final String WLTag = "freee.yhc.feeder.ScheduledUpdater";

    static PowerManager.WakeLock wl = null;
    static int                   wlcnt = 0;

    // Why broadcast receiver?
    // Using pendingIntent from "PendingIntent.getService()" doesn't guarantee that
    //   given service gets controls before device falls into sleep.
    // But broadcast receiver guarantee that device doesn't fall into sleep before returning from 'onReceive'
    // (This is described at Android document)
    // So, broadcast receiver is used event if it's more complex than using "PendingIntent.getService()" directly.
    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            logI("AlarmReceiver : onReceive");

            Intent svc = new Intent(context, ScheduledUpdater.class);
            long cid = intent.getLongExtra("cid", -1);
            if (cid < 0)
                return;
            svc.putExtra("cid", cid);

            synchronized (wl) {
                eAssert(wlcnt >= 0);
                if (null == wl) {
                    wl = ((PowerManager)context.getSystemService(Context.POWER_SERVICE))
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTag);
                    wl.acquire();
                    logI("ScheduledUpdater : WakeLock created and aquired");
                }
                wlcnt++;
                logI("ScheduledUpdater : current WakeLock count: " + wlcnt);
            }
            context.startService(svc);
        }
    }

    private class UpdateBGTask extends BGTaskUpdateChannel implements
    BGTask.OnEvent {
        private long    cid = -1;
        private int     startId = -1;

        UpdateBGTask(long cid, int startId) {
            super();
            this.cid = cid;
            this.startId = startId;
        }

        @Override
        public void
        onProgress(BGTask task, int progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            logI("ScheduledUpdater(onCancel) : stopSelf (" + startId + ")");
            stopSelf(startId);
        }

        @Override
        public void
        onPreRun(BGTask task) {
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            logI("ScheduledUpdater(onPostRun) : stopSelf (" + startId + ")");
            stopSelf(startId);
        }
    }

    // @secs
    //      00:00:00 based value.
    //      ascending ordered value.
    // @return : time to next nearest time to go in milliseconds.
    private static long
    secToNextNearest(long[] secs) {
        eAssert(secs.length > 0);
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long dayBase = cal.getTimeInMillis();
        long dayTime = now - dayBase;

        eAssert(dayTime >= 0);
        for (long s : secs) {
            if (s * 1000 > dayTime)
                return s * 1000 - dayTime;
        }
        // All scheduled time is passed for day.
        // smallest of tomorrow is nearest one.
        return dayBase + dayInMs + secs[0] * 1000 - now;
    }

    static void
    setNextScheduledUpdate(Context context, long cid) {
        // this should be read from DB.
        // But for prototyping scheduled update, hard-coded value is used.
        // (Assume sorted order - ascending)
        String updateTimeStr = "3600/7200"; // <===== hardcoded....
        //updateTimeStr = DBPolicy.S().getChannelInfoString(cid, DB.ColumnChannel.UPDATETIME);

        String[] timestrs = updateTimeStr.split("/");
        long[] secs = new long[timestrs.length];
        try {
            for (int i = 0; i < secs.length; i++)
                secs[i] = Long.parseLong(timestrs[i]);
        } catch (NumberFormatException e) {
            logW("DB is corrupted!!!");
            eAssert(false);
        }

        long nextMs = secToNextNearest(secs);

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("cid", cid);
        PendingIntent pIntent = PendingIntent.getBroadcast(context, 192837, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Get the AlarmManager service
        AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, nextMs, pIntent);
        //am.set(AlarmManager.RTC_WAKEUP, 1000, pIntent);
    }

    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        // If we get killed, after returning from here, restart
        long cid = intent.getLongExtra("cid", 0);
        logI("ScheduledUpdater : onStartCommand : cid(" + cid + ")");

        RTTask.StateUpdate state = RTTask.S().getUpdateState(cid);
        if (RTTask.StateUpdate.Canceling == state
            || RTTask.StateUpdate.Updating == state) {
            stopSelf(startId);
            return START_NOT_STICKY;
        } else {
            // unregister gracefully
            RTTask.S().unregisterUpdate(cid);
        }

        UpdateBGTask task = new UpdateBGTask(cid, startId);
        RTTask.S().registerUpdate(cid, task);
        RTTask.S().bindUpdate(cid, task);
        task.start(new BGTaskUpdateChannel.Arg(cid));

        // register next scheduled-update.
        setNextScheduledUpdate(this, cid);

        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder
    onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void
    onDestroy() {
        logI("ScheduledUpdater : onDestroy");
        synchronized (wl) {
            eAssert(wlcnt >= 0);
            wlcnt--;
            logI("ScheduledUpdater : current WakeLock count: " + wlcnt);
            if (0 == wlcnt) {
                wl.release();
                logI("ScheduledUpdater : WakeLock is released");
            }
        }
    }
}
