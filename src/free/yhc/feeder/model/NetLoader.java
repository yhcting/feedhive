package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class NetLoader {
    DBPolicy dbp    = DBPolicy.get();

    public NetLoader() {
    }

    private RSSParser.Result
    loadUrl(String url)
            throws FeederException {
        logI("Fetching Channel [" + url + "]\n");
        RSSParser.Result res = null;
        int              retry = 5;
        while (0 < retry--) {
            try {
                Utils.beginTimeLog();
                Document dom = DocumentBuilderFactory
                                .newInstance()
                                .newDocumentBuilder()
                                .parse(new URL(url).openStream());
                Utils.endTimeLog("Open URL and Parseing as Dom");
                Utils.beginTimeLog();
                // Only RSS is supported at this version.
                res = new RSSParser().parse(dom);
                Utils.endTimeLog("RSSParsing");
                break; // done
            } catch (IOException e) {
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
            throws InterruptedException {

        logI("Init Loading Channel : " + url);

        // We don't allow inserting duplicated channel.
        // (channel that has same url is regarded as same!)
        // Check it at early stage.
        if (dbp.isDuplicatedChannelUrl(url))
            return Err.DBDuplicatedChannel;

        Utils.beginTimeLog();
        RSSParser.Result res;
        try {
            res = loadUrl(url);
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }
        Utils.endTimeLog("Loading + Parsing");

        Feed.Channel ch = new Feed.Channel(res.channel, res.items);

        // Fill other informations from parsed-data.

        // Update mandatory initial values
        ch.profD.url = url;
        ch.dbD.categoryid = categoryid;
        // NOTE
        //   feed.lastupdate  will be filled inside DBPolicy automatically.

        UIPolicy.setAsDefaultActionType(ch);

        Utils.beginTimeLog();
        try {
            ch.dynD.imageblob = Utils.getDecodedImageData(ch.parD.imageref);
        } catch (FeederException e) {
            ; // ignore image data
        }
        Utils.endTimeLog((null == ch.dynD.imageblob)?"< Fail >":"< Ok >" + "Handling Image");

        // This is perfectly full reload.
        for (Feed.Item item : ch.items)
            item.dynD.state = Feed.Item.State.NEW;

        Utils.beginTimeLog();
        // It's time to update Database!!!
        if (0 > dbp.insertChannel(ch))
            return Err.DBUnknown;

        Utils.endTimeLog("Inserting Channel");

        if (null != outcid)
            outcid[0] = ch.dbD.id;

        return Err.NoErr;
    }

    /*
     * Update feed information only.
     */
    public Err
    updateLoad(long cid, boolean reloadImage)
            throws InterruptedException {
        String url = dbp.getChannelInfoString(cid, DB.ColumnChannel.URL);
        eAssert(null != url);

        logI("Loading Items: " + url);

        Utils.beginTimeLog();
        RSSParser.Result res;
        try {
            res = loadUrl(url);
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }
        Utils.endTimeLog("Loading + Parsing");

        // set to given value forcely due to this is 'update' - Not new insertion.
        Feed.Channel ch = new Feed.Channel(res.channel, res.items);
        ch.dbD.id = cid;

        if (reloadImage) {
            Utils.beginTimeLog();
            try {
                ch.dynD.imageblob = Utils.getDecodedImageData(ch.parD.imageref);
            } catch (FeederException e) {
                ; // ignore image data
            }
            Utils.endTimeLog((null == ch.dynD.imageblob)?"< Fail >":"< Ok >" + "Handling Image");
        }

        Utils.beginTimeLog();
        String state;
        for (Feed.Item item : ch.items) {
            // ignore empty-titled item
            if (null == item.parD.title || item.parD.title.isEmpty())
                continue;
            state = dbp.isDuplicatedItemTitleWithState(cid, item.parD.title);
            if (null != state)
                item.dynD.state = Feed.Item.State.convert(state);
            else
                item.dynD.state = Feed.Item.State.NEW;
        }
        Utils.endTimeLog("Comparing with DB data");

        Utils.beginTimeLog();
        dbp.updateChannel(ch, null != ch.dynD.imageblob);
        Utils.endTimeLog("Updating Items");

        return Err.NoErr;
    }
}
