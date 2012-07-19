package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.HashSet;
import java.util.Iterator;

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
    private static final String NOTI_INTENT_CONTENT_ACTION = "feeder.intent.action.NOTIFICATION_CONTENT";

    private static NotiManager instance = null;

    private final Handler               bgWorkHandler;

    // active notification set.
    private final HashSet<NotiType>     anset = new HashSet<NotiType>();
    private final NotificationManager   nm = (NotificationManager)Utils.getAppContext()
                                                                       .getSystemService(Context.NOTIFICATION_SERVICE);
    private final TaskRunQListener      taskRunQListener = new TaskRunQListener();
    private final ChannUpdatedListener  channUpdatedListener = new ChannUpdatedListener();

    // Should be accessed only by NewItemChecker to avoid race-condition.
    private final HashSet<Long>         newItemChannSet = new HashSet<Long>();

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
        private final boolean   sticky;
        private final int       icon;
        private final int       title;
        private final int       desc;
        private final int       notiId; // notification id

        private final Notification noti;

        private Notification
        buildNotification() {
            Resources res = Utils.getAppContext().getResources();
            CharSequence textTitle = res.getText(title);
            CharSequence textDesc = res.getText(desc);

            Intent intent = new Intent(Utils.getAppContext(), NotiManager.NotiIntentReceiver.class);
            intent.setAction(NOTI_INTENT_DELETE_ACTION);
            intent.putExtra("type", this.name());
            PendingIntent piDelete = PendingIntent.getBroadcast(Utils.getAppContext(), 0, intent,
                                                                PendingIntent.FLAG_UPDATE_CURRENT);

            intent = new Intent(Utils.getAppContext(), NotiManager.NotiIntentReceiver.class);
            intent.setAction(NOTI_INTENT_CONTENT_ACTION);
            PendingIntent piContent = PendingIntent.getBroadcast(Utils.getAppContext(), 0, intent,
                                                                 PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder nbldr = new Notification.Builder(Utils.getAppContext());
            nbldr.setSmallIcon(icon)
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

        NotiType(boolean aSticky, int aIcon, int aTitle, int aDesc) {
            sticky  = aSticky;
            icon   = aIcon;
            title  = aTitle;
            desc   = aDesc;

            // Unique Identification Number for the Notification.
            // 'title' doens't have any meaning except for random unique number.
            notiId = title;

            noti = buildNotification();
        }

        boolean isSticky() {
            return sticky;
        }

        int getNotiId() {
            return notiId;
        }

        Notification getNotification() {
            return noti;
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
            else if (NOTI_INTENT_CONTENT_ACTION.equals(action)) {
                // Launch application
                Intent i = new Intent(Utils.getAppContext(), FeederActivity.class);
                i.setAction(Intent.ACTION_MAIN);
                context.startActivity(i);
            }
        }
    }


    private static class BGWorker extends HandlerThread {
        BGWorker() {
            super("NotiManager", Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    private class PostNewItemChecker implements Runnable {
        private final boolean newItemExist;
        PostNewItemChecker(boolean aNewItemExist) {
            newItemExist = aNewItemExist;
        }

        @Override
        public void
        run() {
            eAssert(Utils.isUiThread());
            if (anset.contains(NotiType.NEWITEM)) {
                if (!newItemExist)
                    removeNotification(NotiType.NEWITEM);
            } else {
                if (newItemExist)
                    addNotification(NotiType.NEWITEM);
            }
        }
    }


    private class NewItemChecker implements Runnable {
        // Channels that have new items.
        private final long[]            cids;
        private final NewItemCheckCmd   cmd;

        NewItemChecker(long[] aCids, NewItemCheckCmd aCmd) {
            cids = aCids;
            cmd = aCmd;
        }

        @Override
        public void
        run() {
            // To avoid race-condition regarding newItemChannSet.
            // This should be only run at bgWorker thread.
            //
            // NOTE
            // Why Mutex or Synchronization is NOT used?
            // I may design this module like follows.
            //   - 'newItemChannSet' is also accessed by other thread (usually UI thread).
            //   - Mutex or Synchronization is used.
            // But, above case there is one critical disadvantage.
            // UI thread may wait for lock released by bgWorker thread.
            // This may lead to sluggish response to user.
            //
            // In addition, we cannot run below checker code in UI thread because,
            //   this requires DB access and accessing DB is very very slow in heavy-DB-access-period
            //   (ex. updating channel)
            eAssert(Thread.currentThread() == bgWorkHandler.getLooper().getThread());
            switch(cmd) {
            case SCAN: {
                // cids are not used in this case
                DBPolicy dbp = DBPolicy.get();
                for (Long cid : dbp.getChannelIds()) {
                    long oldLast = dbp.getChannelInfoLong(cid, ColumnChannel.OLDLAST_ITEMID);
                    long max     = dbp.getItemInfoMaxId(cid);
                    if (oldLast < max)
                        newItemChannSet.add(cid);
                    else
                        newItemChannSet.remove(cid);
                }
                Utils.getUiHandler().post(new PostNewItemChecker(!newItemChannSet.isEmpty()));
            } break;

            case NEW:
                for (Long cid : cids)
                    newItemChannSet.add(cid);
                Utils.getUiHandler().post(new PostNewItemChecker(true));
                break;

            case READ:
                for (Long cid : cids)
                    newItemChannSet.remove(cid);
                Utils.getUiHandler().post(new PostNewItemChecker(!newItemChannSet.isEmpty()));
                break;
            }
        }
    }



    private class TaskRunQListener implements RTTask.OnRunQueueChangedListener {
        private int nrUpdate    = 0;
        private int nrDownload  = 0;
        @Override
        public void onChanged(BGTask enTask, long enId, Action enAct,
                              BGTask deTask, long deId, Action deAct) {
            if (null != enTask) {
                if (Action.UPDATE == enAct)
                    nrUpdate++;
                if (Action.DOWNLOAD == enAct)
                    nrDownload++;
            }

            if (null != deTask) {
                if (Action.UPDATE == deAct)
                    nrUpdate--;
                if (Action.DOWNLOAD == deAct)
                    nrDownload--;
            }

            eAssert(nrUpdate >= 0 && nrDownload >= 0);

            if (anset.contains(NotiType.UPDATE)) {
                if (0 == nrUpdate)
                    removeNotification(NotiType.UPDATE);
            } else {
                if (0 < nrUpdate)
                    addNotification(NotiType.UPDATE);
            }

            if (anset.contains(NotiType.DOWNLOAD)) {
                if (0 == nrDownload)
                    removeNotification(NotiType.DOWNLOAD);
            } else {
                if (0 < nrDownload)
                    addNotification(NotiType.DOWNLOAD);
            }
        }
    }

    private class ChannUpdatedListener implements DBPolicy.OnChannelUpdatedListener {
        @Override
        public void
        onNewItemsUpdated(long cid, int nrNewItems) {
            //logI("NotiManager : NewItemsUpdated");
            bgWorkHandler.post(new NewItemChecker(new long[] { cid }, NewItemCheckCmd.NEW));
        }

        @Override
        public void
        onLastItemIdUpdated(long[] cids) {
            //logI("NotiManager : LastItemIdUpdated");
            bgWorkHandler.post(new NewItemChecker(cids, NewItemCheckCmd.READ));
        }
    }

    private NotiManager() {
        DBPolicy.get().registerChannelUpdatedListener(this, channUpdatedListener);
        RTTask.get().registerRunQChangedListener(this, taskRunQListener);
        HandlerThread hThread = new HandlerThread("NotiManager",Process.THREAD_PRIORITY_BACKGROUND);
        hThread.start();
        bgWorkHandler = new Handler(hThread.getLooper());
        bgWorkHandler.post(new NewItemChecker(null, NewItemCheckCmd.SCAN));
    }

    private void
    addNotification(NotiType type) {
        // To avoid unexpected race-condition.
        eAssert(Utils.isUiThread());
        if (anset.contains(type))
            return; // notification is already notified. Nothing to do.
        anset.add(type);
        // Set event time.
        type.getNotification().when = System.currentTimeMillis();
        nm.notify(type.getNotiId(), type.getNotification());
    }

    private void
    removeNotification(NotiType type) {
        // To avoid unexpected race-condition.
        eAssert(Utils.isUiThread());
        if (anset.contains(type)) {
            anset.remove(type);
            nm.cancel(type.getNotiId());
        }
    }

    public static NotiManager
    get() {
        if (null == instance)
            instance = new NotiManager();
        return instance;
    }

    public void
    removeAllNonStickyNotification() {
        Iterator<NotiType> itr = anset.iterator();
        while (itr.hasNext()) {
            NotiType nty = itr.next();
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
