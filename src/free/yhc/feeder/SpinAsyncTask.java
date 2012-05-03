/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.logI;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.FeederException;
import free.yhc.feeder.model.NetLoader;

public class SpinAsyncTask extends AsyncTask<Object, Integer, Err> implements
DialogInterface.OnDismissListener,
DialogInterface.OnCancelListener,
DialogInterface.OnClickListener
{

    private Context        context      = null;
    private long           cid          = -1;
    private ProgressDialog dialog       = null;
    private int            msgid        = -1;
    private OnEvent        onEvent      = null;
    private boolean        userCancelled= false;
    private boolean        cancelable   = true;

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

    SpinAsyncTask(Context context, OnEvent onEvent, int msgid, boolean cancelable) {
        super();
        this.context = context;
        this.onEvent = onEvent;
        this.msgid   = msgid;
        this.cancelable = cancelable;
    }

    public Err
    updateLoad(Object obj)
            throws FeederException {
        cid = ((Long)obj).longValue();
        try {
            new NetLoader().updateLoad(cid);
            return Err.NoErr;
        } catch (FeederException e) {
            return e.getError();
        }
    }

    // return :
    @Override
    protected Err
    doInBackground(Object... objs) {
        logI("* Start background Job : SpinSyncTask\n");
        Err ret = Err.NoErr;
        if (null != onEvent)
            ret = onEvent.onDoWork(this, objs);
        return ret;
    }

    private boolean
    cancelWork() {
        if (!cancelable)
            return false;
        // See comments in BGTaskUpdateChannel.cancel()
        userCancelled = true;
        cancel(true);
        return true;
    }

    @Override
    public void
    onCancel(DialogInterface dialogI) {
        if (!cancelable)
            return;
        cancelWork();
    }

    @Override
    public void
    onClick(DialogInterface dialogI, int which) {
        if (!cancelable)
            return;
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

        if (cancelable) {
            dialog.setButton(context.getResources().getText(R.string.cancel), this);
            dialog.setOnCancelListener(this);
        } else
            dialog.setCancelable(false);

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