package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.graphics.Bitmap;

public class NetLoader {
    public static final int UPD_DEFAULT     = 0x00;
    public static final int UPD_LOAD_IMG    = 0x01;
    public static final int UPD_ACTION      = 0x02;
    public static final int UPD_INIT        = UPD_ACTION | UPD_LOAD_IMG;

    private static final int NET_RETRY = 5;

    private DBPolicy                dbp     = DBPolicy.S();
    private volatile boolean        cancelled = false;
    private volatile InputStream    istream = null; // Multi-thread access
    private volatile OutputStream   ostream = null;
    private volatile String         tmpFile = null;

    interface OnProgress {
        // "progress < 0" means "Unknown progress"
        // In this case, negative value of bytes processed are passed as value of 'progress'
        void onProgress(NetLoader loader, long progress);
    }

    private void
    closeIstream() throws FeederException {
        try {
            if (null != istream)
                istream.close();
        } catch (IOException e) {
            throw new FeederException (Err.IONet);
        }
    }

    private void
    closeOstream() throws FeederException {
        try {
            if (null != ostream)
                ostream.close();
        } catch (IOException e) {
            throw new FeederException (Err.IOFile);
        }
    }

    private boolean
    clearTempFile() {
        boolean ret = true;
        if (null != tmpFile) {
            ret = new File(tmpFile).delete();
        }
        return ret;
    }

