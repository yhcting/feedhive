package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import free.yhc.feeder.model.DB.ColumnCategory;
import free.yhc.feeder.model.DB.ColumnChannel;
import free.yhc.feeder.model.DB.ColumnItem;

//
// DB synchronizing concept.
// Each one SQLite operation is atomic.
// Using this property of SQLite, all locks are removed.
// (stress-testing is required to verify it.)
//
// For remaining cases for race-condition is blocked by UI.
// (for example, during update channel items, 'deleteChannel' menu is disabled.)
// So, in this module, checking race-condition by using 'eAssert' is enough for debugging!
//
// DEEP INVESTIGATION is required for RACE CONDITION WITHOUT LOCK!
//
//

// Singleton
public class DBPolicy {
    private static final String defaultSchedUpdateTime = "" + (3 * 3600); // 3 o'clock

    //private static Semaphore dbMutex = new Semaphore(1);
    private static DBPolicy instance = null;
    private DB          db       = null;
    private ChannRTMap  chrtmap  = new ChannRTMap();

    // Channel RunTime
    private class ChannRT {
        private          long       cid      = -1;
        private volatile boolean    stateUpdating = false;
        // pending update request

        ChannRT(long cid) {
            this.cid = cid;
        }

        boolean
        isUpdating() {
            return stateUpdating;
        }

        void
        setStateUpdating(boolean state) {
            stateUpdating = state;
        }
    }

    private class ChannRTMap extends HashMap<Long, ChannRT> {
        @Override
        public ChannRT
        put(Long key, ChannRT v) {
            synchronized (this) {
                return super.put(key, v);
            }
        }

        @Override
        public ChannRT
        get(Object key) {
            synchronized (this) {
                return super.get(key);
            }
        }

        @Override
        public ChannRT
        remove(Object key) {
            return super.remove(key);
        }

        ChannRTMap() {
            super();
        }

        void initialise() {
            Cursor c = db.queryChannel(ColumnChannel.ID);
            if (c.moveToFirst()) {
                do {
                    long cid = c.getLong(0);
                    put(cid, new ChannRT(cid));
                } while (c.moveToNext());
            }
            c.close();
        }
    }

    // ======================================================
    //
    // ======================================================
    private DBPolicy() {
        db = DB.db();
        chrtmap.initialise();
    }

    private void
    checkInterrupted() throws FeederException {
        if (Thread.currentThread().isInterrupted())
            throw new FeederException(Err.Interrupted);
    }

    // This is used only for new 'insertion'
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

        // This function is called for insert item.
        // So, update insert time value here.
        values.put(ColumnItem.INSTIME.getName(),             Calendar.getInstance().getTimeInMillis());

