package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.logI;

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


public class ScheduledUpdater extends Service {
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
            stopSelf(startId);
        }

        @Override
        public void
        onPreRun(BGTask task) {
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            stopSelf(startId);
        }
    }

    static void
    setUpdateSchedule(Context context, long cid) {
        // get a Calendar object with current time
        Calendar cal = Calendar.getInstance();
        // add 5 minutes to the calendar object
        cal.add(Calendar.SECOND, 30);
        Intent intent = new Intent(context, ScheduledUpdater.class);
        intent.putExtra("cid", cid);
        PendingIntent sender = PendingIntent.getService(context, 192837, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Get the AlarmManager service
        AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), sender);
    }

    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        logI("ScheduledUpdater : onStartCommand");
        // If we get killed, after returning from here, restart
        long cid = intent.getLongExtra("cid", 0);
        logI("ChannelList : update : " + cid);
        UpdateBGTask task = new UpdateBGTask(cid, startId);
        RTTask.S().registerUpdate(cid, task);
        RTTask.S().bindUpdate(cid, task);
        task.start(new BGTaskUpdateChannel.Arg(cid));
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
