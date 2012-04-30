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
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.ContentValues;
import android.database.Cursor;
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
public class DBPolicy implements
UnexpectedExceptionHandler.TrackedModule {
    private static final Object dummyObject = new Object(); // static dummy object;

    //private static Semaphore dbMutex = new Semaphore(1);
    private static DBPolicy instance = null;
    private DB              db       = null;

    // I'm not sure the reason. But getting max id of item takes much longer time than expected.
    // So, cache is used for it!
    private MCache<Long>    cacheItemMaxId = new MCache<Long>("ItemMaxId");

    enum ItemDataType {
        RAW,
        FILE
    }

    interface ItemDataOpInterface {
        File   getFile(Feed.Item.ParD parD) throws FeederException;
    }

    // ======================================================
    //
    // ======================================================
    private DBPolicy() {
        db = DB.db();
    }

    /**
     * check that current Thread is interrupted.
     * If it is interrupted, FeederException is thrown.
     * @throws FeederException
     */
    private void
    checkInterrupted() throws FeederException {
        if (Thread.currentThread().isInterrupted())
            throw new FeederException(Err.Interrupted);
    }

    // This is used only for new 'insertion'
    /**
     * Build ContentValues for DB insertion with some default values.
     * @param parD
     * @param dbD
     * @return
     */
    private ContentValues
    buildNewItemContentValues(Feed.Item.ParD parD, Feed.Item.DbD dbD) {
        ContentValues values = new ContentValues();

        // information defined by spec.
        values.put(ColumnItem.CHANNELID.getName(),           dbD.cid);
        values.put(ColumnItem.TITLE.getName(),               parD.title);
        values.put(ColumnItem.LINK.getName(),                parD.link);
        values.put(ColumnItem.DESCRIPTION.getName(),         parD.description);
        values.put(ColumnItem.PUBDATE.getName(),             parD.pubDate);
        values.put(ColumnItem.ENCLOSURE_URL.getName(),       parD.enclosureUrl);
        values.put(ColumnItem.ENCLOSURE_LENGTH.getName(),    parD.enclosureLength);
        values.put(ColumnItem.ENCLOSURE_TYPE.getName(),      parD.enclosureType);
        values.put(ColumnItem.STATE.getName(),               Feed.Item.FStatNew);

        // If success to parse pubdate than pubdate is used, if not, current time is used.
        long time = Utils.dateStringToTime(parD.pubDate);
        if (time < 0)
            time = Calendar.getInstance().getTimeInMillis();
        values.put(ColumnItem.PUBTIME.getName(),             time);

        return values;
    }

    // This is used only for new 'insertion'
    /**
     * Build ContentValues for DB insertion with some default values.
     * @param profD
     * @param parD
     * @param dbD
     * @return
     */
    private ContentValues
    buildNewChannelContentValues(Feed.Channel.ProfD profD, Feed.Channel.ParD parD, Feed.Channel.DbD dbD) {
        ContentValues values = new ContentValues();
        // application's internal information
        values.put(ColumnChannel.URL.getName(),              profD.url);
        values.put(ColumnChannel.ACTION.getName(),           Feed.FInvalid);
        values.put(ColumnChannel.UPDATEMODE.getName(),       Feed.Channel.FUpdDefault);
        values.put(ColumnChannel.STATE.getName(),            Feed.Channel.FStatDefault);
        values.put(ColumnChannel.CATEGORYID.getName(),       dbD.categoryid);
        values.put(ColumnChannel.LASTUPDATE.getName(),       dbD.lastupdate);

        // information defined by spec.
        values.put(ColumnChannel.TITLE.getName(),            parD.title);
        values.put(ColumnChannel.DESCRIPTION.getName(),      parD.description);

        values.put(ColumnChannel.IMAGEBLOB.getName(),        new byte[0]);
        // Fill reserved values as default
        // This need to match ChannelSettingActivity's setting value.
        values.put(ColumnChannel.SCHEDUPDATETIME.getName(),  Feed.Channel.defaultSchedUpdateTime); // default (03 o'clock)
        values.put(ColumnChannel.OLDLAST_ITEMID.getName(),   0);
        values.put(ColumnChannel.NRITEMS_SOFTMAX.getName(),  999999);
        // add to last position in terms of UI.
        values.put(ColumnChannel.POSITION.getName(),         getChannelInfoMaxLong(ColumnChannel.POSITION) + 1);
        return values;
    }

    /**
     * FIELD_TYPE BLOB is not supported.
     * @param c
     * @param columnIndex
     *   column index of given cursor.
     * @return
     */
    private Object
    getCursorValue(Cursor c, int columnIndex) {
        switch (c.getType(columnIndex)) {
        case Cursor.FIELD_TYPE_NULL:
            return null;
        case Cursor.FIELD_TYPE_FLOAT:
            return c.getDouble(columnIndex);
        case Cursor.FIELD_TYPE_INTEGER:
            return c.getLong(columnIndex);
        case Cursor.FIELD_TYPE_STRING:
            return c.getString(columnIndex);
        case Cursor.FIELD_TYPE_BLOB:
        }
        eAssert(false);
        return null;
    }

    /**
     * Find channel
     * @param state
     *   [out] result read from DB is stored here.
     * @param url
     * @return
     *   -1 (fail to find) / channel id (success)
     */
    private long
    findChannel(long[] state, String url) {
        long ret = -1;
        Cursor c = db.queryChannel(new ColumnChannel[] { ColumnChannel.ID,
                                                         ColumnChannel.URL,
                                                         ColumnChannel.STATE},
                                   null, dummyObject, null, false, 0);
        if (!c.moveToFirst()) {
            c.close();
            return -1;
        }
        do {
            if (url.equals(c.getString(1))) {
                if (null != state)
                    state[0] = c.getLong(2);
                ret = c.getLong(0);
                break;
            }
        } while(c.moveToNext());

        c.close();
        return ret;
    }

    // ======================================================
    //
    // ======================================================
    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ DBPolicy ]";
    }

    // S : Singleton instance
    public static DBPolicy
    S() {
        if (null == instance) {
            instance = new DBPolicy();
            UnexpectedExceptionHandler.S().registerModule(instance);
        }
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

    /**
     * check channel url which is in use by 'USED' channel.
     * ('USED' channel means "channel whose state is 'used'".)
     * @param url
     * @return
     */
    public boolean
    isChannelUrlUsed(String url) {
        long[] state = new long[1];
        long cid = findChannel(state, url);
        if (cid < 0 || !Feed.Channel.isStatUsed(state[0]))
            return false;
        return true;
    }

    public long
    getDefaultCategoryId() {
        return DB.getDefaultCategoryId();
    }

    public String
    getCategoryName(long categoryid) {
        String ret = null;
        Cursor c = db.queryCategory(ColumnCategory.NAME, ColumnCategory.ID, categoryid);
        if (c.moveToFirst())
            ret = c.getString(0);
        c.close();
        return ret;
    }

    /**
     * Duplicated category name is also allowed.
     * (This function doens't check duplication.)
     * @param category
     * @return
     *   0 (success)
     */
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

    /**
     * Delete category.
     * category id field of channels which have this category id as their field value,
     *   is changed to default category id.
     * @param id
     * @return
     */
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

    /**
     * Update category name.
     * @param id
     * @param name
     * @return
     */
    public long
    updateCategory(long id, String name) {
        return db.updateCategory(id, name);
    }

    /**
     * Get all categories from DB.
     * @return
     */
    public Feed.Category[]
    getCategories() {
        // Column index is used below. So order is important.
        Cursor c = db.queryCategory(new ColumnCategory[] { ColumnCategory.ID, ColumnCategory.NAME, },
                                    null, null);

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

    /**
     * Insert new channel - url.
     * This is to insert channel url and holding place for this new channel at DB.
     * @param categoryid
     * @param url
     * @return
     *   cid (success) / -1 (fails - ex. duplicated-named-channel)
     */
    public long
    insertNewChannel(long categoryid, String url) {

        long[] chState = new long[1];
        long cid = findChannel(chState, url);

        if (cid >= 0 && Feed.Channel.isStatUsed(chState[0]))
            return -1;

        if (cid >= 0 && !Feed.Channel.isStatUsed(chState[0])) {
            if (!UIPolicy.makeChannelDir(cid)) {
                return -1;
            }
            // There is unused existing channel.
            // Let's reuse it.
            // Initialize some channel informations
            ContentValues cvs = new ContentValues();
            cvs.put(ColumnChannel.STATE.getName(),      Feed.Channel.FStatUsed);
            // We didn't verify 'categoryid' here.
            cvs.put(ColumnChannel.CATEGORYID.getName(), categoryid);
            cvs.put(ColumnChannel.ACTION.getName(),     Feed.FInvalid);
            cvs.put(ColumnChannel.POSITION.getName(),   getChannelInfoMaxLong(ColumnChannel.POSITION) + 1);
            cvs.put(ColumnChannel.IMAGEBLOB.getName(),  new byte[0]);
            db.updateChannel(cid, cvs);
            return cid;
        }

        // Real insertion!

        logI("InsertNewChannel DB Section Start");
        // insert and update channel id.

        // Create empty channel information.
        Feed.Channel.ProfD profD = new Feed.Channel.ProfD();
        profD.url = url;
        Feed.Channel.DbD dbD = new Feed.Channel.DbD();
        dbD.categoryid = categoryid;
        dbD.lastupdate = new Date().getTime();
        Feed.Channel.ParD parD = new Feed.Channel.ParD();
        parD.title = profD.url;
        cid = db.insertChannel(buildNewChannelContentValues(profD, parD, dbD));
        // check duplication...
        if (!UIPolicy.makeChannelDir(cid)) {
            db.deleteChannel(cid);
            return -1;
        }

        return cid;
    }

    /**
     * Filtering items that are not in DB from given item array.
     * @param items
     * @param newItems
     *   new item's are added to the last of this linked list.
     * @return
     */
    public Err
    getNewItems(Feed.Item.ParD[] items, LinkedList<Feed.Item.ParD> newItems) {
        eAssert(null != items);
        logI("UpdateChannel DB Section Start");

        try {
            for (Feed.Item.ParD item : items) {
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
                Cursor c = db.queryItem(new ColumnItem[] { ColumnItem.ID },
                                        new ColumnItem[] { ColumnItem.TITLE,
                                                           ColumnItem.PUBDATE,
                                                           ColumnItem.LINK,
                                                           //ColumnItem.DESCRIPTION,
                                                           ColumnItem.ENCLOSURE_URL },
                                        new String[] { item.title,
                                                       item.pubDate,
                                                       item.link,
                                                       //item.parD.description,
                                                       item.enclosureUrl },
                                        0);
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
        } catch (FeederException e) {
            return e.getError();
        }
        return Err.NoErr;
    }

    /**
     * Update channel.
     * ColumnChannel.LASTUPDATE value is set only at this function.
     * @param cid
     * @param ch
     * @param newItems
     *   new items to be added to this channel.
     * @param idop
     *   interface to get item-data-file.
     * @return
     * @throws FeederException
     */
    public int
    updateChannel(long cid, Feed.Channel.ParD ch, LinkedList<Feed.Item.ParD> newItems, ItemDataOpInterface idop)
            throws FeederException {
        logI("UpdateChannel DB Section Start : " + cid);

        String oldTitle = getChannelInfoString(cid, ColumnChannel.TITLE);
        if (!oldTitle.equals(ch.title)) {
            // update channel information
            ContentValues channelUpdateValues = new ContentValues();
            channelUpdateValues.put(ColumnChannel.TITLE.getName(),       ch.title);
            channelUpdateValues.put(ColumnChannel.DESCRIPTION.getName(), ch.description);
            db.updateChannel(cid, channelUpdateValues);
        }

        Iterator<Feed.Item.ParD> iter = newItems.iterator();

        while (iter.hasNext()) {
            Feed.Item.ParD itemParD = iter.next();
            Feed.Item.DbD  itemDbD = new Feed.Item.DbD();
            itemDbD.cid = cid;


            // NOTE
            // Order is very important
            // Order SHOULD be "get item data" => "insert to db"
            // Why?
            // If "insert to db" is done before "get item data", user can see item at UI.
            // So, user may try to get item data by UI action.
            // Then what happens?
            // Two operations for getting same item data are running concurrently!
            // This is not what I expected.
            //
            // Yes! I know.
            // In case of 'file download operation', there can be race-condition even if
            //   operation order is 'get' -> 'insert'.
            //   (User request DB items at the moment between
            //      "file download is done, and item is inserted" and
            //      "renaming file to final name based on item id".)
            // In this case, user may try to download again even if download is done, and second
            //   downloaded file will be overwritten to previous one.
            // This is not normal and my expectation.
            // But it's NOT harmful and it's very RARE case!
            // So, I don't use any synchronization to prevent this race condition.
            // Now we know item id here.
            try {
                File f = null;
                if (null != idop)
                    f = idop.getFile(itemParD);

                // FIXME
                // NOTE
                // There is possible race-condition between below three lines of code.
                // (between "if(.....)" and "cachedItem...")
                // But it's just one-item difference.
                // So, user may think like "During handling user-request, DB may updated."
                // At this moment, let's ignore this race-condition.
                // If issued case is found, let's consider it at the moment.
                if (0 > (itemDbD.id = db.insertItem(buildNewItemContentValues(itemParD, itemDbD))))
                    throw new FeederException(Err.DBUnknown);
                cacheItemMaxId.invalidate("" + cid);

                if (null != idop && null != f) {
                    // NOTE
                    // At this moment, race-condition can be issued.
                    // But, as I mentioned above, it's not harmful and very rare case.
                    if (!f.renameTo(UIPolicy.getItemDataFile(itemDbD.id)))
                        f.delete();
                }
            } catch (FeederException e) {
                if (Err.DBUnknown == e.getError())
                    throw e;
                ; // if feeder fails to get item data, just ignore it!
            }
            checkInterrupted();
        }
        logI("DBPolicy : new " + newItems.size() + " items are inserted");
        db.updateChannel(cid, ColumnChannel.LASTUPDATE, new Date().getTime());
        logI("UpdateChannel DB Section End");
        return 0;
    }

    /**
     * update given channel value.
     * @param cid
     * @param column
     * @param value
     * @return
     */
    public long
    updateChannel(long cid, ColumnChannel column, long value) {
        // Fields those are allowed to be updated.
        eAssert(ColumnChannel.CATEGORYID == column
                || ColumnChannel.OLDLAST_ITEMID == column
                || ColumnChannel.ACTION == column
                || ColumnChannel.UPDATEMODE == column
                || ColumnChannel.POSITION == column
                || ColumnChannel.STATE == column
                || ColumnChannel.NRITEMS_SOFTMAX == column);
        return db.updateChannel(cid, column, value);
    }

    public long
    updateChannel(long cid, ColumnChannel column, byte[] data) {
        // Fields those are allowed to be updated.
        eAssert(ColumnChannel.IMAGEBLOB == column);
        return db.updateChannel(cid, ColumnChannel.IMAGEBLOB, data);
    }

    /**
     * switch ColumnChannel.POSITION values.
     * @param cid0
     * @param cid1
     * @return
     */
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

    /**
     *
     * @param cid
     * @param sec
     *   day of second.
     * @return
     */
    public long
    updateChannel_schedUpdate(long cid, long sec) {
        return updateChannel_schedUpdate(cid, new long[] { sec });
    }

    /**
     *
     * @param cid
     * @param secs
     *   array of day of second.
     * @return
     */
    public long
    updateChannel_schedUpdate(long cid, long[] secs) {
        // verify values SECONDS_OF_DAY
        for (long s : secs)
            eAssert(0 <= s && s <= 60 * 60 * 24);
        return db.updateChannel(cid, ColumnChannel.SCHEDUPDATETIME, Utils.nrsToNString(secs));
    }

    /**
     * Update OLDLAST_ITEMID field to up-to-date.
     * (update to current largest item ID)
     * @param cid
     * @return
     */
    public long
    updateChannel_lastItemId(long cid) {
        long maxId = getItemInfoMaxId(cid);
        updateChannel(cid, ColumnChannel.OLDLAST_ITEMID, maxId);
        return maxId;
    }

    /**
     * Query USED channel column those are belonging to given category.
     * (unused channels are not selected.)
     * @param categoryid
     * @param column
     * @return
     */
    public Cursor
    queryChannel(long categoryid, ColumnChannel column) {
        return queryChannel(categoryid, new ColumnChannel[] { column });
    }

    /**
     * Query USED channel columns those are belonging to given category.
     * (unused channels are not selected.)
     * @param categoryid
     * @param column
     * @return
     */
    public Cursor
    queryChannel(long categoryid, ColumnChannel[] columns) {
        eAssert(categoryid >= 0);
        return db.queryChannel(columns,
                               new ColumnChannel[] { ColumnChannel.STATE,
                                                     ColumnChannel.CATEGORYID },
                               new Object[] { Feed.Channel.FStatUsed,
                                              categoryid },
                               null, false, 0);
    }

    /**
     * Query all USED channel column
     * @param column
     * @return
     */
    public Cursor
    queryChannel(ColumnChannel column) {
        return queryChannel(new ColumnChannel[] { column });
    }

    /**
     * Query all USED channel columns
     * @param column
     * @return
     */
    public Cursor
    queryChannel(ColumnChannel[] columns) {
        return db.queryChannel(columns,
                ColumnChannel.STATE,
                Feed.Channel.FStatUsed,
                null, false, 0);
    }

    /**
     * This function mark given channel's state as 'UNUSED'
     * This function doesn't really delete channel row from DB.
     * If channel is deleted from DB than all items that is belonging to given channel
     *   should be deleted too.
     * Why? Deleting channel only breaks DB's constraints because channel id field of item is foreign key.
     * But, deleting all items from DB takes quite long time and I'm not sure this is really what user want.
     * So, just mark it.
     * TODO : is it suitable for normal use case?
     * @param cid
     * @return
     */
    public int
    deleteChannel(long cid) {
        // Just mark as 'unused' - for future
        long n = db.updateChannel(cid, ColumnChannel.STATE, Feed.Channel.FStatUnused);
        eAssert(0 == n || 1 == n);
        if (1 == n) {
            UIPolicy.removeChannelDir(cid);
            return 0;
        } else
            return -1;
    }

    /**
     * Get all channel ids belonging to given category.
     * @param categoryid
     * @return
     */
    public long[]
    getChannelIds(long categoryid) {
        Cursor c = db.queryChannel(new ColumnChannel[] { ColumnChannel.ID },
                                   new ColumnChannel[] { ColumnChannel.STATE,
                                                         ColumnChannel.CATEGORYID },
                                   new Object[] { Feed.Channel.FStatUsed,
                                                  categoryid },
                                   null, false, 0);
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

    /**
     * Get field value of given 'USED' channel.
     * @param cid
     * @param column
     * @return
     */
    private Object
    getChannelInfoObject(long cid, ColumnChannel column) {
        Cursor c = db.queryChannel(new ColumnChannel[] { column },
                                   new ColumnChannel[] { ColumnChannel.STATE,
                                                         ColumnChannel.ID },
                                   new Object[] { Feed.Channel.FStatUsed,
                                                  cid },
                                   null, false, 0);
        Object ret = null;
        if (c.moveToFirst())
            ret = getCursorValue(c, 0);
        c.close();
        return ret;
    }

    public Long
    getChannelInfoLong(long cid, ColumnChannel column) {
        eAssert(column.getType().equals("integer"));
        return (Long)getChannelInfoObject(cid, column);
    }

    public String
    getChannelInfoString(long cid, ColumnChannel column) {
        eAssert(column.getType().equals("text"));
        return (String)getChannelInfoObject(cid, column);
    }

    /**
     *
     * @param cid
     * @param columns
     * @return
     *   each string values of given column.
     */
    public String[]
    getChannelInfoStrings(long cid, ColumnChannel[] columns) {
        Cursor c = db.queryChannel(columns,
                                    new ColumnChannel[] { ColumnChannel.STATE,
                                                          ColumnChannel.ID },
                                    new Object[] { Feed.Channel.FStatUsed,
                                                   cid },
                                    null, false, 0);
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

    /**
     * Get maximum value of given column.
     * Field type of give column should be 'integer'.
     * @param column
     * @return
     */
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

    /**
     * channel should be USED one.
     * @param cid
     * @return
     */
    public byte[]
    getChannelInfoImageblob(long cid) {
        byte[] blob = new byte[0];
        Cursor c = db.queryChannel(new ColumnChannel[] { ColumnChannel.IMAGEBLOB },
                                   new ColumnChannel[] { ColumnChannel.STATE,
                                                         ColumnChannel.ID },
                                   new Object[] { Feed.Channel.FStatUsed,
                                                  cid },
                                   null, false, 0);
        if (c.moveToFirst())
            blob = c.getBlob(0);
        c.close();
        return blob;
    }

    // NOTE
    // This function takes much longer time than expected.
    // So, cache should be used to improve it!
    /**
     * Get maximum value of item id of given channel.
     * @param cid
     * @return
     */
    public long
    getItemInfoMaxId(long cid) {
        Long v = cacheItemMaxId.get("" + cid);
        if (null != v)
            return v;

        Cursor c = db.queryItem(new ColumnItem[] { ColumnItem.ID },
                                new ColumnItem[] { ColumnItem.CHANNELID },
                                new Object[] { cid },
                                1);
        if (!c.moveToFirst())
            return 0; // there is no item!

        // Why?
        // Default order of item query is 'descending order by ID'.
        // So, this one is last item id.
        long lastId = c.getLong(0);
        c.close();
        cacheItemMaxId.put("" + cid, lastId);
        return lastId;
    }


    private Object
    getItemInfoObject(long id, ColumnItem column) {
        Cursor c = db.queryItem(new ColumnItem[] { column },
                                new ColumnItem[] { ColumnItem.ID },
                                new Object[] { id },
                                0);
        Object ret = null;
        if (c.moveToFirst())
            ret = getCursorValue(c, 0);
        c.close();
        return ret;
    }

    public long
    getItemInfoLong(long id, ColumnItem column) {
        eAssert(column.getType().equals("integer"));
        return (Long)getItemInfoObject(id, column);
    }

    public String
    getItemInfoString(long id, ColumnItem column) {
        eAssert(column.getType().equals("text"));
        return (String)getItemInfoObject(id, column);
    }

    /**
     *
     * @param id
     * @param columns
     * @return
     */
    public String[]
    getItemInfoStrings(long id, ColumnItem[] columns) {
        Cursor c = db.queryItem(columns,
                                new ColumnItem[] { ColumnItem.ID },
                                new Object[] { id },
                                0);
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

    /**
     * Query item information belonging to given channel.
     * @param cid
     * @param columns
     * @return
     */
    public Cursor
    queryItem(long cid, ColumnItem[] columns) {
        return db.queryItem(columns,
                            new ColumnItem[] { ColumnItem.CHANNELID },
                            new Object[] { cid },
                            0);
    }

    /**
     * Update state value of item.
     *
     * @param id
     * @param state
     *   can be one of below
     *     Feed.Channel.FStatUsed
     *     Feed.Channel.FStatUnused
     * @return
     */
    public long
    updateItem_state(long id, long state) {
        // Update item during 'updating channel' is not expected!!
        return db.updateItem(id, ColumnItem.STATE, state);
    }

    // delete downloaded files etc.
    /*
    public int
    cleanItemContents(long cid, long id) {
        eAssert(false); // Not implemented yet!
        return -1;
    }
    */
}
