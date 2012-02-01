package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.FeederException;
import free.yhc.feeder.model.RSS;
import free.yhc.feeder.model.RSSParser;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

//
// Load item from network to DB.
//
class ItemLoader extends AsyncTask<Long, Integer, FeederException.Err> {
    interface OnPostExecute {
        void onPostExecute(FeederException.Err result, long cid, boolean bChannelInfoUpdated);
    }

    private ProgressDialog progress = null;
    private Context        context = null;
    OnPostExecute          postexe = null;
    long                   cid     = -1;
    boolean                bChannelInfoUpdated = false;
    Object                 dbLock = new Object();

    ItemLoader(Context ctxt, OnPostExecute exe) {
        context = ctxt;
        postexe = exe;
    }

    FeederException.Err
    loadChannel(RSS.Channel ch) {
        logI("Fetching Channel [" + ch.url + "]\n");
        RSS rss = null;
        try {
            Document dom = DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(new URL(ch.url).openStream());
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

    // chids  : channel id to load it's item from network.
    //          '0' id means 'All'.
    // return :
    @Override
    protected FeederException.Err
    doInBackground(Long... cids) {
        eAssert(cids[0] >= 0);
        cid = cids[0];
        if (0 == cids[0]) {
            // special case... All!!
            RSS.Channel[] chs = DB.db().getRssChannels();
            FeederException.Err err = FeederException.Err.NoErr;
            for (RSS.Channel ch : chs) {
                err = loadChannel(ch);
                if (FeederException.Err.NoErr != err)
                    break;
            }
            return err;
        } else {
            RSS.Channel ch = DB.db().getRssChannelFromId(cid);
            eAssert(null != ch);
            return loadChannel(ch);
        }
    }

    @Override
    protected void
    onCancelled() {
        synchronized (dbLock) {
            super.onCancelled();
        }
    }

    @Override
    protected void
    onPostExecute(FeederException.Err result) {
        super.onPostExecute(result);

        progress.dismiss();
        logI("FetchTask : onPostExecute (" + result + ")\n");

        if (null != postexe)
            // FIXME
            //   Temporally, bChannelInfoUpdated = true (always)
            postexe.onPostExecute(result, cid, true);

    }

    @Override
    protected void
    onPreExecute() {
        super.onPreExecute();

        progress = new ProgressDialog(context);

        progress.setMessage(context.getResources().getText(R.string.fetch_progess));
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.show();
    }

    @Override
    protected void
    onProgressUpdate(Integer... v) {
        super.onProgressUpdate(v);
    }
}
