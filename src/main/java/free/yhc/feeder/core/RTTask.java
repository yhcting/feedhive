/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

package free.yhc.feeder.core;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import free.yhc.baselib.Logger;
import free.yhc.baselib.async.HelperHandler;
import free.yhc.baselib.async.TmTask;
import free.yhc.baselib.async.TaskManager;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.task.DownloadTask;
import free.yhc.feeder.task.UpdateTask;

import static free.yhc.baselib.util.Util.convertArrayLongTolong;

// Singleton
// Runtime Data
//   : Data that SHOULD NOT be stored at DataBase.
//       but, need to be check in runtime.
// Should be THREAD-SAFE
public class RTTask extends TaskManager implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(RTTask.class, Logger.LOGLV_DEFAULT);

    private static final int AVAILABLE_PROCESSOR = Runtime.getRuntime().availableProcessors();
    private static final int MAX_FAILED_TASK_HISTORY = 500;

    private static RTTask sInstance = null;

    // Dependency on only following modules are allowed
    // - Util
    // - UnexpectedExceptionHandler
    // - DB / DBThread
    // - UIPolicy
    // - DBPolicy
    private final DBPolicy mDbp = DBPolicy.get();

    private final Object mTiLock = new Object();
    private final HashMap<String, TaskInfo> mFailedTasks = new HashMap<>();

    public enum RtState {
        IDLE,
        READY,
        RUN,
        CANCEL,
        FAIL
    }

    public enum Action {
        UPDATE,
        DOWNLOAD;

        @Nullable
        static Action convert(String act) {
            for (Action a : Action.values()) {
                if (a.name().equals(act))
                    return a;
            }
            return null;
        }
    }

    private static boolean
    isFailedTask(@NonNull TmTask task) {
        return !task.isCancel() && null != task.getException();
    }

    // Get singleton instance,.
    @NonNull
    public static RTTask
    get() {
        if (null == sInstance)
            sInstance = new RTTask();
        return sInstance;
    }

    private RTTask() {
        super(HelperHandler.get(),
              2 < AVAILABLE_PROCESSOR? AVAILABLE_PROCESSOR: 2,
              MAX_FAILED_TASK_HISTORY,
              new TaskWatchFilter() {
                  @Override
                  public boolean
                  filter(TaskManager tm,
                         TmTask task, Object result, Exception ex) {
                      // filtering failed task
                      P.bug(task.isDone());
                      return isFailedTask(task);
                  }
              });
        UnexpectedExceptionHandler.get().registerModule(this);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Privates
    //
    ///////////////////////////////////////////////////////////////////////////
    @NonNull
    private static String
    tid(@NonNull Action act, long id) {
        return act.name() + "/" + id;
    }

    @NonNull
    private static Action
    actionFromTid(@NonNull String id) {
        int i = id.indexOf('/');
        Action act = Action.convert(id.substring(0, i));
        P.bug(null != act); // This is unexpected internal error!.
        assert null != act;
        return act;
    }

    private long
    idFromTid(@NonNull String id) {
        int i = id.indexOf('/');
        return Long.parseLong(id.substring(i + 1));
    }

    /**
     * Check that task is running or waiting to be run.
     */
    private boolean
    isTaskInAction(@NonNull TmTask task) {
        switch (getRtState(task)) {
        case READY:
        case RUN:
            return true;
        }
        return false;
    }

    /**
     * Get DB ids that are under given action.
     */
    @NonNull
    private long[]
    getIdsInAction(@NonNull Action action) {
        TmTask[] tsks = getTasks(action);
        LinkedList<Long> idl = new LinkedList<>();
        for (TmTask t : tsks) {
            TaskInfo ti = getTaskInfo(t);
            if (isTaskInAction(t))
                idl.add((Long)ti.ttag);
        }
        return convertArrayLongTolong(idl.toArray(new Long[idl.size()]));
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Protected
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Publics
    //
    ///////////////////////////////////////////////////////////////////////////
    //=========================================================================
    // Overriding
    //=========================================================================
    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ RTTask ]";
    }

    public boolean
    isFailed(@NonNull TmTask task) {
        return isFailedTask(task);
    }

    public boolean
    addTask(@NonNull TmTask task, long id, @NonNull Action act) {
        if (DBG)
            P.bug((Action.DOWNLOAD == act && task instanceof DownloadTask)
                   || (Action.UPDATE == act && task instanceof UpdateTask));
        return super.addTask(task, tid(act, id), act, id);
    }

    @Nullable
    public TmTask
    getTask(long id, Action act) {
        return getTask(tid(act, id));
    }

    public DownloadTask
    getDownloadTask(long id) {
        return (DownloadTask)getTask(id, Action.DOWNLOAD);
    }

    public UpdateTask
    getUpdateTask(long cid) {
        return (UpdateTask)getTask(cid, Action.UPDATE);
    }

    public RtState
    getRtState(TmTask t) {
        if (null == t)
            return RtState.IDLE;
        switch (t.getState()) {
        case READY:
            return RtState.READY;
        case STARTED:
            // Task should be in RunQ
            return RtState.RUN;
        case CANCELLING:
            return RtState.CANCEL;
        case CANCELLED:
        case DONE:
        case TERMINATED:
        case TERMINATED_CANCELLED:
            return isFailed(t)? RtState.FAIL: RtState.IDLE;
        }
        P.bug(false);
        return RtState.IDLE;
    }

    //===============================================
    // Complex UI Specific Requirement.
    //===============================================
    /**
     * Get item ids that are under downloading.
     * In other words, item ids that are bindded to Task whose action type is 'Download'
     */
    public long[]
    getItemsDownloading() {
        return getIdsInAction(Action.DOWNLOAD);
    }

    /**
     * Get items that are under downloading and belonging to given channel.
     * In other words, item ids that are bindded to Task whose action type is 'Download'
     *   and item's channel taskId is same with given cid.
     */
    public long[]
    getItemsDownloading(long cid) {
        return getItemsDownloading(new long[] { cid });
    }

    /**
     * Get items that are under downloading and belonging to given channels.
     * In other words, item ids that are bindded to Task whose action type is 'Download'
     *   and item's channel taskId is same with one of given cids.
     */
    public long[]
    getItemsDownloading(long[] cids) {
        long[] dnids = getItemsDownloading();
        HashSet<Long> set = new HashSet<>();
        for (long cid : cids)
            set.add(cid);

        LinkedList<Long> l = new LinkedList<>();
        try {
            mDbp.getDelayedChannelUpdate();
            for (long dnid : dnids)
                if (set.contains(mDbp.getItemInfoLong(dnid, ColumnItem.CHANNELID)))
                    l.add(dnid);
        } finally {
            mDbp.putDelayedChannelUpdate();
        }
        return convertArrayLongTolong(l.toArray(new Long[l.size()]));
    }

    public long[]
    getChannelsUpdating() {
        return getIdsInAction(Action.UPDATE);
    }
}
