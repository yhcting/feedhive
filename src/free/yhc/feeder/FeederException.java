package free.yhc.feeder;

class FeederException extends Exception {
    private static final long serialVersionUID = 1L;

    // Err codes.
    // TODO : set correct string resource id
    static enum Err {
        NoErr                       (0),
        IOOpenUrl                   (0),
        ParserUnsupportedFormat     (0),
        ParserUnsupportedVersion    (0),
        DBDuplicatedChannel         (0),
        DBUnknown                   (0),
        Unknown                     (0);

        int msgId; // matching message id

        Err(int msgId) {
            this.msgId = msgId;
        }

        int getMsgId() {
            return msgId;
        }
    }

    Err err;

    FeederException() {
        super();
    }

    FeederException(String message, Throwable cause) {
        super(message, cause);
    }

    FeederException(String message) {
        super(message);
    }

    FeederException(Throwable cause) {
        super(cause);
    }

    FeederException(Err err) {
        this.err = err;
    }

    Err
    getError() {
        return err;
    }
}
