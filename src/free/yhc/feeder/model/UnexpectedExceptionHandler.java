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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

public class UnexpectedExceptionHandler implements
UncaughtExceptionHandler {
    private static final String UNKNOWN = "unknown";

    private static UnexpectedExceptionHandler instance = null;

    // This module to capturing unexpected exception.
    // So this SHOULD have minimum set of code in constructor,
    //   because this module SHOULD be instancicate as early as possible
    //   before any other module is instanciated
    //
    // Dependency on only following modules are allowed
    // - Utils
    private final Thread.UncaughtExceptionHandler   oldHandler = Thread.getDefaultUncaughtExceptionHandler();
    private final LinkedList<TrackedModule>         mods = new LinkedList<TrackedModule>();
    private final PackageReport pr = new PackageReport();
    private final BuildReport   br = new BuildReport();

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

    private UnexpectedExceptionHandler() {
        setEnvironmentInfo(Utils.getAppContext());
    }
    // ========================
    // Publics
    // ========================

    // Get singleton instance,.
    public static UnexpectedExceptionHandler
    get() {
        if (null == instance)
            instance = new UnexpectedExceptionHandler();
        return instance;
    }

    /**
     * register module that will be dumped when unexpected exception is issued.
     * @param m
     * @return
     */
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

    @Override
    public void
    uncaughtException(Thread thread, Throwable ex) {
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

        UsageReport.get().storeErrReport(report.toString());
        oldHandler.uncaughtException(thread, ex);
    }
}
