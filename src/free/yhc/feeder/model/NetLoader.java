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
    private String url = null;

    public NetLoader(String url) {
        this.url = url;
    }

    /*
     * Load full information of this channel
     */
    public FeederException.Err
    loadFull() throws FeederException {
        logI("Fetching Channel [" + url + "]\n");
        RSS rss = null;
        try {
            Document dom = DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(new URL(url).openStream());

            rss = new RSSParser().parse(dom);
        } catch (IOException e) {
            e.printStackTrace();
            return FeederException.Err.IOOpenUrl;
        } catch (SAXException e) {
            e.printStackTrace();
            return FeederException.Err.ParserUnsupportedFormat;
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            return FeederException.Err.ParserUnsupportedFormat;
        } catch (FeederException e) {
            e.printStackTrace();
            return e.getError();
        }

        // channel information should be updated here

        // 'url' is internal value of channel.
        eAssert(null == rss.channel.url && null != ch.url);

        // update default internal values.
        rss.channel.url = ch.url;
        rss.channel.id = ch.id;

        // It is parsed successfully!!!
        // It's time to update Database!!!
        DBPolicy dbp = new DBPolicy();

        synchronized (dbLock) {
            // TODO
            //   check/update channel information.
            dbp.updateRSSChannel(rss.channel);

            // update item's channel id field.
            for (RSS.Item i : rss.channel.items) {
                i.channelid = rss.channel.id;
            }

            dbp.updateChannelItems(rss.channel, rss.channel.items);
        }

        return FeederException.Err.NoErr;
    }

    /*
     * Update feed information only.
     */
    public FeederException.Err
    loadFeeds() throws FeederException {

    }
}
