package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import android.database.Cursor;
import free.yhc.feeder.model.DB.ColumnRssChannel;
import free.yhc.feeder.model.DB.ColumnRssItem;

public class DBPolicy {
    private DB      db   = null;

    public DBPolicy() {
        db = DB.db();
    }

    private RSS.Item
    getDummyRSSItem() {
        RSS.Item item = new RSS.Item();
        item.title = "";
        item.state = RSS.ItemState.DUMMY;
        return item;
    }

    boolean
    isDuplicatedChannelUrl(String url) {
        boolean ret = false;
        Cursor c = db.query(DB.TABLE_RSSCHANNEL,
                            new ColumnRssChannel[] {
                                ColumnRssChannel.ID
                            },
                            ColumnRssChannel.URL.getName() + " = '" + url + "'",
                            null, null, null, null);
        if (0 < c.getCount())
            ret = true;
        c.close();
        return ret;
    }

    boolean
    isDuplicatedItemTitle(long cid, String title) {
        boolean ret = false;
        Cursor c = db.query(DB.getRssItemTableName(cid),
                            new ColumnRssItem[] {
                                ColumnRssItem.ID
                            },
                            ColumnRssItem.TITLE.getName() + " = '" + title + "'",
                            null, null, null, null);
        if (0 < c.getCount())
            ret = true;
        c.close();
        return ret;
    }

    // This function is dirty for performance.
    // (To reduce access)
    // return : null if it's not duplicated.
    String
    isDuplicatedItemTitleWithState(long cid, String title) {
        String ret = null;
        Cursor c = db.query(DB.getRssItemTableName(cid),
                            // Column index is used below. So order is important.
                            new ColumnRssItem[] {
                                ColumnRssItem.ID,
                                ColumnRssItem.STATE,
                            },
                            ColumnRssItem.TITLE.getName() + " = '" + title + "'",
                            null, null, null, null);
        if (c.moveToFirst())
            ret = c.getString(1); // '1' is state.
        c.close();
        return ret;
    }

    /**
     * @param rss
     * @return 0 : successfully inserted and DB is changed.
     *        -1 : fails. Error!
     */
    public int
    insertRSSChannel(RSS.Channel ch) {
        // Apply insertion policy
        eAssert(null != ch.actionType
                && null != ch.lastupdate
                && null != ch.title);

        if (!UIPolicy.verifyConstraints(ch))
            return -1;

        // insert and update channel id.
        long cid = db.insertChannel(ch);
        if (cid < 0)
            return -1;

        eAssert(null != ch.items);

        // Add dummy item at the first of item-db
        // This is tightly coupled with implementation of
        //   'ItemListAdapter'
        // See 'ItemListAdapter' for detail reasons.
        if (0 > db.insertItem(cid, getDummyRSSItem())) {
            db.deleteChannel(cid);
            return -1;
        }

        for (RSS.Item item : ch.items) {
            // ignore not-verified item
            if (!UIPolicy.verifyConstraints(item))
                continue;

            if (0 > db.insertItem(cid, item)) {
                // Fail to insert one of item => Rollback DB state.
                db.deleteChannel(cid);
                return -1;
            }
        }

        if (!UIPolicy.makeChannelDir(cid)) {
            db.deleteChannel(cid);
            return -1;
        }

        // All values in 'ch' should be keep untouched until operation is successfully completed.
        // So, setting values of 'ch' should be put here - at the end of successful operation.
        ch.id = cid;
        for (RSS.Item item : ch.items)
            item.channelid = cid;

        return 0;

    }

    // return: -1 (for fail to update)
    public int
    updateRSSItems(RSS.Channel ch) {
        eAssert(null != ch.items);

        Cursor c = db.query(DB.getRssItemTableName(ch.id),
                            // Column index is used below. So order is important.
                            new ColumnRssItem[] {
                                DB.ColumnRssItem.ID,
                                DB.ColumnRssItem.TITLE,
                            },
                            null, null, null, null, null);

        // Create HashMap for title lookup!
        Map m = new HashMap<String, Boolean>();
        for (RSS.Item item : ch.items) {
            // ignore not-verified item
            if (!UIPolicy.verifyConstraints(item))
                continue;
            m.put(item.title, true);
        }

        // Delete unlisted item.
        LinkedList<Long> il = new LinkedList<Long>();
        if (c.moveToFirst()) {
            do {
                if (null != m.get(c.getString(1))); // '1' is 'TITLE'
            } while (c.moveToNext());
        }
        db.cleanChannelItems(ch.id);

        for (RSS.Item item : ch.items) {
            // ignore not-verified item
            if (!UIPolicy.verifyConstraints(item))
                continue;


            if (0 > db.insertItem(ch.id, item))
                return -1;
        }
        return 0;
    }

    public Cursor
    queryChannel(ColumnRssChannel[] columns,
                 String selection) {
        return db.query(DB.TABLE_RSSCHANNEL,
                        columns, selection,
                        null, null, null, null);
    }

    public int
    deleteRSSChannel(long cid) {
        long n = db.deleteChannel(cid);
        eAssert(0 == n || 1 == n);
        if (1 == n) {
            UIPolicy.removeChannelDir(cid);
            return 0;
        } else
            return -1;
    }

    public String
    getRSSChannelInfoString(long cid, ColumnRssChannel column) {
        String ret = null;
        Cursor c = db.query(DB.TABLE_RSSCHANNEL,
                            // Column index is used below. So order is important.
                            new ColumnRssChannel[] {
                                column
                            },
                            ColumnRssChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        if (c.moveToFirst())
            ret = c.getString(0);

        c.close();
        return ret;
    }

    public String[]
    getRSSChannelInfoStrings(long cid, ColumnRssChannel[] columns) {
        Cursor c = db.query(DB.TABLE_RSSCHANNEL,
                            columns,
                            ColumnRssChannel.ID.getName() + " = '" + cid + "'",
                            null, null, null, null);
        if (!c.moveToFirst())
            return null;
        eAssert(c.getColumnCount() == columns.length);
        String[] v = new String[columns.length];
        for (int i = 0; i < c.getColumnCount(); i++)
            v[i] = c.getString(i);

        return v;
    }

    public String
    getRSSItemInfoString(long cid, long id, ColumnRssItem column) {
        String ret = null;
        Cursor c = db.query(DB.getRssItemTableName(cid),
                            // Column index is used below. So order is important.
                            new ColumnRssItem[] {
                                column
                            },
                            ColumnRssItem.ID.getName() + " = '" + id + "'",
                            null, null, null, null);
        if (c.moveToFirst())
            ret = c.getString(0);

        c.close();
        return ret;
    }

    public String[]
    getRSSItemInfoStrings(long cid, long id, ColumnRssItem[] columns) {
        Cursor c = db.query(DB.getRssItemTableName(cid),
                            columns,
                            ColumnRssItem.ID.getName() + " = '" + id + "'",
                            null, null, null, null);
        if (!c.moveToFirst())
            return null;
        eAssert(c.getColumnCount() == columns.length);
        String[] v = new String[columns.length];
        for (int i = 0; i < c.getColumnCount(); i++)
            v[i] = c.getString(i);

        return v;
    }

    public Cursor
    queryItem(long channelid,
              ColumnRssItem[] columns,
              String selection) {
        return db.query(DB.getRssItemTableName(channelid),
                        columns, selection,
                        null, null, null, null);
    }

    // return : old value
    public int
    setRSSItemInfo_state(long cid, long id, RSS.ItemState state) {
        long n = db.updateItem_state(cid, id, state);
        eAssert(0 == n || 1 == n);
        return (0 == n)? -1: 0;

    }
}
