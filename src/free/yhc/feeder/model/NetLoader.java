package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private static final int NET_RETRY = 3;

    private DBPolicy             dbp     = DBPolicy.S();
    private volatile boolean     cancelled = false;
    private volatile InputStream istream = null; // Multi-thread access

    interface OnProgress {
        void onProgress(int progress);
    }

    private void
    checkInterrupted() throws FeederException {
        if (cancelled)
            throw new FeederException(Err.UserCancelled);
        if (Thread.currentThread().isInterrupted())
            throw new FeederException(Err.Interrupted);
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
        eAssert(null == istream);
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
                istream = null;
                logI("TIME: Open URL and Parseing as Dom : " + (System.currentTimeMillis() - time));
                time = System.currentTimeMillis();
                // Only RSS is supported at this version.
                res = new RSSParser().parse(dom);
                logI("TIME: RSSParsing : " + (System.currentTimeMillis() - time));
                break; // done
            } catch (IOException e) {
                if (cancelled)
                    throw new FeederException(Err.UserCancelled);

                if (retry <= 0)
                    throw new FeederException(Err.IONet);
                ; // continue next retry
            } catch (DOMException e) {
                throw new FeederException(Err.ParserUnsupportedFormat);
            } catch (SAXException e) {
                e.printStackTrace();
                throw new FeederException(Err.ParserUnsupportedFormat);
            } catch (ParserConfigurationException e) {
                throw new FeederException(Err.ParserUnsupportedFormat);
            } catch (FeederException e) {
                throw e;
            }
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

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                download(os, imgurl, null);
            } catch (FeederException e) {
                // If getting icon from network fails, just ignore it!
                if (Err.IONet != e.getError())
                    throw e;
            }
            checkInterrupted();
            if (os.size() > 0) {
                Bitmap bm = Utils.decodeImage(os.toByteArray(),
                                              Feed.Channel.ICON_MAX_WIDTH,
                                              Feed.Channel.ICON_MAX_HEIGHT);
                ch.dynD.imageblob = Utils.compressBitmap(bm);
                bm.recycle();
                String prefix = (null == ch.dynD.imageblob)? "< Fail >" : "< Ok >";
                logI("TIME: Handle Image : " + prefix + (System.currentTimeMillis() - time));
                checkInterrupted();
            }
        }

        time = System.currentTimeMillis();

        LinkedList<Feed.Item> newItems = new LinkedList<Feed.Item>();
        dbp.getNewItems(ch.items, newItems);
        DBPolicy.ItemDataOp idop = null;
        if (Feed.Channel.isUpdDn(ch.dynD.updatetype)) {
            idop = new DBPolicy.ItemDataOp() {
                @Override
                public byte[] getData(Feed.Item item) throws FeederException {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try {
                        download(os, item.parD.link, null);
                        return os.toByteArray();
                    } catch (FeederException e) {
                        if (Err.IONet == e.getError())
                            return null;
                        else
                            throw e;
                    }
                }
            };
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
    public void
    download(OutputStream ostream, String urlStr, OnProgress progressListener)
            throws FeederException {
           URL           url = null;
           URLConnection conn = null;
           int           retry = NET_RETRY;
           while (0 < retry--) {
               try {
                   url = new URL(urlStr);
                   conn = url.openConnection();
                   conn.setConnectTimeout(1000);
                   conn.connect();
                   break; // done
               } catch (Exception e) {
                   // SocketTimeoutException
                   // IOException
                   if (cancelled)
                       throw new FeederException(Err.UserCancelled);

                   if (0 >= retry) {
                       e.printStackTrace();
                       logW(e.getMessage());
                       throw new FeederException(Err.IONet);
                   }
               }
           }

           try {
               int lenghtOfFile = conn.getContentLength();
               istream = new BufferedInputStream(conn.getInputStream());

               if (Thread.currentThread().isInterrupted()) {
                   cancel();
                   throw new FeederException(Err.Interrupted);
               }

               byte data[] = new byte[256*1024];

               long total = 0;
               int  count;
               int  prevProgress = -1;
               while (true) {
                   if (-1 == (count = istream.read(data)))
                       break;
                   ostream.write(data, 0, count);
                   total += count;
                   int progress = (int)(total * 100 / lenghtOfFile);
                   if (progress > prevProgress) {
                       if (null != progressListener)
                           progressListener.onProgress(progress);
                       prevProgress = progress;
                   }
               }

               ostream.flush();
               ostream.close();
               istream.close();
               istream = null;

           } catch (IOException e) {
               // User's canceling operation close in/out stream in force.
               // And this leads to IOException here.
               // So, we should check that this Exception is caused by user's cancel or real IOException.
               if (cancelled)
                   throw new FeederException(Err.UserCancelled);
               else {
                   e.printStackTrace();
                   logW(e.getMessage());
                   cancel();
                   throw new FeederException(Err.IONet);
               }
           }
    }

    public boolean
    cancel() {
        cancelled = true;
        // Kind of hack!
        // There is no fast-way to cancel running-java thread.
        // So, make input-stream closed forcely to stop loading/DOM-parsing.
        try {
            if (null != istream)
                istream.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    // ===========================
    // Static
    // ===========================

}