    private boolean
    cleanup() {
        try {
            if (null != istream)
                istream.close();
            if (null != ostream)
                ostream.close();
            if (null != tmpFile)
                new File(tmpFile).delete();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void
    checkInterrupted() throws FeederException {
        if (cancelled)
            throw new FeederException(Err.UserCancelled);
        if (Thread.currentThread().isInterrupted())
            throw new FeederException(Err.Interrupted);
    }

    /**
     * NOTE!
     *   Many caller assumes that 'download' function throws only below three exceptions.
     *   If another exception need to be thrown, all callers should be checked and verified!
     *
     * FeederException : Err.IONet / Err.UserCancelled / Err.Interrupted
     *
     * @param ostream
     * @param urlStr
     * @param progressListener
     * @throws FeederException
     */
    private void
    download(OutputStream outstream, String urlStr, OnProgress progressListener)
            throws FeederException {
           URL           url = null;
           URLConnection conn = null;
           int           retry = NET_RETRY;
           try {
               url = new URL(urlStr);
           } catch (MalformedURLException e) {
               throw new FeederException(Err.InvalidURL);
           }

           while (0 < retry--) {
               try {
                   conn = url.openConnection();
                   conn.setConnectTimeout(1000);
                   conn.connect();
                   break; // done
               } catch (Exception e) {
                   // SocketTimeoutException
                   // IOException
                   if (cancelled)
                       throw new FeederException(Err.UserCancelled);

                   if (0 >= retry)
                       throw new FeederException(Err.IONet);

                   try {
                       Thread.sleep(500);
                   } catch (InterruptedException ie) {
                       if (cancelled)
                           throw new FeederException(Err.UserCancelled);
                       else
                           throw new FeederException(Err.Interrupted);
                   }
               }
           }

           try {
               int lengthOfFile = -1;
               retry = NET_RETRY;
               while (0 < retry--) {
                   lengthOfFile = conn.getContentLength();
                   if (lengthOfFile >= 0)
                       break;
               }
               istream = new BufferedInputStream(conn.getInputStream());

               if (Thread.currentThread().isInterrupted()) {
                   cancel();
                   throw new FeederException(Err.Interrupted);
               }

               byte data[] = new byte[256*1024];

               long total = 0;
               int  count;
               long prevProgress = -1;
               while (true) {
                   if (-1 == (count = istream.read(data)))
                       break;
                   outstream.write(data, 0, count);
                   total += count;
                   long progress = (total * 100 / lengthOfFile);
                   if (lengthOfFile < 0 && null != progressListener) {
                       progressListener.onProgress(this, -total);
                   } else if (progress > prevProgress) {
                       if (null != progressListener)
                           progressListener.onProgress(this, progress);
                       prevProgress = progress;
                   }
               }

               outstream.flush();
               outstream.close();
           } catch (IOException e) {
               // User's canceling operation close in/out stream in force.
               // And this leads to IOException here.
               // So, we should check that this Exception is caused by user's cancel or real IOException.
               if (cancelled)
                   throw new FeederException(Err.UserCancelled);
               else {
                   e.printStackTrace();
                   logW(e.getMessage());
                   throw new FeederException(Err.IONet);
               }
           } finally {
               closeIstream();
           }
    }


    public NetLoader() {
    }

    private RSSParser.Result
    parseFeedUrl(String url)
            throws FeederException {
        logI("Fetching Channel [" + url + "]\n");
        RSSParser.Result res = null;
        long             time;
        int              retry = NET_RETRY;
        try {
            while (0 < retry--) {
                try {
                    time = System.currentTimeMillis();
                    URLConnection conn = new URL(url).openConnection();
                    conn.setReadTimeout(1000);
                    istream = conn.getInputStream();
                    Document dom = DocumentBuilderFactory
                                    .newInstance()
                                    .newDocumentBuilder()
                                    .parse(istream);
                    istream.close();
                    logI("TIME: Open URL and Parseing as Dom : " + (System.currentTimeMillis() - time));
                    time = System.currentTimeMillis();
                    // Only RSS is supported at this version.
                    res = new RSSParser().parse(dom);
                    logI("TIME: RSSParsing : " + (System.currentTimeMillis() - time));
                    break; // done
                } catch (IOException e) {
                    if (cancelled)
                        throw new FeederException(Err.UserCancelled);

                    if (0 >= retry)
                        throw new FeederException(Err.IONet);

                    ; // continue next retry after some time.
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        if (cancelled)
                            throw new FeederException(Err.UserCancelled);
                        else
                            throw new FeederException(Err.Interrupted);
                    }
                }
            }
        } catch (DOMException e) {
            throw new FeederException(Err.ParserUnsupportedFormat);
        } catch (SAXException e) {
            e.printStackTrace();
            throw new FeederException(Err.ParserUnsupportedFormat);
        } catch (ParserConfigurationException e) {
            throw new FeederException(Err.ParserUnsupportedFormat);
        } catch (FeederException e) {
            throw e;
        } finally {
            closeIstream();
        }
        return res;
    }

    /*
     * Update feed information.
     * if (null != imageref)
     * then 'imageref' is used instead of given by RSS channel infomation.
     */
    private void
    update(long cid, int flag, String imageref)
            throws FeederException {
        String url = dbp.getChannelInfoString(cid, DB.ColumnChannel.URL);
        eAssert(null != url);

        logI("Loading Items: " + url);

        long time = System.currentTimeMillis();
        RSSParser.Result res = parseFeedUrl(url);
        logI("TIME: Loading + Parsing : " + (System.currentTimeMillis() - time));

        // set to given value forcely due to this is 'update' - Not new insertion.
        Feed.Channel ch = new Feed.Channel(res.channel, res.items);
        ch.dbD.id = cid;

        // decide action type.
        // Actually deciding only once is enough.
        // But, to simplify code structure this code is here.
        // This is definitely overhead. But, it's not big overhead.
        // Instead of that, we can get simplified code.
        if (0 != (UPD_ACTION & flag)) {
            if (ch.items.length > 0)
                ch.dynD.action = UIPolicy.decideDefaultActionType(ch.parD, ch.items[0].parD);
            else
                ch.dynD.action = UIPolicy.decideDefaultActionType(ch.parD, null);
        }

        if (0 != (UPD_LOAD_IMG & flag)) {
            time = System.currentTimeMillis();
            String imgurl = (null == imageref)? ch.parD.imageref: imageref;

            byte[] bmdata = downloadToRaw(imgurl, null);
            checkInterrupted();
            Bitmap bm = Utils.decodeImage(bmdata,
                                          Feed.Channel.ICON_MAX_WIDTH,
                                          Feed.Channel.ICON_MAX_HEIGHT);
            ch.dynD.imageblob = Utils.compressBitmap(bm);
            bm.recycle();
            String prefix = (null == ch.dynD.imageblob)? "< Fail >" : "< Ok >";
            logI("TIME: Handle Image : " + prefix + (System.currentTimeMillis() - time));
            checkInterrupted();
        }

        time = System.currentTimeMillis();

        LinkedList<Feed.Item> newItems = new LinkedList<Feed.Item>();
        dbp.getNewItems(ch.items, newItems);
        DBPolicy.ItemDataOp idop = null;
        if (Feed.Channel.isUpdDn(ch.dynD.updatetype)) {
            if (Feed.Channel.isActTgtLink(ch.dynD.updatetype)) {
                idop = new DBPolicy.ItemDataOp() {
                    @Override
                    public byte[] getData(Feed.Item item) throws FeederException {
                        return downloadToRaw(item.parD.link, null);
                    }
                };
            } else if (Feed.Channel.isActTgtEnclosure(ch.dynD.updatetype)) {
                idop = new DBPolicy.ItemDataOp() {
                    @Override
                    public byte[] getData(Feed.Item item) throws FeederException {
                        downloadToFile(item.parD.enclosureUrl,
                                       UIPolicy.getItemDownloadTempPath(item.dbD.id),
                                       UIPolicy.getItemFilePath(item.dbD.id, item.parD.title, item.parD.enclosureUrl),
                                       null);
                        return null;
                    }
                };
            }
        }
        checkInterrupted();
        dbp.updateChannel(ch, newItems, idop);

        logI("TIME: Updating Items : " + (System.currentTimeMillis() - time));
    }

    public void
    updateLoad(long cid, int flag)
            throws FeederException {
        update(cid, flag, null);
    }

    public void
    updateLoad(long cid, int flag, String imageref)
            throws FeederException {
        eAssert(null != imageref);
        update(cid, flag, imageref);
    }

    public byte[]
    downloadToRaw(String url, OnProgress progressListener)
            throws FeederException {
        // set as class private for future cleanup.
        ostream = new ByteArrayOutputStream();
        byte[] ret = null;
        try {
            download(ostream, url, null);
            ret = ((ByteArrayOutputStream )ostream).toByteArray();
        } finally {
            closeIstream();
            closeOstream();
        }
        return ret;
    }

    public void
    downloadToFile(String url, String tempFile, String toFile, OnProgress progressListener)
        throws FeederException {
        // Set as class private for future cleanup.
        tmpFile = tempFile;
        try {
            ostream = new FileOutputStream(tmpFile);
            download(ostream, url, progressListener);
            if (!new File(tmpFile).renameTo(new File(toFile)))
                throw new FeederException(Err.IOFile);
        } catch (FileNotFoundException e) {
            throw new FeederException(Err.IOFile);
        } finally {
            closeIstream();
            closeOstream();
            clearTempFile(); // ignore failure
        }

    }


    public boolean
    cancel() {
        cancelled = true;
        // Kind of hack!
        // There is no fast-way to cancel running-java thread.
        // So, make input-stream closed by force to stop loading/DOM-parsing etc.
        cleanup();
        return true;
    }

    void finish() {
        // This is not needed... cleanup should be done before finish()...
        //cleanup();
    }

}
