/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
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

package free.yhc.feeder.model;

public class BGTaskUpdateChannel extends BGTask<BGTaskUpdateChannel.Arg, Object> {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(BGTaskUpdateChannel.class);

    private volatile NetLoader mLoader = null;

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
    }

    @Override
    protected Err
    doBgTask(Arg arg) {
        try {
            mLoader = new NetLoader();
            if (null == arg.customIconref)
                mLoader.updateLoad(arg.cid);
            else
                mLoader.updateLoad(arg.cid, arg.customIconref);
        } catch (FeederException e) {
            //logI("BGTaskUpdateChannel : Updating [" + arg.cid + "] : interrupted!");
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
        if (null != mLoader)
            mLoader.cancel();     // This is HACK for fast-interrupt.
        return true;
    }
}
