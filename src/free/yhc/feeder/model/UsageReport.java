/******************************************************************************
 *    Copyright (C) 2012, 2013, 2014 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of FeedHive
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
 *    along with this program.	If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder.model;

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
import free.yhc.feeder.R;

public class UsageReport implements
UnexpectedExceptionHandler.TrackedModule,
OnSharedPreferenceChangeListener {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(UsageReport.class);

    public  static final String REPORT_RECEIVER         = "yhcting77@gmail.com";
    public  static final String FEEDBACK_REPORT_SUBJECT = "[FeedHive] Feedback Report.";

    private static final long   USAGE_INFO_UPDATE_PERIOD = 1000 * 60 * 60 * 24 * 7; // (ms) 7 days = 1 week
    private static final String ERR_REPORT_SUBJECT      = "[FeedHive] Exception Report.";
    private static final String USAGE_REPORT_SUBJECT    = "[FeedHive] Usage Report.";
    private static final String TIME_STAMP_FILE_SUFFIX  = "____tmstamp___";

    private static UsageReport sInstance = null;

    private final Environ mEnv = Environ.get();

    private boolean mErrReportEnabled = true;
    private boolean mUsageReportEnabled = true;

    private UsageReport() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Environ.getAppContext());
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
        tmstamp.delete();
        f.delete();
    }

    private void
    storeReport(File f, String report) {
        try {
            File tmstamp = getTimeStampFile(f);
            if (!tmstamp.exists())
                tmstamp.createNewFile();

            BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
            bw.write(report);
            bw.flush();
            bw.close();
        } catch (IOException e) { }
    }

    /**
     * Send stored report - crash, improvement etc - to developer as E-mail.
     * @param context
     */
    private void
    sendReportMail(Context context, File reportf, int diagTitle, String subject) {
        if (!Utils.isNetworkAvailable())
            return;

        if (!reportf.exists())
            return; // nothing to do

        StringBuilder sbr = new StringBuilder();
        sbr.append(Utils.readTextFile(reportf)).append("\n\n");
        // we successfully read all log files.
        // let's clean it.
        cleanReportFile(reportf);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { REPORT_RECEIVER });
        intent.putExtra(Intent.EXTRA_TEXT, sbr.toString());
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.setType("message/rfc822");
        intent = Intent.createChooser(intent, context.getResources().getText(diagTitle));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            ; // ignore this report
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
        if ("err_report".equals(key)) {
            String v = prefs.getString("err_report", "yes");
            mErrReportEnabled = v.equals("yes")? true: false;
        } else if ("usage_report".equals(key)) {
            String v = prefs.getString("usage_report", "yes");
            mUsageReportEnabled = v.equals("yes")? true: false;
        }
    }

    /**
     * Overwrite
     * @param report
     */
    void
    storeErrReport(String report) {
        if (!mErrReportEnabled)
            return;
        storeReport(mEnv.getErrLogFile(), report);
    }

    /**
     * Send stored report - crash, improvement etc - to developer as E-mail.
     * @param context
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
