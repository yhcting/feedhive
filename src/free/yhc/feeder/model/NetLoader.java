package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class NetLoader {
    private DBPolicy             dbp     = DBPolicy.S();
    private volatile boolean     cancelled = false;
    private volatile InputStream istream = null; // Multi-thread access

    public NetLoader() {
    }

    private RSSParser.Result
    loadUrl(String url)
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
     * Load full information of this channel (This is first load)
     */
    public Err
    initialLoad(long categoryid, String url, long[] outcid)
            throws FeederException {

        logI("Init Loading Channel : " + url);

        // We don't allow inserting duplicated channel.
        // (channel that has same url is regarded as same!)
        // Check it at early stage.
        if (dbp.isDuplicatedChannelUrl(url))
            return Err.DBDuplicatedChannel;

        long    time;
        String  timePrefix;
        time = System.currentTimeMillis();

        RSSParser.Result res;
        try {
            res = loadUrl(url);
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }
        logI("TIME: Loading + Parsing : " + (System.currentTimeMillis() - time));

        Feed.Channel ch = new Feed.Channel(res.channel, res.items);

        // Fill other informations from parsed-data.

        // Update mandatory initial values
        ch.profD.url = url;
        ch.dbD.categoryid = categoryid;
        // NOTE
        //   feed.lastupdate  will be filled inside DBPolicy automatically.

        UIPolicy.setAsDefaultActionType(ch);

        time = System.currentTimeMillis();
        try {
            ch.dynD.imageblob = Utils.getDecodedImageData(ch.parD.imageref);
        } catch (FeederException e) {
            ; // ignore image data
        }

        timePrefix = (null == ch.dynD.imageblob)? "< Fail >": "< Ok >";
        logI("TIME: Handling Image " + timePrefix + " : " + (System.currentTimeMillis() - time));

        // This is perfectly full reload.
        for (Feed.Item item : ch.items)
            item.dynD.state = Feed.Item.State.NEW;

        time = System.currentTimeMillis();
        // It's time to update Database!!!
        if (0 > dbp.insertChannel(ch))
            return Err.DBUnknown;

        logI("TIME: Inserting Channel : " + (System.currentTimeMillis() - time));

        if (null != outcid)
            outcid[0] = ch.dbD.id;

        return Err.NoErr;
    }

    /*
     * Update feed information only.
     */
    public Err
    updateLoad(long cid, boolean reloadImage)
            throws FeederException {
        String url = dbp.getChannelInfoString(cid, DB.ColumnChannel.URL);
        eAssert(null != url);

        logI("Loading Items: " + url);

        long time = System.currentTimeMillis();
        RSSParser.Result res;
        try {
            res = loadUrl(url);
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }
        logI("TIME: Loading + Parsing : " + (System.currentTimeMillis() - time));

        // set to given value forcely due to this is 'update' - Not new insertion.
        Feed.Channel ch = new Feed.Channel(res.channel, res.items);
        ch.dbD.id = cid;

        if (reloadImage) {
            String prefix;
            time = System.currentTimeMillis();
            try {
                ch.dynD.imageblob = Utils.getDecodedImageData(ch.parD.imageref);
            } catch (FeederException e) {
                ; // ignore image data
            }
            prefix = (null == ch.dynD.imageblob)? "< Fail >" : "< Ok >";
            logI("TIME: Handle Image : " + prefix + (System.currentTimeMillis() - time));
        }

        time = System.currentTimeMillis();
        dbp.updateChannel(ch, null != ch.dynD.imageblob);
        logI("TIME: Updating Items : " + (System.currentTimeMillis() - time));

        return Err.NoErr;
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
}
