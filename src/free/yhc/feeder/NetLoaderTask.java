package free.yhc.feeder;

import android.app.ProgressDialog;
import android.content.Context;
import free.yhc.feeder.model.Err;

public class NetLoaderTask extends AsyncTaskMy {

    private ProgressDialog progress = null;
    private Context        context  = null;

    NetLoaderTask(Context context, OnPostExecute post, OnDoWork worker) {
        super(post, worker);
        this.context = context;
    }

    // chids  : channel id to load it's item from network.
    //          '0' id means 'All'.
    // return :
    @Override
    protected Err
    doInBackground(Object... objs) {
        // FIXME
        //   How can we guarantee NOT-Corrupte-DB
        return super.doInBackground(objs);
    }

    @Override
    protected void
    onCancelled() {
        super.onCancelled();
    }

    @Override
    protected void
    onPostExecute(Err result) {
        progress.dismiss();
        super.onPostExecute(result);
    }

    @Override
    protected void
    onPreExecute() {
        super.onPreExecute();

        progress = new ProgressDialog(context);

        progress.setMessage(context.getResources().getText(R.string.fetch_progess));
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.show();
    }

    @Override
    protected void
    onProgressUpdate(Integer... v) {
        super.onProgressUpdate(v);
    }
}
