package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.os.PowerManager;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.Utils;

// There is no way to notify result of scheduled-update to user.
// So, even if scheduled update may fail, there is no explicit notification.
// But user can know whether scheduled-update successes or fails by checking age since last successful update.
// (See channelListAdapter for 'age' time)
public class ScheduledUpdater extends Service {
    private static final int             ReqCUpdate = 0;

    private static final long            hourInMs = 60 * 60 * 1000;
    private static final long            dayInMs = 24 * hourInMs;

    // Wakelock
    private static final String          WLTag = "free.yhc.feeder.ScheduledUpdater";

    private static PowerManager.WakeLock wl = null;
    private static int                   wlcnt = 0;


    // To stop service at right time - when all tasks started by this service is done.
    // Should be thread-safe. So, 'volatile' is used.
    private volatile int                 taskCnt = 0;

    // Why broadcast receiver?
    // Using pendingIntent from "PendingIntent.getService()" doesn't guarantee that
    //   given service gets controls before device falls into sleep.
    // But broadcast receiver guarantee that device doesn't fall into sleep before returning from 'onReceive'
    // (This is described at Android document)
    // So, broadcast receiver is used event if it's more complex than using "PendingIntent.getService()" directly.
    public static class AlarmReceiver extends BroadcastReceiver {
        // NOTE:
        //   broadcast receiver is run on main ui thread (same as service).
        @Override
        public void onReceive(Context context, Intent intent) {
            logI("AlarmReceiver : onReceive");

            long time = intent.getLongExtra("time", -1);
            Intent svc = new Intent(context, ScheduledUpdater.class);
            if (time < 0) {
                eAssert(false);
                return;
            }
            svc.putExtra("time", time);
            // onStartCommand will be sent!
            context.startService(svc);
            // Update should be started before falling into sleep.
            getWakeLock(context);
        }
    }

