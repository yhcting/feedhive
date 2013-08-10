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

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;


//
// Application Contents Manager
//
public class ContentsManager implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ContentsManager.class);

    // Current Contents Version
    private static final int CONTENTS_VERSION = 1;

    public static final String KEY_CONTENTS_VERSION = "contents_version";

    // Contents are classified by item and data.
    private static final long FLAG_ITEM_DATA  = 0x1;
    private static final long FLAG_CHAN_DATA  = 0x10;
    private static final long FLAG_CHANS_DATA = 0x20;

    private static ContentsManager sInstance = null;

    private final ListenerManager mLm = new ListenerManager();

    // Only for bug reporting.
    private final LinkedList<String>  mWiredChannl = new LinkedList<String>();

    public interface OnContentsUpdatedListener {
        void onContentsUpdated(UpdateType type, Object arg0, Object arg1);
    }

    public enum UpdateType implements ListenerManager.Type {
        ITEM_DATA  (FLAG_ITEM_DATA), // arg0 : item id
        CHAN_DATA  (FLAG_CHAN_DATA), // arg0 : channel id
        CHANS_DATA (FLAG_CHANS_DATA);// arg0 : channel id array
        private final long _mFlag;

        private UpdateType(long flag) {
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
        prefEd.putInt(KEY_CONTENTS_VERSION, CONTENTS_VERSION);
        prefEd.apply();
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        StringBuilder bldr = new StringBuilder("[ ContentManager ]\n");
        synchronized (mWiredChannl) {
            Iterator<String> itr = mWiredChannl.iterator();
            while (itr.hasNext())
                bldr.append("  Wired Channel : " + itr.next() + "\n");
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
     * @param cid
     * @return
     */
    private static String
    getChannelDirPath(long cid) {
        DBPolicy dbp = DBPolicy.get();
        String title = dbp.getChannelInfoString(cid, ColumnChannel.TITLE);
        if (null == title)
            return null;
        String fname = Utils.convertToFilename(title) + "_" + cid;
        return Environ.get().getAppRootDirectoryPath() + fname;
    }

    private static File
    getChannelDirFile(long cid) {
        return new File(getChannelDirPath(cid));
    }
    /**
     * Create clean channel dir.
     * If directory already exists, all files in it are deleted.
     * @param cid
     * @return
     */
    public boolean
    makeChannelDir(long cid) {
        File f = getChannelDirFile(cid);
        if (f.exists())
            Utils.removeFileRecursive(f, true);
        return f.mkdir();
    }

    /**
     * This deletes channel directory itself.
     * @param cid
     * @return
     */
    public boolean
    removeChannelDir(long cid) {
        boolean ret = Utils.removeFileRecursive(getChannelDirFile(cid), true);
        notifyUpdated(UpdateType.CHAN_DATA, cid);
        return ret;
    }

    public boolean
    cleanChannelDir(long cid) {
        boolean ret = Utils.removeFileRecursive(getChannelDirFile(cid), false);
        notifyUpdated(UpdateType.CHAN_DATA, cid);
        return ret;
    }

    public boolean
    cleanChannelDirs(long[] cids) {
        boolean ret = true;
        for (long cid : cids) {
            if (!Utils.removeFileRecursive(getChannelDirFile(cid), false)) {
                ret = false;
                break;
            }
        }
        notifyUpdated(UpdateType.CHANS_DATA, cids);
        return ret;
    }

    /**
     * Get file which contains data for given feed item.
     * Usually, this file is downloaded from internet.
     * (Ex. downloaded web page / downloaded mp3 etc)
     * @param id
     * @return
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
     * @param id
     *   item id
     * @param cid
     *   channel id of this item. if '< 0', than value read from DB is used.
     * @param title
     *   title of this item. if '== null' or 'isEmpty()', than value read from DB is used.
     * @param url
     *   target url of this item. link or enclosure is possible.
     *   if '== null' or is 'isEmpty()', then value read from DB is used.
     * @return
     */
    public File
    getItemInfoDataFile(long id, long cid, String title, String url) {
        DBPolicy dbp = DBPolicy.get();
        if (cid < 0)
            cid = dbp.getItemInfoLong(id, ColumnItem.CHANNELID);

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
        return new File(getChannelDirPath(cid) + fname);
    }

    public boolean
    addItemContent(File f, long id) {
        File itemContentFile = ContentsManager.get().getItemInfoDataFile(id);
        if (f.getAbsolutePath().equals(itemContentFile.getAbsolutePath())
            || f.renameTo(ContentsManager.get().getItemInfoDataFile(id))) {
            notifyUpdated(UpdateType.ITEM_DATA, id);
            return true;
        } else
            return false;
    }

    public boolean
    deleteItemContent(long id) {
        // NOTE
        // There is no use case that "null == f" here.
        File f = getItemInfoDataFile(id);
        eAssert(null != f);
        boolean ret = f.delete();
        if (ret)
            notifyUpdated(UpdateType.ITEM_DATA, id);
        return ret;
    }
}
