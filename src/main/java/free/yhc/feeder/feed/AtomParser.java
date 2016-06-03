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
import free.yhc.feeder.core.FeederException;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

import static free.yhc.baselib.util.Util.bitCompare;

class AtomParser extends FeedParser implements
        UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(AtomParser.class, Logger.LOGLV_DEFAULT);

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
        parseChannel(ChannelValues cv, Node n)  throws FeederException {
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
       parseItem(ItemValues iv, Node n) throws FeederException {
           // 'published' has priority
           final short priUpdated   = 0;
           final short priPublished = 1;

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
            Node attrN ;
            // NOTE YOUTUBE hack
            // YOUTUBE SPECIFIC PARSING - START
            // handle attribute for youtube "yt" namespace in "media:content"
            // larger yt:format value is preferred
            // use yt:format value as node priority.
            if (bitCompare(extend_youtube, extend, extend_mask)) {
                attrN = nnm.getNamedItem("yt:format");
                short ytformat = 1;
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
            return (bitCompare(extend_youtube, extend, extend_mask));
        }

        @Override
        boolean
        parseChannel(ChannelValues cv, Node n) throws FeederException {
            return false;
        }

        @Override
        boolean
        parseItem(ItemValues iv, Node n) throws FeederException {
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
    constructNSParser(LinkedList<NSParser> pl, Node n) throws FeederException {
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
    getTextConstructsValue(Node n) throws FeederException {
        NamedNodeMap nnm = n.getAttributes();
        Node typeN = nnm.getNamedItem("type");
        String type = null != typeN? typeN.getNodeValue(): "text";
        String text = getTextValue(n);
        //noinspection StatementWithEmptyBody
        if ("html".equalsIgnoreCase(type)) {
            // FIXME : text is html value. So, we need to extract pure text contents
        } else
        //noinspection StatementWithEmptyBody
        if ("xhtml".equalsIgnoreCase(type)) {
            // FIXME text is xhtml value. So, we need to extract pure text contents
        }
        return text;
    }

    private String
    getTimeConstructsValue(Node n) throws FeederException {
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
    nodeFeed(Result res, NSParser[] parser, Node fn)  throws FeederException {
        ChannelValues cv = new ChannelValues();
        ItemValues iv = new ItemValues();
        // count number of items in this channel

        LinkedList<Feed.Item.ParD> iteml = new LinkedList<>();
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
        res.items = iteml.toArray(new Feed.Item.ParD[iteml.size()]);
    }

    @Override
    @NonNull
    Result
    parseDom(@NonNull Document dom) throws FeederException {
        UnexpectedExceptionHandler.get().registerModule(this);
        try {
            Element root = dom.getDocumentElement();
            verifyFormat(root.getNodeName().equalsIgnoreCase("feed"));

            LinkedList<NSParser> pl = new LinkedList<>();
            constructNSParser(pl, root);
            Result res = new Result();

            // NOTE YOUTUBE hack
            for (NSParser p : pl.toArray(new NSParser[pl.size()]))
                if ((p instanceof NSMediaParser) && ((NSMediaParser)p).isYoutubeEnabled())
                    res.channel.type = Feed.Channel.Type.EMBEDDED_MEDIA;

            nodeFeed(res, pl.toArray(new NSParser[pl.size()]), root);
            return res;
        } finally {
            UnexpectedExceptionHandler.get().unregisterModule(this);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ AtomParser ]";
    }
}
