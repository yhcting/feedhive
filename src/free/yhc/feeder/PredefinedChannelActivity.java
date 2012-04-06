package free.yhc.feeder;
import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;


public class PredefinedChannelActivity extends Activity {
    private long        categoryid = -1;
    private ListView    list;
    private HashMap<String, Boolean> chMap = new HashMap<String, Boolean>();

    // predefined channel
    private class PDChannel {
        boolean bItem = true; // is this channel item that is described at predefined channel list?
        String name = "";
        String category = "";
        String url = "";
        String iconref = "";
    }

    private class PDChannelAdapter extends ArrayAdapter<PDChannel> {
        private LayoutInflater inflater = null;
        public
        PDChannelAdapter(Context context, int textViewResourceId, PDChannel[] objects) {
            super(context, textViewResourceId, objects);
            inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View
        getView(int position, View convertView, ViewGroup parent) {
            View row;

            if (null == convertView)
                row = inflater.inflate(R.layout.predefined_channel_row, null);
            else
                row = convertView;

            // Check to this is first channel item in this category.
            PDChannel pdc = (PDChannel)list.getAdapter().getItem(position);
            if (pdc.bItem) {
                // category layout should be removed.
                LinearLayout catlo = (LinearLayout)row.findViewById(R.id.category_layout);
                catlo.setVisibility(View.GONE);

                LinearLayout itemlo = (LinearLayout)row.findViewById(R.id.item_layout);
                itemlo.setVisibility(View.VISIBLE);
                int colorRId = (null == chMap.get(pdc.url))? R.color.title_color_new: R.color.title_color_opened;
                TextView tv = (TextView)row.findViewById(R.id.name);
                tv.setTextColor(PredefinedChannelActivity.this.getResources().getColor(colorRId));
                tv.setText(getItem(position).name);

                // TODO
                // It's extremely weird !!!
                // "row.setFocusable(true)" / "row.setFocusable(false)" works exactly opposite
                //   way against the way it should work!!!
                //
                // Current Status
                //   "row.setFocusable(false)" => touch works for item.
                //   "row.setFocusable(true)" => touch doesn't work for item.
                //
                // What happened to this!!!
                // Need to check this!!!
                // (I think this is definitely BUG of ANDROID FRAMEWORK!)
                // => This case is same with below "else" case too.
                row.setFocusable(false);
            } else {
                LinearLayout catlo = (LinearLayout)row.findViewById(R.id.category_layout);
                catlo.setVisibility(View.VISIBLE);
                TextView catv = (TextView)row.findViewById(R.id.category);
                catv.setText(pdc.category);

                // item layout should be removed.
                LinearLayout itemlo = (LinearLayout)row.findViewById(R.id.item_layout);
                itemlo.setVisibility(View.GONE);

                row.setFocusable(true);
            }

            return row;
        }

    }

    private void
    addChannel(String url, String imageref) {
        eAssert(url != null);

        if (url.isEmpty()) {
            LookAndFeel.showTextToast(this, R.string.warn_add_channel);
            return;
        }

        if (null != chMap.get(url)) {
            LookAndFeel.showTextToast(this, R.string.err_duplicated_channel);
            return;
        }

        long cid = DBPolicy.S().insertNewChannel(categoryid, url);
        if (cid < 0) {
            LookAndFeel.showTextToast(this, R.string.warn_add_channel);
            return;
        }
        // full update for this newly inserted channel
        BGTaskUpdateChannel task = new BGTaskUpdateChannel(this);
        RTTask.S().registerUpdate(cid, task);
        if (imageref.isEmpty())
            task.start(new BGTaskUpdateChannel.Arg(cid));
        else
            task.start(new BGTaskUpdateChannel.Arg(cid, imageref));
        ScheduledUpdater.scheduleNextUpdate(this, Calendar.getInstance());
        chMap.put(url, true);
        ((ArrayAdapter)list.getAdapter()).notifyDataSetChanged();
    }

