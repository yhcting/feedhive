package free.yhc.feeder;

import android.os.AsyncTask;
import free.yhc.feeder.model.Err;

public class AsyncTaskMy extends AsyncTask<Object, Integer, Err>  {
    interface OnPostExecute {
        void onPostExecute(Err result);
    }

    interface OnDoWork {
        Err doWork(Object... objs);
    }

    private OnPostExecute  post     = null;
    private OnDoWork       worker   = null;

    AsyncTaskMy() {

    }

    AsyncTaskMy(OnPostExecute post, OnDoWork worker) {
        this.post = post;
        this.worker = worker;
    }

    @Override
    protected Err
    doInBackground(Object... objs) {
        if (null != worker)
            return worker.doWork(objs);
        return Err.NoErr;
    }

    @Override
    protected void
    onCancelled() {
        super.onCancelled();
    }

    @Override
    protected void
    onPostExecute(Err result) {
        super.onPostExecute(result);
        if (null != post)
            post.onPostExecute(result);
    }
}
