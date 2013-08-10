/*****************************************************************************
 *    Copyright (C) 2013 Younghyung Cho. <yhcting77@gmail.com>
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

import java.util.Iterator;
import java.util.LinkedList;

public class ListenerManager implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ListenerManager.class);

    private final KeyBasedLinkedList<ListenerNode> mList = new KeyBasedLinkedList<ListenerNode>();

    public interface Listener {
        void onNotify(Object user, Type type, Object a0, Object a1);
    }

    public interface Type {
        long    flag();
    }

    private class ListenerNode {
        long        flag;
        Listener    l;
        Object      user;
        ListenerNode(Listener al, Object user, long aflag) {
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
        LinkedList<ListenerNode> n = new LinkedList<ListenerNode>();
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
        Iterator<ListenerNode> iter = n.iterator();
        while (iter.hasNext()) {
            ListenerNode ln = iter.next();
            ln.l.onNotify(ln.user, type, arg0, arg1);
        }
    }

    /**
     * Post to ui handler
     * @param type
     * @param arg0
     * @param arg1
     */
    public void
    notifyIndirect(final Type type, final Object arg0, final Object arg1) {
        synchronized (mList) {
            Environ.getUiHandler().post(new Runnable() {
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
     * @param listener
     * @param flag
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
     * Should
     * @param listener
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
