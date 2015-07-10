/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

package free.yhc.feeder.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
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
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DBPolicy;

public class NetLoader {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(NetLoader.class);

    private static final int NET_RETRY = 3;
    private static final int NET_CONN_TIMEOUT = 5000;

    private final DBPolicy  mDbp = DBPolicy.get();

    private volatile boolean mCancelled = false;
    private volatile InputStream mIstream = null; // Multi-thread access
    private volatile OutputStream mOstream = null;
    private volatile File mTmpFile = null;

    interface OnProgress {
        // "progress < 0" means "Unknown progress"
        // In this case, negative value of bytes processed are passed as value of 'progress'
        void onProgress(NetLoader loader, long progress);
    }

    /**
     * Close network input stream of this loader - mIstream.
     * @throws FeederException
     */
    private void
    closeIstream() throws FeederException {
        try {
            if (null != mIstream) {
                mIstream.close();
                mIstream = null;
            }
        } catch (IOException e) {
            throw new FeederException (Err.IO_NET);
        }
    }

    /**
     * Close output stream - mOstream.
     * (Usually, output stream is file stream)
     * @throws FeederException
     */
    private void
    closeOstream() throws FeederException {
        try {
            if (null != mOstream) {
                mOstream.close();
                mOstream = null;
            }
        } catch (IOException e) {
            throw new FeederException (Err.IO_FILE);
        }
    }

    /**
     * Delete temp file - mTmpFile - used.
     */
    private boolean
    clearTempFile() {
        boolean ret = true;
        if (null != mTmpFile)
            ret = mTmpFile.delete();
        return ret;
    }

