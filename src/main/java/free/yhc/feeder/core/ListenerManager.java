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

import java.util.Iterator;
import java.util.LinkedList;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;

public class ListenerManager implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ListenerManager.class, Logger.LOGLV_DEFAULT);

    private final KeyBasedLinkedList<ListenerNode> mList = new KeyBasedLinkedList<>();

    public interface Listener {
        void onNotify(Object user, Type type, Object a0, Object a1);
    }

    public interface Type {
        long flag();
    }

    private class ListenerNode {
        long flag;
        Listener l;
        Object user;
        ListenerNode(Listener al,
                     @SuppressWarnings("unused") Object user,
                     long aflag) {
            l = al;
            flag = aflag;
        }
    }

    public ListenerManager() {
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ListenerManager ]";
    }

    public void
    notifyDirect(final Type type, final Object arg0, final Object arg1) {
        LinkedList<ListenerNode> n = new LinkedList<>();
        synchronized (mList) {
            // copy
            Iterator<ListenerNode> iter = mList.iterator();
            while (iter.hasNext()) {
                ListenerNode ln = iter.next();
                if (0 != (ln.flag & type.flag()))
                    n.add(ln);
            }
        }

        // call notify out of critical section
        for (ListenerNode ln : n)
            ln.l.onNotify(ln.user, type, arg0, arg1);
    }

    /**
     * Post to ui handler
     */
    public void
    notifyIndirect(final Type type, final Object arg0, final Object arg1) {
        synchronized (mList) {
            AppEnv.getUiHandler().post(new Runnable() {
                @Override
                public void
                run() {
                    Iterator<ListenerNode> iter = mList.iterator();
                    while (iter.hasNext()) {
                        ListenerNode ln = iter.next();
                        if (0 != (ln.flag & type.flag()))
                            ln.l.onNotify(ln.user, type, arg0, arg1);
                    }
                }
            });
        }
    }

    public void
    notifyDirect(final Type type, final Object arg0) {
        notifyDirect(type, arg0, null);
    }

    @SuppressWarnings("unused")
    public void
    notifyDirect(final Type type) {
        notifyDirect(type, null);
    }

    public void
    notifyIndirect(final Type type, final Object arg0) {
        notifyIndirect(type, arg0, null);
    }

    public void
    notifyIndirect(final Type type) {
        notifyIndirect(type, null);
    }

    /**
     * Should run on UI Thread.
     */
    public void
    registerListener(Object key, Listener listener, Object user, long flag) {
        synchronized (mList) {
            Iterator<ListenerNode> iter = mList.iterator();
            while (iter.hasNext()) {
                ListenerNode ln = iter.next();
                if (listener == ln.l) {
                    // already registered.
                    // just updating flag is enough.
                    ln.flag = flag;
                    return;
                }
            }
            ListenerNode n = new ListenerNode(listener, user, flag);
            mList.add(key, n);
        }
    }

    public void
    registerListener(Listener listener, Object user, long flag) {
        registerListener(null, listener, user, flag);
    }

    public void
    unregisterListenerByKey(Object key) {
        synchronized (mList) {
            mList.remove(key);
        }
    }

    /**
     *
     */
    public void
    unregisterListener(Listener listener) {
        synchronized (mList) {
            Iterator<ListenerNode> iter = mList.iterator();
            while (iter.hasNext()) {
                ListenerNode ln = iter.next();
                if (listener == ln.l) {
                    iter.remove();
                    return;
                }
            }
        }
    }
}
