/*****************************************************************************
 *    Copyright (C) 2012, 2013 Younghyung Cho. <yhcting77@gmail.com>
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

import java.util.LinkedList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class RSSParser extends FeedParser implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(RSSParser.class);

    // parsing priority of namespace supported (larger number has priority)
    private static final short PRI_ITUNES     = 2;
    private static final short PRI_DEFAULT    = 1; // RSS default
    private static final short PRI_DC         = 0;

    // ========================================
    //        To Support 'itunes' Namespace
    // ========================================
    private class NSItunesParser extends NSParser {
        NSItunesParser() {
            super(PRI_ITUNES);
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n)
                throws FeederException {
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
        parseItem(ItemValues iv, Node n)
                throws FeederException {
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
    //        To Support 'dublincore(dc)' Namespace
    // ========================================
    private class NSDcParser extends NSParser {
        NSDcParser() {
            super(PRI_DC);
        }

        @Override
        boolean
        parseItem(ItemValues iv, Node n)
                throws FeederException {
            boolean ret = true;

            if (n.getNodeName().equalsIgnoreCase("dc:date"))
                setValue(iv.pubDate, getTextValue(n));
            else
                ret = false;

            return ret;
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n) {
            return false;
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
        nodeImage(ChannelValues cv, Node n)
                throws FeederException {
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
        parseChannel(ChannelValues cv, Node n)
                throws FeederException {
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
        parseItem(ItemValues iv, Node n)
                throws FeederException {
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

    private void
    constructNSParser(LinkedList<NSParser> pl, Node n)
            throws FeederException {
        NamedNodeMap nnm = n.getAttributes();
        Node nVer = nnm.getNamedItem("version");

        /*
        if (!"2.0".equals(nVer.getNodeValue()))
            throw new FeederException(Err.ParserUnsupportedVersion);
         */

        // add default namespace parser
        pl.add(new NSDefaultParser());

        // Some element from 'itunes' and 'dc' is supported.
        // So, check it!
        if (null != nnm.getNamedItem("xmlns:itunes"))
            pl.add(new NSItunesParser());

        if (null != nnm.getNamedItem("xmlns:dc"))
            pl.add(new NSDcParser());

    }


    private void
    nodeChannel(Result res, NSParser[] parser, Node chn)
            throws FeederException {
        ChannelValues cv = new ChannelValues();
        ItemValues iv = new ItemValues();
        // count number of items in this channel

        LinkedList<Feed.Item.ParD> iteml = new LinkedList<Feed.Item.ParD>();
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

    // false (fail)
    private boolean
    verifyNotNullPolicy(Result res) {
        if (null == res.channel.title)
            return false;

        for (Feed.Item.ParD item : res.items)
            if (null == item.title)
                return false;

        return true;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ RSSParser ]";
    }

    @Override
    protected Result
    parse(Document dom)
            throws FeederException {
        Result res = null;
        UnexpectedExceptionHandler.get().registerModule(this);
        try {
            Element root = dom.getDocumentElement();
            verifyFormat(root.getNodeName().equalsIgnoreCase("rss"));

            LinkedList<NSParser> pl = new LinkedList<NSParser>();
            constructNSParser(pl, root);

            res = new Result();

            // Some channels use itunes namespace even if they don't have any enclosure media.
            // So, below check doesn't have any meaning.
            /*
            // Channel type which uses itunes namespace is needed to be set as 'Media type'.
            for (NSParser p : pl.toArray(new NSParser[0]))
                if (p instanceof NSItunesParser)
                    res.channel.type = Feed.Channel.CHANN_TYPE_MEDIA;
             */

            // For Channel node
            Node n = findNodeByNameFromSiblings(root.getFirstChild(), "channel");

            nodeChannel(res, pl.toArray(new NSParser[0]), n);

            if (!verifyNotNullPolicy(res))
                throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
            //logI(feed.channel.dump());
        } finally {
            UnexpectedExceptionHandler.get().unregisterModule(this);
        }
        return res;
    }
}
