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

package free.yhc.feeder.core;

import java.io.File;

public class BGTaskDownloadToFile extends BGTask<BGTaskDownloadToFile.Arg, Object> {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Utils.Logger P = new Utils.Logger(BGTaskDownloadToFile.class);

    private volatile NetLoader mLoader = null;
    private volatile int mProgress = 0;

    public static class Arg {
        final String url;
        final File toFile;
        final File tempFile;

        public Arg(String aUrl, File aToFile, File aTempFile) {
            Utils.eAssert(null != aUrl && null != aToFile && null != aTempFile);
            url = aUrl;
            toFile = aToFile;
            tempFile = aTempFile;
        }
    }

    private boolean
    cleanupStream() {
        return true;
    }

    public
    BGTaskDownloadToFile(Arg arg) {
        super(arg, OPT_WAKELOCK | OPT_WIFILOCK);
    }

    @Override
    public void
    registerEventListener(Object key, OnEventListener listener, boolean hasPriority) {
        super.registerEventListener(key, listener, hasPriority);
        publishProgress(mProgress);
    }

    @Override
    protected Err
    doBgTask(Arg arg) {
        //logI("* Start background Job : DownloadToFileTask\n" +
        //     "    Url : " + arg.url);
        mLoader = new NetLoader();

        Err result = Err.NO_ERR;
        try {
            mLoader.downloadToFile(arg.url,
                                   arg.tempFile,
                                   arg.toFile,
                                   new NetLoader.OnProgress() {
                @Override
                public void
                onProgress(NetLoader loader, long prog) {
                    mProgress = (int)prog;
                    publishProgress(mProgress);
                }
            });
        } catch (FeederException e) {
            result = e.getError();
        }

        return result;
    }

    @Override
    public boolean
    cancel(Object param) {
        // HACK for fast-interrupt
        // Raise IOException in force
        super.cancel(param);
        if (null != mLoader)
            mLoader.cancel();
        cleanupStream();
        return true;
    }
}