        return values;
    }

    // This is used only for new 'insertion'
    private ContentValues
    channelToContentValues(Feed.Channel ch) {
        ContentValues values = new ContentValues();
        // application's internal information
        values.put(ColumnChannel.URL.getName(),              ch.profD.url);
        values.put(ColumnChannel.ACTION.getName(),           ch.dynD.action.name());
        values.put(ColumnChannel.CATEGORYID.getName(),       ch.dbD.categoryid);
        values.put(ColumnChannel.LASTUPDATE.getName(),       ch.dbD.lastupdate);

        if (null != ch.dynD.imageblob)
            values.put(ColumnChannel.IMAGEBLOB.getName(),    ch.dynD.imageblob);

        // information defined by spec.
        values.put(ColumnChannel.TITLE.getName(),            ch.parD.title);
        values.put(ColumnChannel.DESCRIPTION.getName(),      ch.parD.description);

        // Fill reserved values as default
        // This need to match ChannelSettingActivity's setting value.
        values.put(ColumnChannel.SCHEDUPDATETIME.getName(),  defaultSchedUpdateTime); // default (03 o'clock)
        values.put(ColumnChannel.OLDLAST_ITEMID.getName(),   0);
        values.put(ColumnChannel.NRITEMS_SOFTMAX.getName(),  999999);
        // add to last position in terms of UI.
        values.put(ColumnChannel.POSITION.getName(),         getChannelInfoMaxLong(ColumnChannel.POSITION) + 1);
        return values;
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

    public boolean
    isDefaultCategoryId(long id) {
        return id == getDefaultCategoryId();
    }

    public boolean
    isDuplicatedCategoryName(String name) {
        boolean ret = false;
        Cursor c = db.queryCategory(ColumnCategory.NAME,
                                    ColumnCategory.NAME,
                                    name);
        if (0 < c.getCount())
            ret = true;
        c.close();
        return ret;
    }

    public boolean
    isDuplicatedChannelUrl(String url) {
        boolean ret = false;
        Cursor c = db.queryChannel(ColumnChannel.ID,
                                   ColumnChannel.URL,
                                   url);
        if (0 < c.getCount())
            ret = true;
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
        // change category of each channel to default firstly.
        // (removing category first leads to DB inconsistency.
        //  => channel has category id as one of it's foreign key.)
        long[] cids = getChannelIds(id);
        for (long cid : cids)
            updateChannel(cid, DB.ColumnChannel.CATEGORYID, DB.getDefaultCategoryId());
        return (1 == db.deleteCategory(id))? 0: -1;
    }

    public long
    updateCategory(long id, String name) {
        return db.updateCategory(id, name);
    }

    public Feed.Category[]
    getCategories() {
        // Column index is used below. So order is important.
        Cursor c = db.queryCategory(new ColumnCategory[] { ColumnCategory.ID, ColumnCategory.NAME, });

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

    // Insert new channel - url.
    // This is to insert channel url and holding place for this new channel at DB.
    //
    // @url    : url of new channel
    // @return : success : cid / fails : -1 (invalid cid)
    //
    public long
    insertNewChannel(long categoryid, String url) {
        long cid = -1;

        logI("InsertNewChannel DB Section Start");
        // insert and update channel id.

        // Create empty channel information.
        Feed.Channel ch = new Feed.Channel();
        ch.profD.url = url;
        ch.dbD.categoryid = categoryid;
        ch.dbD.lastupdate = new Date().getTime();
        cid = db.insertChannel(channelToContentValues(ch));
        if (cid < 0)
            return -1;

        if (!UIPolicy.makeChannelDir(cid)) {
            db.deleteChannel(cid);
            return -1;
        }
        chrtmap.put(cid, new ChannRT(cid));

        return cid;
    }

    //
    // Progress is hard-coded...
    // Any better way???
    //
    // return: -1 (for fail to update)
    //
    public int
    updateChannel(Feed.Channel ch, boolean updateImage)
            throws FeederException {
        eAssert(null != ch.items);
        logI("UpdateChannel DB Section Start");

        // walk items parsed with checking item is in DB or not
        chrtmap.get(ch.dbD.id).setStateUpdating(true);
        try {
            LinkedList<Feed.Item> newItems = new LinkedList<Feed.Item>();
            for (Feed.Item item : ch.items) {
                // ignore not-verified item
                if (!UIPolicy.verifyConstraints(item))
                    continue;

                // TODO
                //   Correct algorithm to check duplicated item.
                //     - store last item id(say LID) in last update to channel column.
                //     - if (there is duplicated item 'A') then
                //           if (A.id < LID)
                //               then duplicated
                //           else
                //               then this is not duplicated.
                //
                //   What this means?
                //   Even if title, description etc are same, this is newly updated item.
                //   So, this newly updated item should be listed as 'new item'.
                //
                //   But at this moment, I think this is over-engineering.
                //   So, below simple algorithm is used.

                // TODO
                //   Lots of items for WHERE clause may lead to...
                //     Pros : increase correctness.
                //     Cons : drop performance.
                //   So, I need to tune it.
                //   At this moment, correctness is more important than performance.
                Cursor c = db.queryItem(ch.dbD.id,
                                        new ColumnItem[] { ColumnItem.ID },
                                        new ColumnItem[] { ColumnItem.TITLE,
                                                           ColumnItem.PUBDATE,
                                                           ColumnItem.LINK,
                                                           //ColumnItem.DESCRIPTION,
                                                           ColumnItem.ENCLOSURE_URL },
                                        new String[] { item.parD.title,
                                                       item.parD.pubDate,
                                                       item.parD.link,
                                                       //item.parD.description,
                                                       item.parD.enclosureUrl });
                if (c.getCount() > 0) {
                    c.close();
                    continue; // duplicated
                }

                c.close();

                // NOTE
                //   Why add to First?
                //   Usually, recent item is located at top of item list in the feed.
                //   So, to make bottom item have smaller ID, 'addFirst' is used.
                newItems.addFirst(item);

                checkInterrupted();
            }

            // update channel information
            ContentValues channelUpdateValues = new ContentValues();
            channelUpdateValues.put(ColumnChannel.TITLE.getName(),       ch.parD.title);
            channelUpdateValues.put(ColumnChannel.DESCRIPTION.getName(), ch.parD.description);
            channelUpdateValues.put(ColumnChannel.ACTION.getName(),      ch.dynD.action.name());
            if (updateImage)
                channelUpdateValues.put(ColumnChannel.IMAGEBLOB.getName(), ch.dynD.imageblob);

            // NOTE
            //   Since here, rollback is not implemented!
            //   Why?
            //   'checkInterrupt()' is for 'fast-canceling'
            //   But, rollback itself should block 'canceling'.
            //   So, it's non-sense!
            Iterator<Feed.Item> iter = newItems.iterator();
            while (iter.hasNext()) {
                Feed.Item item = iter.next();

                item.dbD.cid = ch.dbD.id;
                if (0 > db.insertItem(ch.dbD.id, itemToContentValues(item))) {
                    chrtmap.get(ch.dbD.id).setStateUpdating(false);
                    return -1;
                }
                checkInterrupted();
            }
            logI("DBPolicy : new " + newItems.size() + " items are inserted");
            channelUpdateValues.put(ColumnChannel.LASTUPDATE.getName(), new Date().getTime());
            db.updateChannel(ch.dbD.id, channelUpdateValues);
            chrtmap.get(ch.dbD.id).setStateUpdating(false);
        } catch (FeederException e) {
            chrtmap.get(ch.dbD.id).setStateUpdating(false);
            throw e;
        }
        logI("UpdateChannel DB Section End");
        return 0;
    }

    public long
    updateChannel(long cid, ColumnChannel column, long value) {
        // Fields those are allowed to be updated.
        eAssert(ColumnChannel.CATEGORYID == column
                || ColumnChannel.OLDLAST_ITEMID == column);
        return db.updateChannel(cid, column, value);
    }

    public long
    updateChannel(long cid, ColumnChannel column, byte[] data) {
        eAssert(!chrtmap.get(cid).isUpdating());

        // Fields those are allowed to be updated.
        eAssert(ColumnChannel.IMAGEBLOB == column);
        return db.updateChannel(cid, ColumnChannel.IMAGEBLOB, data);
    }

    public long
    updatechannel_switchPosition(long cid0, long cid1) {
        Long pos0 = getChannelInfoLong(cid0, ColumnChannel.POSITION);
        Long pos1 = getChannelInfoLong(cid1, ColumnChannel.POSITION);
        if (null == pos0 || null == pos1)
            return 0;
        db.updateChannel(cid0, ColumnChannel.POSITION, pos1);
        db.updateChannel(cid1, ColumnChannel.POSITION, pos0);
        return 2;
    }

    public long
    updateChannel_schedUpdate(long cid, long sec) {
        return updateChannel_schedUpdate(cid, new long[] { sec });
    }

    public long
    updateChannel_schedUpdate(long cid, long[] secs) {
        // verify values SECONDS_OF_DAY
        for (long s : secs)
            eAssert(0 <= s && s <= 60 * 60 * 24);
        return db.updateChannel(cid, ColumnChannel.SCHEDUPDATETIME, Utils.nrsToNString(secs));
    }

    // "update OLDLAST_ITEMID to up-to-date"
    public long
    updateChannel_LastItemId(long cid) {
        long maxId = getItemInfoMaxId(cid);
        updateChannel(cid, ColumnChannel.OLDLAST_ITEMID, maxId);
        return maxId;
    }

    public Cursor
    queryChannel(long categoryid, ColumnChannel column) {
        return queryChannel(categoryid, new ColumnChannel[] { column });
    }

    public Cursor
    queryChannel(long categoryid, ColumnChannel[] columns) {
        eAssert(categoryid >= 0);
        return db.queryChannel(columns, ColumnChannel.CATEGORYID, categoryid);
    }

    public Cursor
    queryChannel(ColumnChannel column) {
        return db.queryChannel(column);
    }

    public Cursor
    queryChannel(ColumnChannel[] columns) {
        return db.queryChannel(columns);
    }

    public int
    deleteChannel(long cid) {
        eAssert(!chrtmap.get(cid).isUpdating());
        long n = db.deleteChannel(cid);
        eAssert(0 == n || 1 == n);
        if (1 == n) {
            UIPolicy.removeChannelDir(cid);
            chrtmap.remove(cid);
            return 0;
        } else {
            return -1;
        }
    }

    public long[]
    getChannelIds(long categoryid) {
        Cursor c = db.queryChannel(ColumnChannel.ID,
                                   ColumnChannel.CATEGORYID,
                                   categoryid);

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
    getChannelInfoLong(long cid, ColumnChannel column) {
        Long ret = null;
        Cursor c = db.queryChannel(cid, column);
        if (c.moveToFirst())
            ret = c.getLong(0);

        c.close();
        return ret;
    }

    public String
    getChannelInfoString(long cid, ColumnChannel column) {
        String ret = null;
        Cursor c = db.queryChannel(cid, column);
        if (c.moveToFirst())
            ret = c.getString(0);

        c.close();
        return ret;
    }

    public String[]
    getChannelInfoStrings(long cid, ColumnChannel[] columns) {
        Cursor c = db.queryChannel(cid, columns);
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

    public long
    getChannelInfoMaxLong(ColumnChannel column) {
        eAssert(column.getType().equals("integer"));
        Cursor c = db.queryChannelMax(column);
        if (!c.moveToFirst()) {
            c.close();
            return 0;
        }

        long max = c.getLong(0);
        c.close();
        return max;
    }

    public Bitmap
    getChannelImage(long cid) {
        Cursor c = db.queryChannel(cid, ColumnChannel.IMAGEBLOB);
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

    public long
    getItemInfoMaxId(long cid) {
        Cursor c = db.queryItem(cid, new ColumnItem[] { ColumnItem.ID }, 1);
        if (!c.moveToFirst())
            return 0; // there is no item!

        // Why?
        // Default order of item query is 'descending order by ID'.
        // So, this one is last item id.
        long lastId = c.getLong(0);
        c.close();
        return lastId;
    }

    public String
    getItemInfoString(long cid, long id, ColumnItem column) {
        String ret = null;
        Cursor c = db.queryItem(cid, id, column);
        if (c.moveToFirst())
            ret = c.getString(0);
        c.close();
        return ret;
    }

    public String[]
    getItemInfoStrings(long cid, long id, ColumnItem[] columns) {
        // Default is ASC order by ID
        Cursor c = db.queryItem(cid, id, columns);
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
    queryItem(long cid, ColumnItem[] columns) {
        return db.queryItem(cid, columns);
    }

    public int
    updateItem_state(long cid, long id, Feed.Item.State state) {
        ChannRT chrt = chrtmap.get(cid);
        if (null == chrt)
            return -1;

        // Update item during 'updating channel' is not expected!!
        // eAssert(!isUnderUpdating(cid));
        ContentValues values = new ContentValues();
        values.put(ColumnItem.STATE.getName(), state.name());
        long n = 0;
        Cursor c = db.queryItem(cid, id, ColumnItem.TITLE);
        if (c.moveToFirst()) {
            n = db.updateItem(cid, id, values);
        }
        eAssert(0 == n || 1 == n);
        return (0 == n)? -1: 0;

    }

    // delete downloaded files etc.
    public int
    cleanItemContents(long cid, long id) {
        eAssert(false); // Not implemented yet!
        return -1;
    }
}
