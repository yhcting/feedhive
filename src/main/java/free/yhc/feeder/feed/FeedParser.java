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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import android.support.annotation.NonNull;
import android.text.Html;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import free.yhc.baselib.Logger;
import free.yhc.baselib.async.HelperHandler;
import free.yhc.baselib.net.NetReadTask;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.FeederException;
import free.yhc.feeder.core.Util;

public abstract class FeedParser {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(FeedParser.class, Logger.LOGLV_DEFAULT);

    // Result data format from parse.
    public static class Result {
        public Feed.Channel.ParD channel = new Feed.Channel.ParD();
        public Feed.Item.ParD[] items = null;
    }

    protected static class NodeValue {
        int priority; // priority value of parsing modules which updates this value.
        String value;

        NodeValue() {
            init();
        }

        void
        init() {
            priority = -1;
            value = "";
        }
    }

    protected static class ChannelValues {
        NodeValue title = new NodeValue();
        NodeValue description = new NodeValue();
        NodeValue imageref = new NodeValue();

        void
        init() {
            title.init();
            description.init();
            imageref.init();
        }

        void
        set(Feed.Channel.ParD ch) {
            ch.title = title.value;
            ch.description = description.value;
            ch.imageref = imageref.value;
        }
    }

    protected static class ItemValues {
        // static pattern to check "String represents web protocol"
        private static final Pattern _sWebProtoPattern
            = Pattern.compile("^http(s)?\\Q://\\E.*", Pattern.CASE_INSENSITIVE);

        NodeValue title = new NodeValue();
        NodeValue description = new NodeValue();
        NodeValue link = new NodeValue();
        NodeValue enclosure_length = new NodeValue();
        NodeValue enclosure_url = new NodeValue();
        NodeValue enclosure_type = new NodeValue();
        NodeValue pubDate = new NodeValue();

        // Below values is NOT required at STANDARD case.
        // But, at some Feed - ex. hanitv sisagate, enclosure url is empty.
        // But, 'guid' has valid enclosure url.
        // In this case, 'guid' should be used as enclosure url.
        // This is DEFINITELY out of spec. and out of standard...
        // But... *sigh*
        // To handle those exceptional case, below variables are defined.
        // (You might be able to regard them as overhead.)
        // Below values SHOULD NOT be exported to Feed data.
        // (This is SHOULD BE EXCLUDED at EXTERNAL interface.)
        NodeValue guid = new NodeValue();

        void
        init() {
            title.init();
            description.init();
            link.init();
            enclosure_length.init();
            enclosure_url.init();
            enclosure_type.init();
            pubDate.init();

            // To handle exceptional case
            guid.init();
        }

        void
        set(Feed.Item.ParD item) {
            item.title = title.value;
            item.description = description.value;
            item.link = link.value;
            if (null != enclosure_length.value
                || null != enclosure_url.value
                || null != enclosure_type.value) {
                item.enclosureLength = enclosure_length.value;
                item.enclosureUrl = enclosure_url.value;
                item.enclosureType = enclosure_type.value;
            }
            item.pubDate = pubDate.value;

            //
            // Handle exceptional case.
            //
            if (!isValidItem(item)) {
                // Try to fixup
                if (Util.isValidValue(guid.value)
                    && _sWebProtoPattern.matcher(guid.value).matches()) {
                    if (Util.isValidValue(item.enclosureType)
                        && !Util.isValidValue(item.enclosureUrl))
                        item.enclosureUrl = guid.value;
                    else if (!Util.isValidValue(item.link))
                        item.link = guid.value;
                }
            }
        }
    }


    // ===========================================================
    //
    //                    Name space parsor
    //
    // ===========================================================
    protected abstract static class NSParser {
        private static final int NS_PRI_SHIFT = 16;
        private static final short NODE_PRI_DEFAULT = 0;

        private short _mNspri;

        @SuppressWarnings("unused")
        private NSParser(){} // block default constructor.

        NSParser(short priority) {
            _mNspri = priority;
        }

        private int
        pri(int nsPri, int nodePri) {
            return (nsPri << NS_PRI_SHIFT) | nodePri;
        }

        /**
         * Set value of this node if priority is higher than node value.
         * @return true(value is set) false(value is not set)
         */
        protected boolean
        setValue(NodeValue nv, String value) {
            return setValue(nv, value, NODE_PRI_DEFAULT);
        }

        /**
         * Set value of this node if priority is higher than node value.
         * @param nodePri should be 2 byte value.
         * @return true(value is set) false(value is not set)
         */
        protected boolean
        setValue(NodeValue nv, String value, short nodePri) {
            int newPri = pri(_mNspri, nodePri);
            if (nv.priority <= newPri) {
                nv.value = value;
                nv.priority = newPri;
                return true;
            }
            return false;
        }

        /**
         *
         * @return true(handled) false(passed)
         * @throws FeederException
         */
        abstract boolean
        parseChannel(ChannelValues cv, Node n)
                throws FeederException;

        /**
         *
         * @return true(handled) false(passed)
         * @throws FeederException
         */
        abstract boolean
        parseItem(ItemValues iv, Node n)
                throws FeederException;
    }


    // ========================================
    //
    //        Common Utility Functions
    //
    // ========================================
    /**
     * Print(logI) next node name.
     */
    @SuppressWarnings("unused")
    protected final void
    printNexts(Node n) {
        String msg = "";
        while (null != n) {
            msg = msg + " / " + n.getNodeName();
            n = n.getNextSibling();
        }
        if (DBG) P.d(msg + "\n");
    }