    private class UpdateBGTask extends BGTaskUpdateChannel implements
    BGTask.OnEvent {
        private long    cid = -1;
        private int     startId = -1;

        UpdateBGTask(long cid, int startId) {
            super(ScheduledUpdater.this);
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

    private static class ChannelValue {
        long cid;
        long v;
        ChannelValue(long cid, long v) {
            this.cid = cid;
            this.v = v;
        }
    }

    private static class ChannelValueComparator implements Comparator<ChannelValue> {
        @Override
        public int compare(ChannelValue cv0, ChannelValue cv1) {
            if (cv0.v < cv1.v)
                return -1;
            else if (cv0.v > cv1.v)
                return 1;
            else
                return 0;
        }
    }

    private static void
    getWakeLock(Context context) {
        // getWakeLock() and putWakeLock() are used only at main ui thread (broadcast receiver, onStartCommand).
        // So, we don't need to synchronize it!
        eAssert(wlcnt >= 0);
        if (null == wl) {
            wl = ((PowerManager)context.getSystemService(Context.POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTag);
            logI("ScheduledUpdater : WakeLock created and aquired");
            wl.acquire();
        }
        wlcnt++;
        logI("ScheduledUpdater(GET) : current WakeLock count: " + wlcnt);
    }

    private static void
    putWakeLock() {
        eAssert(wlcnt > 0);
        wlcnt--;
        logI("ScheduledUpdater(PUT) : current WakeLock count: " + wlcnt);
        if (0 == wlcnt) {
            wl.release();
            // !! NOTE : Important.
            // if below line "wl = null" is removed, then RuntimeException is raised
            //   when 'getWakeLock' -> 'putWakeLock' -> 'getWakeLock' -> 'putWakeLock(*)'
            //   (at them moment of location on * is marked - last 'putWakeLock').
            // That is, once WakeLock is released, reusing is dangerous in current Android Framework.
            // I'm not sure that this is Android FW's bug... or I missed something else...
            // Anyway, let's set 'wl' as 'null' here to re-create new WakeLock at next time.
            wl = null;
            logI("ScheduledUpdater : WakeLock is released");
        }
    }

    // out[0] : time to go from dayTime0 to dayTime1
    // out[1] : time to go from dayTime1 to dayTime0
    private static void
    dayBasedDistanceMs(long[] out, long dayms0, long dayms1) {
        eAssert(dayms0 <= dayInMs && dayms1 <= dayInMs);
        if (dayms0 > dayms1) {
            out[1] = dayms0 - dayms1;
            out[0] = dayInMs - out[1];
        } else if (dayms0 < dayms1) {
            out[0] = dayms1 - dayms0;
            out[1] = dayInMs - out[0];
        }
    }

    // NOTE!
    //   Next Scheduled updates which are at least 1-min after, will be scheduled.
    static void
    scheduleNextUpdate(Context context, Calendar calNow) {
        long daybase = Utils.dayBaseMs(calNow);
        long dayms = calNow.getTimeInMillis() - daybase;
        if (dayms < 0)
            dayms = 0; // To compensate 1 sec error from '/' operation.
        eAssert(dayms <= dayInMs);

        // If we get killed, after returning from here, restart
        Cursor c = DBPolicy.S().queryChannel(DB.ColumnChannel.SCHEDUPDATETIME);
        if (!c.moveToFirst()) {
            c.close();
            return; // There is no channel.
        }

        long nearestNext = dayInMs * 2; // large enough value.
        do {
            String sStr = c.getString(0);
            // NOTE : IMPORTANT
            //   Time stored at DB is HOUR_OF_DAY (0 - 23)
            //   (See comments regarding Column at DB.)
            //   We cannot guarantee that service is started at exact time.
            //   So, we should compensate it (see comments at 'onStartCommand'
            long[] secs   = Utils.nStringToNrs(sStr);
            long[] out  = new long[2];
            for (long s : secs) {
                long sms = Utils.secToMs(s);
                dayBasedDistanceMs(out, dayms, sms);

                // out[0] is time to go from 'dayms' to 'hms'
                if (out[0] < nearestNext)
                    nearestNext = out[0];
            }
        } while (c.moveToNext());
        c.close();

        { // just for variable scope
            long h = nearestNext / hourInMs;
            long m = (nearestNext - h * hourInMs) / (60 * 1000);
            logI("ScheduledUpdater : next wakeup after [ " + h + " hours, " + m + " miniutes ]");
        }

        // convert into real time.
        nearestNext += calNow.getTimeInMillis();
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("time", nearestNext);
        PendingIntent pIntent = PendingIntent.getBroadcast(context, ReqCUpdate, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Get the AlarmManager service
        AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, nearestNext, pIntent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    // NOTE:
    //   onStartCommand is run on main ui thread (same as onReceive).
    //   So, we don't need to concern about race-condition between these two.
    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        long schedTime = intent.getLongExtra("time", -1);
        logI("ScheduledUpdater : onStartCommand :" +
             "    - startId : " + startId +
             "    - flags   : " + flags +
             "    - time(ms): " + schedTime);

        Calendar calNow = Calendar.getInstance();
        long daybase = Utils.dayBaseMs(calNow);
        long dayms = calNow.getTimeInMillis() - daybase;

        if (schedTime < 0 // something wrong!!
            || calNow.getTimeInMillis() < schedTime // scheduled too early
            || calNow.getTimeInMillis() > schedTime + hourInMs) { // scheduled too late
            logW("WARN : ScheduledUpdater : weired scheduling!!!\n" +
                 "    scheduled time(ms) : " + schedTime + "\n" +
                 "    current time(ms)   : " + calNow.getTimeInMillis());
            scheduleNextUpdate(this, calNow);
            putWakeLock();
            return START_NOT_STICKY;
        }


        // If we get killed, after returning from here, restart
        Cursor c = DBPolicy.S().queryChannel(new DB.ColumnChannel[] {
                DB.ColumnChannel.ID,
                DB.ColumnChannel.SCHEDUPDATETIME });
        // below values are 'Column Index' for above query.
        final int iId   = 0;
        final int iTime = 1;

        if (!c.moveToFirst()) {
            c.close();
            putWakeLock();
            return START_NOT_STICKY; // There is no channel.
        }

        // NOTE : IMPORTANT
        // Service is started behind it's original plan by amount of 'schedError'
        // So, service should run scheduled-update whose planed-sched-time is
        //   between 'schedTime' and 'current'.
        long schedError = calNow.getTimeInMillis() - schedTime;
        LinkedList<Long> chl = new LinkedList<Long>();
        do {
            String sStr = c.getString(iTime);
            //   We cannot guarantee that service is started at exact time.
            //   So, we need to check error and find next scheduled based on this error.
            long[] ss  = Utils.nStringToNrs(sStr);
            long   cid = c.getLong(iId);
            long[] out = new long[2];
            for (long s : ss) {
                long sms = Utils.secToMs(s);
                dayBasedDistanceMs(out, dayms, sms);

                // out[1] is time to go from 'hms' to 'dayms'
                // This means 'time passed since 'hms' because 'dayms' is current time.
                if (out[1] <= schedError)
                    chl.add(cid);
            }
        } while (c.moveToNext());
        c.close();

        Iterator<Long> itr = chl.listIterator();
        while (itr.hasNext()) {
            long cid = itr.next();
            RTTask.StateUpdate state = RTTask.S().getUpdateState(cid);
            if (RTTask.StateUpdate.Canceling == state
                || RTTask.StateUpdate.Updating == state) {
                logI("Channel [" + cid + "] is already under updating/canceling.\n" +
                     "So scheduled update is skipped");
            } else {
                // unregister gracefully to start update.
                // There is sanity check in BGTaskManager - see BGTaskManager
                //   to know why this 'unregister' is required.
                RTTask.S().unregisterUpdate(cid);
            }
            UpdateBGTask task = new UpdateBGTask(cid, startId);
            RTTask.S().registerUpdate(cid, task);
            RTTask.S().bindUpdate(cid, task);
            logI("ScheduledUpdater : start update BGTask for [" + cid + "]");
            task.start(new BGTaskUpdateChannel.Arg(cid));
        }

        // register next scheduled-update.
        // NOTE
        //   Real-Now - Calendar.getInstance() - SHOULD NOT be used here!
        //   We already have error between 'calNow' and Real-Now
        //     (we already spends some time to run some code!)
        //   But, some channels may be scheduled between these two time - 'calNow' and Real-Now.
        //   If Real-Now is chosen as current calendar, tasks mentioned at above line, is missed from scheduled-update!
        //   So, 'calNow' should be used as current calendar!
        scheduleNextUpdate(this, calNow);

        // update task has it's own wakelock.
        // We don't need to worry about update jobs.
        // Just release wakelock for this command.
        putWakeLock();
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
        super.onDestroy();
    }
}
