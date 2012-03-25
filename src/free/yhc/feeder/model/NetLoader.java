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
import java.util.Iterator;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class NetLoader {
    public static final int UPD_DEFAULT     = 0x00;
    public static final int UPD_LOAD_IMG    = 0x01;
    public static final int UPD_ACTION      = 0x02;
    public static final int UPD_INIT        = UPD_ACTION | UPD_LOAD_IMG;


    private DBPolicy             dbp     = DBPolicy.S();
    private volatile boolean     cancelled = false;
    private volatile InputStream istream = null; // Multi-thread access

    interface OnProgress {
        void onProgress(int progress);
    }

    public NetLoader() {
    }

    private RSSParser.Result
    parseFeedUrl(String url)
            throws FeederException {
        logI("Fetching Channel [" + url + "]\n");
        RSSParser.Result res = null;
        long             time;
        int              retry = 5;
        eAssert(null == istream);
        while (0 < retry--) {
            try {
                time = System.currentTimeMillis();
                istream = new URL(url).openStream();
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
    private Err
    update(long cid, int flag, String imageref)
            throws FeederException {
        String url = dbp.getChannelInfoString(cid, DB.ColumnChannel.URL);
        eAssert(null != url);

        logI("Loading Items: " + url);

        long time = System.currentTimeMillis();
        RSSParser.Result res;
        try {
            res = parseFeedUrl(url);
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }
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
            String prefix;
            time = System.currentTimeMillis();
            try {
                if (null == imageref)
                    ch.dynD.imageblob = Utils.getDecodedImageData(ch.parD.imageref);
                else
                    ch.dynD.imageblob = Utils.getDecodedImageData(imageref);
            } catch (FeederException e) {
                ; // ignore image data
            }
            prefix = (null == ch.dynD.imageblob)? "< Fail >" : "< Ok >";
            logI("TIME: Handle Image : " + prefix + (System.currentTimeMillis() - time));
        }

        time = System.currentTimeMillis();

        LinkedList<Feed.Item> newItems = new LinkedList<Feed.Item>();
        dbp.getNewItems(ch.dbD.id, ch.items, newItems);
        if (Feed.Channel.UpdateType.DN_LINK == ch.dynD.updatetype) {
            Iterator<Feed.Item> itr = newItems.iterator();
            while (itr.hasNext()) {
                Feed.Item item = itr.next();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                Err result = download(os, item.parD.link, null);
                if (Err.NoErr == result)
                    item.dynD.rawdata = os.toByteArray();

                if (Err.UserCancelled == result)
                    return result;

                if (Thread.currentThread().isInterrupted())
                    return Err.Interrupted;

            }
        }
        dbp.updateChannel(ch, newItems);
        logI("TIME: Updating Items : " + (System.currentTimeMillis() - time));

        return Err.NoErr;
    }

    public Err
    updateLoad(long cid, int flag)
            throws FeederException {
        return update(cid, flag, null);
    }

    public Err
    updateLoad(long cid, int flag, String imageref)
            throws FeederException {
        eAssert(null != imageref);
        return update(cid, flag, imageref);
    }

    public Err
    download(OutputStream ostream, String urlStr, OnProgress progressListener) {
           URL           url = null;
           URLConnection conn = null;
           int           retry = 5;
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
               istream = new BufferedInputStream(url.openStream());

               if (Thread.currentThread().isInterrupted()) {
                   cancel();
                   return Err.Interrupted;
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

               return Err.NoErr;

           } catch (IOException e) {
               // User's canceling operation close in/out stream in force.
               // And this leads to IOException here.
               // So, we should check that this Exception is caused by user's cancel or real IOException.
               if (cancelled)
                   return Err.UserCancelled;
               else {
                   e.printStackTrace();
                   logW(e.getMessage());
                   cancel();
                   return Err.IONet;
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
