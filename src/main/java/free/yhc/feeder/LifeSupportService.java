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
package free.yhc.feeder;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import free.yhc.baselib.Logger;
import free.yhc.abaselib.LifeSupporter;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

public class LifeSupportService extends Service implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(LifeSupportService.class, Logger.LOGLV_DEFAULT);

    private static final LifeSupporter sLifeSup
            = LifeSupporter.create(LifeSupportService.class,
                                LifeSupporter.LOCK_WAKE | LifeSupporter.LOCK_WIFI);

    public static void
    init() {
        LifeSupporter.addGuestTaskManager(sLifeSup, RTTask.get());
    }

    public static void
    start() {
        sLifeSup.start();
    }

    public static void
    stop() {
        sLifeSup.stop();
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
