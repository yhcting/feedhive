package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.util.ByteArrayBuffer;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.graphics.Bitmap;

public class NetLoader {
    DBPolicy dbp              = DBPolicy.get();

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

    public ByteArrayBuffer
    download(String dnurl)
            throws FeederException {
        if (null == dnurl)
            return null;

        ByteArrayBuffer bab = null;
        try {
            URL url = new URL(dnurl);
            long startTime = System.currentTimeMillis();

            logI("Start Downloading : " + dnurl);

            // Open a connection to that URL.
            URLConnection ucon = url.openConnection();
            // Define InputStreams to read from the URLConnection.
            InputStream is = ucon.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);

            // Read bytes to the Buffer until there is nothing more to read(-1).
            bab = new ByteArrayBuffer(1024);
            int current = 0;
            while ((current = bis.read()) != -1)
                    bab.append((byte) current);

            logI("End Downloading : "
                            + ((System.currentTimeMillis() - startTime) / 1000)
                            + " sec");

        } catch (IOException e) {
            throw new FeederException(Err.IONet);
        }

        return bab;
    }

    /*
     * Load full information of this channel (This is first load)
     */
    public Err
    initialLoad(String url)
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
        feed.channel.lastupdate = DateUtils.getCurrentDateString();
        UIPolicy.setAsDefaultActionType(feed.channel);

        ByteArrayBuffer imgBab = null;
        try {
            imgBab = download(feed.channel.imageref);
        } catch (FeederException e) {
            if (Err.IONet != e.getError()) {
                e.printStackTrace();
                return e.getError();
            }
        }

        //
        // [ In case of RSS. ]
        // Lots of sites doesn't obey RSS spec. related with channel image.
        // Spec. says max value for width = 144 / for height = 400.
        // But, there are lots of out-of-spec-sites
        // So, we need to consider this case (image size is out-of-spec.)
        // Solution used below is
        //   * shrink downloaded image and save it to DB.
        //     (To save memory and increase performance.)
        if (null != imgBab) {
            Bitmap bm = Utils.decodeImage(imgBab.toByteArray(),
                                          Feed.CHANNEL_IMAGE_MAX_WIDTH,
                                          Feed.CHANNEL_IMAGE_MAX_HEIGHT);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            feed.channel.imageblob = baos.toByteArray();
        }

        // This is perfectly full reload.
        for (Feed.Item item : feed.channel.items)
            item.state = Feed.Item.State.NEW;

        // It's time to update Database!!!
        if (0 > dbp.insertFeedChannel(feed.channel))
            return Err.DBUnknown;

        return Err.NoErr;
    }

    /*
     * Update feed information only.
     */
    public Err
    loadFeeds(long cid)
            throws InterruptedException {
        String url = dbp.getFeedChannelInfoString(cid, DB.ColumnFeedChannel.URL);
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

        dbp.updateFeedItems(feed.channel);

        return Err.NoErr;
    }
}
