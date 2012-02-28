package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import android.os.Handler;

public class BGTask<User, RunParam, CancelParam> extends Thread {
    // volatile is used to be thread safe.
    // (handler and onEvent is used in and out side of 'run()').
    private volatile Handler handler = null;
    private volatile OnEvent onEvent = null;
    private volatile Err     result  = Err.NoErr;
    private volatile boolean cancelled = false;

    private User             userObj     = null;
    private RunParam         runParam    = null;
    private CancelParam      cancelParam = null;

    public interface OnEvent<User, RunParam, CancelParam> {
        // return : false (DO NOT run this task)
        void onPreRun  (BGTask<User, RunParam, CancelParam> task, User user);
        void onPostRun (BGTask<User, RunParam, CancelParam> task, User user, Err result);
        void onCancel  (BGTask<User, RunParam, CancelParam> task, CancelParam param, User user);
        void onProgress(BGTask<User, RunParam, CancelParam> task, User user, int progress);
    }

    public BGTask(User obj) {
        super();
        userObj = obj;
        // context of default handler is current(caller) thread!
        handler = new Handler();
    }

    public BGTask(OnEvent onEvent, User obj) {
        super();
        userObj = obj;
        // context of default handler is current(caller) thread!
        handler = new Handler();
        this.onEvent = onEvent;
    }

    void
    resetResult() {
        eAssert(!isAlive());
        result = Err.NoErr;
    }

    public Err
    getResult() {
        eAssert(null != result);
        return result;
    }

    public User
    getUserObject() {
        return userObj;
    }

    // Attach to given thread handler.
    public void
    attach(Handler handler) {
        this.handler = handler;
    }

    // Attach to current thread
    public void
    attach() {
        this.handler = new Handler();
    }

    public void
    setOnEventListener(OnEvent<?, ?, ?> onEvent) {
        // This is atomic expression
        this.onEvent = onEvent;
    }

    // main background task to do.
    protected Err
    doBGTask(RunParam runParam)
        throws InterruptedException {
        return Err.NoErr;
    }

    @Override
    public final void
    run() {
        boolean bInterrupted = false;
        try {
            result =  doBGTask(runParam);
            eAssert(null != result);
        } catch (InterruptedException e) {
            bInterrupted = true;
        }

        if (!bInterrupted)
            bInterrupted = Thread.interrupted();

        eAssert(null != handler && null != onEvent);

        if (bInterrupted
            || cancelled
            || Err.Interrupted == result
            || Err.UserCancelled == result) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onEvent.onCancel(BGTask.this, cancelParam, userObj);
                }
            });

        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onEvent.onPostRun(BGTask.this, userObj, result);
                }
            });
        }
    }

    public void
    publishProgress(final int progress) {
        eAssert(null != handler && null != onEvent);
        handler.post(new Runnable() {
            @Override
            public void run() {
                onEvent.onProgress(BGTask.this, userObj, progress);
            }
        });
    }

    public final void
    start(RunParam runParam) {
        eAssert(null != onEvent);
        onEvent.onPreRun(BGTask.this, userObj);
        this.runParam = runParam;
        super.start();
    }

    public boolean
    cancel(CancelParam param) {
        cancelled = true;
        result = Err.UserCancelled;
        cancelParam = param;
        interrupt();
        return true; // always success.
    }
}
