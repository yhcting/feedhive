package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import android.database.Cursor;
import free.yhc.feeder.model.DB.ColumnFeedChannel;
import free.yhc.feeder.model.DB.ColumnFeedItem;

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

    private Feed.Item
    getDummyFeedItem() {
        Feed.Item item = new Feed.Item();
        item.title = "";
        item.state = Feed.Item.State.DUMMY;
        return item;
    }

    boolean
    isDuplicatedChannelUrl(String url)
            throws InterruptedException {
        boolean ret = false;
        lock();
        Cursor c = db.query(DB.TABLE_FEEDCHANNEL,
                            new ColumnFeedChannel[] {
                                ColumnFeedChannel.ID
                            },
                            ColumnFeedChannel.URL.getName() + " = '" + url + "'",
                            null, null, null, null);
        unlock();
        if (0 < c.getCount())
            ret = true;
        c.close();
        return ret;
    }

    boolean
    isDuplicatedItemTitle(long cid, String title)
            throws InterruptedException {
        boolean ret = false;
        lock();
        Cursor c = db.query(DB.getFeedItemTableName(cid),
                            new ColumnFeedItem[] {
                                ColumnFeedItem.ID
                            },
                            ColumnFeedItem.TITLE.getName() + " = '" + title + "'",
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
    String
    isDuplicatedItemTitleWithState(long cid, String title)
            throws InterruptedException {
        String ret = null;
        lock();
        Cursor c = db.query(DB.getFeedItemTableName(cid),
                            // Column index is used below. So order is important.
                            new ColumnFeedItem[] {
                                ColumnFeedItem.ID,
                                ColumnFeedItem.STATE,
                            },
                            ColumnFeedItem.TITLE.getName() + " = '" + title + "'",
                            null, null, null, null);
        unlock();
        if (c.moveToFirst())
            ret = c.getString(1); // '1' is state.
        c.close();
        return ret;
    }

    /*
     * Progress is hard-coded...
     * Any better way???
     *
     * @return 0 : successfully inserted and DB is changed.
     *        -1 : fails. Error!
     */
    public int
    insertFeedChannel(Feed.Channel ch)
            throws InterruptedException {
        // Apply insertion policy
        eAssert(null != ch.actionType
                && null != ch.lastupdate
                && null != ch.title
                && null != ch.items);

        long cid = -1;

        try { // Big block

            if (!UIPolicy.verifyConstraints(ch))
                return -1;

            logI("+++ Start insert channel----");

            lock();
            // insert and update channel id.
            cid = db.insertChannel(ch);
            if (cid < 0) {
                unlock();
                return -1;
            }

            // Add dummy item at the first of item-db
            // This is tightly coupled with implementation of
            //   'ItemListAdapter'
            // See 'ItemListAdapter' for detail reasons.
            if (0 > db.insertItem(cid, getDummyFeedItem())) {
                db.deleteChannel(cid);
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
            ch.id = cid;
            for (Feed.Item item : ch.items)
                item.channelid = cid;

            // logI("+++ End insert channel----");

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
    updateFeedItems(Feed.Channel ch)
            throws InterruptedException {
        eAssert(null != ch.items);

        lock();
        Cursor c = db.query(DB.getFeedItemTableName(ch.id),
                            // Column index is used below. So order is important.
                            new ColumnFeedItem[] {
                                DB.ColumnFeedItem.TITLE,
                                DB.ColumnFeedItem.LINK,
                                DB.ColumnFeedItem.ENCLOSURE_URL,
                                // runtime information of item.
                                DB.ColumnFeedItem.STATE,
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
            m.put(item.title, item);
        }

        // Delete unlisted item.
        if (c.moveToFirst()) {
            do {
                String title = c.getString(COLUMN_TITLE);
                Feed.Item item = m.get(title);
                if (null == item) {
                    // This is unlisted old-item.
                    new File(UIPolicy.getItemFilePath(ch.id, title, c.getString(COLUMN_LINK))).delete();
                    new File(UIPolicy.getItemFilePath(ch.id, title, c.getString(COLUMN_ENCLOSURE_URL))).delete();
                } else {
                    // copy runtime information stored in DB.
                    item.state = Feed.Item.State.convert(c.getString(COLUMN_STATE));
                }
            } while (c.moveToNext());
        }
        c.close();

        lock();
        db.prepareUpdateItemTable(ch.id);
        try {
            if (0 > db.insertItem(ch.id, getDummyFeedItem())) {
                db.rollbackUpdateItemTable(ch.id);
                unlock();
                return -1;
            }

            int cnt = 0;
            for (Feed.Item item : ch.items) {
                // ignore not-verified item
                if (!UIPolicy.verifyConstraints(item))
                    continue;

                if (0 > db.insertItem(ch.id, item)) {
                    db.rollbackUpdateItemTable(ch.id);
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
            db.completeUpdateItemTable(ch.id);
            unlock();
        } catch (InterruptedException e) {
            db.rollbackUpdateItemTable(ch.id);
            throw new InterruptedException();
        }

        return 0;
    }

    public Cursor
    queryChannel(ColumnFeedChannel[] columns,
                 String selection)
                         throws InterruptedException {
        lock();
        Cursor c = db.query(DB.TABLE_FEEDCHANNEL,
                            columns, selection,
                            null, null, null, null);
        unlock();
        return c;
    }

    public int
    deleteFeedChannel(long cid)
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

    public String
    getFeedChannelInfoString(long cid, ColumnFeedChannel column)
            throws InterruptedException {
        String ret = null;
        lock();
        Cursor c = db.query(DB.TABLE_FEEDCHANNEL,
                            // Column index is used below. So order is important.
                            new ColumnFeedChannel[] {
                                column
                            },
                            ColumnFeedChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        unlock();
        if (c.moveToFirst())
            ret = c.getString(0);

        c.close();
        return ret;
    }

    public String[]
    getFeedChannelInfoStrings(long cid, ColumnFeedChannel[] columns)
            throws InterruptedException {
        lock();
        Cursor c = db.query(DB.TABLE_FEEDCHANNEL,
                            columns,
                            ColumnFeedChannel.ID.getName() + " = '" + cid + "'",
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

    public String
    getFeedItemInfoString(long cid, long id, ColumnFeedItem column)
            throws InterruptedException {
        String ret = null;
        lock();
        Cursor c = db.query(DB.getFeedItemTableName(cid),
                            // Column index is used below. So order is important.
                            new ColumnFeedItem[] {
                                column
                            },
                            ColumnFeedItem.ID.getName() + " = '" + id + "'",
                            null, null, null, null);
        unlock();
        if (c.moveToFirst())
            ret = c.getString(0);

        c.close();
        return ret;
    }

    public String[]
    getFeedItemInfoStrings(long cid, long id, ColumnFeedItem[] columns)
            throws InterruptedException {
        lock();
        Cursor c = db.query(DB.getFeedItemTableName(cid),
                            columns,
                            ColumnFeedItem.ID.getName() + " = '" + id + "'",
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
              ColumnFeedItem[] columns,
              String selection)
                      throws InterruptedException {
        lock();
        Cursor c = db.query(DB.getFeedItemTableName(channelid),
                            columns, selection,
                            null, null, null, null);
        unlock();
        return c;
    }

    // return : old value
    public int
    setFeedItemInfo_state(long cid, long id, Feed.Item.State state)
            throws InterruptedException {
        lock();
        long n = db.updateItem_state(cid, id, state);
        unlock();
        eAssert(0 == n || 1 == n);
        return (0 == n)? -1: 0;

    }
}
