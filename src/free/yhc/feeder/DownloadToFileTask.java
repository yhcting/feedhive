package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.logI;
import static free.yhc.feeder.model.Utils.logW;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Utils;

public class DownloadToFileTask extends AsyncTask<String, Integer, Err> implements
DialogInterface.OnClickListener,
DialogInterface.OnCancelListener {
    interface OnEvent {
        void onPostExecute(DownloadToFileTask task, Err result);
    }

    private Context        context      = null;
    private OnEvent        onEvent      = null;
    private ProgressDialog dialog;
    private String         outFilePath  = "";
    private String         tempFilePath = "";
    private InputStream    inputStream  = null;
    private OutputStream   outputStream = null;
    private boolean        userCancelled= false;

    // Why is 'tempFilePath' required.
    // If orientation is changed, UI may check whether file is exists or not again.
    // Therefore, writing data to destination file directly may lead UI to misunderstand that
    //   destination file is already exists even if it is under downloading.
    // To prevent this, tempFilePath is used until download is fully completed.
    DownloadToFileTask(Context context, String outFilePath, String tempFilePath, OnEvent onEvent) {
        super();
        Utils.eAssert(Utils.isValidValue(outFilePath) && Utils.isValidValue(tempFilePath));
        this.context = context;
        this.outFilePath = outFilePath;
        this.tempFilePath = tempFilePath;
        this.onEvent = onEvent;
    }

    @Override
    protected void
    onPreExecute() {
        // remove tempfile to use.
        new File(tempFilePath).delete();

        dialog = new ProgressDialog(context);
        dialog.setMessage(context.getResources().getText(R.string.downloading));
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setButton(context.getResources().getText(R.string.cancel_downloading), this);
        dialog.setOnCancelListener(this);
        dialog.show();
    }

    private boolean
    cleanupStream() {
        try {
            if (null != inputStream)
                inputStream.close();
            if (null != outputStream) {
                outputStream.close();
                if (!new File(tempFilePath).delete()) {
                    logI("**** Fail to delete temporal downloaded file!!! [" + tempFilePath +"]\n");
                    return false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logW(e.getMessage());
            return false;
        }
        return true;
    }

    // args[0] : url to download
    @Override
    protected Err
    doInBackground(String... aurl) {
        try {
            URL url = new URL(aurl[0]);
            URLConnection conn = url.openConnection();
            conn.connect();

            int lenghtOfFile = conn.getContentLength();
            logI("Download File\n" +
                 "    lenght: " + lenghtOfFile + "\n" +
                 "    to    : " + outFilePath);

            inputStream = new BufferedInputStream(url.openStream());
            outputStream = new FileOutputStream(tempFilePath);

            byte data[] = new byte[256*1024];

            long total = 0;
            int count;

            while (true) {
                if (-1 == (count = inputStream.read(data)))
                    break;
                outputStream.write(data, 0, count);
                total += count;
                publishProgress((int) (total * 100 / lenghtOfFile));
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            // User's canceling operation close in/out stream in force.
            // And this leads to IOException here.
            // So, we should check that this Exception is caused by user's cancel or real IOException.
            if (userCancelled) {
                return Err.NoErr;
            } else {
                e.printStackTrace();
                logW(e.getMessage());
                cleanupStream();
                return Err.IONet;
            }
        }
        return Err.NoErr;
    }

    private boolean
    cancelWork() {
        int retry = 20;
        userCancelled = true;
        while (0 < retry-- && !cancel(true));
        if (retry > 0) {
            int retryCleanup = 5;
            while (0 < retryCleanup-- && !cleanupStream());
        }
        return retry > 0? true: false;
    }

    @Override
    public void
    onClick(DialogInterface dialogI, int which) {
        dialog.cancel();
    }

    @Override
    public void
    onCancel(DialogInterface dialogI) {
        cancelWork();
    }

    @Override
    protected void
    onProgressUpdate(Integer... progress) {
        dialog.setProgress(progress[0]);
    }

    @Override
    protected void
    onPostExecute(Err result) {
        dialog.dismiss();

        // In normal case, onPostExecute is not called in case of 'user-cancel'.
        // below code is for safety.
        if (userCancelled)
            return; // onPostExecute SHOULD NOT be called in case of user-cancel

        if (Err.NoErr == result) {
            if (!new File(tempFilePath).renameTo(new File(outFilePath)))
                result = Err.IOFile;
        }

        if (null != onEvent)
            onEvent.onPostExecute(this, result);
    }
}
