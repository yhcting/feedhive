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

    private Feed
    loadUrl(String url)
            throws FeederException {
        logI("Fetching Channel [" + url + "]\n");
        Feed feed = null;
        try {
            Document dom = DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(new URL(url).openStream());
            // Only RSS is supported at this version.
            feed = new RSSParser().parse(dom);
        } catch (IOException e) {
            throw new FeederException(Err.IONet);
        } catch (SAXException e) {
            e.printStackTrace();
            throw new FeederException(Err.ParserUnsupportedFormat);
        } catch (ParserConfigurationException e) {
            throw new FeederException(Err.ParserUnsupportedFormat);
        } catch (FeederException e) {
            throw e;
        }
        return feed;
    }

    /*
     * Load full information of this channel (This is first load)
     */
    public Err
    initialLoad(long categoryid, String url, long[] outcid)
            throws InterruptedException {
        Feed feed;

        // We don't allow inserting duplicated channel.
        // (channel that has same url is regarded as same!)
        // Check it at early stage.
        if (dbp.isDuplicatedChannelUrl(url))
            return Err.DBDuplicatedChannel;

        try {
            feed = loadUrl(url);
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }

        // Update mandatory initial values
        feed.channel.url = url;
        feed.channel.categoryid = categoryid;
        // NOTE
        //   feed.lastupdate  will be filled inside DBPolicy automatically.

        UIPolicy.setAsDefaultActionType(feed.channel);

        try {
            feed.channel.imageblob = Utils.getDecodedImageData(feed.channel.imageref);
        } catch (FeederException e) {
            ; // ignore image data
        }

        // This is perfectly full reload.
        for (Feed.Item item : feed.channel.items)
            item.state = Feed.Item.State.NEW;

        // It's time to update Database!!!
        if (0 > dbp.insertChannel(feed.channel))
            return Err.DBUnknown;

        if (null != outcid)
            outcid[0] = feed.channel.id;

        return Err.NoErr;
    }

    /*
     * Update feed information only.
     */
    public Err
    loadFeeds(long cid)
            throws InterruptedException {
        String url = dbp.getChannelInfoString(cid, DB.ColumnChannel.URL);
        eAssert(null != url);

        Feed feed;
        try {
            feed = loadUrl(url);
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }

        // set to given value forcely due to this is 'update'.
        feed.channel.id = cid;

        String state;
        for (Feed.Item item : feed.channel.items) {
            // ignore empty-titled item
            if (null == item.title || item.title.isEmpty())
                continue;
            state = dbp.isDuplicatedItemTitleWithState(cid, item.title);
            if (null != state)
                item.state = Feed.Item.State.convert(state);
            else
                item.state = Feed.Item.State.NEW;
        }

        dbp.updateItems(feed.channel);

        return Err.NoErr;
    }
}
