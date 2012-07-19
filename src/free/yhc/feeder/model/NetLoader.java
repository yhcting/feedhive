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
import static free.yhc.feeder.model.Utils.logW;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.graphics.Bitmap;

public class NetLoader {
    private static final int NET_RETRY = 5;

    private final UIPolicy  uip = UIPolicy.get();
    private final DBPolicy  dbp = DBPolicy.get();

    private volatile boolean        cancelled = false;
    private volatile InputStream    istream = null; // Multi-thread access
    private volatile OutputStream   ostream = null;
    private volatile File           tmpFile = null;

    interface OnProgress {
        // "progress < 0" means "Unknown progress"
        // In this case, negative value of bytes processed are passed as value of 'progress'
        void onProgress(NetLoader loader, long progress);
    }

    /**
     * Close network input stream of this loader - istream.
     * @throws FeederException
     */
    private void
    closeIstream() throws FeederException {
        try {
            if (null != istream)
                istream.close();
        } catch (IOException e) {
            throw new FeederException (Err.IO_NET);
        }
    }

    /**
     * Close output stream - ostream.
     * (Usually, output stream is file stream)
     * @throws FeederException
     */
    private void
    closeOstream() throws FeederException {
        try {
            if (null != ostream)
                ostream.close();
        } catch (IOException e) {
            throw new FeederException (Err.IO_FILE);
        }
    }

    /**
     * Delete temp file - tmpFile - used.
     * @return
     */
    private boolean
    clearTempFile() {
        boolean ret = true;
        if (null != tmpFile)
            ret = tmpFile.delete();
        return ret;
    }

