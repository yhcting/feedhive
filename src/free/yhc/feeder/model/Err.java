package free.yhc.feeder.model;

// Err codes.
// TODO : set correct string resource id
public enum Err {
    NoErr                       (0),
    IONet                       (0),
    IOFile                      (0),
    ParserUnsupportedFormat     (0),
    ParserUnsupportedVersion    (0),
    DBDuplicatedChannel         (0),
    DBUnknown                   (0),
    Unknown                     (0);

    int msgId; // matching message id

    Err(int msgId) {
        this.msgId = msgId;
    }

    public int getMsgId() {
        return msgId;
    }
}
