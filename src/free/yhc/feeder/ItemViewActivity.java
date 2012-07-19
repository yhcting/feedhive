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

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskDownloadToFile;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class ItemViewActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    public static final int RESULT_DOWNLOAD = 1;

    private final UIPolicy      uip = UIPolicy.get();
    private final DBPolicy      dbp = DBPolicy.get();
    private final RTTask        rtt = RTTask.get();
    private final LookAndFeel   lnf = LookAndFeel.get();

    private long        id      = -1;
    private String      netUrl  = "";
    private String      fileUrl = "";
    private String      currUrl = "";
    private WebView     wv      = null;
    private ProgressBar pb      = null;

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

        @Override
        public void onReachedMaxAppCacheSize(long spaceNeeded, long totalUsedQuota,
                                             WebStorage.QuotaUpdater quotaUpdater) {
            logI("ItemViewActivity : WebView : ReachedMaxAppCacheSize : " + spaceNeeded);
            quotaUpdater.updateQuota(spaceNeeded * 2);
        }
    }

    private class RTTaskRegisterListener implements RTTask.OnRegisterListener {
        @Override
        public void
        onRegister(BGTask task, long cid, RTTask.Action act) {
            if (RTTask.Action.DOWNLOAD == act)
                rtt.bind(id, RTTask.Action.DOWNLOAD, this, new DownloadBGTaskListener());
        }

        @Override
        public void onUnregister(BGTask task, long cid, RTTask.Action act) { }
    }

    private class DownloadBGTaskListener implements BGTask.OnEventListener<BGTaskDownloadToFile.Arg, Object> {
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
        onPreRun(BGTask task) {
            postSetupLayout();
        }

        @Override
        public void
        onPostRun(BGTask task, Err result) {
            logI("ItemViewActivity : DownloadToDBBGTaskListener : onPostRun");
            Intent i = new Intent();
            i.putExtra("id", id);
            setResult(RESULT_DOWNLOAD, i);
            postSetupLayout();
        }
    }

    private void
    startDownload() {
        BGTaskDownloadToFile dnTask
            = new BGTaskDownloadToFile(new BGTaskDownloadToFile.Arg(netUrl,
                                                                    dbp.getItemInfoDataFile(id),
                                                                    uip.getNewTempFile()));
        rtt.register(id, RTTask.Action.DOWNLOAD, dnTask);
        rtt.start(id, RTTask.Action.DOWNLOAD);
    }

    private void
    cancelDownload() {
        rtt.cancel(id, RTTask.Action.DOWNLOAD, null);
    }

    private void
    notifyResult() {
        Err result = rtt.getErr(id, RTTask.Action.DOWNLOAD);
        lnf.showTextToast(ItemViewActivity.this, result.getMsgId());
        rtt.consumeResult(id, RTTask.Action.DOWNLOAD);
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

        RTTask.TaskState state = rtt.getState(id, RTTask.Action.DOWNLOAD);
        logI("ItemViewActivity : setupLayout : state : " + state.name());
        switch(state) {
        case IDLE:
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
                if (dbp.getItemInfoDataFile(id).exists()) {
                    ItemViewActivity.this.findViewById(R.id.imgbtn).setVisibility(View.GONE);
                } else {
                    imgbtn.setImageResource(R.drawable.download_anim0);
                    imgbtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startDownload();
                            postSetupLayout();
                        }
                    });
                }
            }
            break;

        case RUNNING:
        case READY:
            imgbtn.setImageResource(R.anim.download);
            ((AnimationDrawable)imgbtn.getDrawable()).start();
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelDownload();
                    postSetupLayout();
                }
            });
            break;

        case CANCELING:
            imgbtn.setImageResource(R.drawable.ic_block);
            imgbtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_inout));
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lnf.showTextToast(ItemViewActivity.this, R.string.wait_cancel);
                }
            });
            break;

        case FAILED:
            imgbtn.setImageResource(R.drawable.ic_info);
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    notifyResult();
                }
            });
            break;

        default:
            eAssert(false);
        }
    }

    private void
    setWebSettings(WebView wv) {
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setSavePassword(true);

        // Enabling cache.
        ws.setDomStorageEnabled(true);
        // Set cache size to 8 mb by default. should be more than enough
        ws.setAppCacheMaxSize(1024*1024*8);
        ws.setAppCachePath(getCacheDir().getAbsolutePath());
        ws.setAllowFileAccess(true);
        ws.setAppCacheEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ItemViewActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.item_view);
        pb = (ProgressBar)findViewById(R.id.progressbar);
        pb.setMax(100); // to use percent
        wv = (WebView)findViewById(R.id.webview);
        wv.setWebViewClient(new WVClient());
        wv.setWebChromeClient(new WCClient());
        setWebSettings(wv);

        id = getIntent().getLongExtra("id", -1);
        eAssert(id >= 0);

        File f = dbp.getItemInfoDataFile(id);
        netUrl = dbp.getItemInfoString(id, DB.ColumnItem.LINK);
        fileUrl = "file:///" + f.getAbsolutePath();
        currUrl = f.exists()? fileUrl: netUrl;
        wv.loadUrl(currUrl);
    }

    @Override
    protected void
    onResume() {
        super.onResume();
        // See comments in 'ChannelListActivity.onResume' around 'registerManagerEventListener'
        rtt.registerRegisterEventListener(this, new RTTaskRegisterListener());

        // Bind download task if needed
        RTTask.TaskState state = rtt.getState(id, RTTask.Action.DOWNLOAD);
        if (RTTask.TaskState.IDLE != state)
            rtt.bind(id, RTTask.Action.DOWNLOAD, this, new DownloadBGTaskListener());

        setupLayout();
    }

    @Override
    protected void
    onPause() {
        //logI("==> ItemListActivity : onPause");
        // See comments in 'ChannelListActivity.onPause' around 'unregisterManagerEventListener'
        rtt.unregisterRegisterEventListener(this);
        // See comments in 'ChannelListActivity.onPause()'
        rtt.unbind(this);
        super.onPause();
    }

    @Override
    protected void
    onStop() {
        //logI("==> ItemListActivity : onStop");
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        if (null != wv)
            wv.destroy();
        super.onDestroy();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }
}
