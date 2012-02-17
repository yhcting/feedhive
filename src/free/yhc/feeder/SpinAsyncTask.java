package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.NetLoader;
import free.yhc.feeder.model.Utils;

public class SpinAsyncTask extends AsyncTask<Object, Integer, Err> implements
DialogInterface.OnDismissListener,
DialogInterface.OnCancelListener,
DialogInterface.OnClickListener
{

    private Context        context      = null;
    private ProgressDialog dialog       = null;
    private int            msgid        = -1;
    private OnEvent        onEvent      = null;
    private boolean        userCancelled= false;

    interface OnEvent {
        Err  onDoWork(SpinAsyncTask task, Object... objs);
        void onPostExecute(SpinAsyncTask task, Err result);
    }

    SpinAsyncTask(Context context, OnEvent onEvent, int msgid) {
        super();
        this.context = context;
        this.onEvent = onEvent;
        this.msgid   = msgid;
    }

    public Err
    initialLoad(long[] outcid, Object... objs)
            throws InterruptedException {
        long categoryid = ((Long)objs[0]).longValue();
        String url      = (String)objs[1];
        return new NetLoader().initialLoad(categoryid, url, outcid);
    }

    public Err
    loadFeeds(Object obj)
            throws InterruptedException {
        return new NetLoader().loadFeeds(((Long)obj).longValue());
    }

    // return :
    @Override
    protected Err
    doInBackground(Object... objs) {
        Utils.resetTimeLog();
        logI("* Start background Job : SpinSyncTask\n");
        Err ret = Err.NoErr;
        if (null != onEvent)
            ret = onEvent.onDoWork(this, objs);
        return ret;
    }

    private boolean
    cancelWork() {
        userCancelled = true;
        // Canceling backgroud task may corrupt DB
        //   by interrupting in the middle of transaction.
        // To avoid this case, start db transaction to cancel!.
        int retry = 20;
        try {
            DBPolicy.get().lock();
        } catch (InterruptedException e) {
            eAssert(false);
            return false;
        }
        while (0 < retry-- && !cancel(true));
        DBPolicy.get().unlock();

        return retry > 0? true: false;
    }

    @Override
    public void
    onCancel(DialogInterface dialogI) {
        cancelWork();
    }

    @Override
    public void
    onClick(DialogInterface dialogI, int which) {
        dialog.setMessage(context.getResources().getText(R.string.wait_cancel));
        dialog.cancel();
    }

    @Override
    protected void
    onPostExecute(Err result) {
        dialog.dismiss();

        // In normal case, onPostExecute is not called in case of 'user-cancel'.
        // below code is for safety.
        if (userCancelled)
            return; // onPostExecute SHOULD NOT be called in case of user-cancel

        if (null != onEvent)
            onEvent.onPostExecute(this, result);
    }

    @Override
    public void
    onDismiss(DialogInterface dialog) {
    }

    @Override
    protected void
    onPreExecute() {
        dialog = new ProgressDialog(context);
        dialog.setMessage(context.getResources().getText(msgid));
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCanceledOnTouchOutside(false);
        //dialog.setCancelable(false);
        dialog.setButton(context.getResources().getText(R.string.cancel_processing), this);
        dialog.setOnCancelListener(this);
        dialog.setOnDismissListener(this);
        dialog.show();
    }

    /*
    @Override
    protected void
    onProgressUpdate(Integer... v) {
        super.onProgressUpdate(v);
    }
    */
}