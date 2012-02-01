package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;

public class DBPolicy {
    private DB    db   = null;

    public DBPolicy() {
        db = DB.db();
    }

    /**
     * @param rss
     * @return true  : DB is changed.
     *         false : DB is not changed.
     */
    public boolean
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
    public boolean
    updateRSSChannel(RSS.Channel ch) {
        // FIXME implement policy.
        eAssert(ch.id > 0);
        db.updateChannel(ch);
        return true;
    }

    public boolean
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
    public long
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
