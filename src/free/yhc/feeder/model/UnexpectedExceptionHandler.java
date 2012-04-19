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
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import free.yhc.feeder.R;

public class UnexpectedExceptionHandler implements
UncaughtExceptionHandler,
OnSharedPreferenceChangeListener {
    private static final String REPORT_RCVR = "yhcting77@gmail.com";
    private static final String REPORT_SUBJECT = "[Feeder] Exception Report.";

    private static final String UNKNOWN = "unknown";

    private static UnexpectedExceptionHandler instance = null;

    private Thread.UncaughtExceptionHandler   oldHandler;
    private LinkedList<TrackedModule>         mods = new LinkedList<TrackedModule>();
    private boolean                           reportEnabled = true;

    private PackageReport pr = new PackageReport();
    private BuildReport   br = new BuildReport();

    private class PackageReport {
        String packageName          = UNKNOWN;
        String versionName          = UNKNOWN;
        String filesDir             = UNKNOWN;
    }
    // Useful Informations
    private class BuildReport {
        String androidVersion       = UNKNOWN;
        String board                = UNKNOWN;
        String brand                = UNKNOWN;
        String device               = UNKNOWN;
        String display              = UNKNOWN;
        String fingerPrint          = UNKNOWN;
        String host                 = UNKNOWN;
        String id                   = UNKNOWN;
        String manufacturer         = UNKNOWN;
        String model                = UNKNOWN;
        String product              = UNKNOWN;
        String tags                 = UNKNOWN;
        long   time                 = 0;
        String type                 = UNKNOWN;
        String user                 = UNKNOWN;
    }

    public enum DumpLevel {
        FULL
    }

    public interface TrackedModule {
        String dump(DumpLevel lvl);
    }

    // ========================
    // Privates
    // ========================
    private void
    setEnvironmentInfo(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            pr.versionName = pi.versionName;
            pr.packageName = pi.packageName;
        }catch (NameNotFoundException e) {
            ; // ignore
        }
        pr.filesDir        = context.getFilesDir().getAbsolutePath();
        br.model           = android.os.Build.MODEL;
        br.androidVersion  = android.os.Build.VERSION.RELEASE;
        br.board           = android.os.Build.BOARD;
        br.brand           = android.os.Build.BRAND;
        br.device          = android.os.Build.DEVICE;
        br.display         = android.os.Build.DISPLAY;
        br.fingerPrint     = android.os.Build.FINGERPRINT;
        br.host            = android.os.Build.HOST;
        br.id              = android.os.Build.ID;
        br.product         = android.os.Build.PRODUCT;
        br.tags            = android.os.Build.TAGS;
        br.time            = android.os.Build.TIME;
        br.type            = android.os.Build.TYPE;
        br.user            = android.os.Build.USER;
    }


    private void
    appendCommonReport(StringBuilder report) {
        report.append("==================== Package Information ==================\n")
              .append("  - name        : " + pr.packageName + "\n")
              .append("  - version     : " + pr.versionName + "\n")
              .append("  - filesDir    : " + pr.filesDir + "\n")
              .append("\n")
              .append("===================== Device Information ==================\n")
              .append("  - androidVer  : " + br.androidVersion + "\n")
              .append("  - board       : " + br.board + "\n")
              .append("  - brand       : " + br.brand + "\n")
              .append("  - device      : " + br.device + "\n")
              .append("  - display     : " + br.display + "\n")
              .append("  - fingerprint : " + br.fingerPrint + "\n")
              .append("  - host        : " + br.host + "\n")
              .append("  - id          : " + br.id + "\n")
              .append("  - manufactuere: " + br.manufacturer + "\n")
              .append("  - model       : " + br.model + "\n")
              .append("  - product     : " + br.product + "\n")
              .append("  - tags        : " + br.tags + "\n")
              .append("  - time        : " + br.time + "\n")
              .append("  - type        : " + br.type + "\n")
              .append("  - user        : " + br.user + "\n")
              .append("\n\n");
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
        if (null == instance)
            instance = new UnexpectedExceptionHandler(Thread.getDefaultUncaughtExceptionHandler());
        return instance;
    }

    public void
    init(Context context) {
        setEnvironmentInfo(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, "err_report");
    }

    @Override
    public void
    onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("err_report".equals(key)) {
            String v = prefs.getString("err_report", "yes");
            reportEnabled = v.equals("yes")? true: false;
        }
    }

    public boolean
    registerModule(TrackedModule m) {
        synchronized (mods) {
            if (mods.contains(m))
                return false;

            mods.addLast(m);
            return true;
        }
    }

    public boolean
    unregisterModule(TrackedModule m) {
        synchronized (mods) {
            return mods.remove(m);
        }
    }


    public void
    sendReportMail(Context context) {
        if (!reportEnabled || !Utils.isNetworkAvailable(context))
            return;

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
        if (!reportEnabled) {
            oldHandler.uncaughtException(thread, ex);
            return;
        }

        StringBuilder report = new StringBuilder();
        appendCommonReport(report);

        // collect dump informations
        Iterator<TrackedModule> iter = mods.iterator();
        while (iter.hasNext()) {
            TrackedModule tm = iter.next();
            report.append(tm.dump(DumpLevel.FULL)).append("\n\n");
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
