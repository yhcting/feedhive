/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
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
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.BGTask;
import free.yhc.feeder.model.BGTaskDownloadToItemContent;
import free.yhc.feeder.model.BaseBGTask;
import free.yhc.feeder.model.ContentsManager;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ItemViewActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ItemViewActivity.class);

    public static final int RESULT_DOWNLOAD = 1;

    private final DBPolicy      mDbp = DBPolicy.get();
    private final ContentsManager mCm = ContentsManager.get();
    private final RTTask        mRtt = RTTask.get();

    private long        mId      = -1;
    private String      mNetUrl  = "";
    private String      mFileUrl = "";
    private String      mCurUrl = "";
    private WebView     mWv      = null;
    private ProgressBar mPb      = null;

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
            mPb.setVisibility(View.GONE);
        }
    }

    private class WCClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            // Activities and WebViews measure progress with different scales.
            // The progress meter will automatically disappear when we reach 100%
            mPb.setProgress(progress);
        }

        @Override
        public void onReachedMaxAppCacheSize(long spaceNeeded, long totalUsedQuota,
                                             WebStorage.QuotaUpdater quotaUpdater) {
            if (DBG) P.d("space : " + spaceNeeded);
            quotaUpdater.updateQuota(spaceNeeded * 2);
        }
    }

    private class RTTaskRegisterListener implements RTTask.OnRegisterListener {
        @Override
        public void
        onRegister(BGTask task, long cid, RTTask.Action act) {
            if (RTTask.Action.DOWNLOAD == act)
                mRtt.bind(mId, RTTask.Action.DOWNLOAD, this, new DownloadBGTaskListener());
        }

        @Override
        public void onUnregister(BGTask task, long cid, RTTask.Action act) { }
    }

    private class DownloadBGTaskListener extends BaseBGTask.OnEventListener {
        @Override
        public void
        onCancelled(BaseBGTask task, Object param) {
            postSetupLayout();
        }

        @Override
        public void
        onPreRun(BaseBGTask task) {
            postSetupLayout();
        }

        @Override
        public void
        onPostRun(BaseBGTask task, Err result) {
            if (DBG) P.v("Enter");
            Intent i = new Intent();
            i.putExtra("id", mId);
            setResult(RESULT_DOWNLOAD, i);
            postSetupLayout();
        }
    }

    private void
    startDownload() {
        BGTaskDownloadToItemContent dnTask
            = new BGTaskDownloadToItemContent(mNetUrl, mId);
        mRtt.register(mId, RTTask.Action.DOWNLOAD, dnTask);
        mRtt.start(mId, RTTask.Action.DOWNLOAD);
    }

    private void
    cancelDownload() {
        mRtt.cancel(mId, RTTask.Action.DOWNLOAD, null);
    }

    private void
    notifyResult() {
        Err result = mRtt.getErr(mId, RTTask.Action.DOWNLOAD);
        UiHelper.showTextToast(ItemViewActivity.this, result.getMsgId());
        mRtt.consumeResult(mId, RTTask.Action.DOWNLOAD);
        setupLayout();
    }

    private void
    postSetupLayout() {
        mWv.post(new Runnable() {
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

        RTTask.TaskState state = mRtt.getState(mId, RTTask.Action.DOWNLOAD);
        if (DBG) P.v("state : " + state.name());
        switch(state) {
        case IDLE:
            if (mCurUrl.equals(mFileUrl)) {
                imgbtn.setImageResource(R.drawable.ic_goto);
                imgbtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCurUrl = mNetUrl;
                        mPb.setProgress(0);
                        mPb.setVisibility(View.VISIBLE);
                        mWv.loadUrl(mCurUrl);
                        postSetupLayout();
                    }
                });
            } else {
                if (mCm.getItemInfoDataFile(mId).exists()) {
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
                    UiHelper.showTextToast(ItemViewActivity.this, R.string.wait_cancel);
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
        mPb = (ProgressBar)findViewById(R.id.progressbar);
        mPb.setMax(100); // to use percent
        mWv = (WebView)findViewById(R.id.webview);
        mWv.setWebViewClient(new WVClient());
        mWv.setWebChromeClient(new WCClient());
        setWebSettings(mWv);

        mId = getIntent().getLongExtra("id", -1);
        eAssert(mId >= 0);

        // NOTE
        // There is no use case that 'null == f' here!
        File f = mCm.getItemInfoDataFile(mId);
        eAssert(null != f);
        mNetUrl = mDbp.getItemInfoString(mId, ColumnItem.LINK);
        mFileUrl = "file:///" + f.getAbsolutePath();
        mCurUrl = f.exists()? mFileUrl: mNetUrl;
        mWv.loadUrl(mCurUrl);
    }

    @Override
    protected void
    onResume() {
        super.onResume();
        // See comments in 'ChannelListActivity.onResume' around 'registerManagerEventListener'
        mRtt.registerRegisterEventListener(this, new RTTaskRegisterListener());

        // Bind download task if needed
        RTTask.TaskState state = mRtt.getState(mId, RTTask.Action.DOWNLOAD);
        if (RTTask.TaskState.IDLE != state)
            mRtt.bind(mId, RTTask.Action.DOWNLOAD, this, new DownloadBGTaskListener());

        setupLayout();
    }

    @Override
    protected void
    onPause() {
        // See comments in 'ChannelListActivity.onPause' around 'unregisterManagerEventListener'
        mRtt.unregisterRegisterEventListener(this);
        // See comments in 'ChannelListActivity.onPause()'
        mRtt.unbind(this);
        super.onPause();
    }

    @Override
    protected void
    onStop() {
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        if (null != mWv)
            mWv.destroy();
        super.onDestroy();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }
}
