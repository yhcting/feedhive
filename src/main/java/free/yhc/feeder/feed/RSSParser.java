/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.feeder.feed;

import android.support.annotation.NonNull;

import java.util.LinkedList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import free.yhc.baselib.Logger;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.FeederException;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

public class RSSParser extends FeedParser implements
        UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(RSSParser.class, Logger.LOGLV_DEFAULT);

    // parsing priority of namespace supported (larger number has priority)
    private static final short PRI_ITUNES  = 2;
    private static final short PRI_DEFAULT = 1; // RSS default
    private static final short PRI_DC      = 0;

    // ========================================
    //        To Support 'itunes' Namespace
    // ========================================
    private class NSItunesParser extends NSParser {
        NSItunesParser() {
            super(PRI_ITUNES);
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n) throws FeederException {
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
        parseItem(ItemValues iv, Node n) throws FeederException {
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
        parseChannel(ChannelValues cv, Node n) throws FeederException {
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
        parseItem(ItemValues iv, Node n) throws FeederException {
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
            else if (n.getNodeName().equalsIgnoreCase("guid"))
                setValue(iv.guid, getTextValue(n));
            else
                ret = false;

            return ret;
        }
    }

    // ===========================================================
    //
    // ===========================================================

    private void
    constructNSParser(LinkedList<NSParser> pl, Node n) throws FeederException {
        NamedNodeMap nnm = n.getAttributes();
        @SuppressWarnings("unused")
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
    nodeChannel(Result res, NSParser[] parser, Node chn) throws FeederException {
        ChannelValues cv = new ChannelValues();
        ItemValues iv = new ItemValues();
        // count number of items in this channel

        LinkedList<Feed.Item.ParD> iteml = new LinkedList<>();
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
        res.items = iteml.toArray(new Feed.Item.ParD[iteml.size()]);
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
    @NonNull
    Result
    parseDom(@NonNull Document dom) throws FeederException {
        UnexpectedExceptionHandler.get().registerModule(this);
        try {
            Element root = dom.getDocumentElement();
            verifyFormat(root.getNodeName().equalsIgnoreCase("rss"));

            LinkedList<NSParser> pl = new LinkedList<>();
            constructNSParser(pl, root);

            Result res = new Result();

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

            nodeChannel(res, pl.toArray(new NSParser[pl.size()]), n);

            if (!verifyNotNullPolicy(res))
                throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
            return res;
        } finally {
            UnexpectedExceptionHandler.get().unregisterModule(this);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ RSSParser ]";
    }
}
