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

package free.yhc.feeder;

import static free.yhc.feeder.core.Utils.eAssert;

import java.util.HashSet;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.Environ;
import free.yhc.feeder.core.ListenerManager;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.Utils;


public class NotiManager implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(NotiManager.class);

    private static final String NOTI_INTENT_DELETE_ACTION = "feeder.intent.action.NOTIFICATION_DELETE";
    private static NotiManager sInstance = null;

    private final Handler mBgWorkHandler;

    // active notification set.
    private final HashSet<NotiType> mAnset = new HashSet<>();
    private final NotificationManager mNm = (NotificationManager)Environ.getAppContext()
                                                                        .getSystemService(Context.NOTIFICATION_SERVICE);
    @SuppressWarnings("FieldCanBeLocal")
    private final ChannUpdatedListener mChannUpdatedListener = new ChannUpdatedListener();

    // Should be accessed only by NewItemChecker to avoid race-condition.
    private final HashSet<Long> mNewItemChannSet = new HashSet<>();

    private enum NotiType {
        NEWITEM   (true,
                   R.drawable.noti_newitem,
                   R.string.noti_newitem_title,
                   R.string.noti_newitem_desc),
        ACTION    (false,
                   R.drawable.noti_action,
                   R.string.noti_action_title,
                   R.string.noti_action_desc);

        // true : for keep notification alive even if app. is killed.
        // false: notification should be removed when app. is killed.
        private final boolean _mSticky;
        private final int _mIcon;
        private final int _mTitle;
        private final int _mDesc;
        private final int _mNotiId; // notification id

        private Notification _mNoti;

        private Notification
        buildNotification() {
            Context cxt = Environ.getAppContext();
            Resources res = cxt.getResources();
            CharSequence textTitle = res.getText(_mTitle);
            CharSequence textDesc = res.getText(_mDesc);

            Intent intent = new Intent(cxt, NotiManager.NotiIntentReceiver.class);
            intent.setAction(NOTI_INTENT_DELETE_ACTION);
            intent.putExtra("type", name());
            PendingIntent piDelete = PendingIntent.getBroadcast(cxt, 0, intent,
                                                                PendingIntent.FLAG_UPDATE_CURRENT);

            intent = new Intent(cxt, FeederActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent piContent = PendingIntent.getActivity(cxt, 0, intent, 0);
            Notification.Builder nbldr = new Notification.Builder(cxt);
            nbldr.setSmallIcon(_mIcon)
                 .setTicker(textTitle)
                 .setContentTitle(textTitle)
                 .setContentText(textDesc)
                 .setAutoCancel(true)
                 .setContentIntent(piContent)
                 .setDeleteIntent(piDelete);
            return nbldr.getNotification();
        }

        static NotiType convert(String act) {
            for (NotiType n : NotiType.values()) {
                if (n.name().equals(act))
                    return n;
            }
            return null;
        }

        NotiType(boolean sticky, int icon, int title, int desc) {
            _mSticky  = sticky;
            _mIcon   = icon;
            _mTitle  = title;
            _mDesc   = desc;

            // Unique Identification Number for the Notification.
            // 'title' doens't have any meaning except for random unique number.
            _mNotiId = title;

            // NOTE
            // THIS IS REALLY STRANGE! BUT returning 'noti' doesn't work as expected.
            // PendingIntent sent by notification is NOT the one that is created.
            // For example, NotiType.NEW_ITEM is shown at status bar. And user delete it.
            // Then, pendingIntent for DELETE_ACTION is sent to receiver.
            // That intent SHOULD have extra data string - name - 'NEW_ITEM'.
            // But interestingly, it has 'DOWNLOAD' as it's extra data.
            // One possibility is, it's too early to make pending intent because,
            //   it is called at Application's onCreate().
            // But, still it is NOT understandable!!
            // So, based on experimental result, below code is commented out!
            //
            //noti = buildNotification();
        }

        boolean isSticky() {
            return _mSticky;
        }

        int getNotiId() {
            return _mNotiId;
        }

        Notification getNotification() {
            // Below code is a kind of workaround.
            // Instead of building notification at the constructor,
            //   delaying building notification as much as possible - on demand.
            //return buildNotification();
            if (null == _mNoti)
                _mNoti = buildNotification();
            return _mNoti;
        }
    }

    private enum NewItemCheckCmd {
        SCAN,
        NEW,
        READ
    }

    public static class NotiIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (NOTI_INTENT_DELETE_ACTION.equals(action))
                NotiManager.get().removeNotification(NotiType.convert(intent.getStringExtra("type")));
        }
    }

    private class PostNewItemChecker implements Runnable {
        private final boolean _mNewItemExist;
        PostNewItemChecker(boolean newItemExist) {
            _mNewItemExist = newItemExist;
        }

        @Override
        public void
        run() {
            eAssert(Utils.isUiThread());
            if (mAnset.contains(NotiType.NEWITEM)) {
                if (!_mNewItemExist)
                    removeNotification(NotiType.NEWITEM);
            } else {
                if (_mNewItemExist)
                    addNotification(NotiType.NEWITEM);
            }
        }
    }

    private class NewItemChecker implements Runnable {
        // Channels that have new items.
        private final long[] _mCids;
        private final NewItemCheckCmd _mCmd;

        NewItemChecker(long[] cids, NewItemCheckCmd cmd) {
            _mCids = cids;
            _mCmd = cmd;
        }

        @Override
        public void
        run() {
            // To avoid race-condition regarding mNewItemChannSet.
            // This should be only run at bgWorker thread.
            //
            // NOTE
            // Why Mutex or Synchronization is NOT used?
            // I may design this module like follows.
            //   - 'mNewItemChannSet' is also accessed by other thread (usually UI thread).
            //   - Mutex or Synchronization is used.
            // But, above case there is one critical disadvantage.
            // UI thread may wait for lock released by bgWorker thread.
            // This may lead to sluggish response to user.
            //
            // In addition, we cannot run below checker code in UI thread because,
            //   this requires DB access and accessing DB is very very slow in heavy-DB-access-period
            //   (ex. updating channel)
            eAssert(Thread.currentThread() == mBgWorkHandler.getLooper().getThread());
            switch(_mCmd) {
            case SCAN: {
                // cids are not used in this case
                DBPolicy dbp = DBPolicy.get();
                for (Long cid : dbp.getChannelIds()) {
                    long oldLast = dbp.getChannelInfoLong(cid, ColumnChannel.OLDLAST_ITEMID);
                    long max     = dbp.getItemInfoMaxId(cid);
                    if (oldLast < max)
                        mNewItemChannSet.add(cid);
                    else
                        mNewItemChannSet.remove(cid);
                }
                Environ.getUiHandler().post(new PostNewItemChecker(!mNewItemChannSet.isEmpty()));
            } break;

            case NEW:
                for (Long cid : _mCids)
                    mNewItemChannSet.add(cid);
                Environ.getUiHandler().post(new PostNewItemChecker(true));
                break;

            case READ:
                for (Long cid : _mCids)
                    mNewItemChannSet.remove(cid);
                Environ.getUiHandler().post(new PostNewItemChecker(!mNewItemChannSet.isEmpty()));
                break;
            }
        }
    }

    private class ChannUpdatedListener implements ListenerManager.Listener {
        @Override
        public void
        onNotify(Object user, ListenerManager.Type type, Object a0, Object a1) {
            if (DBG) P.i("Noty : " + ((DBPolicy.UpdateType)type).name());
            switch ((DBPolicy.UpdateType)type) {
            case NEW_ITEMS: {
                long cid = (Long)a0;
                mBgWorkHandler.post(new NewItemChecker(new long[] { cid }, NewItemCheckCmd.NEW));
            } break;

            case LAST_ITEM_ID: {
                long[] cids = (long[])a0;
                mBgWorkHandler.post(new NewItemChecker(cids, NewItemCheckCmd.READ));
            } break;
            default:
                Utils.eAssert(false);
            }
        }
    }

    private NotiManager() {
        DBPolicy.get().registerChannelUpdatedListener(this, mChannUpdatedListener);
        HandlerThread hThread = new HandlerThread("NotiManager",Process.THREAD_PRIORITY_BACKGROUND);
        hThread.start();
        mBgWorkHandler = new Handler(hThread.getLooper());
        mBgWorkHandler.post(new NewItemChecker(null, NewItemCheckCmd.SCAN));
    }

    private void
    addNotification(NotiType type) {
        // To avoid unexpected race-condition.
        eAssert(Utils.isUiThread());
        if (NotiType.ACTION == type // UPDATE Notification is used only for 'foreground service' noti.
            || mAnset.contains(type) // already notified.
            || (NotiType.NEWITEM == type // new item but, user don't want to see.
                && !Utils.isPrefNewmsgNoti()))
            return; // notification is already notified. Nothing to do.

        mAnset.add(type);
        // Set event time.
        Notification n = type.getNotification();
        n.when = System.currentTimeMillis();
        mNm.notify(type.getNotiId(), n);
    }

    private void
    removeNotification(NotiType type) {
        // To avoid unexpected race-condition.
        eAssert(Utils.isUiThread());
        if (mAnset.contains(type)) {
            mAnset.remove(type);
            mNm.cancel(type.getNotiId());
        }
    }

    public static NotiManager
    get() {
        if (null == sInstance)
            sInstance = new NotiManager();
        return sInstance;
    }

    public Notification
    getForegroundNotification() {
        Notification n = NotiType.ACTION.getNotification();
        n.when = System.currentTimeMillis();
        return n;
    }

    public int
    getForegroundNotificationId() {
        return NotiType.ACTION.getNotiId();
    }

    public void
    removeAllNonStickyNotification() {
        for (NotiType nty : NotiType.values()) {
            if (!nty.isSticky())
                removeNotification(nty);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ NotiManager ]";
    }
}
