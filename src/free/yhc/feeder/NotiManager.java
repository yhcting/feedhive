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
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.DB.ColumnChannel;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.RTTask.Action;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;


public class NotiManager implements
UnexpectedExceptionHandler.TrackedModule {
    private static final String NOTI_INTENT_DELETE_ACTION = "feeder.intent.action.NOTIFICATION_DELETE";

    private static NotiManager sInstance = null;

    private final Handler               mBgWorkHandler;

    // active notification set.
    private final HashSet<NotiType>     mAnset = new HashSet<NotiType>();
    private final NotificationManager   mNm = (NotificationManager)Utils.getAppContext()
                                                                        .getSystemService(Context.NOTIFICATION_SERVICE);
    private final TaskQListener         mTaskQListener = new TaskQListener();
    private final ChannUpdatedListener  mChannUpdatedListener = new ChannUpdatedListener();

    // Should be accessed only by NewItemChecker to avoid race-condition.
    private final HashSet<Long>         mNewItemChannSet = new HashSet<Long>();

    private static enum NotiType {
        NEWITEM   (true,
                   R.drawable.noti_newitem,
                   R.string.noti_newitem_title,
                   R.string.noti_newitem_desc),
        UPDATE    (false,
                   R.drawable.noti_update,
                   R.string.noti_update_title,
                   R.string.noti_update_desc),
        DOWNLOAD  (false,
                   R.drawable.noti_download,
                   R.string.noti_download_title,
                   R.string.noti_download_desc);

        // true : for keep notification alive even if app. is killed.
        // false: notification should be removed when app. is killed.
        private final boolean   _mSticky;
        private final int       _mIcon;
        private final int       _mTitle;
        private final int       _mDesc;
        private final int       _mNotiId; // notification id

        private Notification    _mNoti;

        private Notification
        buildNotification() {
            Resources res = Utils.getAppContext().getResources();
            CharSequence textTitle = res.getText(_mTitle);
            CharSequence textDesc = res.getText(_mDesc);

            Intent intent = new Intent(Utils.getAppContext(), NotiManager.NotiIntentReceiver.class);
            intent.setAction(NOTI_INTENT_DELETE_ACTION);
            intent.putExtra("type", name());
            PendingIntent piDelete = PendingIntent.getBroadcast(Utils.getAppContext(), 0, intent,
                                                                PendingIntent.FLAG_UPDATE_CURRENT);

            intent = new Intent(Utils.getAppContext(), FeederActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent piContent = PendingIntent.getActivity(Utils.getAppContext(), 0, intent, 0);
            Notification.Builder nbldr = new Notification.Builder(Utils.getAppContext());
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

    private static enum NewItemCheckCmd {
        SCAN,
        NEW,
        READ
    };

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
        private final long[]            _mCids;
        private final NewItemCheckCmd   _mCmd;

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
                Utils.getUiHandler().post(new PostNewItemChecker(!mNewItemChannSet.isEmpty()));
            } break;

            case NEW:
                for (Long cid : _mCids)
                    mNewItemChannSet.add(cid);
                Utils.getUiHandler().post(new PostNewItemChecker(true));
                break;

            case READ:
                for (Long cid : _mCids)
                    mNewItemChannSet.remove(cid);
                Utils.getUiHandler().post(new PostNewItemChecker(!mNewItemChannSet.isEmpty()));
                break;
            }
        }
    }



    private class TaskQListener implements RTTask.OnTaskQueueChangedListener {
        private int _mNrUpdate    = 0;
        private int _mNrDownload  = 0;
        @Override
        public void
        onEnQ(BGTask task, long id, Action act) {
            if (Action.UPDATE == act) {
                _mNrUpdate++;
                // notification may be deleted by user in the middle.
                // So, checking with only 'nrUpdate' is not enough.
                // 'mAnset' should be checked too!
                if (!mAnset.contains(NotiType.UPDATE))
                    addNotification(NotiType.UPDATE);
            }

            if (Action.DOWNLOAD == act) {
                _mNrDownload++;
                // See comments above regarding 'UPDATE'
                if (!mAnset.contains(NotiType.DOWNLOAD))
                    addNotification(NotiType.DOWNLOAD);
            }
        }

        @Override
        public void
        onDeQ(BGTask task, long id, Action act) {
            if (Action.UPDATE == act) {
                if (0 == --_mNrUpdate
                    && mAnset.contains(NotiType.UPDATE))
                    removeNotification(NotiType.UPDATE);
            }

            if (Action.DOWNLOAD == act) {
                if (0 == --_mNrDownload
                    && mAnset.contains(NotiType.DOWNLOAD))
                    removeNotification(NotiType.DOWNLOAD);
            }
        }
    }

    private class ChannUpdatedListener implements DBPolicy.OnChannelUpdatedListener {
        @Override
        public void
        onNewItemsUpdated(long cid, int nrNewItems) {
            //logI("NotiManager : NewItemsUpdated");
            mBgWorkHandler.post(new NewItemChecker(new long[] { cid }, NewItemCheckCmd.NEW));
        }

        @Override
        public void
        onLastItemIdUpdated(long[] cids) {
            //logI("NotiManager : LastItemIdUpdated");
            mBgWorkHandler.post(new NewItemChecker(cids, NewItemCheckCmd.READ));
        }
    }

    private NotiManager() {
        DBPolicy.get().registerChannelUpdatedListener(this, mChannUpdatedListener);
        RTTask.get().registerTaskQChangedListener(this, mTaskQListener);
        HandlerThread hThread = new HandlerThread("NotiManager",Process.THREAD_PRIORITY_BACKGROUND);
        hThread.start();
        mBgWorkHandler = new Handler(hThread.getLooper());
        mBgWorkHandler.post(new NewItemChecker(null, NewItemCheckCmd.SCAN));
    }

    private void
    addNotification(NotiType type) {
        // To avoid unexpected race-condition.
        eAssert(Utils.isUiThread());
        if (mAnset.contains(type))
            return; // notification is already notified. Nothing to do.

        if (NotiType.NEWITEM == type
            && !Utils.isPrefNewmsgNoti())
            return;

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
