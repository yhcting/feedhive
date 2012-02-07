package free.yhc.feeder.model;

public class FeederException extends Exception {
    private static final long serialVersionUID = 1L;

    private Err err;

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
