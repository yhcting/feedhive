package free.yhc.feeder.model;

public class FeederException extends Exception {
    private static final long serialVersionUID = 1L;

    // Err codes.
    // TODO : set correct string resource id
    public static enum Err {
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

    protected Err err;

    public FeederException() {
        super();
    }

    public FeederException(String message, Throwable cause) {
        super(message, cause);
    }

    public FeederException(String message) {
        super(message);
    }

    public FeederException(Throwable cause) {
        super(cause);
    }

    public FeederException(Err err) {
        this.err = err;
    }

    public Err
    getError() {
        return err;
    }
}
