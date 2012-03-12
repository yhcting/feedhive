package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
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

        Intent intent = new Intent(context, ScheduledUpdater.class);
        intent.putExtra("cid", cid);
        PendingIntent sender = PendingIntent.getService(context, 192837, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Get the AlarmManager service
        AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, nextMs, sender);
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
    }
}
