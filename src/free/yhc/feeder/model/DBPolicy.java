package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.Semaphore;

import android.content.ContentValues;
import android.database.Cursor;
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

    private HashMap<Long, LinkedList<ItemUpdateRequest>> updatingChannels
                                            = new HashMap<Long, LinkedList<ItemUpdateRequest>>();

    private class ItemUpdateRequest {
        long            id;
        String          title;
        ContentValues   cvs;
        ItemUpdateRequest(long id, String title, ContentValues cvs) {
            this.id = id;
            this.title = title;
            this.cvs = cvs;
        }
    }


    private DBPolicy() {
        db = DB.db();
    }


    // ======================================================
    //
    // ======================================================
    private ContentValues
    itemToContentValues(Feed.Item item) {
        ContentValues values = new ContentValues();

        // information defined by spec.
        values.put(ColumnItem.CHANNELID.getName(),           item.dbD.cid);
        values.put(ColumnItem.TITLE.getName(),               item.parD.title);
        values.put(ColumnItem.LINK.getName(),                item.parD.link);
        values.put(ColumnItem.DESCRIPTION.getName(),         item.parD.description);
        values.put(ColumnItem.PUBDATE.getName(),             item.parD.pubDate);
        values.put(ColumnItem.ENCLOSURE_URL.getName(),       item.parD.enclosureUrl);
        values.put(ColumnItem.ENCLOSURE_LENGTH.getName(),    item.parD.enclosureLength);
        values.put(ColumnItem.ENCLOSURE_TYPE.getName(),      item.parD.enclosureType);
        values.put(ColumnItem.STATE.getName(),               item.dynD.state.name());

        return values;
    }

    private ContentValues
    channelToContentValues(Feed.Channel ch) {
        ContentValues values = new ContentValues();
        // application's internal information
        values.put(ColumnChannel.URL.getName(),              ch.profD.url);
        values.put(ColumnChannel.ACTION.getName(),           ch.dynD.action.name());
        values.put(ColumnChannel.ORDER.getName(),            ch.dynD.order.name());
        values.put(ColumnChannel.CATEGORYID.getName(),       ch.dbD.categoryid);
        values.put(ColumnChannel.LASTUPDATE.getName(),       ch.dbD.lastupdate);

        // temporal : this column is for future use.
        values.put(ColumnChannel.UNOPENEDCOUNT.getName(),    0);

        if (null != ch.dynD.imageblob)
            values.put(ColumnChannel.IMAGEBLOB.getName(),    ch.dynD.imageblob);

        // information defined by spec.
        values.put(ColumnChannel.TITLE.getName(),            ch.parD.title);
        values.put(ColumnChannel.DESCRIPTION.getName(),      ch.parD.description);
        return values;
    }


    private boolean
    isUnderUpdating(long cid) {
        return (null != updatingChannels.get(cid));
    }


    private long
    prepareUpdateItemTable(long cid) {
        // to make sure.
        // This operation may return error.
        db.dropTempItemTable(cid);

        updatingChannels.put(cid, new LinkedList<ItemUpdateRequest>());
        if (0 > db.createTempItemTable(cid)) {
            updatingChannels.remove(cid);
            return -1;
        }
        return 0;
    }

    private long
    completeUpdateItemTable(long cid) {
        // process delayed-updated-item
        LinkedList<ItemUpdateRequest> ll = updatingChannels.get(cid);
        if (null != ll) {
            ListIterator<ItemUpdateRequest> iter = ll.listIterator();
            while (iter.hasNext()) {
                ItemUpdateRequest uItem = iter.next();
                if (1 != db.updateItemToTemp(cid, uItem.title, 0, uItem.cvs));
                    logW("Fail to delayed update : cid(" + cid + ") title(" + uItem.title + ")");
            }
        }

        db.dropItemTable(cid);
        db.alterItemTable_toMain(cid);
        updatingChannels.remove(cid);
        return 0;
    }

    private long
    rollbackUpdateItemTable(long cid) {
        db.dropTempItemTable(cid);
        updatingChannels.remove(cid);
        return 0;
    }

    // ======================================================
    //
    // ======================================================

    // S : Singleton instance
    public static DBPolicy
    S() {
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
        Cursor c = db.queryCategory(ColumnCategory.NAME,
                                    ColumnCategory.NAME,
                                    name);
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
        Cursor c = db.queryChannel(ColumnChannel.ID,
                                   ColumnChannel.URL,
                                   url);
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
        Cursor c = db.queryItem(cid,
                                // Column index is used below. So order is important.
                                new ColumnItem[] { ColumnItem.ID, ColumnItem.STATE, },
                                ColumnItem.TITLE,
                                title);
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
        // Column index is used below. So order is important.
        Cursor c = db.queryCategory(new ColumnCategory[] { ColumnCategory.ID, ColumnCategory.NAME, });
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

    //
    // Progress is hard-coded...
    // Any better way???
    //
    // @cid : out value (channel id of DB)
    // @ch  : constant
    // @return 0 : successfully inserted and DB is changed.
    //        -1 : fails. Error!
    //
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
            logI("InsertChannel DB Section Start");
            // insert and update channel id.
            cid = db.insertChannel(channelToContentValues(ch));
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

                item.dbD.cid = cid;
                if (0 > db.insertItem(cid, itemToContentValues(item))) {
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
                // logI("Inserting item----");
            }
            logI("InsertChannel DB Section End (Success)");
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

    //
    // Progress is hard-coded...
    // Any better way???
    //
    // return: -1 (for fail to update)
    //
    public int
    updateChannel(Feed.Channel ch, boolean updateImage)
            throws InterruptedException {
        eAssert(null != ch.items);

        logI("UpdateChannel DB Section Start");
        lock();
        Cursor c = db.queryItem(ch.dbD.id,
                                // Column index is used below. So order is important.
                                new ColumnItem[] {
                                    DB.ColumnItem.TITLE,
                                    DB.ColumnItem.LINK,
                                    DB.ColumnItem.ENCLOSURE_URL,
                                    // runtime information of item.
                                    DB.ColumnItem.STATE,
                                    DB.ColumnItem.ID,
                                });
        unlock();

        final int COLUMN_TITLE          = 0;
        final int COLUMN_LINK           = 1;
        final int COLUMN_ENCLOSURE_URL  = 2;
        final int COLUMN_STATE          = 3;
        final int COLUMN_ID             = 4;

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
                    new File(UIPolicy.getItemFilePath(ch.dbD.id,
                                                      c.getLong(COLUMN_ID),
                                                      title,
                                                      c.getString(COLUMN_LINK)))
                        .delete();
                    new File(UIPolicy.getItemFilePath(ch.dbD.id,
                                                      c.getLong(COLUMN_ID),
                                                      title,
                                                      c.getString(COLUMN_ENCLOSURE_URL)))
                        .delete();
                } else {
                    // copy runtime information stored in DB.
                    item.dynD.state = Feed.Item.State.convert(c.getString(COLUMN_STATE));
                }
            } while (c.moveToNext());
        }
        c.close();

        lock();
        // update channel information
        ContentValues cvs = new ContentValues();
        cvs.put(ColumnChannel.TITLE.getName(), ch.parD.title);
        cvs.put(ColumnChannel.DESCRIPTION.getName(), ch.parD.description);
        if (updateImage)
            cvs.put(ColumnChannel.IMAGEBLOB.getName(), ch.dynD.imageblob);
        db.updateChannel(ch.dbD.id, cvs);
        unlock();

        lock();
        prepareUpdateItemTable(ch.dbD.id);
        try {
            int cnt = 0;
            for (Feed.Item item : ch.items) {
                // ignore not-verified item
                if (!UIPolicy.verifyConstraints(item))
                    continue;

                item.dbD.cid = ch.dbD.id;
                if (0 > db.insertItemToTemp(ch.dbD.id, itemToContentValues(item))) {
                    rollbackUpdateItemTable(ch.dbD.id);
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
            completeUpdateItemTable(ch.dbD.id);
            // update lastupdate-time for this channel
            db.updateChannel(ch.dbD.id, ColumnChannel.LASTUPDATE, DateUtils.getCurrentDateString());
            unlock();
        } catch (InterruptedException e) {
            rollbackUpdateItemTable(ch.dbD.id);
            throw new InterruptedException();
        }
        logI("UpdateChannel DB Section End");
        return 0;
    }

    public long
    updateChannel_category(long cid, long categoryid)
            throws InterruptedException {
        eAssert(!isUnderUpdating(cid));
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
        Cursor c = db.queryChannel(cid, ColumnChannel.ORDER);
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
        db.updateChannel(cid, ColumnChannel.ORDER, order.name());
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
        eAssert(!isUnderUpdating(cid));
        return db.updateChannel(cid, ColumnChannel.IMAGEBLOB, data);
    }

    // "categoryid < 0" measn all channel.
    public Cursor
    queryChannel(long categoryid, ColumnChannel[] columns)
                         throws InterruptedException {
        Cursor c;
        lock();
        if (0 > categoryid)
            c = db.queryChannel(columns);
        else
            c = db.queryChannel(columns, ColumnChannel.CATEGORYID, categoryid);
        unlock();
        return c;
    }

    public int
    deleteChannel(long cid)
            throws InterruptedException {
        eAssert(!isUnderUpdating(cid));
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
        Cursor c = db.queryChannel(ColumnChannel.ID,
                                   ColumnChannel.CATEGORYID,
                                   categoryid);
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
        // Column index is used below. So order is important.
        Cursor c = db.queryChannel(cid, column);
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
        Cursor c = db.queryChannel(cid, column);
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
        Cursor c = db.queryChannel(cid, columns);
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
        Cursor c = db.queryChannel(cid, ColumnChannel.IMAGEBLOB);
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
        Cursor c = db.queryItem(cid, id, column);
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
        Cursor c = db.queryItem(cid, id, columns);
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
    queryItem(long cid, ColumnItem[] columns)
            throws InterruptedException {
        lock();
        Cursor c = db.queryItem(cid, columns);
        unlock();
        return c;
    }

    public Cursor
    queryItem_reverse(long cid,
                      ColumnItem[] columns)
                      throws InterruptedException {
        lock();
        // NOTE!!
        // < "'" + DB.ColumnItem.ID.getName() + "' DESC" > as 'orderby'
        //   doesn't work!
        // Column name SHOULD NOT be wrapped by quotation mark!
        /*
        Cursor c = db.query(DB.getItemTableName(cid),
                            columns, null,
                            null, null, null,
                            DB.ColumnItem.ID.getName() + " DESC");
        */
        // TODO : reverse doesn't deprecated....
        Cursor c = queryItem(cid, columns);
        unlock();
        return c;
    }


    // return : old value
    public int
    updateItem_state(long cid, long id, Feed.Item.State state)
            throws InterruptedException {
        lock();
        // Update item during 'updating channel' is not expected!!
        // eAssert(!isUnderUpdating(cid));
        ContentValues values = new ContentValues();
        values.put(ColumnItem.STATE.getName(), state.name());
        if (isUnderUpdating(cid)) {
            LinkedList<ItemUpdateRequest> ll = updatingChannels.get(cid);
            eAssert(null != ll);
            Cursor c = db.queryItem(cid, id, ColumnItem.TITLE);
            if (c.moveToFirst()) {
                ll.addLast(new ItemUpdateRequest(id, c.getString(0), values));
                c.close();
                logW("update item is delayed- DB is under update now.");
            } else
                c.close();
        }
        long n = db.updateItem(cid, id, values);
        unlock();
        eAssert(0 == n || 1 == n);
        return (0 == n)? -1: 0;

    }
}
