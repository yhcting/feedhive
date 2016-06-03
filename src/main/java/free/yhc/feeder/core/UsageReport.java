/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

package free.yhc.feeder.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.util.FileUtil;
import free.yhc.feeder.R;

public class UsageReport implements
UnexpectedExceptionHandler.TrackedModule,
OnSharedPreferenceChangeListener {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(UsageReport.class, Logger.LOGLV_DEFAULT);

    public static final String REPORT_RECEIVER = "yhcting77dev0@gmail.com";
    public static final String FEEDBACK_REPORT_SUBJECT = "[FeedHive] Feedback Report.";

    private static final long USAGE_INFO_UPDATE_PERIOD = 1000 * 60 * 60 * 24 * 7; // (ms) 7 days = 1 week
    private static final String ERR_REPORT_SUBJECT = "[FeedHive] Exception Report.";
    private static final String USAGE_REPORT_SUBJECT = "[FeedHive] Usage Report.";
    private static final String TIME_STAMP_FILE_SUFFIX = "____tmstamp___";

    private static UsageReport sInstance = null;

    private final Environ mEnv = Environ.get();

    private boolean mErrReportEnabled = true;
    private boolean mUsageReportEnabled = true;

    private UsageReport() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(AppEnv.getAppContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, "err_report");
        onSharedPreferenceChanged(prefs, "usage_report");
    }

    private File
    getTimeStampFile(File f) {
        return new File(f.getAbsoluteFile() + TIME_STAMP_FILE_SUFFIX);
    }

    private void
    cleanReportFile(File f) {
        File tmstamp = getTimeStampFile(f);
        //noinspection ResultOfMethodCallIgnored
        tmstamp.delete();
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private void
    storeReport(File f, String report) {
        try {
            File tmstamp = getTimeStampFile(f);
            if (!tmstamp.exists())
                //noinspection ResultOfMethodCallIgnored
                tmstamp.createNewFile();

            BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
            bw.write(report);
            bw.flush();
            bw.close();
        } catch (IOException ignored) { }
    }

    /**
     * Send stored report - crash, improvement etc - to developer as E-mail.
     */
    private void
    sendReportMail(Context context, File reportf, int diagTitle, String subject) {
        if (!Util.isNetworkAvailable())
            return;

        if (!reportf.exists())
            return; // nothing to do

        String text;
        try {
            text = FileUtil.readTextFile(reportf);
        } catch (IOException e) {
            text = "";
        }
        text += "\n\n";
        // we successfully read all log files.
        // let's clean it.
        cleanReportFile(reportf);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { REPORT_RECEIVER });
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.setType("message/rfc822");
        intent = Intent.createChooser(intent, context.getResources().getText(diagTitle));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // ignore this report
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ UsageReport ]";
    }

    // S : Singleton instance
    public static UsageReport
    get() {
        if (null == sInstance) {
            sInstance = new UsageReport();
            UnexpectedExceptionHandler.get().registerModule(sInstance);
        }
        return sInstance;
    }

    @Override
    public void
    onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Util.getResString(R.string.cserr_report).equals(key))
            mErrReportEnabled = prefs.getBoolean(Util.getResString(R.string.cserr_report), true);
        else if (Util.getResString(R.string.csusage_report).equals(key))
            mUsageReportEnabled = prefs.getBoolean(Util.getResString(R.string.csusage_report), true);
    }

    /**
     * Overwrite
     */
    void
    storeErrReport(String report) {
        if (!mErrReportEnabled)
            return;
        storeReport(mEnv.getErrLogFile(), report);
    }

    /**
     * Send stored report - crash, improvement etc - to developer as E-mail.
     */
    public void
    sendErrReportMail(Context context) {
        if (!mErrReportEnabled)
            return;
        sendReportMail(context, mEnv.getErrLogFile(), R.string.send_err_report, ERR_REPORT_SUBJECT);
    }

    public void
    storeUsageReport(String report) {
        if (!mUsageReportEnabled)
            return;
        storeReport(mEnv.getUsageLogFile(), report);
    }

    public void
    sendUsageReportMail(Context context) {
        if (!mUsageReportEnabled)
            return;
        File f = mEnv.getUsageLogFile();
        File tmstamp = getTimeStampFile(f);
        long tmPassed = 0;
        if (tmstamp.exists())
            tmPassed = System.currentTimeMillis() - tmstamp.lastModified();
        if (USAGE_INFO_UPDATE_PERIOD > tmPassed)
            return; // time is not passed enough

        sendReportMail(context, mEnv.getUsageLogFile(), R.string.send_usage_report, USAGE_REPORT_SUBJECT);
    }

    public boolean
    sendFeedbackReportMain(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { REPORT_RECEIVER });
        intent.putExtra(Intent.EXTRA_SUBJECT, FEEDBACK_REPORT_SUBJECT);
        intent.setType("message/rfc822");
        intent = Intent.createChooser(intent, context.getResources().getText(R.string.send_feedback_report));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            return false;
        }
        return true;
    }
}
