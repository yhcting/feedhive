package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskDownloadToDB;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DB.ColumnItem;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.Utils;

public class ItemViewActivity extends Activity {
    private long    id  = -1;
    private String  netUrl = "";
    private String  fileUrl = "";
    private String  currUrl = "";
    private WebView wv  = null;
    private ProgressBar pb = null;

    private class WVClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Should NOT open at NEW window.
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            pb.setVisibility(View.GONE);
        }
    }

    private class WCClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            // Activities and WebViews measure progress with different scales.
            // The progress meter will automatically disappear when we reach 100%
            pb.setProgress(progress);
          }
    }

    private class RTTaskManagerEventHandler implements RTTask.OnRTTaskManagerEvent {
        @Override
        public void
        onUpdateBGTaskRegister(long cid, BGTask task) { }

        @Override
        public void onUpdateBGTaskUnregister(long cid, BGTask task) { }

        @Override
        public void
        onDownloadBGTaskRegster(long id, BGTask task) {
            RTTask.S().bindDownload(id, new DownloadToDBBGTaskOnEvent());
        }

        @Override
        public void onDownloadBGTaskUnegster(long id, BGTask task) { }
    }

    private class DownloadToDBBGTaskOnEvent implements
    BGTaskDownloadToDB.OnEvent<BGTaskDownloadToDB.Arg, Object> {
        @Override
        public void
        onProgress(BGTask task, long progress) { }

        @Override
        public void
        onCancel(BGTask task, Object param) {
            postSetupLayout();
        }

        @Override
        public void
        onPreRun(BGTask task) { }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            logI("ItemViewActivity : DownloadToDBBGTaskOnEvent : onPostRun");
            postSetupLayout();
        }
    }

    private void
    startDownload() {
        BGTaskDownloadToDB dnTask = new BGTaskDownloadToDB(this);
        RTTask.S().registerDownload(id, dnTask);
        dnTask.start(new BGTaskDownloadToDB.Arg(netUrl, id,
                                                DB.ColumnItem.RAWDATA));
    }

    private void
    cancelDownload() {
        BGTask task = RTTask.S().getDownload(id);
        task.cancel(null);
    }

    private void
    notifyResult() {
        Err result = RTTask.S().getDownloadErr(id);
        LookAndFeel.showTextToast(ItemViewActivity.this, result.getMsgId());
        RTTask.S().consumeDownloadResult(id);
        setupLayout();
    }

    private void
    postSetupLayout() {
        wv.post(new Runnable() {
            @Override
            public void run() {
                setupLayout();
            }
         });
    }

    private void
    setupLayout() {
        ImageView imgbtn = (ImageView)findViewById(R.id.imgbtn);

        ItemViewActivity.this.findViewById(R.id.imgbtn).setVisibility(View.VISIBLE);
        Animation anim = imgbtn.getAnimation();
        if (null != anim) {
            anim.cancel();
            anim.reset();
        }
        imgbtn.setAlpha(0.5f);

        RTTask.StateDownload state = RTTask.S().getDownloadState(id);
        logI("ItemViewActivity : setupLayout : state : " + state.name());
        if (RTTask.StateDownload.Idle == state) {

            if (currUrl.equals(fileUrl)) {
                imgbtn.setImageResource(R.drawable.ic_goto);
                imgbtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        currUrl = netUrl;
                        pb.setProgress(0);
                        pb.setVisibility(View.VISIBLE);
                        wv.loadUrl(currUrl);
                        postSetupLayout();
                    }
                });
            } else {
                byte[] htmldata = DBPolicy.S().getItemInfoData(id, DB.ColumnItem.RAWDATA);
                if (0 == htmldata.length) {
                    imgbtn.setImageResource(R.drawable.download_anim0);
                    imgbtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startDownload();
                            postSetupLayout();
                        }
                    });
                } else {
                    ItemViewActivity.this.findViewById(R.id.imgbtn).setVisibility(View.GONE);
                }
            }

        } else if (RTTask.StateDownload.Downloading == state) {

            imgbtn.setImageResource(R.drawable.download);
            ((AnimationDrawable)imgbtn.getDrawable()).start();
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelDownload();
                    postSetupLayout();
                }
            });

        } else if (RTTask.StateDownload.Canceling == state) {

            imgbtn.setImageResource(R.drawable.ic_block);
            imgbtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_inout));
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LookAndFeel.showTextToast(ItemViewActivity.this, R.string.wait_cancel);
                }
            });

        } else if (RTTask.StateDownload.DownloadFailed == state) {

            imgbtn.setImageResource(R.drawable.ic_info);
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    notifyResult();
                }
            });

        } else
            eAssert(false);
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.item_view);
        pb = (ProgressBar)findViewById(R.id.progressbar);
        pb.setMax(100); // to use percent
        wv = (WebView)findViewById(R.id.webview);
        wv.setWebViewClient(new WVClient());
        wv.setWebChromeClient(new WCClient());

        id = getIntent().getLongExtra("id", -1);
        eAssert(id >= 0);

        netUrl = DBPolicy.S().getItemInfoString(id, ColumnItem.LINK);
        // Create html file from DataBase raw data of this link page.
        String tempHtmlPath = getFilesDir() + File.separator + "___itemView_temp__.html";
        fileUrl = "file:///" + tempHtmlPath;

        byte[] htmldata = DBPolicy.S().getItemInfoData(id, DB.ColumnItem.RAWDATA);
        if (0 == htmldata.length)
            currUrl = netUrl;
        else {
            Utils.writeToFile(tempHtmlPath, htmldata);
            new File(tempHtmlPath).deleteOnExit();
            currUrl = fileUrl;
        }
        wv.loadUrl(currUrl);

        RTTask.S().registerManagerEventListener(this, new RTTaskManagerEventHandler());
    }

    @Override
    protected void
    onResume() {
        super.onResume();
        // Bind download task if needed
        RTTask.StateDownload state = RTTask.S().getDownloadState(id);
        if (RTTask.StateDownload.Idle != state)
            RTTask.S().bindDownload(id, new DownloadToDBBGTaskOnEvent());

        setupLayout();
    }

    @Override
    protected void
    onPause() {
        logI("==> ItemListActivity : onPause");
        super.onPause();

        // See comments in 'ChannelListActivity.onPause()'
        RTTask.S().unbind();
    }

    @Override
    protected void
    onStop() {
        logI("==> ItemListActivity : onStop");
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        if (null != wv)
            wv.destroy();
        super.onDestroy();
    }
}
