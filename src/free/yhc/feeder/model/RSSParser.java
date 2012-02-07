package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.util.LinkedList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public final class RSSParser {

    // parsing priority of namespace supported.
    private static final int PRI_ITUNES     = 2;
    private static final int PRI_DC         = 1;
    private static final int PRI_DEFAULT    = 0;

    private class NodeValue {
        int    priority; // priority value of parsing modules which updates this value.
        String value;

        NodeValue() {
            init();
        }

        void
        init() {
            priority = -1;
            value = null;
        }
    }

    private class ChannelValues {
        NodeValue   title       = new NodeValue();
        NodeValue   description = new NodeValue();
        NodeValue   imageref    = new NodeValue();

        void
        init() {
            title.init();
            description.init();
            imageref.init();
        }

        void
        set(RSS.Channel ch) {
            ch.title = title.value;
            ch.description = description.value;
            ch.imageref = imageref.value;
        }
    }

    private class ItemValues {
        NodeValue   title           = new NodeValue();
        NodeValue   description     = new NodeValue();
        NodeValue   link            = new NodeValue();
        NodeValue   enclosure_length= new NodeValue();
        NodeValue   enclosure_url   = new NodeValue();
        NodeValue   enclosure_type  = new NodeValue();
        NodeValue   pubDate         = new NodeValue();

        void
        init() {
            title.init();
            description.init();
            link.init();
            enclosure_length.init();
            enclosure_url.init();
            enclosure_type.init();
            pubDate.init();
        }

        void
        set(RSS.Item item) {
            item.title = title.value;
            item.description = description.value;
            item.link = link.value;
            if (null != enclosure_length.value
                || null != enclosure_url.value
                || null != enclosure_type.value) {
                item.enclosure = new RSS.Enclosure();
                item.enclosure.length = enclosure_length.value;
                item.enclosure.url = enclosure_url.value;
                item.enclosure.type = enclosure_type.value;
            }
            item.pubDate = pubDate.value;
        }
    }

    private class RSSAttr {
        String              ver = "2.0"; // by default
        LinkedList<String>  nsl = new LinkedList<String>(); // name space list.
    }

    private void
    printNexts(Node n) {
        String msg = "";
        while (null != n) {
            msg = msg + " / " + n.getNodeName();
            n = n.getNextSibling();
        }
        logI(msg + "\n");
    }

    private void
    verifyFormat(boolean cond)
            throws FeederException {
        if (!cond)
            throw new FeederException(Err.ParserUnsupportedFormat);
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

        /*
         * [ Issue of CDATA section. ]
         * CDATA section should be ignored at XML parser.
         * But, lots of rss feeds supported by Korean presses, uses CDATA section as a kind of text section.
         *
         * For example
         *     <title><![CDATA[[사설]지상파·케이블의 밥그릇 싸움과 방통위의 무능]]></title>
         *
         * In this case, there is no title string!!!
         * To support this case, we uses 'cdata' section if there is no valid 'text' section.
         * (This is kind of parsing policy!!)
         *
         * TODO
         *   This new Parsing Policy is best way to support this?
         *   Is there any elegant code structure to support various parsing policy?
         */
        if (null == t)
            t = findNodeByNameFromSiblings(n.getFirstChild(), "#cdata-section");

        /*
         * NOTE
         *   Why "" is returned instead of null?
         *   Calling this function means interesting node is found.
         *   But, in case node has empty text, DOM parser doesn't havs '#text' node.
         *
         *   For example
         *      <title></title>
         *
         *   In this case node 'title' doesn't have '#text'node as it's child.
         *   So, t becomes null.
         *   But, having empty string as an text value of node 'title', is
         *     more reasonable than null as it's value.
         */
        return (null == t)? "": t.getNodeValue();
    }

    // ===========================================================
    //
    //                    Name space parsor
    //
    // ===========================================================
    private class NSParser {
        private int     priority;

        private NSParser(){} // block default constructor.

        NSParser(int priority) {
            eAssert(priority >= PRI_DEFAULT);
            this.priority = priority;
        }

        /*
         * return: true(value is set) false(value is not set)
         */
        protected boolean
        setValue(NodeValue nv, String value) {
            if (nv.priority <= priority) {
                nv.value = value;
                return true;
            }
            return false;
        }

        /*
         * return: true(handled) false(passed)
         */
        boolean
        parseChannel(ChannelValues cv, Node n) {
            return false;
        }

        boolean
        parseItem(ItemValues iv, Node n) {
            return false;
        }
    }

    // ========================================
    //        To Support 'itunes' Namespace
    // ========================================
    private class NSItunesParser extends NSParser {
        NSItunesParser() {
            super(PRI_ITUNES);
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n) {
            boolean ret = true;

            if (n.getNodeName().equalsIgnoreCase("itunes:summary"))
                setValue(cv.description, getTextValue(n));
            else if (n.getNodeName().equalsIgnoreCase("itunes:image")) {
                NamedNodeMap nnm = n.getAttributes();
                Node img = nnm.getNamedItem("href");
                if (null != img)
                    setValue(cv.imageref, img.getNodeValue());
            } else
                ret = false;

            return ret;
        }

        @Override
        boolean
        parseItem(ItemValues iv, Node n) {
            boolean ret = true;

            if (n.getNodeName().equalsIgnoreCase("itunes:summary"))
                setValue(iv.description, getTextValue(n));
            else if (n.getNodeName().equalsIgnoreCase("itunes:duration"))
                setValue(iv.enclosure_length, getTextValue(n));
            else
                ret = false;

            return ret;
        }

    }

    // ========================================
    //        To Support 'dc' Namespace
    // ========================================
    private class NSDcParser extends NSParser {
        NSDcParser() {
            super(PRI_DC);
        }

        @Override
        boolean
        parseItem(ItemValues iv, Node n) {
            boolean ret = true;

            if (n.getNodeName().equalsIgnoreCase("dc:date"))
                setValue(iv.pubDate, getTextValue(n));
            else
                ret = false;

            return ret;
        }

    }
    // ========================================
    //        Default RSS Parser
    // ========================================
    private class NSDefaultParser extends NSParser {
        NSDefaultParser() {
            super(PRI_DEFAULT);
        }

        private void
        nodeImage(ChannelValues cv, Node n) {
            n = n.getFirstChild();
            while (null != n) {
                if (n.getNodeName().equalsIgnoreCase("url")) {
                    setValue(cv.imageref, getTextValue(n));
                    return;
                }
                n = n.getNextSibling();
            }
        }

        private void
        nodeEnclosure(ItemValues iv, Node n) {
            NamedNodeMap nnm = n.getAttributes();
            n = nnm.getNamedItem("url");
            if (null != n)
                setValue(iv.enclosure_url, n.getNodeValue());

            n = nnm.getNamedItem("length");
            if (null != n)
                setValue(iv.enclosure_length, n.getNodeValue());

            n = nnm.getNamedItem("type");
            if (null != n)
                setValue(iv.enclosure_type, n.getNodeValue());
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n) {
            boolean ret = true;

            if (n.getNodeName().equalsIgnoreCase("title"))
                setValue(cv.title, getTextValue(n));
            else if (n.getNodeName().equalsIgnoreCase("description"))
                setValue(cv.description, getTextValue(n));
            else if (n.getNodeName().equalsIgnoreCase("image"))
                nodeImage(cv, n);
            else
                ret = false;

            return ret;
        }

        @Override
        boolean
        parseItem(ItemValues iv, Node n) {
            boolean ret = true;

            if (n.getNodeName().equalsIgnoreCase("title"))
                setValue(iv.title, getTextValue(n));
            else if (n.getNodeName().equalsIgnoreCase("link"))
                setValue(iv.link, getTextValue(n));
            else if (n.getNodeName().equalsIgnoreCase("description"))
                setValue(iv.description, getTextValue(n));
            else if (n.getNodeName().equalsIgnoreCase("enclosure"))
                nodeEnclosure(iv, n);
            else if (n.getNodeName().equalsIgnoreCase("pubDate"))
                setValue(iv.pubDate, getTextValue(n));
            else
                ret = false;

            return ret;
        }
    }

    // ===========================================================
    //
    // ===========================================================

    private RSSAttr
    nodeRssAttr(Node n) {
        RSSAttr rss = new RSSAttr();
        NamedNodeMap nnm = n.getAttributes();
        Node nVer = nnm.getNamedItem("version");
        if (null != nVer)
            rss.ver = nVer.getNodeValue();

        // Some element from 'itunes' and 'dc' is supported.
        // So, check it!
        if (null != nnm.getNamedItem("xmlns:itunes"))
            rss.nsl.addLast("itunes");

        if (null != nnm.getNamedItem("xmlns:dc"))
            rss.nsl.addLast("dc");

        return rss;
    }

    private RSS.Item
    nodeItem(Node n) {
        RSS.Item item = new RSS.Item();
        n = n.getFirstChild();
        while (null != n) {


            n = n.getNextSibling();
        }

        return item;
    }

    private RSS.Channel
    nodeChannel(NSParser[] parser, Node chn) {
        ChannelValues cv = new ChannelValues();
        ItemValues iv = new ItemValues();
        // count number of items in this channel

        LinkedList<RSS.Item> iteml = new LinkedList<RSS.Item>();
        cv.init();
        Node n = chn.getFirstChild();
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase("item")) {
                // Parsing elements of 'item'
                iv.init(); // to reuse
                Node in = n.getFirstChild();
                while (null != in) {
                    for (NSParser p : parser) {
                        if (p.parseItem(iv, in))
                            break; // handled
                    }
                    in = in.getNextSibling();
                }
                RSS.Item item = new RSS.Item();
                iv.set(item);
                iteml.addLast(item);
            } else {
                for (NSParser p : parser) {
                    if (p.parseChannel(cv, n))
                        break; // handled
                }
            }
            n = n.getNextSibling();
        }

        RSS.Channel ch = new RSS.Channel();
        cv.set(ch);
        ch.items = iteml.toArray(new RSS.Item[0]);

        return ch;
    }

    // false (fail)
    private boolean
    verifyNotNullPolicy(RSS rss) {
        if (null == rss.channel.title)
            return false;

        for (RSS.Item item : rss.channel.items)
            if (null == item.title)
                return false;

        return true;
    }

    public RSS
    parse(Document dom)
            throws FeederException {
        Element root = dom.getDocumentElement();
        verifyFormat(root.getNodeName().equalsIgnoreCase("rss"));

        RSSAttr rssAttr = nodeRssAttr(root);
        if (!rssAttr.ver.equals("2.0"))
            throw new FeederException(Err.ParserUnsupportedVersion);

        // Set parser
        LinkedList<NSParser> pl = new LinkedList<NSParser>();
        for (String s : rssAttr.nsl.toArray(new String[0])) {
            NSParser p = null;
            if (s.equals("itunes"))
                p = new NSItunesParser();
            else if (s.equals("dc"))
                p = new NSDcParser();
            else
                eAssert(false); // Not-supported namespace is parsed!!
            pl.add(p);
        }
        pl.add(new NSDefaultParser());


        // For Channel node
        Node n = findNodeByNameFromSiblings(root.getFirstChild(), "channel");

        RSS rss = new RSS();
        rss.channel = nodeChannel(pl.toArray(new NSParser[0]), n);

        if (!verifyNotNullPolicy(rss))
            throw new FeederException(Err.ParserUnsupportedFormat);
        //logI(rss.channel.dump());

        return rss;
    }
}
