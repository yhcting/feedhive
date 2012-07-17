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

package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class DBThread extends Thread implements
UnexpectedExceptionHandler.TrackedModule {
    private static DBThread     instance = null;

    // Below two is for MsgArg
    private static final Object idlock = new Object();
    private static       long   idcnt = 0;

    private Handler handler = null;

    public class MsgArg {
        long            id;     // This is unique id of DBThread
                                // This value is set when instance is created
        public Handler  sender; // handler of message sender
                                // This is the place where response will be sent back
        public int      cmd;
        public Object   arg;

        MsgArg(Handler handler, int cmd, Object arg) {
            sender = handler;
            this.cmd = cmd;
            this.arg = arg;
            synchronized (idlock) {
                id = idcnt++;
            }
        }

        public long id() {
            return id;
        }
    }

    private DBThread() {
        // Create singleton instances
        DB.newSession().open();
    }

    // S : Singleton instance
    public static DBThread
    S() {
        eAssert(null != instance);
        return instance;
    }

    public static void
    createSingleton() {
        eAssert(null == instance);
        instance = new DBThread();
        UnexpectedExceptionHandler.S().registerModule(instance);
    }

    public Handler
    handler() {
        return handler;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ DBThread ]";
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                handleMsg(msg);
            }
        };
        Looper.loop();
    }

    // ===================================================
    //
    // Main message handler
    //
    // ===================================================
    private void
    handleMsg(Message msg) {

    }
}