    /**
     * Close input/output stream (mIstream, mOstream) and
     *   delete temp file (mTmpFile)
     */
    private boolean
    cleanup() {
        try {
            closeIstream();
            closeOstream();
            if (null != mTmpFile)
                //noinspection ResultOfMethodCallIgnored
                mTmpFile.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void
    checkInterrupted() throws FeederException {
        if (mCancelled)
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
     * @throws FeederException
     */
    private void
    download(OutputStream outstream, String urlStr, OnProgress progressListener)
        throws FeederException {
        URL url;
        URLConnection conn = null;
        int retry = NET_RETRY;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new FeederException(Err.INVALID_URL);
        }

        // What happen if network state is changed
        //   in the middle of download or something like that?
        if (!Utils.isNetworkAvailable())
            throw new FeederException(Err.IO_NET);

        while (0 < retry--) {
           try {
               conn = url.openConnection();
               conn.setConnectTimeout(NET_CONN_TIMEOUT);
               conn.connect();
               break; // done
           } catch (Exception e) {
               // SocketTimeoutException
               // IOException
               if (mCancelled)
                   throw new FeederException(Err.USER_CANCELLED);

               if (0 >= retry)
                   throw new FeederException(Err.IO_NET);

               try {
                   Thread.sleep(500);
               } catch (InterruptedException ie) {
                   if (mCancelled)
                       throw new FeederException(Err.USER_CANCELLED);
                   else
                       throw new FeederException(Err.INTERRUPTED);
               }
           }
        }

        if (null == conn)
            throw new FeederException(Err.UNKNOWN);

        String locurl = conn.getHeaderField("Location");
        if (locurl != null
           && !urlStr.equals(locurl)) {
           // May be redirection
           download(outstream, locurl, progressListener);
           return;
        }

        try {
           int lengthOfFile = -1;
           retry = NET_RETRY;
           while (0 < retry--) {
               lengthOfFile = conn.getContentLength();
               if (lengthOfFile >= 0)
                   break;
           }
           mIstream = new BufferedInputStream(conn.getInputStream());

           if (Thread.currentThread().isInterrupted()) {
               cancel();
               throw new FeederException(Err.INTERRUPTED);
           }

           byte data[] = new byte[256*1024];

           long total = 0;
           int  count;
           long prevProgress = -1;
           while (true) {
               // Check network state as often as possible to confirm that
               //   network what user want to use is available.
               if (!Utils.isNetworkAvailable())
                   throw new FeederException(Err.IO_NET);

               if (-1 == (count = mIstream.read(data)))
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
           if (mCancelled)
               throw new FeederException(Err.USER_CANCELLED);
           else {
               e.printStackTrace();
               if (DBG) P.w(e.getMessage());
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

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        RSSParser.Result res = null;
        try {
            download(os, url, null);
            mIstream = new ByteArrayInputStream(os.toByteArray());
            Document dom = DocumentBuilderFactory
                               .newInstance()
                               .newDocumentBuilder()
                               .parse(mIstream);
            closeIstream();
            res = FeedParser.getParser(dom).parse(dom);
        } catch (MalformedURLException e) {
            throw new FeederException(Err.INVALID_URL);
        } catch (IOException e) {
            if (mCancelled)
                throw new FeederException(Err.USER_CANCELLED);
            else
                throw new FeederException(Err.IO_NET);
        } catch (DOMException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
            throw new FeederException(Err.PARSER_UNSUPPORTED_FORMAT);
        } finally {
            try {
                os.close();
            } catch (IOException ignored) { }
            closeIstream();
        }
        return res;
    }

    /**
     * Update feed information.
     * @param imageref if (null != imageref)
     *                 then 'imageref' is used instead of given by RSS channel infomation.
     * @throws FeederException
     */
    private void
    update(long cid, String imageref)
        throws FeederException {
        String url = mDbp.getChannelInfoString(cid, ColumnChannel.URL);
        Utils.eAssert(null != url);

        //logI("Loading Items: " + url);

        RSSParser.Result parD = parseFeedUrl(url);
        //logI("TIME: Loading + Parsing : " + (System.currentTimeMillis() - time));

        // set to given value forcely due to this is 'update' - Not new insertion.

        // decide action type.
        // Actually deciding only once is enough in general case.
        // But, rarely whole channel item type may be changed and requires different action.
        // So, whenever update is executed try to adjust 'action' type.
        long oldAction = mDbp.getChannelInfoLong(cid, ColumnChannel.ACTION);
        long action = FeedPolicy.decideActionType(oldAction,
                                                  parD.channel,
                                                  parD.items.length > 0? parD.items[0]: null);
        if (action != oldAction)
            mDbp.updateChannel(cid, ColumnChannel.ACTION, action);

        if (null == mDbp.getChannelImageBitmap(cid)) {
            // Kind Of Policy!!
            // Original image reference always has priority!
            byte[] bmdata = null;
            try {
                if (Utils.isValidValue(parD.channel.imageref))
                    bmdata = downloadToRaw(parD.channel.imageref, null);
            } catch (FeederException ignored) { }
            checkInterrupted();

            try {
                if (null == bmdata && Utils.isValidValue(imageref))
                    bmdata = downloadToRaw(imageref, null);
            } catch (FeederException ignored) { }
            checkInterrupted();

            if (null != bmdata) {
                Bitmap bm = Utils.decodeImage(bmdata,
                                              Feed.Channel.ICON_MAX_WIDTH,
                                              Feed.Channel.ICON_MAX_HEIGHT);
                if (null != bm) {
                    byte[] imageblob = Utils.compressBitmap(bm);
                    bm.recycle();
                    if (null != imageblob)
                        mDbp.updateChannel(cid, ColumnChannel.IMAGEBLOB, imageblob);
                }
            }
            //logI("TIME: Handle Image : " + (System.currentTimeMillis() - time));
            checkInterrupted();
        }

        LinkedList<Feed.Item.ParD> newItems = new LinkedList<>();
        mDbp.getNewItems(cid, parD.items, newItems);

        DBPolicy.ItemDataOpInterface idop = null;
        // NOTE
        // Information in "ch.dynD" is not available in case update.
        // ('imageblob' and 'action' is exception case controlled with argument.)
        // This is dynamically assigned variable.
        long updateMode = mDbp.getChannelInfoLong(cid, ColumnChannel.UPDATEMODE);
        if (Feed.Channel.isUpdDn(updateMode)) {
            final long chact = action;
            idop = new DBPolicy.ItemDataOpInterface() {
                @Override
                public File getFile(Feed.Item.ParD parD) throws FeederException {
                    String url = FeedPolicy.getDynamicActionTargetUrl(chact, parD.link, parD.enclosureUrl);
                    if (!Utils.isValidValue(url))
                        return null;

                    File f = Utils.getNewTempFile();
                    downloadToFile(url, Utils.getNewTempFile(), f, null);
                    return f;
                }
            };
        }
        checkInterrupted();
        mDbp.updateChannel(cid, parD.channel, newItems, idop);

        //logI("TIME: Updating Items : " + (System.currentTimeMillis() - time));
    }

    /**
     * Update given channel
     */
    public void
    updateLoad(long cid)
        throws FeederException {
        update(cid, null);
    }

    /**
     * Update given channel.
     * @param imageref URL of channel icon.
     * @throws FeederException
     */
    public void
    updateLoad(long cid, String imageref)
        throws FeederException {
        Utils.eAssert(null != imageref);
        update(cid, imageref);
    }

    /**
     * Download to memory(byte[]).
     * @throws FeederException
     */
    public byte[]
    downloadToRaw(String url,
                  @SuppressWarnings("unused") OnProgress progressListener)
        throws FeederException {
        // set as class private for future cleanup.
        mOstream = new ByteArrayOutputStream();
        byte[] ret = null;
        try {
            download(mOstream, url, null);
            ret = ((ByteArrayOutputStream )mOstream).toByteArray();
        } finally {
            closeIstream();
            closeOstream();
        }
        return ret;
    }

    /**
     * Download data and make it as file.
     * @param tempFile Where downloaded data is written during download is in progress.
     *                 This file is renamed to final target file when download is complete.
     * @param toFile Final target file path where downloaded data is stored at.
     * @throws FeederException
     */
    public void
    downloadToFile(String url, File tempFile, File toFile, OnProgress progressListener)
        throws FeederException {
        // secure directory in which tempFile and toFile are located in.
        String parent = tempFile.getParent();
        //noinspection ResultOfMethodCallIgnored
        new File(parent).mkdirs();
        parent = toFile.getParent();
        //noinspection ResultOfMethodCallIgnored
        new File(parent).mkdirs();

        // Set as class private for future cleanup.
        mTmpFile = tempFile;
        try {
            mOstream = new FileOutputStream(mTmpFile);
            download(mOstream, url, progressListener);
            if (!mTmpFile.renameTo(toFile))
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
     */
    public boolean
    cancel() {
        mCancelled = true;
        // Kind of hack!
        // There is no fast-way to cancel running-java thread.
        // So, make input-stream closed by force to stop loading/DOM-parsing etc.
        cleanup();
        return true;
    }

    @SuppressWarnings("unused")
    void finish() {
        // This is not needed... cleanup should be done before finish()...
        //cleanup();
    }

}
