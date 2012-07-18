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

import static free.yhc.feeder.model.Utils.logI;

public class BGTaskUpdateChannel extends BGTask<BGTaskUpdateChannel.Arg, Object> {
    private volatile NetLoader loader = null;

    public static class Arg {
        final long    cid;
        final String  customIconref;

        public Arg(long aCid) {
            cid = aCid;
            customIconref = null;
        }
        public Arg(long aCid, String aCustomIconref) {
            cid = aCid;
            customIconref = aCustomIconref;
        }

    }

    public
    BGTaskUpdateChannel(Arg arg) {
        super(arg, BGTask.OPT_WAKELOCK | BGTask.OPT_WIFILOCK);
        setPriority(UIPolicy.get().getPrefBGTaskPriority());
    }

    @Override
    protected Err
    doBGTask(Arg arg) {
        try {
            loader = new NetLoader();
            if (null == arg.customIconref)
                loader.updateLoad(arg.cid);
            else
                loader.updateLoad(arg.cid, arg.customIconref);
        } catch (FeederException e) {
            logI("BGTaskUpdateChannel : Updating [" + arg.cid + "] : interrupted!");
            return e.getError();
        }
        return Err.NO_ERR;
    }

    @Override
    public boolean
    cancel(Object param) {
        // I may misunderstand that canceling background task may corrupt DB
        //   by interrupting in the middle of transaction.
        // But java thread doesn't interrupt it's executing.
        // So, I don't worry about this (different from C.)
        super.cancel(param); // cancel thread
        if (null != loader)
            loader.cancel();     // This is HACK for fast-interrupt.
        return true;
    }
}
