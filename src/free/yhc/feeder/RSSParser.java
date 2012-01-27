package free.yhc.feeder;

import static free.yhc.feeder.Utils.logI;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import free.yhc.feeder.FeederException.Err;

class RSSParser {

    protected void
    printNexts(Node n) {
        String msg = "";
        while (null != n) {
            msg = msg + " / " + n.getNodeName();
            n = n.getNextSibling();
        }
        logI(msg + "\n");
    }

    protected void
    verifyFormat(boolean cond)
            throws FeederException {
        if (!cond)
            throw new FeederException(Err.ParserUnsupportedFormat);
    }

    protected Node
    findNodeByNameFromSiblings(Node n, String name) {
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase(name))
                return n;
            n = n.getNextSibling();
        }
        return null;
    }

    protected String
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

    protected RSS.Guid
    nodeGuid(Node n) {
        RSS.Guid guid = new RSS.Guid();
        guid.value = getTextValue(n);

        NamedNodeMap nnm = n.getAttributes();
        n = nnm.getNamedItem("isPermaLink");
        if (null != n)
            guid.isPermaLink = n.getNodeValue();

        return guid;
    }

    protected RSS.Source
    nodeSource(Node n) {
        RSS.Source src = new RSS.Source();
        src.value = getTextValue(n);

        NamedNodeMap nnm = n.getAttributes();
        n = nnm.getNamedItem("url");
        if (null != n)
            src.url = n.getNodeValue();

        return src;
    }

    protected RSS.Category
    nodeCategory(Node n) {
        RSS.Category cat = new RSS.Category();
        cat.value = getTextValue(n);

        NamedNodeMap nnm = n.getAttributes();
        n = nnm.getNamedItem("domain");
        if (null != n)
            cat.domain = n.getNodeValue();

        return cat;
    }

    protected RSS.Enclosure
    nodeEnclosure(Node n) {
        RSS.Enclosure en = new RSS.Enclosure();

        NamedNodeMap nnm = n.getAttributes();
        n = nnm.getNamedItem("url");
        if (null != n)
            en.url = n.getNodeValue();

        n = nnm.getNamedItem("length");
        if (null != n)
            en.length = n.getNodeValue();

        n = nnm.getNamedItem("type");
        if (null != n)
            en.type = n.getNodeValue();

        return en;
    }

    protected RSS.Image
    nodeImage(Node n) {
        RSS.Image img = new RSS.Image();
        n = n.getFirstChild();
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase("url"))
                img.url = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("title"))
                img.title = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("link"))
                img.link = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("width"))
                img.width = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("height"))
                img.height = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("description"))
                img.description = getTextValue(n);

            n = n.getNextSibling();
        }
        return img;
    }

    protected RSS
    nodeRss(Node n) {
        RSS rss = new RSS();
        NamedNodeMap nnm = n.getAttributes();
        Node nVer = nnm.getNamedItem("version");
        if (null != nVer)
            rss.version = nVer.getNodeValue();

        // TODO support 'xmlns' attributes.
        return rss;
    }

    protected RSS.Item
    nodeItem(Node n) {
        RSS.Item item = new RSS.Item();
        n = n.getFirstChild();
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase("title")) {
                item.title = getTextValue(n);
            } else if (n.getNodeName().equalsIgnoreCase("link"))
                item.link = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("description"))
                item.description = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("author"))
                item.author = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("category"))
                item.category = nodeCategory(n);
            else if (n.getNodeName().equalsIgnoreCase("comments"))
                item.comments = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("enclosure"))
                item.enclosure = nodeEnclosure(n);
            else if (n.getNodeName().equalsIgnoreCase("guid"))
                item.guid = nodeGuid(n);
            else if (n.getNodeName().equalsIgnoreCase("pubDate"))
                item.pubDate = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("source"))
                item.source = nodeSource(n);

            n = n.getNextSibling();
        }

        return item;
    }

    protected RSS.Channel
    nodeChannel(Node chn) {
        RSS.Channel ch = new RSS.Channel();
        Node n = chn.getFirstChild();

        // count number of items in this channel
        int  cnt = 0;
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase("item"))
                cnt++;
            n = n.getNextSibling();
        }

        // alloc memory for items
        ch.items = new RSS.Item[cnt];

        // Start parsing
        cnt = 0;
        n = chn.getFirstChild();
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase("item"))
                ch.items[cnt++] = nodeItem(n);
            else if (n.getNodeName().equalsIgnoreCase("title"))
                ch.title = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("link"))
                ch.link = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("description"))
                ch.description = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("language"))
                ch.language = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("copyright"))
                ch.copyright = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("managingEditor"))
                ch.managingEditor = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("webMaster"))
                ch.webMaster = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("pubDate"))
                ch.pubDate = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("lastBuildDate"))
                ch.lastBuildDate = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("generator"))
                ch.generator = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("docs"))
                ch.docs = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("cloud"))
                ch.cloud = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("ttl"))
                ch.ttl = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("rating"))
                ch.rating = ""; // TODO: support this field
            else if (n.getNodeName().equalsIgnoreCase("textInput"))
                ch.textInput = ""; // TODO: support this field
            else if (n.getNodeName().equalsIgnoreCase("skipHours"))
                ch.skipHours = getTextValue(n);
            else if (n.getNodeName().equalsIgnoreCase("category"))
                ch.category = nodeCategory(n);
            else if (n.getNodeName().equalsIgnoreCase("image"))
                ch.image = nodeImage(n);

            n = n.getNextSibling();
        }
        return ch;
    }

    RSS
    parse(Document dom)
            throws FeederException {
        RSS     rss;
        Element root = dom.getDocumentElement();
        logI("Root : " + root.getNodeName());
        verifyFormat(root.getNodeName().equalsIgnoreCase("rss"));
        rss = nodeRss(root);
        if (!rss.version.equals("2.0"))
            throw new FeederException(Err.ParserUnsupportedVersion);

        // For Channel node
        Node n = findNodeByNameFromSiblings(root.getFirstChild(), "channel");
        rss.channel = nodeChannel(n);

        return rss;
    }
}
