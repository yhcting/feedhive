package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import free.yhc.feeder.model.DB.ColumnCategory;
import free.yhc.feeder.model.DB.ColumnChannel;
import free.yhc.feeder.model.DB.ColumnItem;

// Singleton
public class DBPolicy {
    //private static Semaphore dbMutex = new Semaphore(1);
    private static DBPolicy instance = null;
    private Semaphore dbMutex = new Semaphore(1);
    private DB        db   = null;

    private DBPolicy() {
        db = DB.db();
    }

    public static DBPolicy
    get() {
        if (null == instance)
            instance = new DBPolicy();
        return instance;
    }

    public void
    lock() throws InterruptedException {
        dbMutex.acquire();
    }

    public void
    unlock() {
        dbMutex.release();
    }

    public boolean
    isDefaultCategoryId(long id) {
        return id == getDefaultCategoryId();
    }

    public boolean
    isDuplicatedCategoryName(String name)
            throws InterruptedException {
        boolean ret = false;
        lock();
        Cursor c = db.query(DB.TABLE_CATEGORY,
                            new ColumnCategory[] {
                                ColumnCategory.NAME
                            },
                            ColumnCategory.NAME.getName() + " = " + DatabaseUtils.sqlEscapeString(name),
                            null, null, null, null);
        unlock();
        if (0 < c.getCount())
            ret = true;
        c.close();
        return ret;
    }

    public boolean
    isDuplicatedChannelUrl(String url)
            throws InterruptedException {
        boolean ret = false;
        lock();
        Cursor c = db.query(DB.TABLE_CHANNEL,
                            new ColumnChannel[] {
                                ColumnChannel.ID
                            },
                            ColumnChannel.URL.getName() + " = " + DatabaseUtils.sqlEscapeString(url),
                            null, null, null, null);
        unlock();
        if (0 < c.getCount())
            ret = true;
        c.close();
        return ret;
    }

    // This function is dirty for performance.
    // (To reduce access)
    // return : null if it's not duplicated.
    public String
    isDuplicatedItemTitleWithState(long cid, String title)
            throws InterruptedException {
        String ret = null;
        lock();
        Cursor c = db.query(DB.getItemTableName(cid),
                            // Column index is used below. So order is important.
                            new ColumnItem[] {
                                ColumnItem.ID,
                                ColumnItem.STATE,
                            },
                            ColumnItem.TITLE.getName() + " = " + DatabaseUtils.sqlEscapeString(title),
                            null, null, null, null);
        unlock();
        if (c.moveToFirst())
            ret = c.getString(1); // '1' is state.
        c.close();
        return ret;
    }

    public long
    getDefaultCategoryId() {
        return DB.getDefaultCategoryId();
    }

    // return : 0 : successfully inserted and DB is changed.
    public int
    insertCategory(Feed.Category category) {
        eAssert(null != category.name);
        long id = db.insertCategory(category);
        if (0 > id)
            return -1;
        else {
            category.id = id;
            return 0;
        }
    }

    public int
    deleteCategory(long id) {
        return (0 < db.deleteCategory(id))? 0: -1;
    }

    public int
    deleteCategory(String name) {
        eAssert(null != name);
        return (0 < db.deleteCategory(name))? 0: -1;
    }

    public Feed.Category[]
    getCategories()
        throws InterruptedException {
        lock();
        Cursor c = db.query(DB.TABLE_CATEGORY,
                            // Column index is used below. So order is important.
                            new ColumnCategory[] {
                                ColumnCategory.ID,
                                ColumnCategory.NAME,
                            },
                            null, null, null, null, null);
        unlock();

        int i = 0;
        Feed.Category[] cats = new Feed.Category[c.getCount()];
        if (c.moveToFirst()) {
            do {
                cats[i] = new Feed.Category();
                cats[i].id = c.getLong(0);
                cats[i].name = c.getString(1);
                i++;
            } while(c.moveToNext());
        }
        c.close();

        return cats;
    }

