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
DialogInterface.OnClickListener
{
    interface OnEvent {
        Err  onDoWork(NetLoaderTask task, Object... objs);
        void onPostExecute(NetLoaderTask task, Err result);
    }

    private ProgressDialog progress = null;
    private Context        context  = null;
    private OnEvent        onEvent  = null;

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

    public void
    onClick(DialogInterface dialog, int which) {
        progress.setMessage(context.getResources().getText(R.string.wait_cancel));
        // Canceling backgroud task may corrupt DB
        //   by interrupting in the middle of transaction.
        // To avoid this case, start db transaction to cancel!.
        int retry = 20;
        try {
            DBPolicy.get().lock();
        } catch (InterruptedException e) {
            eAssert(false);
            return;
        }

        while (0 < retry--) {
            cancel(true);
            if (isCancelled())
                break;
        }

        DBPolicy.get().unlock();

        progress.dismiss();
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

    @Override
    protected void
    onCancelled(Err err) {
        // TODO
        //   Handle cancelled case!!!
        ;
    }

    @Override
    protected void
    onPostExecute(Err result) {
        progress.dismiss();
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
        progress = new ProgressDialog(context);
        progress.setMessage(context.getResources().getText(R.string.load_progress));
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setCanceledOnTouchOutside(false);
        progress.setCancelable(false);
        progress.setButton(context.getResources().getText(R.string.cancel_loading), this);
        progress.setOnDismissListener(this);
        progress.show();
    }

    /*
    @Override
    protected void
    onProgressUpdate(Integer... v) {
        super.onProgressUpdate(v);
    }
    */
}