    protected final void
    verifyFormat(boolean cond)
            throws FeederException {
        if (!cond)
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
    }

    protected final Node
    findNodeByNameFromSiblings(Node n, String name) {
        while (null != n) {
            if (n.getNodeName().equalsIgnoreCase(name))
                return n;
            n = n.getNextSibling();
        }
        return null;
    }

    protected final String
    getTextValue(Node n)
            throws FeederException {

        if (Thread.interrupted())
            throw new FeederException(Err.INTERRUPTED);

        String text;
        Node t = findNodeByNameFromSiblings(n.getFirstChild(), "#text");

        //
        // [ Issue of CDATA section. ]
        // CDATA section should be ignored at XML parser.
        // But, lots of rss feeds supported by Korean presses, uses CDATA section as a kind of text section.
        //
        // For example
        //     <title><![CDATA[[사설]지상파·케이블의 밥그릇 싸움과 방통위의 무능]]></title>
        //
        // At some cases, 'desciption' elements includs several 'cdata-section'
        // For example
        //     <description><![CDATA[[ xxx ]]> <![CDATA[[xxxx]]></description>
        //
        // In this case, there is no title string!!!
        // To support this case, we uses 'cdata' section if there is no valid 'text' section.
        // In case of there is several 'cdata-section', merge all into one string.
        // (This is kind of parsing policy!!)
        //
        // TODO
        //   This new Parsing Policy is best way to support this?
        //   Is there any elegant code structure to support various parsing policy?
        //
        if (null == t
            || t.getNodeValue().matches("^\\s*$")) {
            // There is NO '#text' section or there is NO valid text.
            StringBuilder sbuilder = new StringBuilder();
            n = n.getFirstChild();
            while (null != n) {
                if (n.getNodeName().equalsIgnoreCase("#cdata-section"))
                    sbuilder.append(n.getNodeValue());
                n = n.getNextSibling();
            }
            text = sbuilder.toString();
        } else
            text = t.getNodeValue();

        // Lots of RSS serviced in South Korea uses raw HTML string in
        //   'title' 'description' or 'cdata-section'
        // So, we need to beautify this string.(remove ugly tags and entities)
        // This may time-consuming job.
        text = Html.fromHtml(text).toString();

        //
        // [ remove leading and trailing new line. ]
        //
        // + 'xxx' is stored.
        //     <tag>xxx</tag>
        //
        // + '\nxxx\n' is stored.
        //     <tag>
        //     xxx
        //     </tag>
        //
        // [ removing wrapping white space between '￼' character ] ???
        // Usually, image is wrapped by several white spaces.
        // This beautifies web pages, but ugly in short description.
        // Open discussion for this...
        text = Util.removeLeadingTrailingWhiteSpace(text);

        //
        // NOTE
        //   Why "" is returned instead of null?
        //   Calling this function means interesting node is found.
        //   But, in case node has empty text, DOM parser doesn't havs '#text' node.
        //
        //   For example
        //      <title></title>
        //
        //   In this case node 'title' doesn't have '#text'node as it's child.
        //   So, t becomes null.
        //   But, having empty string as an text value of node 'title', is
        //     more reasonable than null as it's value.
        //
        return text;
    }

    // ========================================
    //
    //        Constraints
    //
    // ========================================
    protected static boolean
    isValidItem(Feed.Item.ParD iParD) {
        return Util.isValidValue(iParD.title)
               && (Util.isValidValue(iParD.link) || Util.isValidValue(iParD.enclosureUrl));
    }

    // ========================================
    //
    //        Interface functions
    //
    // ========================================
    /**
     * Get real parser for this document
     */
    @NonNull
    static FeedParser
    getParser(@NonNull Document dom) throws FeederException {
        Element root = dom.getDocumentElement();
        if (null == root)
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
        if ("rss".equalsIgnoreCase(root.getNodeName()))
            return new RSSParser();
        else if ("feed".equalsIgnoreCase(root.getNodeName()))
            return new AtomParser();
        else
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
    }

    @NonNull
    public static Result
    parse(@NonNull URL url) throws FeederException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            NetReadTask.Builder<NetReadTask.Builder> b
                    = new NetReadTask.Builder<>(Util.createNetConn(url), baos);
            b.setOwner(HelperHandler.get());
            b.create().startSync();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                return parse(bais);
            }
        } catch (IOException e) {
            throw new FeederException(Err.IO_NET);
        } catch (InterruptedException e) {
            P.bug(false); // Never happend!
            throw new FeederException(Err.USER_CANCELLED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public static Result
    parse(@NonNull String url) throws FeederException {
        try {
            return parse(new URL(url));
        } catch (MalformedURLException e) {
            throw new FeederException(Err.INVALID_URL);
        }
    }

    @NonNull
    abstract Result
    parseDom(@NonNull Document dom) throws FeederException;

    // real parser should implement 'parse' function.
    @NonNull
    public static Result
    parse(@NonNull InputStream is) throws FeederException {
        try {
            Document dom = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(is);
            return FeedParser.getParser(dom).parseDom(dom);
        } catch (DOMException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
        } catch (IOException e) {
            e.printStackTrace();
            throw new FeederException(Err.UNKNOWN);
        }
    }

}