    /*
     * Progress is hard-coded...
     * Any better way???
     *
     * @cid : out value (channel id of DB)
     * @ch  : constant
     * @return 0 : successfully inserted and DB is changed.
     *        -1 : fails. Error!
     */
    public int
    insertChannel(Feed.Channel ch)
            throws InterruptedException {
        long cid = -1;

        // update with current data.
        ch.dbD.lastupdate = DateUtils.getCurrentDateString();

        try { // Big block

            if (!UIPolicy.verifyConstraints(ch))
                return -1;

            lock();
            // insert and update channel id.
            cid = db.insertChannel(ch);
            if (cid < 0) {
                unlock();
                return -1;
            }

            if (!UIPolicy.makeChannelDir(cid)) {
                db.deleteChannel(cid);
                unlock();
                return -1;
            }
            unlock();

            int cnt = 0;
            lock();
            for (Feed.Item item : ch.items) {
                // ignore not-verified item
                if (!UIPolicy.verifyConstraints(item))
                    continue;

                if (0 > db.insertItem(cid, item)) {
                    // Fail to insert one of item => Rollback DB state.
                    db.deleteChannel(cid);
                    unlock();
                    return -1;
                }

                cnt++;
                // For performance reason, lock/unlock pair isn't used at every loop.
                if (0 == cnt % 10) {
                    unlock();
                    // give chance to be interrupted.
                    lock();
                }
                // logI("Inerting item----");
            }
            unlock();

            // All values in 'ch' should be keep untouched until operation is successfully completed.
            // So, setting values of 'ch' should be put here - at the end of successful operation.
            ch.dbD.id = cid;
            for (Feed.Item item : ch.items)
                item.dbD.cid = cid;

            return 0;

        } catch (InterruptedException e) {
            if (cid >= 0) {
                try {
                    lock();
                    db.deleteChannel(cid);
                    unlock();
                } catch (InterruptedException e2) {
                    eAssert(false);
                }
            }
            throw new InterruptedException();
        }
    }

    /*
     * Progress is hard-coded...
     * Any better way???
     *
     * return: -1 (for fail to update)
     * */
    public int
    updateChannel(Feed.Channel ch, boolean updateImage)
            throws InterruptedException {
        eAssert(null != ch.items);

        lock();
        Cursor c = db.query(DB.getItemTableName(ch.dbD.id),
                            // Column index is used below. So order is important.
                            new ColumnItem[] {
                                DB.ColumnItem.TITLE,
                                DB.ColumnItem.LINK,
                                DB.ColumnItem.ENCLOSURE_URL,
                                // runtime information of item.
                                DB.ColumnItem.STATE,
                            },
                            null, null, null, null, null);
        unlock();

        final int COLUMN_TITLE          = 0;
        final int COLUMN_LINK           = 1;
        final int COLUMN_ENCLOSURE_URL  = 2;
        final int COLUMN_STATE          = 3;

        // Create HashMap for title lookup!
        HashMap<String, Feed.Item> m = new HashMap<String, Feed.Item>();
        for (Feed.Item item : ch.items) {
            // ignore not-verified item
            if (!UIPolicy.verifyConstraints(item))
                continue;
            m.put(item.parD.title, item);
        }

        // Delete unlisted item.
        if (c.moveToFirst()) {
            do {
                String title = c.getString(COLUMN_TITLE);
                Feed.Item item = m.get(title);
                if (null == item) {
                    // This is unlisted old-item.
                    new File(UIPolicy.getItemFilePath(ch.dbD.id, title, c.getString(COLUMN_LINK))).delete();
                    new File(UIPolicy.getItemFilePath(ch.dbD.id, title, c.getString(COLUMN_ENCLOSURE_URL))).delete();
                } else {
                    // copy runtime information stored in DB.
                    item.dynD.state = Feed.Item.State.convert(c.getString(COLUMN_STATE));
                }
            } while (c.moveToNext());
        }
        c.close();

        lock();
        // update channel information
        db.updateChannel(ch.dbD.id,
                         new ColumnChannel[] {
                            ColumnChannel.TITLE,
                            ColumnChannel.DESCRIPTION },
                         new String[] {
                            ch.parD.title,
                            ch.parD.description });
        if (updateImage)
            db.updateChannel(ch.dbD.id, ColumnChannel.IMAGEBLOB, ch.dynD.imageblob);
        unlock();

        lock();
        db.prepareUpdateItemTable(ch.dbD.id);
        try {
            int cnt = 0;
            for (Feed.Item item : ch.items) {
                // ignore not-verified item
                if (!UIPolicy.verifyConstraints(item))
                    continue;

                if (0 > db.insertItem(ch.dbD.id, item)) {
                    db.rollbackUpdateItemTable(ch.dbD.id);
                    unlock();
                    return -1;
                }

                cnt++;
                // For performance reason, lock/unlock pair isn't used at every loop.
                if (0 == cnt % 10) {
                    unlock();
                    // give chance to be interrupted.
                    lock();
                }
            }
            // update lastupdate-time for this channel
            db.updateChannel(ch.dbD.id, ColumnChannel.LASTUPDATE, DateUtils.getCurrentDateString());
            db.completeUpdateItemTable(ch.dbD.id);
            unlock();
        } catch (InterruptedException e) {
            db.rollbackUpdateItemTable(ch.dbD.id);
            throw new InterruptedException();
        }

        return 0;
    }

