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
    private static final String reportReceiver = "yhcting77@gmail.com";
    private static final String errReportSubject = "[Feeder] Exception Report.";
    private static final String usageReportSubject = "[Feeder] Usage Report.";
    private static final String timeStampFileSuffix = "____tmstamp___";

    private static UsageReport instance;

    private boolean errReportEnabled = true;
    private boolean usageReportEnabled = true;

    private UsageReport() {
    }

    private File
    getTimeStampFile(File f) {
        return new File(f.getAbsoluteFile() + timeStampFileSuffix);
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
        if (!Utils.isNetworkAvailable(context))
            return;

        if (!reportf.exists())
            return; // nothing to do

        StringBuilder sbr = new StringBuilder();
        sbr.append(Utils.readTextFile(reportf)).append("\n\n");
        // we successfully read all log files.
        // let's clean it.
        cleanReportFile(reportf);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { reportReceiver });
        intent.putExtra(Intent.EXTRA_TEXT, sbr.toString());
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.setType("message/rfc822");
        try {
            context.startActivity(Intent.createChooser(intent,
                                                       context.getResources().getText(diagTitle)));
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
    S() {
        if (null == instance) {
            instance = new UsageReport();
            UnexpectedExceptionHandler.S().registerModule(instance);
        }
        return instance;
    }

    public void
    init(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, "err_report");
        onSharedPreferenceChanged(prefs, "usage_report");
    }

    @Override
    public void
    onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("err_report".equals(key)) {
            String v = prefs.getString("err_report", "yes");
            errReportEnabled = v.equals("yes")? true: false;
        } else if ("usage_report".equals(key)) {
            String v = prefs.getString("usage_report", "yes");
            usageReportEnabled = v.equals("yes")? true: false;
        }
    }

    /**
     * Overwrite
     * @param report
     */
    void
    storeErrReport(String report) {
        if (!errReportEnabled)
            return;
        storeReport(UIPolicy.getErrLogFile(), report);
    }

    /**
     * Send stored report - crash, improvement etc - to developer as E-mail.
     * @param context
     */
    public void
    sendErrReportMail(Context context) {
        if (!errReportEnabled)
            return;
        sendReportMail(context, UIPolicy.getErrLogFile(), R.string.send_err_report, errReportSubject);
    }

    public void
    storeUsageReport(String report) {
        if (!usageReportEnabled)
            return;
        storeReport(UIPolicy.getUsageLogFile(), report);
    }

    public void
    sendUsageReportMail(Context context) {
        if (!usageReportEnabled)
            return;
        File f = UIPolicy.getUsageLogFile();
        File tmstamp = getTimeStampFile(f);
        long tmPassed = 0;
        if (tmstamp.exists())
            tmPassed = System.currentTimeMillis() - tmstamp.lastModified();
        if (UIPolicy.usageInfoUpdatePeriod > tmPassed)
            return; // time is not passed enough

        sendReportMail(context, UIPolicy.getUsageLogFile(), R.string.send_usage_report, usageReportSubject);
    }
}
