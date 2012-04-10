package free.yhc.feeder.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import free.yhc.feeder.R;

public class UnexpectedExceptionHandler implements UncaughtExceptionHandler {
    private static final String REPORT_RCVR = "yhcting77@gmail.com";
    private static final String REPORT_SUBJECT = "Feeder Exception Report.";

    private static UnexpectedExceptionHandler instance = null;

    private Thread.UncaughtExceptionHandler   oldHandler;
    private LinkedList<TrackedModule>         mods = new LinkedList<TrackedModule>();

    public enum DumpLevel {
        FULL
    }

    public interface TrackedModule {
        String getDump(DumpLevel lvl);
    }

    // ========================
    // Privates
    // ========================
    private void
    appendCommonInfo(StringBuilder report) {

    }

    private void
    storeReport(String report) {
        FileOutputStream fo;
        try {
            fo = new FileOutputStream(UIPolicy.getNewLogFile());
        } catch (FileNotFoundException e) {
            return; // nothing to do
        }
        // file is NOT writable here!!! why?????
        PrintStream ps = new PrintStream(fo);
        ps.print(report);
        ps.flush();
        ps.close();
    }

    private UnexpectedExceptionHandler() {}
    private UnexpectedExceptionHandler(UncaughtExceptionHandler old) {
        oldHandler = old;
    }
    // ========================
    // Publics
    // ========================

    // Get singleton instance,.
    public static UnexpectedExceptionHandler
    S() {
        return instance;
    }

    public static void
    instanciate(UncaughtExceptionHandler old) {
        if (null == instance)
            instance = new UnexpectedExceptionHandler(old);
    }

    public boolean
    registerModule(TrackedModule m) {
        if (mods.contains(m))
            return false;

        mods.addLast(m);
        return true;
    }

    public boolean
    unregisterModule(TrackedModule m) {
        return mods.remove(m);
    }

    public void
    sendReportMail(Context context) {
        File[] fs = UIPolicy.getLogFiles();

        if (fs.length <= 0)
            return; // nothing to do.

        StringBuilder sbr = new StringBuilder();
        for (File f : fs) {
            sbr.append(Utils.readTextFile(f)).append("\n\n");
        }
        // we successfully read all log files.
        // let's clean it.
        UIPolicy.cleanLogFiles();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { REPORT_RCVR });
        intent.putExtra(Intent.EXTRA_TEXT, sbr.toString());
        intent.putExtra(Intent.EXTRA_SUBJECT, REPORT_SUBJECT);
        intent.setType("message/rfc822");
        try {
            context.startActivity(Intent.createChooser(intent,
                                                       context.getResources().getText(R.string.send_err_report)));
        } catch (ActivityNotFoundException e) {
            ; // ignore this report
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        StringBuilder report = new StringBuilder();
        appendCommonInfo(report);

        // collect dump informations
        Iterator<TrackedModule> iter = mods.iterator();
        while (iter.hasNext()) {
            TrackedModule tm = iter.next();
            report.append(tm.getDump(DumpLevel.FULL));
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        report.append(sw.toString());
        pw.close();

        storeReport(report.toString());
        // TODO
        //sendReportMail("Test error report", report.toString());
        oldHandler.uncaughtException(thread, ex);
    }

}