    public long
    updateChannel_category(long cid, long categoryid)
            throws InterruptedException {
        try {
            return db.updateChannel(cid, ColumnChannel.CATEGORYID, categoryid);
        } catch (SQLException e) {
            // Error!
            eAssert(false);
            return -1;
        }
    }

    public long
    updateChannel_reverseOrder(long cid)
            throws InterruptedException {
        lock();
        Cursor c = db.query(DB.TABLE_CHANNEL,
                            new ColumnChannel[] { ColumnChannel.ORDER },
                            ColumnChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        Feed.Channel.Order order = null;
        if (c.moveToFirst())
            order = Feed.Channel.Order.convert(c.getString(0));
        else {
            eAssert(false);
            return -1;
        }
        c.close();

        // reverse
        order = (Feed.Channel.Order.NORMAL == order)?
                    Feed.Channel.Order.REVERSE: Feed.Channel.Order.NORMAL;

        db.updateChannel(cid, order);
        unlock();

        return 1; // number of rows affected.
    }


    public long
    updateChannel_categoryToDefault(long cid)
            throws InterruptedException {
        return updateChannel_category(cid, DB.getDefaultCategoryId());
    }

    public long
    updateChannel_image(long cid, byte[] data)
            throws InterruptedException {
        return db.updateChannel(cid, ColumnChannel.IMAGEBLOB, data);
    }

    // "categoryid < 0" measn all channel.
    public Cursor
    queryChannel(long categoryid, ColumnChannel[] columns)
                         throws InterruptedException {
        Cursor c;
        lock();
        if (0 > categoryid)
            c = db.query(DB.TABLE_CHANNEL,
                         columns, null,
                         null, null, null, null);
        else
            c = db.query(DB.TABLE_CHANNEL,
                    columns,
                    ColumnChannel.CATEGORYID.getName() + " = " + categoryid,
                    null, null, null, null);
        unlock();
        return c;
    }

    public int
    deleteChannel(long cid)
            throws InterruptedException {
        lock();
        long n = db.deleteChannel(cid);
        eAssert(0 == n || 1 == n);
        if (1 == n) {
            UIPolicy.removeChannelDir(cid);
            unlock();
            return 0;
        } else {
            unlock();
            return -1;
        }
    }

    public long[]
    getChannelIds(long categoryid)
            throws InterruptedException {
        lock();
        Cursor c = db.query(DB.TABLE_CHANNEL,
                            // Column index is used below. So order is important.
                            new ColumnChannel[] { ColumnChannel.ID },
                            ColumnChannel.CATEGORYID.getName() + " = '" + categoryid + "'",
                            null, null, null, null);
        unlock();

        long[] cids = new long[c.getCount()];
        if (c.moveToFirst()) {
            int i = 0;
            do {
                cids[i++] = c.getLong(0);
            } while (c.moveToNext());
        }
        c.close();

        return cids;
    }

    public Long
    getChannelInfoLong(long cid, ColumnChannel column)
            throws InterruptedException {
        Long ret = null;
        lock();
        Cursor c = db.query(DB.TABLE_CHANNEL,
                            // Column index is used below. So order is important.
                            new ColumnChannel[] {
                                column
                            },
                            ColumnChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        unlock();
        if (c.moveToFirst())
            ret = c.getLong(0);

        c.close();
        return ret;
    }

    public String
    getChannelInfoString(long cid, ColumnChannel column)
            throws InterruptedException {
        String ret = null;
        lock();
        Cursor c = db.query(DB.TABLE_CHANNEL,
                            // Column index is used below. So order is important.
                            new ColumnChannel[] {
                                column
                            },
                            ColumnChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        unlock();
        if (c.moveToFirst())
            ret = c.getString(0);

        c.close();
        return ret;
    }

    public String[]
    getChannelInfoStrings(long cid, ColumnChannel[] columns)
            throws InterruptedException {
        lock();
        Cursor c = db.query(DB.TABLE_CHANNEL,
                            columns,
                            ColumnChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        unlock();
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }
        eAssert(c.getColumnCount() == columns.length);
        String[] v = new String[columns.length];
        for (int i = 0; i < c.getColumnCount(); i++)
            v[i] = c.getString(i);

        c.close();
        return v;
    }

    public Bitmap
    getChannelImage(long cid)
            throws InterruptedException {
        lock();
        Cursor c = db.query(DB.TABLE_CHANNEL,
                            new ColumnChannel[] { ColumnChannel.IMAGEBLOB },
                            ColumnChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        unlock();
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }

        Bitmap bm = null;
        if (Cursor.FIELD_TYPE_NULL != c.getType(0)) {
            byte[] imgRaw= c.getBlob(0);
            bm = BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length);
        }

        c.close();

        return bm;
    }

    public String
    getItemInfoString(long cid, long id, ColumnItem column)
            throws InterruptedException {
        String ret = null;
        lock();
        Cursor c = db.query(DB.getItemTableName(cid),
                            // Column index is used below. So order is important.
                            new ColumnItem[] {
                                column
                            },
                            ColumnItem.ID.getName() + " = '" + id + "'",
                            null, null, null, null);
        unlock();
        if (c.moveToFirst())
            ret = c.getString(0);

        c.close();
        return ret;
    }

    public String[]
    getItemInfoStrings(long cid, long id, ColumnItem[] columns)
            throws InterruptedException {
        lock();
        // Default is ASC order by ID
        Cursor c = db.query(DB.getItemTableName(cid),
                            columns,
                            ColumnItem.ID.getName() + " = '" + id + "'",
                            null, null, null, null);
        unlock();
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }
        eAssert(c.getColumnCount() == columns.length);
        String[] v = new String[columns.length];
        for (int i = 0; i < c.getColumnCount(); i++)
            v[i] = c.getString(i);

        c.close();
        return v;
    }

    public Cursor
    queryItem(long channelid,
              ColumnItem[] columns)
                      throws InterruptedException {
        lock();
        Cursor c = db.query(DB.getItemTableName(channelid),
                            columns, null,
                            null, null, null,
                            null);
        unlock();
        return c;
    }

    public Cursor
    queryItem_reverse(long channelid,
                      ColumnItem[] columns)
                      throws InterruptedException {
        lock();
        // NOTE!!
        // < "'" + DB.ColumnItem.ID.getName() + "' DESC" > as 'orderby'
        //   doesn't work!
        // Column name SHOULD NOT be wrapped by quotation mark!
        Cursor c = db.query(DB.getItemTableName(channelid),
                            columns, null,
                            null, null, null,
                            DB.ColumnItem.ID.getName() + " DESC");
        unlock();
        return c;
    }


    // return : old value
    public int
    setItemInfo_state(long cid, long id, Feed.Item.State state)
            throws InterruptedException {
        lock();
        long n = db.updateItem(cid, id, state);
        unlock();
        eAssert(0 == n || 1 == n);
        return (0 == n)? -1: 0;

    }
}
