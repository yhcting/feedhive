package free.yhc.feeder;

import static free.yhc.feeder.Utils.eAssert;

class DBPolicy {
    private DB    db   = null;

    DBPolicy() {
        db = DB.db();
    }

    /**
     * @param rss
     * @return true  : DB is changed.
     *         false : DB is not changed.
     */
    boolean
    insertRSSChannel(RSS.Channel ch) {
        // FIXME implement policy.

        // insert and update channel id.
        ch.id = db.insertChannel(ch);
        if (ch.id <= 0)
            return false;
        return true;
    }

    // return : true  : if there is real update.
    //          false : nothing to update. (up-to-dated)
    boolean
    updateRSSChannel(RSS.Channel ch) {
        // FIXME implement policy.
        eAssert(ch.id > 0);
        db.updateChannel(ch);
        return true;
    }

    boolean
    deleteRSSChannel(long id) {
        long n = db.deleteChannel(id);
        eAssert(0 == n || 1 == n);
        return (0 == n)? false: true;
    }

    /**
     *
     * @param items
     * @return : number of items that fails to insert.
     */
    long
    updateChannelItems(RSS.Channel ch, RSS.Item[] items) {
        // remove all items and insert new values
        db.cleanChannelItems(ch.id);
        for (RSS.Item i : items) {
            // FIXME return value need to be checked!
            i.id = db.insertItem(ch.id, i);
            eAssert(i.id >= 0);
        }
        return 0;
    }
}
