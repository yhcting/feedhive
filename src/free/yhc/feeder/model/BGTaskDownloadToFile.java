package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;


public class BGTaskDownloadToFile extends BGTask<BGTaskDownloadToFile.ItemInfo, BGTaskDownloadToFile.Arg, Object> {
    private InputStream    istream  = null;
    private OutputStream   ostream  = null;
    private Arg            arg      = null;

    public static class ItemInfo {
        public long  cid;
        public long  id;

        public ItemInfo(long cid, long id) {
            this.cid = cid;
            this.id = id;
        }
    }

    public static class Arg {
        String      url         = null;
        String      toFile      = null;
        String      tempFile    = null;

        public Arg(String url, String toFile, String tempFile) {
            this.url = url;
            this.toFile = toFile;
            this.tempFile = tempFile;
        }
    }

    private boolean
    cleanupStream() {
        try {
            if (null != istream)
                istream.close();
            if (null != ostream) {
                ostream.close();
                if (null != arg)
                    if (!new File(arg.tempFile).delete()) {
                        logI("**** Fail to delete temporal downloaded file!!! [" + arg.tempFile +"]\n");
                        return false;
                    }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logW(e.getMessage());
            return false;
        }
        return true;
    }

    public
    BGTaskDownloadToFile(ItemInfo item) {
        super(item);
    }

    @Override
    protected Err
    doBGTask(Arg arg) {
        logI("* Start background Job : DownloadToFileTask\n" +
             "    Url : " + arg.url);
        this.arg = arg;

        URL           url = null;
        URLConnection conn = null;
        int           retry = 5;
        while (0 < retry--) {
            try {
                url = new URL(arg.url);
                conn = url.openConnection();
                conn.setConnectTimeout(1000);
                conn.connect();
                break; // done
            } catch (Exception e) {
                // SocketTimeoutException
                // IOException
                if (Err.UserCancelled == getResult())
                    return Err.UserCancelled;

                if (0 >= retry) {
                    e.printStackTrace();
                    logW(e.getMessage());
                    return Err.IONet;
                }
            }
        }

        try {
            int lenghtOfFile = conn.getContentLength();
            logI("Download File\n" +
                 "    lengh: " + lenghtOfFile + "\n" +
                 "    to   : " + arg.toFile);

            istream = new BufferedInputStream(url.openStream());
            ostream = new FileOutputStream(arg.tempFile);

            if (Thread.currentThread().isInterrupted()) {
                cleanupStream();
                if (Err.NoErr != getResult())
                    return getResult();
                else
                    return Err.Interrupted;
            }

            byte data[] = new byte[256*1024];

            long total = 0;
            int count;

            while (true) {
                if (-1 == (count = istream.read(data)))
                    break;
                ostream.write(data, 0, count);
                total += count;
                publishProgress((int) (total * 100 / lenghtOfFile));
            }

            ostream.flush();
            ostream.close();
            istream.close();
            if (total == lenghtOfFile) {
                if (!new File(arg.tempFile).renameTo(new File(arg.toFile)))
                    return Err.IOFile;
                else
                    return Err.NoErr;
            } else
                return Err.IONet;

        } catch (IOException e) {
            // User's canceling operation close in/out stream in force.
            // And this leads to IOException here.
            // So, we should check that this Exception is caused by user's cancel or real IOException.
            if (Err.UserCancelled == getResult())
                return Err.UserCancelled;
            else {
                e.printStackTrace();
                logW(e.getMessage());
                cleanupStream();
                return Err.IONet;
            }
        }
    }

    @Override
    public boolean
    cancel(Object param) {
        // HACK for fast-interrupt
        // Raise IOException in force
        super.cancel(param);
        cleanupStream();
        return true;
    }
}
