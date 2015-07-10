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

import java.io.File;
import java.util.LinkedList;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import free.yhc.feeder.R;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;


//
// Application Contents Manager
//
public class ContentsManager implements
UnexpectedExceptionHandler.TrackedModule {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Utils.Logger P = new Utils.Logger(ContentsManager.class);

    // Current Contents Version
    private static final int CONTENTS_VERSION = 1;

    // Contents are classified by item and data.
    private static final long FLAG_CHAN_DATA = 0x1;
    private static final long FLAG_ITEM_DATA = 0x2;

    private static ContentsManager sInstance = null;

    private final ListenerManager mLm = new ListenerManager();

    // Only for bug reporting.
    private final LinkedList<String> mWiredChannl = new LinkedList<>();

    @SuppressWarnings("unused")
    public interface OnContentsUpdatedListener {
        void onContentsUpdated(UpdateType type, Object arg0, Object arg1);
    }

    public enum UpdateType implements ListenerManager.Type {
        CHAN_DATA (FLAG_CHAN_DATA), // arg0 : channel id array
        ITEM_DATA (FLAG_ITEM_DATA); // arg0 : channel id array

        private final long _mFlag;

        UpdateType(long flag) {
            _mFlag = flag;
        }

        @Override
        public long
        flag() {
            return _mFlag;
        }
    }

    private ContentsManager() {
        int ver = Utils.getPrefContentVersion();
        if (ver < CONTENTS_VERSION)
            upgrade(ver, CONTENTS_VERSION);
    }

    public static ContentsManager
    get() {
        if (null == sInstance)
            sInstance = new ContentsManager();
        return sInstance;
    }


    // ========================================================================
    //
    // UPGRADE
    //
    // ========================================================================
    private static void
    upgradeTo1() {
        File rt = new File(Environ.get().getAppRootDirectoryPath());
        for (File f : rt.listFiles()) {
            if (f.isDirectory()
                && f.getName().matches("^[0-9]+$")) {
                // directory and it's name is 'number' => channel directory.
                // NumberFormatException is NOT expected here!
                long cid = Long.parseLong(f.getName());
                String newDirPath = getChannelDirPath(cid);
                if (null == newDirPath)
                    // This directory doens't have matching channel.
                    // This is garbage.
                    // Remove it!
                    Utils.removeFileRecursive(f, true);
                else
                    // rename to human readable format.
                    //noinspection ResultOfMethodCallIgnored
                    f.renameTo(new File(newDirPath));
            }
        }
    }

    private static void
    upgrade(int oldVersion, int newVersion) {
        int dbv = oldVersion;
        while (dbv < newVersion) {
            switch (dbv) {
            case 0:
                upgradeTo1();
                break;

            default:
                // SHOULD NOT reach here.
                Utils.eAssert(false);
            }
            dbv++;
        }

        // update contents version string to shared preference
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Environ.getAppContext());
        SharedPreferences.Editor prefEd = prefs.edit();
        prefEd.putInt(Utils.getResString(R.string.cscontent_version), CONTENTS_VERSION);
        prefEd.apply();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        StringBuilder bldr = new StringBuilder("[ ContentManager ]\n");
        synchronized (mWiredChannl) {
            for (String c : mWiredChannl)
                //noinspection StringConcatenationInsideStringBufferAppend
                bldr.append("  Wired Channel : " + c + "\n");
        }
        return bldr.toString();
    }

    // ========================================================================
    //
    // Update Listener Handling
    //
    // ========================================================================
    private void
    notifyUpdated(final UpdateType type, final Object arg0, final Object arg1) {
        mLm.notifyIndirect(type, arg0, arg1);
    }

    private void
    notifyUpdated(final UpdateType type, final Object arg0) {
        notifyUpdated(type, arg0, null);
    }

    @SuppressWarnings("unused")
    private void
    notifyUpdated(final UpdateType type) {
        notifyUpdated(type, null, null);
    }

    public void
    registerUpdatedListener(ListenerManager.Listener listener, long flag) {
        mLm.registerListener(listener, null, flag);
    }

    public void
    unregisterUpdatedListener(ListenerManager.Listener listener) {
        mLm.unregisterListener(listener);
    }


    // ========================================================================
    //
    // Contents(Directory/File) Management
    //
    // ========================================================================
    /**
     * ChannelDirLink is human-readable directory name.
     * This is for user to access to feed contents easily by using external tools.
     */
    private static String
    getChannelDirPath(long cid) {
        DBPolicy dbp = DBPolicy.get();
        String title = dbp.getChannelInfoString(cid, ColumnChannel.TITLE);
        if (!Utils.isValidValue(title))
            return null;
        String fname = Utils.convertToFilename(title) + "_" + cid;
        return Environ.get().getAppRootDirectoryPath() + fname + "/";
    }

    private static File
    getChannelDirFile(long cid) {
        String path = getChannelDirPath(cid);
        return (null == path)? null: new File(path);
    }

    /**
     * Create channel dir.
     * If @force == true and directory already exists, all files in it are deleted,
     *   and new empty directory will be generated.
     */
    public boolean
    makeChannelDir(long cid, boolean force) {
        File f = getChannelDirFile(cid);
        if (null == f)
            return false; // Channel information is NOT updated yet.
        if (f.exists() && force)
            Utils.removeFileRecursive(f, true);
        return f.mkdir();
    }

    /**
     * This deletes channel directory itself.
     */
    public boolean
    removeChannelDir(long cid) {
        File f = getChannelDirFile(cid);
        if (null == f)
            return false; // Channel information is NOT updated yet.
        boolean ret = Utils.removeFileRecursive(f, true);
        notifyUpdated(UpdateType.CHAN_DATA, new long[] { cid });
        return ret;
    }

    private boolean
    cleanChannelDir(long cid, boolean notify) {
        File f = getChannelDirFile(cid);
        // Channel is NOT successfully updated yet.
        // So, there is no channel directory.
        if (null == f)
            return true;
        boolean ret = Utils.removeFileRecursive(f, false);
        if (notify)
            notifyUpdated(UpdateType.CHAN_DATA, new long[] { cid });
        return ret;
    }

    public boolean
    cleanChannelDir(long cid) {
        return cleanChannelDir(cid, true);
    }

    public boolean
    cleanChannelDirs(long[] cids) {
        for (long cid : cids)
            cleanChannelDir(cid, false);
        notifyUpdated(UpdateType.CHAN_DATA, cids);
        return true;
    }

    public void
    cleanAllChannelDirs() {
        Cursor c = DBPolicy.get().queryChannel(ColumnChannel.ID);
        if (!c.moveToFirst()) {
            c.close();
            return;
        }

        long[] cids = new long[c.getCount()];
        int i = 0;
        do {
            cids[i++] = c.getLong(0);
        } while (c.moveToNext());
        c.close();
        cleanChannelDirs(cids);
    }

    public LinkedList<File>
    getContentFiles(long cids[]) {
        LinkedList<File> l = new LinkedList<>();
        for (long cid : cids)
            Utils.getFilesRecursive(l, getChannelDirFile(cid));
        return l;
    }

    public LinkedList<File>
    getContentFiles() {
        Cursor c = DBPolicy.get().queryChannel(ColumnChannel.ID);
        if (!c.moveToFirst()) {
            c.close();
            return new LinkedList<>(); // return empty list.
        }

        long[] cids = new long[c.getCount()];
        int i = 0;
        do {
            cids[i++] = c.getLong(0);
        } while (c.moveToNext());
        c.close();
        return getContentFiles(cids);
    }

    /**
     * Get file which contains data for given feed item.
     * Usually, this file is downloaded from internet.
     * (Ex. downloaded web page / downloaded mp3 etc)
     */
    public File
    getItemInfoDataFile(long id) {
        return getItemInfoDataFile(id, -1, null, null);
    }

    // NOTE
    // Why this parameter is given even if we can get from DB?
    // This is only for performance reason!
    // postfix : usually, extension;
    /**
     * NOTE
     * Why these parameters - title, url - are given even if we can get from DB?
     * This is only for performance reason!
     * @param id item id
     * @param cid channel id of this item. if '< 0', than value read from DB is used.
     * @param title title of this item. if '== null' or 'isEmpty()', than value read from DB is used.
     * @param url target url of this item. link or enclosure is possible.
     *            if '== null' or is 'isEmpty()', then value read from DB is used.
     */
    public File
    getItemInfoDataFile(long id, long cid, String title, String url) {
        DBPolicy dbp = DBPolicy.get();
        if (cid < 0)
            cid = dbp.getItemInfoLong(id, ColumnItem.CHANNELID);

        String chanDirPath = getChannelDirPath(cid);
        if (null == chanDirPath)
            return null;

        if (!Utils.isValidValue(title))
            title = dbp.getItemInfoString(id, ColumnItem.TITLE);

        if (!Utils.isValidValue(url)) {
            long action = dbp.getChannelInfoLong(cid, ColumnChannel.ACTION);
            String link = dbp.getItemInfoString(id, ColumnItem.LINK);
            String enclosure = dbp.getItemInfoString(id, ColumnItem.ENCLOSURE_URL);
            url = FeedPolicy.getDynamicActionTargetUrl(action, link, enclosure);
        }

        // we don't need to create valid filename with empty url value.
        if (!Utils.isValidValue(url)) {
            synchronized (mWiredChannl) {
                mWiredChannl.add(dbp.getChannelInfoString(cid, ColumnChannel.URL));
            }
            return null;
        }

        String ext = Utils.getExtentionFromUrl(url);

        // Title may include character that is not allowed as file name
        // (ex. '/')
        // Item is id is preserved even after update.
        // So, item ID can be used as file name to match item and file.
        String fname = Utils.convertToFilename(title) + "_" + id;
        int endIndex = Utils.MAX_FILENAME_LENGTH - ext.length() - 1; // '- 1' for '.'
        if (endIndex > fname.length())
            endIndex = fname.length();

        fname = fname.substring(0, endIndex);
        fname = fname + '.' + ext;


        // NOTE
        //   In most UNIX file systems, only '/' and 'null' are reserved.
        //   So, we don't worry about "converting string to valid file name".
        return new File(chanDirPath + fname);
    }

    public long
    getIdFromContentFileName(String fname) {
        // Item data file format
        // <channel dir>/<title string>_<id>.<ext>
        int idot = fname.lastIndexOf('.');
        int iubar = fname.lastIndexOf('_');
        try {
            String idstr = fname.substring(iubar + 1, idot);
            return Long.parseLong(idstr);
        } catch (Exception e) {
            // NumberFormatException, IndexOutOfBoundsException
            return -1;
        }
    }

    public boolean
    addItemContent(File f, long id) {
        File itemContentFile = ContentsManager.get().getItemInfoDataFile(id);
        if (f.getAbsolutePath().equals(itemContentFile.getAbsolutePath())
            || f.renameTo(ContentsManager.get().getItemInfoDataFile(id))) {
            notifyUpdated(UpdateType.ITEM_DATA, new long[] { id });
            return true;
        } else
            return false;
    }

    public int
    deleteItemContents(long ids[]) {
        int failcnt = 0;
        LinkedList<Long> l = new LinkedList<>();
        for (long id : ids) {
            File f = getItemInfoDataFile(id);
            Utils.eAssert(null != f);
            //noinspection ConstantConditions
            if (null == f
                || !f.delete())
                ++failcnt;
            else
                l.add(id);
        }
        // Item ids that fails to delete it's content, are ignored intentionally.
        notifyUpdated(UpdateType.ITEM_DATA,
                      Utils.convertArrayLongTolong(l.toArray(new Long[l.size()])));
        return failcnt;
    }

    public boolean
    deleteItemContent(long id) {
        // NOTE
        // There is no use case that "null == f" here.
        File f = getItemInfoDataFile(id);
        Utils.eAssert(null != f);
        if (f.delete()) {
            notifyUpdated(UpdateType.ITEM_DATA, new long[] { id });
            return true;
        }
        return false;
    }
}
