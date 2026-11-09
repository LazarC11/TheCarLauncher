package com.thecarlauncher.util;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes the launcher's crashes to Download/TheCarLauncher-crash.txt (newest first, kept short),
 * so a user on an unknown head unit can send what went wrong. The device report includes it.
 */
public final class CrashLog {
    private static final String NAME = "TheCarLauncher-crash.txt";
    private static boolean installed;

    private CrashLog() {}

    public static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    write(app, t, e);
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }

    private static void write(Context c, Thread t, Throwable e) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + "  launcher " + Updater.installedName(c) + " build=" + com.thecarlauncher.unit.Unit.ID
                + "  " + Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE);
        pw.println("thread " + t.getName());
        e.printStackTrace(pw);
        pw.println();
        pw.flush();
        String entry = sw.toString();
        String old = read(c);
        String all = entry + (old == null ? "" : old);
        if (all.length() > 60000) all = all.substring(0, 60000);
        for (File f : files(c)) {
            if (f == null) continue;
            try {
                f.getParentFile().mkdirs();
                FileOutputStream o = new FileOutputStream(f);
                o.write(all.getBytes("UTF-8"));
                o.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static File[] files(Context c) {
        File own = c.getExternalFilesDir(null);
        return new File[]{new File(new File(android.os.Environment.getExternalStorageDirectory(), "Download"), NAME),
                own == null ? null : new File(own, NAME)};
    }

    /** The saved crashes, newest first, or null. */
    public static String read(Context c) {
        for (File f : files(c)) {
            if (f == null || !f.canRead()) continue;
            try {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                byte[] b = new byte[(int) Math.min(f.length(), 60000)];
                int n = in.read(b);
                in.close();
                if (n > 0) return new String(b, 0, n, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
