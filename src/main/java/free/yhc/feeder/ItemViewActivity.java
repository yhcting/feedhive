/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

import java.io.File;
import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
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

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.TmTask;
import free.yhc.baselib.async.TaskBase;
import free.yhc.baselib.async.TaskManagerBase;
import free.yhc.baselib.net.NetReadTask;
import free.yhc.abaselib.util.UxUtil;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.task.DownloadTask;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

public class ItemViewActivity extends Activity implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ItemViewActivity.class, Logger.LOGLV_DEFAULT);

    public static final int RESULT_DOWNLOAD = 1;

    private final DBPolicy mDbp = DBPolicy.get();
    private final ContentsManager mCm = ContentsManager.get();
    private final RTTask mRtt = RTTask.get();
    private final RtTaskQEventListener mRtTaskQEventListener = new RtTaskQEventListener();
    private final DownloadTaskListener mDownloadTaskListener = new DownloadTaskListener();

    private long mId = -1;
    private String mNetUrl = "";
    private String mFileUrl = "";
    private String mCurUrl = "";
    private WebView mWv = null;
    private ProgressBar mPb = null;

    private class WVClient extends WebViewClient {
        @Override
        public void
        onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public boolean
        shouldOverrideUrlLoading(WebView view, String url) {
            // Should NOT open at NEW window.
            return false;
        }

        @Override
        public void
        onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mPb.setVisibility(View.GONE);
        }
    }

    private class WCClient extends WebChromeClient {
        @Override
        public void
        onProgressChanged(WebView view, int progress) {
            // Activities and WebViews measure progress with different scales.
            // The progress meter will automatically disappear when we reach 100%
            mPb.setProgress(progress);
        }

        @Override
        public void
        onReachedMaxAppCacheSize(long spaceNeeded,
                                 long totalUsedQuota,
                                 @NonNull WebStorage.QuotaUpdater quotaUpdater) {
            if (DBG) P.d("space : " + spaceNeeded);
            quotaUpdater.updateQuota(spaceNeeded * 2);
        }
    }

    private class RtTaskQEventListener implements RTTask.TaskQEventListener {
        @Override
        public void
        onEvent(@NonNull TaskManagerBase tm,
                @NonNull TaskManagerBase.TaskQEvent ev,
                int szReady, int szRun,
                @NonNull TmTask task) {
            RTTask rtt = (RTTask)tm;
            RTTask.TaskInfo ti = rtt.getTaskInfo(task);
            // This should be run on ui handler thread.
            switch (ev) {
            case ADDED_TO_READY:
                if (RTTask.Action.DOWNLOAD == ti.ttype) {
                    DownloadTask t = (DownloadTask)task;
                    t.addEventListener(mDownloadTaskListener);
                }
                break;
            case MOVED_TO_RUN:
            case REMOVED_FROM_READY:
            case REMOVED_FROM_RUN:
            }
        }
    }

    private class DownloadTaskListener extends TaskBase.EventListener<DownloadTask, NetReadTask.Result> {
        private void
        handleTaskDone(@NonNull DownloadTask task) {
            task.removeEventListener(mDownloadTaskListener);
        }

        @Override
        public void
        onStarted(@NonNull DownloadTask task) {
            postSetupLayout();
        }

        @Override
        public void
        onCancel(@NonNull DownloadTask task, Object param) {
            postSetupLayout();
        }

        @Override
        public void
        onCancelled(@NonNull DownloadTask task, Object param) {
            postSetupLayout();
            handleTaskDone(task);
        }

        @Override
        public void
        onPostRun(@NonNull DownloadTask task,
                  NetReadTask.Result result,
                  Exception ex) {
            if (DBG) P.v("Enter");
            Intent i = new Intent();
            i.putExtra("id", mId);
            setResult(RESULT_DOWNLOAD, i);
            postSetupLayout();
            handleTaskDone(task);
        }
    }

    private void
    startDownload() {
        try {
            DownloadTask t = DownloadTask.create(mNetUrl, mId);
            mRtt.addTask(t, mId, RTTask.Action.DOWNLOAD);
        } catch (IOException e) {
            UxUtil.showTextToast(Err.IO_FILE.getMsgId());
        }
    }

    private void
    cancelDownload() {
        DownloadTask t = mRtt.getDownloadTask(mId);
        if (null != t)
            mRtt.cancelTask(t, null);
    }

    private void
    notifyResult() {
        DownloadTask t = mRtt.getDownloadTask(mId);
        if (null == t)
            return; // nothing to do
        UxUtil.showTextToast(t.getErr().getMsgId());
        mRtt.removeWatchedTask(t);
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

        DownloadTask t = mRtt.getDownloadTask(mId);
        switch (mRtt.getRtState(t)) {
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
                        }
                    });
                }
            }
            break;

        case RUN:
        case READY:
            //noinspection ResourceType
            imgbtn.setImageResource(R.drawable.download);
            ((AnimationDrawable)imgbtn.getDrawable()).start();
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelDownload();
                }
            });
            break;

        case CANCEL:
            imgbtn.setImageResource(R.drawable.ic_block);
            imgbtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_inout));
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UxUtil.showTextToast(R.string.wait_cancel);
                }
            });
            break;

        case FAIL:
            imgbtn.setImageResource(R.drawable.ic_info);
            imgbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    notifyResult();
                }
            });
            break;

        default:
            P.bug(false);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void
    setWebSettings(WebView wv) {
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setSavePassword(true);

        // Enabling cache.
        ws.setDomStorageEnabled(true);
        // Set cache size to 8 mb by default. should be more than enough
        ws.setAppCacheMaxSize(1024 * 1024 * 8);
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
        P.bug(mId >= 0);

        // NOTE
        // There is no use case that 'null == f' here!
        File f = mCm.getItemInfoDataFile(mId);
        P.bug(null != f);
        mNetUrl = mDbp.getItemInfoString(mId, ColumnItem.LINK);
        mFileUrl = "file:///" + f.getAbsolutePath();
        mCurUrl = f.exists()? mFileUrl: mNetUrl;
        mWv.loadUrl(mCurUrl);
    }

    @Override
    protected void
    onResume() {
        super.onResume();
        mRtt.addTaskQEventListener(AppEnv.getUiHandlerAdapter(), mRtTaskQEventListener);
        DownloadTask t = mRtt.getDownloadTask(mId);
        if (RTTask.RtState.IDLE != mRtt.getRtState(t)) {
            P.bug(null != t);
            assert null != t;
            t.addEventListener(AppEnv.getUiHandlerAdapter(), mDownloadTaskListener);
        }
        setupLayout();
    }

    @Override
    protected void
    onPause() {
        mRtt.removeTaskQEventListener(mRtTaskQEventListener);
        DownloadTask t = mRtt.getDownloadTask(mId);
        if (RTTask.RtState.IDLE != mRtt.getRtState(t)) {
            P.bug(null != t);
            assert null != t;
            t.removeEventListener(mDownloadTaskListener);
        }
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
