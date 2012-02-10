package free.yhc.feeder.model;

import free.yhc.feeder.R;

// Err codes.
// TODO : set correct string resource id
public enum Err {
    NoErr                       (R.string.ok),
    Interrupted                 (R.string.err_interrupted),
    UserCancelled               (R.string.err_user_cancelled),
    IONet                       (R.string.err_ionet),
    IOFile                      (R.string.err_iofile),
    ParserUnsupportedFormat     (R.string.err_parse_unsupported_format),
    ParserUnsupportedVersion    (R.string.err_parse_unsupported_version),
    DBDuplicatedChannel         (R.string.err_dbunknown),
    DBUnknown                   (R.string.err_dbunknown),
    Unknown                     (R.string.err_unknwon);

    int msgId; // matching message id

    Err(int msgId) {
        this.msgId = msgId;
    }

    public int getMsgId() {
        return msgId;
    }
}
