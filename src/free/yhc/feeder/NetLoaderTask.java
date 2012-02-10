package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.NetLoader;

public class NetLoaderTask extends AsyncTask<Object, Integer, Err> implements
DialogInterface.OnDismissListener,
DialogInterface.OnCancelListener,
DialogInterface.OnClickListener
{
    interface OnEvent {
        Err  onDoWork(NetLoaderTask task, Object... objs);
        void onPostExecute(NetLoaderTask task, Err result);
    }

    private ProgressDialog dialog = null;
    private Context        context  = null;
    private OnEvent        onEvent  = null;
    private boolean        userCancelled = false;

    NetLoaderTask(Context context, OnEvent onEvent) {
        super();
        this.context = context;
        this.onEvent = onEvent;
    }

    public Err
    initialLoad(Object... objs)
            throws InterruptedException {
        Err err = Err.NoErr;

        for (Object o : objs) {
            String s = (String)o;
            err = new NetLoader().initialLoad(s);

            // TODO : handle returning error!!!
            if (Err.NoErr != err)
                break;
        }
        return err;
    }

    public Err
    loadFeeds(Object... objs)
            throws InterruptedException {
        Err err = Err.NoErr;

        for (Object o : objs) {
            Long l = (Long) o;
            err = new NetLoader().loadFeeds(l.longValue());

            // TODO : handle returning error!!!
            if (Err.NoErr != err)
                break;
        }
        return err;
    }

    // return :
    @Override
    protected Err
    doInBackground(Object... objs) {
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
        dialog.setMessage(context.getResources().getText(R.string.load_progress));
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCanceledOnTouchOutside(false);
        //dialog.setCancelable(false);
        dialog.setButton(context.getResources().getText(R.string.cancel_loading), this);
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
