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
import static free.yhc.feeder.model.Utils.logW;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.HandlerThread;
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
    // Checking duplication inside whole DB is very very inefficient.
    // It requires extremely frequent and heavy DB access.
    // So, instead of comparing with whole DB, just compare with enough large number of
    //   previous items of the channel is better.
    // Then how many items should be used to check duplication?
    // To do that, below factor is used.
    // At most, recent items by amount of "<current # items> * DUP_CHECK_SCOPE_FACTOR" is used
    //   for comparison to check duplication.
    private static final int   DUP_SCOPE_FACTOR       = 2;
    // At least, <DUP_SCOPE_MIN> items SHOULD be used to compare duplication.
    private static final int   DUP_SCOPE_MIN          = 200;
    // unexpectedly large number of scope.
    // Warning log will be shown.
    private static final int   DUP_SCOPE_WARNING      = 3000;

    private static DBPolicy instance = null;

    // Dependency on only following modules are allowed
    // - Utils
    // - UnexpectedExceptionHandler
    // - DB / DBThread
    // - UIPolicy
    private final DB        db      = DB.get();
    private final UIPolicy  uip     = UIPolicy.get();
    private final Handler   asyncHandler;

    // Only for bug reporting.
    private final LinkedList<String>  wiredChannl = new LinkedList<String>();
    // Getting max item id of channel takes longer time than expected.
    // So, let's caching it.
    // It is used at very few places.
    // Therefore, it is easy to caching and easy to avoid cache-synchronization issue.
    private final HashMap<Long, Long> maxIdCache = new HashMap<Long, Long>(); // special cache for max Id.

    // NOTE
    // This is a kind of dirty-HACK!
    // When user try to access item tables at DB during updating channels(adding new items),
    //   it may take too long time because DB is continuously accessed by channel updater.
    // This may let user annoying.
    // So, this HACK is used!
    private final AtomicInteger       delayedChannelUpdate = new AtomicInteger(0);

    private final KeyBasedLinkedList<OnChannelUpdatedListener> channUpdatedListenerl
                        = new KeyBasedLinkedList<OnChannelUpdatedListener>();

    public interface OnChannelUpdatedListener {
        // Called back after updating channel in case that new items are newly inserted.
        void onNewItemsUpdated(long cid, int nrNewItems);
        void onLastItemIdUpdated(long[] cids);
    }

    enum ItemDataType {
        RAW,
        FILE
    }

    interface ItemDataOpInterface {
        File   getFile(Feed.Item.ParD parD) throws FeederException;
    }

    private class DBAsyncThread extends HandlerThread {
        DBAsyncThread() {
            super("DBAsyncThread");
        }
    }

    private static class ItemUrls {
        final long    id;
        final String  link;
        final String  enclosure;
        ItemUrls(long aId, String aLink, String aEnclosure) {
            id = aId;
            link = aLink;
            enclosure = aEnclosure;
        }
    }

    // ======================================================
    //
    // ======================================================
    private DBPolicy() {
        UnexpectedExceptionHandler.get().registerModule(this);
        DBAsyncThread async = new DBAsyncThread();
        async.start();
        asyncHandler = new Handler(async.getLooper());
    }

    /**
     * check that current Thread is interrupted.
     * If it is interrupted, FeederException is thrown.
     * @throws FeederException
     */
    private void
    checkInterrupted() throws FeederException {
        if (Thread.currentThread().isInterrupted())
            throw new FeederException(Err.INTERRUPTED);
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
        values.put(ColumnItem.STATE.getName(),               Feed.Item.FSTAT_DEFAULT);

        // If success to parse pubdate than pubdate is used, if not, current time is used.
        long time = Utils.dateStringToTime(parD.pubDate);
        if (time < 0)
            time = new Date().getTime();
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
        values.put(ColumnChannel.ACTION.getName(),           Feed.FINVALID);
        values.put(ColumnChannel.UPDATEMODE.getName(),       Feed.Channel.FUPD_DEFAULT);
        values.put(ColumnChannel.STATE.getName(),            Feed.Channel.FSTAT_DEFAULT);
        values.put(ColumnChannel.CATEGORYID.getName(),       dbD.categoryid);
        values.put(ColumnChannel.LASTUPDATE.getName(),       dbD.lastupdate);

        // information defined by spec.
        values.put(ColumnChannel.TITLE.getName(),            parD.title);
        values.put(ColumnChannel.DESCRIPTION.getName(),      parD.description);

        values.put(ColumnChannel.IMAGEBLOB.getName(),        new byte[0]);
        // Fill reserved values as default
        // This need to match ChannelSettingActivity's setting value.
        values.put(ColumnChannel.SCHEDUPDATETIME.getName(),  Feed.Channel.DEFAULT_SCHEDUPDATE_TIME); // default (03 o'clock)
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
                                                         ColumnChannel.STATE},
                                   ColumnChannel.URL, url,
                                   null, false, 0);
        if (!c.moveToFirst()) {
            c.close();
            return -1;
        }

        do {
            if (null != state)
                state[0] = c.getLong(1);
            ret = c.getLong(0);
            break;
        } while(c.moveToNext());

        c.close();
        return ret;
    }

    private void
    checkDelayedChannelUpdate() {
        long timems = System.currentTimeMillis();
            // Dangerous!!
            // Always be careful when using 'delayedChannelUpdate'
            // This may lead to infinite loop!
        while (0 < delayedChannelUpdate.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
            if (System.currentTimeMillis() - timems > 10 * 60 * 1000)
                // Over 10 minutes, updating is delayed!
                // This is definitely unexpected error!!
                eAssert(false);
        }
    }

    // ======================================================
    //
    // ======================================================
    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        StringBuilder bldr = new StringBuilder("[ DBPolicy ]\n");
        synchronized (wiredChannl) {
            Iterator<String> itr = wiredChannl.iterator();
            while (itr.hasNext())
                bldr.append("  Wired Channel : " + itr.next() + "\n");
        }
        return bldr.toString();
    }

    // S : Singleton instance
    public static DBPolicy
    get() {
        if (null == instance)
            instance = new DBPolicy();
        return instance;
    }

    public void
    reloadDatabase() {
        db.reloadDatabase();
    }

    // ======================================================
    //
    // FATAL FUNCTIONS
    //
    // ======================================================
    /**
     * Delay channel DB update (inserting items) until all 'get' request is put by calling
     *   {@link DBPolicy#putDelayedChannelUpdate()}.
     * This is very dangerous! (May lead to infinite loop!)
     * So, DO NOT USER this function if you don't know what your are doing!
     * (Only main UI Thread can use this function!)
     */
    public void
    getDelayedChannelUpdate() {
        eAssert(delayedChannelUpdate.get() >= 0);
        delayedChannelUpdate.incrementAndGet();
    }

    /**
     * See {@link DBPolicy#getDelayedChannelUpdate()}
     */
    public void
    putDelayedChannelUpdate() {
        eAssert(delayedChannelUpdate.get() > 0);
        delayedChannelUpdate.decrementAndGet();
    }

    // ======================================================
    //
    // Event Listeners
    //
    // ======================================================

    // ------------------------------------------------------
    // Channel Event Listeners
    // ------------------------------------------------------
    public void
    registerChannelUpdatedListener(Object key, OnChannelUpdatedListener listener) {
        synchronized (channUpdatedListenerl) {
            channUpdatedListenerl.addLast(key, listener);
        }
    }

    public void
    unregisterChannelUpdatedListener(Object key) {
        synchronized (channUpdatedListenerl) {
            channUpdatedListenerl.remove(key);
        }
    }

    private void
    notifyNewItemsUpdated(long cid, int nrNewItems) {
        synchronized (channUpdatedListenerl) {
            Iterator<OnChannelUpdatedListener> itr = channUpdatedListenerl.iterator();
            while (itr.hasNext())
                itr.next().onNewItemsUpdated(cid, nrNewItems);
        }
    }

    private void
    notifyLastItemIdUpdated(long[] cids) {
        synchronized (channUpdatedListenerl) {
            Iterator<OnChannelUpdatedListener> itr = channUpdatedListenerl.iterator();
            while (itr.hasNext())
                itr.next().onLastItemIdUpdated(cids);
        }
    }

    // ======================================================
    //
    //
    //
    // ======================================================

    public void
    beginTransaction() {
        db.beginTransaction();
    }

    public void
    setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    public void
    endTransaction() {
        db.endTransaction();
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
     * check channel url is already in the DB.
     * @param url
     * @return
     */
    public boolean
    isDuplicatedChannelUrl(String url) {
        long[] state = new long[1];
        long cid = findChannel(state, url);
        return cid >= 0;
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
     *   cid (success)
     * @throws FeederException
     */
    public long
    insertNewChannel(long categoryid, String url)
            throws FeederException {
        eAssert(!url.endsWith("/"));

        long[] chState = new long[1];
        long cid = findChannel(chState, url);

        if (cid >= 0)
            throw new FeederException(Err.DB_DUPLICATED_CHANNEL);

        // logI("InsertNewChannel DB Section Start");
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
        if (!uip.makeChannelDir(cid)) {
            db.deleteChannel(ColumnChannel.ID, cid);
            throw new FeederException(Err.IO_FILE);
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
    getNewItems(long cid, Feed.Item.ParD[] items, LinkedList<Feed.Item.ParD> newItems) {
        eAssert(null != items);
        logI("UpdateChannel DB Section Start : cid[" + cid + "]");

        if (0 == items.length)
            return Err.NO_ERR;

        boolean pubDateAvail = Utils.isValidValue(items[0].pubDate);
        HashMap<String, HashMap<String, ItemUrls>> mainMap
            = new HashMap<String, HashMap<String, ItemUrls>>();
        HashMap<String, ItemUrls> subMap;

        // -----------------------------------------------------------------------
        // check whether there is duplicated item in DB or not.
        // (DB access is expensive operation)
        // -----------------------------------------------------------------------

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

        // NOTE
        //   In case of some RSS sites(ex. iblug), link/enclosure url is continuously changed
        //     without any modification.
        //   At this case, Feeder regarded this updated item as new one.
        //   So, to avoid this, algorithm is changed to less-strict-way to tell whether
        //     this item is new or not.
        //   New algorithm is, (Note that 'pubdate' is optional element of RSS.)
        //     if [ 'pubdate' is available ]
        //         if [ 'pubdate' and 'title' is same ]
        //             this is same item
        //             if [ 'link' or 'enclosure_url' is changed ]
        //                 update to new one
        //         else
        //             this is new item.
        //     else
        //         if [ 'link' and 'enclosure_url' is same ]
        //             this is same item
        //         else
        //             this is new item.

        // -----------------------------------------------------------------------
        // Build hash tree with items from DB to check duplication.
        //
        // NOTE
        // main/sub key SHOULD MATCH DB query's where clause, below!
        // -----------------------------------------------------------------------
        Cursor c = null;
        int    nrscope = items.length * DUP_SCOPE_FACTOR;
        nrscope = (nrscope < DUP_SCOPE_MIN)? DUP_SCOPE_MIN: nrscope;

        if (nrscope > DUP_SCOPE_WARNING)
            logW("DB Update : Number of scope to check duplication is unexpectedly big! : " + nrscope + "\n" +
                 "    OOM is concerned!!");

        final int idI        = 0;
        final int linkI      = 1;
        final int enclosureI = 2;
        int       mainKeyI;
        int       subKeyI;
        String    mainKey;
        String    subKey;
        ItemUrls  iurls;
        if (pubDateAvail) {
            c = db.queryItemAND(new ColumnItem[] { ColumnItem.ID,           // SHOULD BE index 0
                                                   ColumnItem.LINK,         // SHOULD BE index 1
                                                   ColumnItem.ENCLOSURE_URL,// SHOULD BE index 2
                                                   ColumnItem.TITLE,
                                                   ColumnItem.PUBDATE },
                                new ColumnItem[] { ColumnItem.CHANNELID },
                                new String[] { "" + cid },
                                0);
            mainKeyI = 3; // title
            subKeyI = 4;  // pubdate
        } else {
            c = db.queryItemAND(new ColumnItem[] { ColumnItem.ID,           // SHOULD BE index 0
                                                   ColumnItem.LINK,         // SHOULD BE index 1
                                                   ColumnItem.ENCLOSURE_URL },// SHOULD BE index 2
                                new ColumnItem[] { ColumnItem.CHANNELID },
                                new String[] { "" + cid },
                                0);
            mainKeyI = 1; // link
            subKeyI = 2;  // enclosure url
        }

        // Create hash map of candidates items to check duplication.
        try {
            if (c.moveToFirst() && nrscope > 0) {
                do {
                    mainKey = c.getString(mainKeyI);
                    subKey = c.getString(subKeyI);
                    subMap = mainMap.get(mainKey);
                    if (null == subMap) {
                        subMap = new HashMap<String, ItemUrls>();
                        mainMap.put(mainKey, subMap);
                    }
                    iurls = subMap.get(subKey);
                    if (null == iurls) {
                        subMap.put(subKey,
                                   new ItemUrls(c.getLong(idI),
                                                c.getString(linkI),
                                                c.getString(enclosureI)));
                    } else {
                        logW("Duplicated Item in DB - This is unexpected but not harmful.\n" +
                             "    main key : " + mainKey + "\n" +
                             "    sub key  : " + subKey + "\n");
                    }
                    checkDelayedChannelUpdate();
                    checkInterrupted();
                } while(--nrscope > 0 && c.moveToNext());
            }
        } catch (FeederException e) {
            return e.getError();
        } finally {
            c.close();
        }

        try {
            for (Feed.Item.ParD item : items) {
                // ignore not-verified item
                if (!uip.verifyConstraints(item))
                    continue;

                // -----------------------------------------------------------------------
                // NOTE
                // main/sub key SHOULD MATCH DB query's where clause, below!
                // -----------------------------------------------------------------------
                if (pubDateAvail) {
                    mainKey = item.title;  // should match mainKeyI
                    subKey = item.pubDate; // should match subKeyI
                } else {
                    mainKey = item.link;        // should match mainKeyI
                    subKey = item.enclosureUrl; // should match subKeyI
                }

                subMap = mainMap.get(mainKey);
                if (null == subMap) {
                    subMap = new HashMap<String, ItemUrls>();
                    mainMap.put(mainKey, subMap);
                }

                iurls = subMap.get(subKey);
                if (null == iurls) {
                    // New Item.
                    // NOTE
                    //   Why add to First?
                    //   Usually, recent item is located at top of item list in the feed.
                    //   So, to make bottom item have smaller ID, 'addFirst' is used.
                    newItems.addFirst(item);
                    // This is new item.
                    subMap.put(subKey, new ItemUrls(-1, mainKey, subKey));
                } else {
                    // This is duplicated item.
                    // But, it is still needed to be checked whether item information is updated or not.
                    // Normally, news or magazine doesn't update the link.
                    // But, in case of multimedia RSS like podcast, this happens a lot.
                    // And usually, multimedia RSS includes all items in the RSS syndication.
                    // So, I don't need to check whole database.
                    // Checking hashed item in memory is enough.
                    if (pubDateAvail &&
                        !(item.link.equals(iurls.link)
                            && item.enclosureUrl.equals(iurls.enclosure))) {
                        if (iurls.id < 0) {
                            logW("Channel includes same title/pubDate but different link or enclosure url!\n" +
                                 "    title " + mainKey + "\n" +
                                 "    pubDate" + subKey + "\n");
                        } else {
                            // Item information is updated with different value.
                            // Let's update DB!
                            ContentValues cvs = new ContentValues();
                            cvs.put(ColumnItem.LINK.getName(), item.link);
                            cvs.put(ColumnItem.ENCLOSURE_URL.getName(), item.enclosureUrl);
                            db.updateItem(iurls.id, cvs);
                        }
                    }
                }
                checkDelayedChannelUpdate();
                checkInterrupted();
            }
        } catch (FeederException e) {
            return e.getError();
        }

        return Err.NO_ERR;
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
                    throw new FeederException(Err.DB_UNKNOWN);
                // Invalidate cached value.
                synchronized (maxIdCache) {
                    maxIdCache.put(cid, itemDbD.id);
                }

                if (null != idop && null != f) {
                    // NOTE
                    // At this moment, race-condition can be issued.
                    // But, as I mentioned above, it's not harmful and very rare case.
                    if (!f.renameTo(getItemInfoDataFile(itemDbD.id)))
                        f.delete();
                }
            } catch (FeederException e) {
                if (Err.DB_UNKNOWN == e.getError())
                    throw e;
                ; // if feeder fails to get item data, just ignore it!
            }
            checkDelayedChannelUpdate();
            checkInterrupted();
        }
        logI("DBPolicy : new " + newItems.size() + " items are inserted");
        db.updateChannel(cid, ColumnChannel.LASTUPDATE, new Date().getTime());

        if (newItems.size() > 0)
            notifyNewItemsUpdated(cid, newItems.size());

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
            eAssert(0 <= s && s <= Utils.DAY_IN_SEC);
        return db.updateChannel(cid, ColumnChannel.SCHEDUPDATETIME, Utils.nrsToNString(secs));
    }

    /**
     * Update OLDLAST_ITEMID field to up-to-date.
     * (update to current largest item ID)
     * @param cid
     */
    public void
    updateChannel_lastItemId(long cid) {
        updateChannel_lastItemIds(new long[] { cid });
    }

    /**
     * Update OLDLAST_ITEMID field to up-to-date.
     * (update to current largest item ID)
     * @param cids
     */
    public void
    updateChannel_lastItemIds(long[] cids) {
        Long[] whereValues = Utils.convertArraylongToLong(cids);
        Long[] targetValues = new Long[cids.length];
        for (int i = 0; i < whereValues.length; i++)
            targetValues[i] = getItemInfoMaxId(whereValues[i]);

        db.updateChannelSet(ColumnChannel.OLDLAST_ITEMID, targetValues,
                            ColumnChannel.ID, whereValues);
        notifyLastItemIdUpdated(cids);
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
        return db.queryChannel(columns, ColumnChannel.CATEGORYID, categoryid, null, false, 0);
    }

    /**
     * Query column of all channels
     * @param column
     * @return
     */
    public Cursor
    queryChannel(ColumnChannel column) {
        return queryChannel(new ColumnChannel[] { column });
    }

    /**
     * Query columns of all channels
     * @param column
     * @return
     */
    public Cursor
    queryChannel(ColumnChannel[] columns) {
        return db.queryChannel(columns, (ColumnChannel[])null, null, null, false, 0);
    }

    /**
     *
     * @param cid
     * @return
     *   number of items deleted
     */
    public long
    deleteChannel(long cid) {
        return deleteChannel(new long[] { cid });
    }

    /**
     *
     * @param cids
     * @return
     *   number of items deleted
     */
    public long
    deleteChannel(long[] cids) {
        ColumnChannel[] cols = new ColumnChannel[cids.length];
        for (int i = 0; i < cols.length; i++)
            cols[i] = ColumnChannel.ID;
        long nr = db.deleteChannelOR(cols, Utils.convertArraylongToLong(cids));
        for (long cid : cids)
            uip.removeChannelDir(cid);
        return nr;
    }

    /**
     * get all channel ids.
     * @return
     */
    public long[]
    getChannelIds() {
        Cursor c = queryChannel(ColumnChannel.ID);
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
     * Get all channel ids belonging to given category.
     * @param categoryid
     * @return
     */
    public long[]
    getChannelIds(long categoryid) {
        Cursor c = db.queryChannel(new ColumnChannel[] { ColumnChannel.ID },
                                   ColumnChannel.CATEGORYID, categoryid,
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
                                   ColumnChannel.ID, cid,
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
                                   ColumnChannel.ID, cid,
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
                                   ColumnChannel.ID, cid,
                                   null, false, 0);
        if (c.moveToFirst())
            blob = c.getBlob(0);
        c.close();
        return blob;
    }

    /**
     * Get number items belonging to the given channel.
     * @param cid
     * @return
     */
    public int
    getChannelInfoNrItems(long cid) {
        Cursor c = db.queryItemCount(ColumnItem.ID,
                                     ColumnItem.CHANNELID, cid);
        if (!c.moveToFirst())
            eAssert(false);

        int ret = c.getInt(0);
        c.close();
        return ret;
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
        synchronized (maxIdCache) {
            Long v = maxIdCache.get(cid);
            if (null != v)
                return v;
        }

        Cursor c = db.queryItemIds(cid, 1);
        if (!c.moveToFirst())
            return 0; // there is no item!
        // Why?
        // Order of result generated by this query is 'descending order by ID'.
        // So, this one is last item id.
        long lastId = c.getLong(0);
        c.close();

        synchronized (maxIdCache) {
            maxIdCache.put(cid, lastId);
        }
        return lastId;
    }

    /**
     *
     * @return
     */
    public long
    getItemMinPubtime() {
        return getItemMinPubtime(null);
    }

    /**
     *
     * @param cid
     * @return
     *   -1 if there is no item otherwise time in millis.
     */
    public long
    getItemMinPubtime(long cid) {
        return getItemMinPubtime(new long[] { cid });
    }

    /**
     *
     * @param cids
     * @return
     *   -1 if there is no item otherwise time in millis.
     */
    public long
    getItemMinPubtime(long[] cids) {
        long v = -1;
        Cursor c = db.queryItemMinMax(cids, DB.ColumnItem.PUBTIME, false);
        if (c.moveToFirst())
            v = c.getLong(0);
        c.close();
        return v;
    }

    /**
     *
     * @param where
     * @param mask
     * @param value
     * @return
     *   -1 if there is no item otherwise time in millis.
     */
    public long
    getItemMinPubtime(ColumnItem where, long mask, long value) {
        long v = -1;
        Cursor c = db.queryItemMinMax(where, mask, value, DB.ColumnItem.PUBTIME, false);
        if (c.moveToFirst())
            v = c.getLong(0);
        c.close();
        return v;
    }

    private Object
    getItemInfoObject(long id, ColumnItem column) {
        Cursor c = db.queryItemAND(new ColumnItem[] { column },
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
        Cursor c = db.queryItemAND(columns,
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
        if (cid < 0)
            cid = getItemInfoLong(id, DB.ColumnItem.CHANNELID);

        if (!Utils.isValidValue(title))
            title = getItemInfoString(id, DB.ColumnItem.TITLE);

        if (!Utils.isValidValue(url)) {
            long action = getChannelInfoLong(cid, DB.ColumnChannel.ACTION);
            if (Feed.Channel.isActTgtLink(action))
                url = getItemInfoString(id, DB.ColumnItem.LINK);
            else if (Feed.Channel.isActTgtEnclosure(action))
                url = getItemInfoString(id, DB.ColumnItem.ENCLOSURE_URL);
            else
                url = "";
        }

        // we don't need to create valid filename with empty url value.
        if (url.isEmpty()) {
            synchronized (wiredChannl) {
                wiredChannl.add(getChannelInfoString(cid, DB.ColumnChannel.URL));
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
        return new File(uip.getAppRootDirectoryPath() + cid + "/" + fname);
    }

    public Cursor
    queryItem(ColumnItem[] columns) {
        return queryItem(null, columns);
    }

    public Cursor
    queryItem(ColumnItem[] columns,
              String search, long fromPubtime, long toPubtime) {
        return queryItem(null, columns, search, fromPubtime, toPubtime);
    }

    /**
     * Query item information belonging to given channel.
     * @param cid
     * @param columns
     * @return
     */
    public Cursor
    queryItem(long cid, ColumnItem[] columns) {
        return queryItem(cid, columns, null, -1, -1);
    }

    /**
     * Query item information belonging to given channel.
     * @param cid
     * @param columns
     * @param search
     * @param fromPubtime
     * @param toPubtime
     * @return
     */
    public Cursor
    queryItem(long cid, ColumnItem[] columns,
              String search, long fromPubtime, long toPubtime) {
        return queryItem(new long[] { cid }, columns, search, fromPubtime, toPubtime);
    }


    /**
     * Query item information belonging to given channels.
     * @param cids
     * @param columns
     * @return
     */
    public Cursor
    queryItem(long[] cids, ColumnItem[] columns) {
        return queryItem(cids, columns, null, -1, -1);
    }

    /**
     * Query item information belonging to given channels.
     * @param cids
     * @param columns
     * @param search
     * @param fromPubtime
     * @param toPubtime
     * @return
     */
    public Cursor
    queryItem(long[] cids, ColumnItem[] columns,
              String search, long fromPubtime, long toPubtime) {
        ColumnItem[] cols = null;
        if (null != cids) {
            cols = new ColumnItem[cids.length];
            for (int i = 0; i < cols.length; i++)
                cols[i] = ColumnItem.CHANNELID;
        }
        return db.queryItemOR(columns,
                              cols,
                              null != cids? Utils.convertArraylongToLong(cids): null,
                              new ColumnItem[] { ColumnItem.TITLE, ColumnItem.DESCRIPTION },
                              new String[] { search, search },
                              fromPubtime, toPubtime,
                              0, true);
    }

    /**
     * Query items with masking value.
     * Usually used to select items with flag value.
     * @param columns
     * @param mask
     * @param value
     * @return
     */
    public Cursor
    queryItemMask(ColumnItem[] columns, ColumnItem where, long mask, long value) {
        return queryItemMask(columns, where, mask, value, null, -1, -1);
    }

    /**
     * Query items with masking value.
     * Usually used to select items with flag value.
     * @param columns
     * @param mask
     * @param value
     * @param search
     * @param fromPubtime
     * @param toPubtime
     * @return
     */
    public Cursor
    queryItemMask(ColumnItem[] columns,
                  ColumnItem where, long mask, long value,
                  String search, long fromPubtime, long toPubtime) {
        return db.queryItemMask(columns, where, mask, value,
                                new ColumnItem[] { ColumnItem.TITLE, ColumnItem.DESCRIPTION },
                                new String[] { search, search },
                                fromPubtime, toPubtime, true);
    }


    /**
     * Update state value of item.
     * @param id
     * @param state
     *   see Feed.Item.FStatxxx values
     * @return
     */
    public long
    updateItem_state(long id, long state) {
        // Update item during 'updating channel' is not expected!!
        return db.updateItem(id, ColumnItem.STATE, state);
    }

    /**
     * See {@link DBPolicy#updateItem_state(long, long)}
     * @param id
     * @param state
     * @return
     */
    public void
    updateItemAsync_state(final long id, final long state) {
        asyncHandler.post(new Runnable() {
            @Override
            public void run() {
                long old = getItemInfoLong(id, ColumnItem.STATE);
                if (old != state)
                    updateItem_state(id, state);
            }
        });
    }

    /**
     * delete items.
     * @param where
     * @param value
     * @return
     *   number of deleted items.
     */
    public long
    deleteItem(ColumnItem where, Object value) {
        return db.deleteItem(where, value);
    }

    public long
    deleteItemOR(ColumnItem[] wheres, Object[] values) {
        return db.deleteItemOR(wheres, values);
    }

    // ===============================================
    //
    // For DB Management
    //
    // ===============================================
    /**
     *
     * @param cid
     *   channel id to delete old items.
     *   '-1' means 'for all channel'.
     * @param percent
     *   percent to delete.
     * @return
     *   number of items deleted
     */
    public int
    deleteOldItems(long cid, int percent) {
        return db.deleteOldItems(cid, percent);
    }

    // ===============================================
    //
    // DB Watcher (Just delegation)
    //
    // ===============================================
    /**
     * watcher is updated if channel information is updated in DB.
     * So, to check whether  DB is updated
     * @param key
     */
    public void
    registerChannelWatcher(Object key) {
        db.registerChannelWatcher(key);
    }

    public boolean
    isChannelWatcherRegistered(Object key) {
        return db.isChannelWatcherRegistered(key);
    }

    public void
    unregisterChannelWatcher(Object key) {
        db.unregisterChannelWatcher(key);
    }

    public boolean
    isChannelWatcherUpdated(Object key, long cid) {
        return db.isChannelWatcherUpdated(key, cid);
    }

    public long[]
    getChannelWatcherUpdated(Object key) {
        return db.getChannelWatcherUpdated(key);
    }

    // -------------- Item Table ------------------
    public void
    registerItemTableWatcher(Object key) {
        db.registerItemTableWatcher(key);
    }

    public boolean
    isItemTableWatcherRegistered(Object key) {
        return db.isItemTableWatcherRegistered(key);
    }

    public void
    unregisterItemTableWatcher(Object key) {
        db.unregisterItemTableWatcher(key);
    }

    public boolean
    isItemTableWatcherUpdated(Object key) {
        return db.isItemTableWatcherUpdated(key);
    }

    // -------------- Channel Table ------------------
    public void
    registerChannelTableWatcher(Object key) {
        db.registerChannelTableWatcher(key);
    }

    public boolean
    isChannelTableWatcherRegistered(Object key) {
        return db.isChannelTableWatcherRegistered(key);
    }

    public void
    unregisterChannelTableWatcher(Object key) {
        db.unregisterChannelTableWatcher(key);
    }

    public boolean
    isChannelTableWatcherUpdated(Object key) {
        return db.isChannelTableWatcherUpdated(key);
    }
    // -------------- Category Table ------------------
    public void
    registerCategoryTableWatcher(Object key) {
        db.registerCategoryTableWatcher(key);
    }

    public boolean
    isCategoryTableWatcherRegistered(Object key) {
        return db.isCategoryTableWatcherRegistered(key);
    }

    public void
    unregisterCategoryTableWatcher(Object key) {
        db.unregisterCategoryTableWatcher(key);
    }

    public boolean
    isCategoryTableWatcherUpdated(Object key) {
        return db.isCategoryTableWatcherUpdated(key);
    }
}