    private Node
    findNodeByNameFromSiblings(Node n, String name) {
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase(name))
                return n;
            n = n.getNextSibling();
        }
        return null;
    }

    private String
    getTextValue(Node n) {
        Node t = findNodeByNameFromSiblings(n.getFirstChild(), "#text");
        return (null != t)? t.getNodeValue(): "";
    }

    private Err
    parsePredefinedChannelFile(LinkedList<PDChannel> chl,
                               int[] nrCat, // [out] number of categories
                               String channelFile) {
        File f = new File(channelFile);
        if (!f.exists())
            return Err.IOFile;

        Document dom = null;
        try {
            dom = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(f);
        } catch (IOException e) {
            return Err.IOFile;
        } catch (DOMException e) {
            return Err.ParserUnsupportedFormat;
        } catch (SAXException e) {
            return Err.ParserUnsupportedFormat;
        } catch (ParserConfigurationException e) {
            return Err.ParserUnsupportedFormat;
        }

        HashMap<String, Boolean> catmap = new HashMap<String, Boolean>();
        Element root = dom.getDocumentElement();
        Node n = root.getFirstChild();
        while (null != n) {
            logI("Node : " + n.getNodeName());
            if(n.getNodeName().startsWith("#")) {
                n = n.getNextSibling();
                continue;
            }

            if(!n.getNodeName().equals("channel"))
                return Err.ParserUnsupportedFormat;

            PDChannel ch = new PDChannel();
            Node cn = n.getFirstChild();
            while (null != cn) {
                logI("Child Node : " + cn.getNodeName());
                if(cn.getNodeName().startsWith("#")) {
                    cn = cn.getNextSibling();
                    continue;
                }

                if (cn.getNodeName().equals("name"))
                    ch.name = getTextValue(cn);
                else if (cn.getNodeName().equals("category"))
                    ch.category = getTextValue(cn);
                else if (cn.getNodeName().equals("url"))
                    ch.url = getTextValue(cn);
                else if (cn.getNodeName().equals("icon"))
                    ch.iconref = getTextValue(cn);
                else
                    return Err.ParserUnsupportedFormat;

                cn = cn.getNextSibling();
            }
            if (ch.category.isEmpty())
                ch.category = "-"; // not specified category name

            catmap.put(ch.category, true);
            chl.addLast(ch);
            n = n.getNextSibling();
        }
        if (null != nrCat)
            nrCat[0] = catmap.size();

        return Err.NoErr;
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        categoryid = this.getIntent().getLongExtra("category", -1);
        eAssert(categoryid >= 0);

        setContentView(R.layout.predefined_channel);
        list = ((ListView) findViewById(R.id.list));
        eAssert(null != list);

        // Get already-registered channels
        Cursor c = DBPolicy.S().queryChannel(DB.ColumnChannel.URL);
        if (c.moveToFirst()) {
            do {
                chMap.put(c.getString(0), true);
            } while (c.moveToNext());
        }

        LinkedList<PDChannel> chl = new LinkedList<PDChannel>();
        int[] nrCat = new int[1];
        Err result = parsePredefinedChannelFile(chl, nrCat, UIPolicy.getPredefinedChannelsFilePath());

        if (Err.NoErr != result) {
            LookAndFeel.showTextToast(this, result.getMsgId());
            finish();
        }

        PDChannel[] chs = chl.toArray(new PDChannel[0]);
        Arrays.sort(chs, new Comparator<PDChannel>() {
            @Override
            public int
            compare(PDChannel ch0, PDChannel ch1) {
                return ch0.category.compareTo(ch1.category);
            }
        });

        if (0 == chs.length) {
            LookAndFeel.showTextToast(this, R.string.warn_no_predefined_channel);
            finish();
        }

        PDChannel[] adpChs = new PDChannel[chs.length + nrCat[0]];

        String catName = ""; // initial value of cat name
        int adpChsi = 0;
        int chsi = 0;
        while (adpChsi < adpChs.length && chsi < chs.length) {
            eAssert(!chs[chsi].category.isEmpty());
            if (!catName.equals(chs[chsi].category)) {
                catName = chs[chsi].category;
                PDChannel pdc = new PDChannel();
                pdc.bItem = false;
                pdc.category = catName;
                adpChs[adpChsi++] = pdc;
            }
            adpChs[adpChsi++] = chs[chsi++];
        }
        eAssert(adpChsi == adpChs.length && chsi == chs.length);

        list.setAdapter(new PDChannelAdapter(this, 0, adpChs));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PDChannel ch = (PDChannel)list.getAdapter().getItem(position);
                addChannel(ch.url, ch.iconref);
            }
        });
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
    }
}
