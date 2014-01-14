/******************************************************************************
 *    Copyright (C) 2012, 2013, 2014 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of FeedHive
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
 *    along with this program.	If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.Environ;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.RTTask.Action;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class LifeSupportService extends Service implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(LifeSupportService.class);

    public static final String ACTION_START = "feeder.intent.action.START_LIFE_SUPPORT";
    // Wakelock
    private static final String WLTAG = "free.yhc.feeder.LifeSupportService";

    private static PowerManager.WakeLock sWl = null;
    private static WifiManager.WifiLock  sWfl = null;
    private static int                   sWlcnt = 0;

    private static final TaskQListener   sTaskQListener = new TaskQListener();


    private static class TaskQListener implements RTTask.OnTaskQueueChangedListener {
        private int _mNrAction = 0;

        @Override
        public void
        onEnQ(BGTask task, long id, Action act) {
            if (Action.UPDATE == act
                || Action.DOWNLOAD == act) {
                if (0 == _mNrAction++)
                    // First action...
                    LifeSupportService.start();
            }
        }

        @Override
        public void
        onDeQ(BGTask task, long id, Action act) {
            if (Action.UPDATE == act
                || Action.DOWNLOAD == act) {
                if (0 == --_mNrAction)
                    LifeSupportService.stop();
            }
        }
    }


    public static void
    init() {
        RTTask.get().registerTaskQChangedListener(LifeSupportService.class, sTaskQListener);
    }

    public static void
    start() {
        if (DBG) P.v("Enter");
        Intent i = new Intent(Environ.getAppContext(), LifeSupportService.class);
        i.setAction(ACTION_START);
        Environ.getAppContext().startService(i);
    }

    public static void
    stop() {
        if (DBG) P.v("Enter");
        Intent i = new Intent(Environ.getAppContext(), LifeSupportService.class);
        Environ.getAppContext().stopService(i);
    }

    public static void
    getWakeLock() {
        eAssert(Utils.isUiThread());
        // getWakeLock() and putWakeLock() are used only at main ui thread (broadcast receiver, onStartCommand).
        // So, we don't need to synchronize it!
        eAssert(sWlcnt >= 0);
        if (null == sWl) {
            sWl = ((PowerManager)Environ.getAppContext().getSystemService(Context.POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG);
            sWfl = ((WifiManager)Environ.getAppContext().getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL, WLTAG);
            //logI("ScheduledUpdateService : WakeLock created and aquired");
            sWl.acquire();
            sWfl.acquire();
        }
        sWlcnt++;
        //logI("ScheduledUpdateService(GET) : current WakeLock count: " + mWlcnt);
    }

    public static void
    putWakeLock() {
        eAssert(Utils.isUiThread());

        if (sWlcnt <= 0)
            return; // nothing to put!

        eAssert(sWlcnt > 0);
        sWlcnt--;
        //logI("ScheduledUpdateService(PUT) : current WakeLock count: " + mWlcnt);
        if (0 == sWlcnt) {
            sWl.release();
            sWfl.release();
            // !! NOTE : Important.
            // if below line "mWl = null" is removed, then RuntimeException is raised
            //   when 'getWakeLock' -> 'putWakeLock' -> 'getWakeLock' -> 'putWakeLock(*)'
            //   (at them moment of location on * is marked - last 'putWakeLock').
            // That is, once WakeLock is released, reusing is dangerous in current Android Framework.
            // I'm not sure that this is Android FW's bug... or I missed something else...
            // Anyway, let's set 'mWl' as 'null' here to re-create new WakeLock at next time.
            sWl = null;
            sWfl = null;
            //logI("ScheduledUpdateService : WakeLock is released");
        }
    }



    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lvl) {
        return this.getClass().getName();
    }

    @Override
    public void
    onCreate() {
        if (DBG) P.v("Enter");
        super.onCreate();
        UnexpectedExceptionHandler.get().registerModule(this);
    }

    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        if (!intent.getAction().equals(ACTION_START)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startForeground(NotiManager.get().getForegroundNotificationId(),
                        NotiManager.get().getForegroundNotification());
        return START_NOT_STICKY;
    }

    @Override
    public void
    onDestroy() {
        if (DBG) P.v("Enter");
        UnexpectedExceptionHandler.get().unregisterModule(this);
        super.onDestroy();
    }

    @Override
    public IBinder
    onBind(Intent intent) {
        return null;
    }
}
