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
import free.yhc.feeder.model.Err;

public class DownloadToFileTask extends AsyncTaskMy {
    private ProgressDialog progressDialog;
    private String         outFilePath;
    private InputStream    inputStream = null;
    private OutputStream   outputStream = null;
    private Context        context = null;

    DownloadToFileTask(String outFilePath, Context context,
                       OnPostExecute post, OnDoWork work) {
        super(post, work);
        this.context = context;
        this.outFilePath = outFilePath;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Downloading file..");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    // args[0] : url to download
    @Override
    protected Err
    doInBackground(Object... aurl) {
        super.doInBackground(aurl);

        try {
            URL url = new URL((String)aurl[0]);
            URLConnection conn = url.openConnection();
            conn.connect();

            int lenghtOfFile = conn.getContentLength();
            logI("Download File\n" +
                 "    lenght: " + lenghtOfFile + "\n" +
                 "    to    : " + outFilePath);

            inputStream = new BufferedInputStream(url.openStream());
            outputStream = new FileOutputStream(outFilePath);

            // alloc a-little-bit-big-buffer for performance.
            byte data[] = new byte[256*1024];

            long total = 0;
            int count;
            while ((count = inputStream.read(data)) != -1) {
                total += count;
                publishProgress((int) (total * 100 / lenghtOfFile));
                outputStream.write(data, 0, count);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            logW(e.getMessage());
            try {
                if (null != inputStream)
                    inputStream.close();
                if (null != outputStream) {
                    outputStream.close();
                    new File(outFilePath).delete();
                }
            } catch (IOException e2) {
                e2.printStackTrace();
                logW(e2.getMessage());
            }
            return Err.IONet;
        }
        return Err.NoErr;
    }

    @Override
    protected void
    onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);
        progressDialog.setProgress(progress[0]);
    }

    @Override
    protected void
    onCancelled() {
        try {
            if (null != outputStream)
                outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            logW(e.getMessage());
        }
        new File(outFilePath).delete();

        super.onCancelled();
    }

    @Override
    protected void
    onPostExecute(Err result) {
        progressDialog.dismiss();

        super.onPostExecute(result);
    }
}
