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
    private static final short PRI_DEFAULT = 0;

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
           // 'updated' has priority
           final short priUpdated    = 1;
           final short priPublished  = 0;

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

    // ===========================================================
    //
    // ===========================================================

    private void
    constructNSParser(LinkedList<NSParser> pl, Node n)
            throws FeederException {
        NamedNodeMap nnm = n.getAttributes();
        // add default namespace parser
        pl.add(new NSDefaultParser());
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
        return "[ RSSParser ]";
    }

    @Override
    Result
    parse(Document dom)
            throws FeederException {
        eAssert(null != dom);
        Result res = null;
        UnexpectedExceptionHandler.S().registerModule(this);
        try {
            Element root = dom.getDocumentElement();
            verifyFormat(root.getNodeName().equalsIgnoreCase("feed"));

            LinkedList<NSParser> pl = new LinkedList<NSParser>();
            constructNSParser(pl, root);
            res = new Result();
            nodeFeed(res, pl.toArray(new NSParser[0]), root);
        } finally {
            UnexpectedExceptionHandler.S().unregisterModule(this);
        }
        return res;
    }
}