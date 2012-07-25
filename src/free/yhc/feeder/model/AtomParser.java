/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.LinkedList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class AtomParser extends FeedParser implements
UnexpectedExceptionHandler.TrackedModule {
    // parsing priority of namespace supported (larger number has priority)
    private static final short PRI_DEFAULT = 3;
    private static final short PRI_MEDIA   = 2;

    // ========================================
    //        Default Atom Parser
    // ========================================
    private class NSDefaultParser extends NSParser {
        NSDefaultParser() {
            super(PRI_DEFAULT);
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n)
                throws FeederException {
           boolean ret = true;

           if (n.getNodeName().equalsIgnoreCase("title"))
               setValue(cv.title, getTextConstructsValue(n));
           if (n.getNodeName().equalsIgnoreCase("subtitle"))
               setValue(cv.description, getTextConstructsValue(n));
           else if (n.getNodeName().equalsIgnoreCase("logo"))
               setValue(cv.imageref, getTextValue(n));
           else
               ret = false;

           return ret;
       }

       @Override
       boolean
       parseItem(ItemValues iv, Node n)
               throws FeederException {
           // 'published' has priority
           final short priUpdated    = 0;
           final short priPublished  = 1;

           boolean ret = true;

           if (n.getNodeName().equalsIgnoreCase("title"))
               setValue(iv.title, getTextConstructsValue(n));
           else if (n.getNodeName().equalsIgnoreCase("content"))
               setValue(iv.description, getTextConstructsValue(n));
           else if (n.getNodeName().equalsIgnoreCase("link")) {
               if (getLinkRelType(n).equalsIgnoreCase("alternate"))
                   setValue(iv.link, getLinkHref(n));
               else if (getLinkRelType(n).equalsIgnoreCase("enclosure"))
                   setValue(iv.enclosure_url, getLinkHref(n));
           } else if (n.getNodeName().equalsIgnoreCase("updated"))
               setValue(iv.pubDate, getTimeConstructsValue(n), priUpdated);
           else if (n.getNodeName().equalsIgnoreCase("published"))
               setValue(iv.pubDate, getTimeConstructsValue(n), priPublished);
           else
               ret = false;

           return ret;
       }
    }
    // ========================================
    //        Media
    // ========================================
    private class NSMediaParser extends NSParser {
        private static final long extend_youtube = 0x1;
        private static final long extend_mask    = 0x1;
        private long extend = 0;

        NSMediaParser() {
            super(PRI_MEDIA);
        }

        private void
        setContent(ItemValues iv, Node n) {
            NamedNodeMap nnm = n.getAttributes();
            short nodepri = 0;
            Node attrN = null;
            // NOTE YOUTUBE hack
            // YOUTUBE SPECIFIC PARSING - START
            // handle attribute for youtube "yt" namespace in "media:content"
            // larger yt:format value is preferred
            // use yt:format value as node priority.
            if (Utils.bitIsSet(extend, extend_youtube, extend_mask)) {
                attrN = nnm.getNamedItem("yt:format");
                short  ytformat = 1;
                if (null != attrN)
                    ytformat = Short.parseShort(attrN.getNodeValue());

                // yt:format 5 has highest priority
                if (5 == ytformat)
                    nodepri += 100;
                else
                    nodepri += ytformat;
            }
            // YOUTUBE SPECIFIC PARSING - END


            attrN = nnm.getNamedItem("url");
            if (null != attrN)
                setValue(iv.enclosure_url, attrN.getNodeValue(), nodepri);
            attrN = nnm.getNamedItem("type");
            if (null != attrN)
                setValue(iv.enclosure_type, attrN.getNodeValue(), nodepri);
        }

        // NOTE YOUTUBE hack
        void
        enableYoutube() {
            extend |= extend_youtube;
        }

        // NOTE YOUTUBE hack
        boolean
        isYoutubeEnabled() {
            return (Utils.bitIsSet(extend, extend_youtube, extend_mask));
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n)
                throws FeederException {
            return false;
        }

        @Override
        boolean
        parseItem(ItemValues iv, Node n)
                throws FeederException {
            if (!n.getNodeName().equalsIgnoreCase("media:group"))
                return false;

            n = n.getFirstChild();
            while (null != n) {
                if (n.getNodeName().equalsIgnoreCase("media:description"))
                    setValue(iv.description, getTextConstructsValue(n));
                else if (n.getNodeName().equalsIgnoreCase("media:content"))
                    setContent(iv, n);
                n = n.getNextSibling();
            }

            return true;
        }
    }
    // ===========================================================
    //
    // ===========================================================

    private void
    constructNSParser(LinkedList<NSParser> pl, Node n)
            throws FeederException {
        NamedNodeMap nnm = n.getAttributes();
        // add default namespace parser
        pl.add(new NSDefaultParser());

        // To support 'media' name space.
        if (null != nnm.getNamedItem("xmlns:media")) {
            NSMediaParser nsmp = new NSMediaParser();

            // NOTE YOUTUBE hack
            if (null != nnm.getNamedItem("xmlns:yt"))
                nsmp.enableYoutube();

            pl.add(nsmp);
        }

    }

    private String
    getTextConstructsValue(Node n)
            throws FeederException {
        NamedNodeMap nnm = n.getAttributes();
        Node typeN = nnm.getNamedItem("type");
        String type = null != typeN? typeN.getNodeValue(): "text";
        String text = getTextValue(n);
        if ("html".equalsIgnoreCase(type)) {
            // text is html value. So, we need to extract pure text contents
            // FIXME
        } else if ("xhtml".equalsIgnoreCase(type)) {
            // text is xhtml value. So, we need to extract pure text contents
            // FIXME
        }
        return text;
    }

    private String
    getTimeConstructsValue(Node n)
            throws FeederException {
        // Nothing specical.
        return getTextValue(n);
    }

    private String
    getLinkRelType(Node n) {
        NamedNodeMap nnm = n.getAttributes();
        Node relN = nnm.getNamedItem("rel");
        return (null == relN)? "alternate": relN.getNodeValue();
    }

    private String
    getLinkHref(Node n) {
        NamedNodeMap nnm = n.getAttributes();
        return nnm.getNamedItem("href").getNodeValue();
    }


    private void
    nodeFeed(Result res, NSParser[] parser, Node fn)
            throws FeederException {
        ChannelValues cv = new ChannelValues();
        ItemValues iv = new ItemValues();
        // count number of items in this channel

        LinkedList<Feed.Item.ParD> iteml = new LinkedList<Feed.Item.ParD>();
        cv.init();
        Node n = fn.getFirstChild();
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase("entry")) {
                // Parsing elements of 'entry'
                iv.init(); // to reuse
                Node in = n.getFirstChild();
                while (null != in) {
                    for (NSParser p : parser) {
                        if (p.parseItem(iv, in))
                            break; // handled
                    }
                    in = in.getNextSibling();
                }
                Feed.Item.ParD item = new Feed.Item.ParD();
                iv.set(item);
                if (isValidItem(item))
                    iteml.addLast(item);
            } else {
                for (NSParser p : parser) {
                    if (p.parseChannel(cv, n))
                        break; // handled
                }
            }
            n = n.getNextSibling();
        }

        cv.set(res.channel);
        res.items = iteml.toArray(new Feed.Item.ParD[0]);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ AtomParser ]";
    }

    @Override
    Result
    parse(Document dom)
            throws FeederException {
        eAssert(null != dom);
        Result res = null;
        UnexpectedExceptionHandler.get().registerModule(this);
        try {
            Element root = dom.getDocumentElement();
            verifyFormat(root.getNodeName().equalsIgnoreCase("feed"));

            LinkedList<NSParser> pl = new LinkedList<NSParser>();
            constructNSParser(pl, root);
            res = new Result();

            // NOTE YOUTUBE hack
            for (NSParser p : pl.toArray(new NSParser[0]))
                if ((p instanceof NSMediaParser) && ((NSMediaParser)p).isYoutubeEnabled())
                    res.channel.type = Feed.Channel.Type.EMBEDDED_MEDIA;

            nodeFeed(res, pl.toArray(new NSParser[0]), root);
        } finally {
            UnexpectedExceptionHandler.get().unregisterModule(this);
        }
        return res;
    }
}
