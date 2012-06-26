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
import static free.yhc.feeder.model.Utils.logW;

import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

// There is no way to notify result of scheduled-update to user.
// So, even if scheduled update may fail, there is no explicit notification.
// But user can know whether scheduled-update successes or fails by checking age since last successful update.
// (See channelListAdapter for 'age' time)
public class ScheduledUpdater extends Service implements
UnexpectedExceptionHandler.TrackedModule {
    // Should match manifest's intent filter
    private static final String schedUpdateIntentAction = "feeder.intent.action.SCHEDULED_UPDATE";
    // Wakelock
    private static final String WLTag = "free.yhc.feeder.ScheduledUpdater";

    private static final int    ReqCUpdate = 0;

    private static final long   hourInMs = 60 * 60 * 1000;
    private static final long   dayInMs = 24 * hourInMs;

    private static final String CMD_ALARM   = "alarm";
    private static final String CMD_RESCHED = "resched";
    private static final String CMD_CANCEL  = "cancel";

    private static PowerManager.WakeLock wl = null;
    private static WifiManager.WifiLock  wfl = null;
    private static int                   wlcnt = 0;

    // Unique Identification Number for the Notification.
    // R.string.update doens't have any meaning except for random unique number.
    private final int                    notificationId = R.string.update_service_noti_title;
    private NotificationManager          nm;
    private final HashSet<Long>          taskset = new HashSet<Long>();

    // If time is changed Feeder need to re-scheduling scheduled-update.
    public static class DateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO
            // At some devices, time is set whenever device back to active from sleep.
            // So, this receiver is called too often.
            // To avoid this, just ignore this intent until any better solution is found.
            // => Back to active this code again. Rescheduling often is no harmful and
            //      sometimes it keep App. safe from unexpected-not-scheduled-bug.
            //
            // FIXME
            // Because Feeder ignores this intent, when system time is changed,
            //   scheduled update isn't re-scheduled!
            // This is bug, but in most cases, system time is not changed manually.
            // So, let's ignore this exceptional case.
            //
            // TODO
            // Any better way??
            Intent svc = new Intent(context, ScheduledUpdater.class);
            svc.putExtra("cmd", CMD_RESCHED);
            // onStartCommand will be sent!
            context.startService(svc);
            // Update should be started before falling into sleep.
            getWakeLock(context.getApplicationContext());
        }
    }

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
            if (time < 0) {
                eAssert(false);
                return;
            }
            Intent svc = new Intent(context, ScheduledUpdater.class);
            svc.putExtra("cmd", CMD_ALARM);
            svc.putExtra("time", time);
            // onStartCommand will be sent!
            context.startService(svc);
            // Update should be started before falling into sleep.
            getWakeLock(context.getApplicationContext());
        }
    }

    private class UpdateBGTask extends BGTaskUpdateChannel implements
    BGTask.OnEvent {
        private long    cid = -1;
        private int     startId = -1;

        UpdateBGTask(long cid, int startId, BGTaskUpdateChannel.Arg arg) {
            super(ScheduledUpdater.this, arg);
            this.cid = cid;
            this.startId = startId;
        }

        @Override
        public void
        onProgress(BGTask task, long progress) {
        }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            logI("ScheduledUpdater(onCancel) : " + cid);
            synchronized (taskset) {
                taskset.remove(cid);
                logI("    taskset size : " + taskset.size());
                if (taskset.isEmpty())
                    stopSelf();
            }
        }

        @Override
        public void
        onPreRun(BGTask task) {
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            logI("ScheduledUpdater(onPostRun) : " + cid + " (" + getResources().getText(result.getMsgId()) + ")");
            synchronized (taskset) {
                taskset.remove(cid);
                if (taskset.isEmpty())
                    stopSelf();
            }
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
            wfl = ((WifiManager)context.getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL, WLTag);
            logI("ScheduledUpdater : WakeLock created and aquired");
            wl.acquire();
            wfl.acquire();
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
            wfl.release();
            // !! NOTE : Important.
            // if below line "wl = null" is removed, then RuntimeException is raised
            //   when 'getWakeLock' -> 'putWakeLock' -> 'getWakeLock' -> 'putWakeLock(*)'
            //   (at them moment of location on * is marked - last 'putWakeLock').
            // That is, once WakeLock is released, reusing is dangerous in current Android Framework.
            // I'm not sure that this is Android FW's bug... or I missed something else...
            // Anyway, let's set 'wl' as 'null' here to re-create new WakeLock at next time.
            wl = null;
            wfl = null;
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

    /**
     * NOTE
     * Next updates which are at least 1-min after, will be scheduled.
     * @param context
     * @param calNow
     */
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

        final long invalidNearestNext = dayInMs * 2;
        long nearestNext = invalidNearestNext; // large enough value.
        do {
            String sStr = c.getString(0);
            if (Utils.isValidValue(sStr)) {
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
            }
        } while (c.moveToNext());
        c.close();

        if (nearestNext != invalidNearestNext) {
            // There is valid update
            { // just for variable scope
                long h = nearestNext / hourInMs;
                long m = (nearestNext - h * hourInMs) / (60 * 1000);
                logI("ScheduledUpdater : next wakeup after [ " + h + " hours, " + m + " miniutes ]");
            }

            // convert into real time.
            nearestNext += calNow.getTimeInMillis();
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction(schedUpdateIntentAction);
            intent.putExtra("time", nearestNext);
            PendingIntent pIntent = PendingIntent.getBroadcast(context, ReqCUpdate, intent, PendingIntent.FLAG_CANCEL_CURRENT);

            // Get the AlarmManager service
            AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
            am.set(AlarmManager.RTC_WAKEUP, nearestNext, pIntent);
        }
    }

    private void
    doCmdResched(int startId) {
        // Just reschedule and return.
        scheduleNextUpdate(this, Calendar.getInstance());
    }

    private void
    doCmdAlarm(int startId, long schedTime) {
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
            return;
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
            return; // There is no channel.
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
            // NOTE
            // onStartCommand() is run on UIThread.
            // So, I don't need to worry about race-condition caused from re-entrance of this function
            // That's why 'getState' and 'unregister/register' are not synchronized explicitly.
            RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
            if (RTTask.TaskState.Canceling == state
                || RTTask.TaskState.Running == state
                || RTTask.TaskState.Ready == state) {
                logI("doCmdAlarm : Channel [" + cid + "] is already active.\n" +
                     "             So scheduled update is skipped");
            } else {
                // unregister gracefully to start update.
                // There is sanity check in BGTaskManager - see BGTaskManager
                //   to know why this 'unregister' is required.
                RTTask.S().unregister(cid, RTTask.Action.Update);

                UpdateBGTask task = new UpdateBGTask(cid, startId, new BGTaskUpdateChannel.Arg(cid));
                RTTask.S().register(cid, RTTask.Action.Update, task);
                RTTask.S().bind(cid, RTTask.Action.Update, null, task);
                logI("doCmdAlarm : start update BGTask for [" + cid + "]");
                synchronized (taskset) {
                    if (!taskset.add(cid)) {
                        logW("doCmdAlarm : starts duplicated update! : " + cid);
                    }
                }
                RTTask.S().start(cid, RTTask.Action.Update);
            }
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
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ScheduledUpdater ]";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        UnexpectedExceptionHandler.S().registerModule(this);
        nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence title = getText(R.string.update_service_noti_title);
        CharSequence desc = getText(R.string.update_service_noti_desc);

        Intent intent = new Intent(this, ScheduledUpdater.class);
        intent.putExtra("cmd", CMD_CANCEL);

        PendingIntent pi = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder nbldr = new Notification.Builder(this);
        nbldr.setSmallIcon(R.drawable.icon_mini)
             .setTicker(title)
             .setWhen(System.currentTimeMillis())
             .setContentTitle(title)
             .setContentText(desc)
             .setDeleteIntent(pi);
        Notification noti = nbldr.getNotification();
        nm.notify(notificationId, noti);
        // NOTE
        // ScheduledUpdate is NOT high priority service.
        // So, this don't need to be foreground.
        //startForeground(notificationId, noti);
    }

    // NOTE:
    //   onStartCommand is run on main ui thread (same as onReceive).
    //   So, we don't need to concern about race-condition between these two.
    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        String cmd = intent.getStringExtra("cmd");

        logI("ScheduledUpdate : onStartCommand : " + cmd);
        try {
            // 'cmd' can be null.
            // So, DO NOT use "cmd.equals()"...
            if (CMD_RESCHED.equals(cmd))
                doCmdResched(startId);
            else if (CMD_ALARM.equals(cmd)) {
                long schedTime = intent.getLongExtra("time", -1);
                doCmdAlarm(startId, schedTime);
            } else if (CMD_CANCEL.equals(cmd)) {
                ; // Do nothing at this moment.
                // below code doesn't work expected... why?? Am I missing something???
                // RTTask.S().cancelAll();
                // This intent comes from notification.
                // But, in this case wakelock isn't acquired (different from other cases.)
                // So, acquire here to balance with 'putWakeLock()' below (in 'finally' scope).
                getWakeLock(this.getApplicationContext());
                // Stop this service.
                // stopSelf();
            } else
                eAssert(false);
        } finally {
            // At any case wakelock should be released.
            // BGTask itself will manage wakelock for the background tasking.
            // We don't need to worry about update jobs.
            // Just release wakelock for this command.
            putWakeLock();
        }

        synchronized (taskset) {
            if (taskset.isEmpty())
                stopSelf();
        }
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
        nm.cancel(notificationId);
        UnexpectedExceptionHandler.S().unregisterModule(this);
        logI("ScheduledUpdater : onDestroy");
        super.onDestroy();
    }
}
