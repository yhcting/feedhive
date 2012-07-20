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

import java.io.File;

public class BGTaskDownloadToFile extends BGTask<BGTaskDownloadToFile.Arg, Object> {
    private volatile NetLoader      loader   = null;
    private volatile long           progress = 0;

    public static class Arg {
        final String url;
        final File   toFile;
        final File   tempFile;

        public Arg(String aUrl, File aToFile, File aTempFile) {
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
        super(arg, BGTask.OPT_WAKELOCK | BGTask.OPT_WIFILOCK);
        setPriority(UIPolicy.get().getPrefBGTaskPriority());
    }

    public void
    registerEventListener(Object key, OnEventListener listener) {
        super.registerEventListener(key, listener, false);
        publishProgress(progress);
    }

    @Override
    protected Err
    doBGTask(Arg arg) {
        //logI("* Start background Job : DownloadToFileTask\n" +
        //     "    Url : " + arg.url);
        loader = new NetLoader();

        Err result = Err.NO_ERR;
        try {
            loader.downloadToFile(arg.url,
                    arg.tempFile,
                    arg.toFile,
                    new NetLoader.OnProgress() {
                @Override
                public void onProgress(NetLoader loader, long prog) {
                    // TODO Auto-generated method stub
                    progress = prog;
                    publishProgress(prog);
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
        if (null != loader)
            loader.cancel();
        cleanupStream();
        return true;
    }
}