    /**
     * Close input/output stream (istream, ostream) and
     *   delete temp file (tmpFile)
     * @return
     */
    private boolean
    cleanup() {
        try {
            if (null != istream)
                istream.close();
            if (null != ostream)
                ostream.close();
            if (null != tmpFile)
                tmpFile.delete();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void
    checkInterrupted() throws FeederException {
        if (cancelled)
            throw new FeederException(Err.USER_CANCELLED);
        if (Thread.currentThread().isInterrupted())
            throw new FeederException(Err.INTERRUPTED);
    }

    /**
     * NOTE!
     *   Many caller assumes that 'download' function throws only below three exceptions.
     *   If another exception need to be thrown, all callers should be checked and verified!
     *
     * FeederException : Err.IONet / Err.UserCancelled / Err.Interrupted
     * @param ostream
     * @param urlStr
     * @param progressListener
     * @throws FeederException
     */
    private void
    download(OutputStream outstream, String urlStr, OnProgress progressListener)
            throws FeederException {
           URL           url = null;
           URLConnection conn = null;
           int           retry = NET_RETRY;
           try {
               url = new URL(urlStr);
           } catch (MalformedURLException e) {
               throw new FeederException(Err.INVALID_URL);
           }

           while (0 < retry--) {
               try {
                   conn = url.openConnection();
                   conn.setConnectTimeout(1000);
                   conn.connect();
                   break; // done
               } catch (Exception e) {
                   // SocketTimeoutException
                   // IOException
                   if (cancelled)
                       throw new FeederException(Err.USER_CANCELLED);

                   if (0 >= retry)
                       throw new FeederException(Err.IO_NET);

                   try {
                       Thread.sleep(500);
                   } catch (InterruptedException ie) {
                       if (cancelled)
                           throw new FeederException(Err.USER_CANCELLED);
                       else
                           throw new FeederException(Err.INTERRUPTED);
                   }
               }
           }

           try {
               int lengthOfFile = -1;
               retry = NET_RETRY;
               while (0 < retry--) {
                   lengthOfFile = conn.getContentLength();
                   if (lengthOfFile >= 0)
                       break;
               }
               istream = new BufferedInputStream(conn.getInputStream());

               if (Thread.currentThread().isInterrupted()) {
                   cancel();
                   throw new FeederException(Err.INTERRUPTED);
               }

               byte data[] = new byte[256*1024];

               long total = 0;
               int  count;
               long prevProgress = -1;
               while (true) {
                   if (-1 == (count = istream.read(data)))
                       break;
                   outstream.write(data, 0, count);
                   total += count;
                   long progress = (total * 100 / lengthOfFile);
                   if (lengthOfFile < 0 && null != progressListener) {
                       progressListener.onProgress(this, -total);
                   } else if (progress > prevProgress) {
                       if (null != progressListener)
                           progressListener.onProgress(this, progress);
                       prevProgress = progress;
                   }
               }

               outstream.flush();
               outstream.close();
           } catch (IOException e) {
               // User's canceling operation close in/out stream in force.
               // And this leads to IOException here.
               // So, we should check that this Exception is caused by user's cancel or real IOException.
               if (cancelled)
                   throw new FeederException(Err.USER_CANCELLED);
               else {
                   e.printStackTrace();
                   logW(e.getMessage());
                   throw new FeederException(Err.IO_NET);
               }
           } finally {
               closeIstream();
           }
    }


    public NetLoader() {
    }

    private RSSParser.Result
    parseFeedUrl(String url)
            throws FeederException {
        //logI("Fetching Channel [" + url + "]");
        RSSParser.Result res = null;
        long             time;
        int              retry = NET_RETRY;
        try {
            while (0 < retry--) {
                try {
                    time = System.currentTimeMillis();
                    URLConnection conn = new URL(url).openConnection();
                    conn.setReadTimeout(1000);
                    istream = conn.getInputStream();
                    Document dom = DocumentBuilderFactory
                                    .newInstance()
                                    .newDocumentBuilder()
                                    .parse(istream);
                    istream.close();
                    //logI("TIME: Open URL and Parseing as Dom : " + (System.currentTimeMillis() - time));
                    time = System.currentTimeMillis();
                    // Only RSS is supported at this version.
                    res = FeedParser.getParser(dom).parse(dom);
                    //logI("TIME: RSSParsing : " + (System.currentTimeMillis() - time));
                    break; // done
                } catch (MalformedURLException e) {
                    throw new FeederException(Err.INVALID_URL);
                } catch (IOException e) {
                    if (cancelled)
                        throw new FeederException(Err.USER_CANCELLED);

                    if (0 >= retry)
                        throw new FeederException(Err.IO_NET);

                    ; // continue next retry after some time.
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        if (cancelled)
                            throw new FeederException(Err.USER_CANCELLED);
                        else
                            throw new FeederException(Err.INTERRUPTED);
                    }
                }
            }
        } catch (DOMException e) {
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
        } catch (SAXException e) {
            e.printStackTrace();
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
        } catch (ParserConfigurationException e) {
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
        } catch (FeederException e) {
            throw e;
        } finally {
            closeIstream();
        }
        return res;
    }

    /**
     * Update feed information.
     * @param cid
     * @param imageref
     *   if (null != imageref)
     *   then 'imageref' is used instead of given by RSS channel infomation.
     * @throws FeederException
     */
    private void
    update(long cid, String imageref)
            throws FeederException {
        String url = dbp.getChannelInfoString(cid, DB.ColumnChannel.URL);
        eAssert(null != url);

        //logI("Loading Items: " + url);

        long time = System.currentTimeMillis();
        RSSParser.Result parD = parseFeedUrl(url);
        //logI("TIME: Loading + Parsing : " + (System.currentTimeMillis() - time));

        // set to given value forcely due to this is 'update' - Not new insertion.

        // decide action type.
        // Actually deciding only once is enough in general case.
        // But, rarely whole channel item type may be changed and requires different action.
        // So, whenever update is executed try to adjust 'action' type.
        long oldAction = dbp.getChannelInfoLong(cid, DB.ColumnChannel.ACTION);
        long action = uip.decideActionType(oldAction,
                                           parD.channel,
                                           parD.items.length > 0? parD.items[0]: null);
        if (action != oldAction)
            dbp.updateChannel(cid, DB.ColumnChannel.ACTION, action);

        byte[] imageblob = dbp.getChannelInfoImageblob(cid);
        if (imageblob.length <= 0) {
            time = System.currentTimeMillis();
            // Kind Of Policy!!
            // Original image reference always has priority!
            byte[] bmdata = null;
            try {
                if (Utils.isValidValue(parD.channel.imageref))
                    bmdata = downloadToRaw(parD.channel.imageref, null);
            } catch (FeederException e) { }
            checkInterrupted();

            try {
                if (null == bmdata && Utils.isValidValue(imageref))
                    bmdata = downloadToRaw(imageref, null);
            } catch (FeederException e) { }
            checkInterrupted();

            if (null != bmdata) {
                Bitmap bm = Utils.decodeImage(bmdata,
                                              Feed.Channel.ICON_MAX_WIDTH,
                                              Feed.Channel.ICON_MAX_HEIGHT);
                imageblob = Utils.compressBitmap(bm);
                bm.recycle();
                if (null != imageblob)
                    dbp.updateChannel(cid, DB.ColumnChannel.IMAGEBLOB, imageblob);
            }
            //logI("TIME: Handle Image : " + (System.currentTimeMillis() - time));
            checkInterrupted();
        }

        time = System.currentTimeMillis();

        LinkedList<Feed.Item.ParD> newItems = new LinkedList<Feed.Item.ParD>();
        dbp.getNewItems(cid, parD.items, newItems);

        DBPolicy.ItemDataOpInterface idop = null;
        // NOTE
        // Information in "ch.dynD" is not available in case update.
        // ('imageblob' and 'action' is exception case controlled with argument.)
        // This is dynamically assigned variable.
        long updateMode = dbp.getChannelInfoLong(cid, DB.ColumnChannel.UPDATEMODE);
        if (Feed.Channel.isUpdDn(updateMode)) {
            final long chact = action;
            idop = new DBPolicy.ItemDataOpInterface() {
                @Override
                public File getFile(Feed.Item.ParD parD) throws FeederException {
                    String url = null;
                    if (Feed.Channel.isActTgtLink(chact))
                        url = parD.link;
                    else if (Feed.Channel.isActTgtEnclosure(chact))
                        url = parD.enclosureUrl;
                    else
                        eAssert(false);
                    File f = uip.getNewTempFile();
                    downloadToFile(url, uip.getNewTempFile(), f, null);
                    return f;
                }
            };
        }
        checkInterrupted();
        dbp.updateChannel(cid, parD.channel, newItems, idop);

        //logI("TIME: Updating Items : " + (System.currentTimeMillis() - time));
    }

    /**
     * Update given channel
     * @param cid
     * @throws FeederException
     */
    public void
    updateLoad(long cid)
            throws FeederException {
        update(cid, null);
    }

    /**
     * Update given channel.
     * @param cid
     * @param imageref
     *   URL of channel icon.
     * @throws FeederException
     */
    public void
    updateLoad(long cid, String imageref)
            throws FeederException {
        eAssert(null != imageref);
        update(cid, imageref);
    }

    /**
     * Download to memory(byte[]).
     * @param url
     * @param progressListener
     * @return
     * @throws FeederException
     */
    public byte[]
    downloadToRaw(String url, OnProgress progressListener)
            throws FeederException {
        // set as class private for future cleanup.
        ostream = new ByteArrayOutputStream();
        byte[] ret = null;
        try {
            download(ostream, url, null);
            ret = ((ByteArrayOutputStream )ostream).toByteArray();
        } finally {
            closeIstream();
            closeOstream();
        }
        return ret;
    }

    /**
     * Download data and make it as file.
     * @param url
     * @param tempFile
     *   Where downloaded data is written during download is in progress.
     *   This file is renamed to final target file when download is complete.
     * @param toFile
     *   Final target file path where downloaded data is stored at.
     * @param progressListener
     * @throws FeederException
     */
    public void
    downloadToFile(String url, File tempFile, File toFile, OnProgress progressListener)
        throws FeederException {
        // secure directory in which tempFile and toFile are located in.
        String parent = tempFile.getParent();
        new File(parent).mkdirs();
        parent = toFile.getParent();
        new File(parent).mkdirs();

        // Set as class private for future cleanup.
        tmpFile = tempFile;
        try {
            ostream = new FileOutputStream(tmpFile);
            download(ostream, url, progressListener);
            if (!tmpFile.renameTo(toFile))
                throw new FeederException(Err.IO_FILE);
        } catch (FileNotFoundException e) {
            throw new FeederException(Err.IO_FILE);
        } finally {
            closeIstream();
            closeOstream();
            clearTempFile(); // ignore failure
        }
    }

    /**
     * Cancel network loading (usually downloading.)
     * @return
     */
    public boolean
    cancel() {
        cancelled = true;
        // Kind of hack!
        // There is no fast-way to cancel running-java thread.
        // So, make input-stream closed by force to stop loading/DOM-parsing etc.
        cleanup();
        return true;
    }

    void finish() {
        // This is not needed... cleanup should be done before finish()...
        //cleanup();
    }

}
