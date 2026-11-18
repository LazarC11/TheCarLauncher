package com.thecarlauncher.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Whether the launcher knows this head unit, the device report for units it does not know yet,
 * and the later "compatibility update" (a release whose unit rules now include this unit).
 *
 * The report goes to the repository as a GitHub issue only when the build carries a report key
 * (assets/report_token, written by CI from the REPORT_TOKEN secret; a fine-grained token that may
 * only create issues), at most {@link #DAILY_LIMIT} a day per unit. It is always saved on the unit
 * too, so the owner can still send it by hand.
 */
public final class Compat {
    public static final String P_UNSUPPORTED = "compat_unsupported";
    public static final String P_REPORT_PENDING = "compat_report_pending";
    public static final String P_REPORT_SENT = "compat_report_sent";
    private static final String P_LAST_CHECK = "compat_last_check";
    /** Reports sent today: "yyyy-MM-dd count". */
    private static final String P_SENT_TODAY = "compat_reports_today";
    /** At most this many reports a day leave one unit (the report is always saved on the unit). */
    public static final int DAILY_LIMIT = 3;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Compat() {}

    /** The launcher has rules for this unit (not only the default build). */
    public static boolean recognised(Context c) { return UnitKind.recognised(c); }

    public static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- Device report -------------------------------------------------------------------------

    public interface Sent { void onSent(String url, String savedPath, String error); }

    /** The report key built into this APK, or null. */
    static String token(Context c) {
        try {
            InputStream in = c.getAssets().open("report_token");
            byte[] b = new byte[512];
            int n = in.read(b);
            in.close();
            String t = n > 0 ? new String(b, 0, n, "UTF-8").trim() : "";
            return t.isEmpty() ? null : t;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean canSend(Context c) { return token(c) != null; }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
    }

    /** Reports this unit sent today. */
    public static int sentToday(Context c) {
        String v = Prefs.str(c, P_SENT_TODAY, "");
        if (v == null || !v.startsWith(today() + " ")) return 0;
        try {
            return Integer.parseInt(v.substring(v.indexOf(' ') + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void countSent(Context c) { Prefs.put(c, P_SENT_TODAY, today() + " " + (sentToday(c) + 1)); }

    /**
     * Builds the report, saves it on the unit and, with a report key and internet, opens a GitHub
     * issue with it. Without internet it is sent later ({@link #retryPending}). Answer on the main thread.
     */
    public static void sendReport(final Context c, final Sent done) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text = DeviceReport.build(c);
                String path = DeviceReport.save(c, text);
                String url = null, err = null;
                if (token(c) == null) {
                    err = "no report key";
                } else if (sentToday(c) >= DAILY_LIMIT) {
                    err = "daily limit";
                } else if (!online(c)) {
                    err = "offline";
                    Prefs.put(c, P_REPORT_PENDING, true);
                } else {
                    try {
                        url = postIssue(c, text);
                        countSent(c);
                        Prefs.put(c, P_REPORT_PENDING, false);
                        Prefs.put(c, P_REPORT_SENT, url);
                    } catch (Exception e) {
                        err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                        Prefs.put(c, P_REPORT_PENDING, true);
                    }
                }
                final String u = url, p = path, e = err;
                if (done != null) MAIN.post(new Runnable() {
                    public void run() { done.onSent(u, p, e); }
                });
            }
        }, "report-send").start();
    }

    /** A report that could not go out earlier (no internet) is sent now. */
    public static void retryPending(Context c) {
        if (!Prefs.bool(c, P_REPORT_PENDING, false) || !online(c) || token(c) == null || sentToday(c) >= DAILY_LIMIT) return;
        sendReport(c, null);
    }

    static String unitTitle() {
        return "Unit report: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.HARDWARE + ", Android "
                + Build.VERSION.RELEASE + ")";
    }

    private static String postIssue(Context c, String report) throws Exception {
        String body = report.length() > 60000 ? report.substring(0, 60000) + "\n…(cut)" : report;
        JSONObject o = new JSONObject();
        o.put("title", unitTitle());
        o.put("body", "Sent automatically by the launcher's first-start check: this unit is not supported yet.\n\n```\n"
                + body + "\n```");
        HttpURLConnection h = (HttpURLConnection) new URL("https://api.github.com/repos/" + Updater.REPO + "/issues").openConnection();
        h.setRequestMethod("POST");
        h.setConnectTimeout(15000);
        h.setReadTimeout(20000);
        h.setDoOutput(true);
        h.setRequestProperty("Authorization", "Bearer " + token(c));
        h.setRequestProperty("Accept", "application/vnd.github+json");
        h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        h.setRequestProperty("User-Agent", "MibDroid");
        OutputStream os = h.getOutputStream();
        os.write(o.toString().getBytes("UTF-8"));
        os.close();
        int code = h.getResponseCode();
        InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) sb.append(new String(b, 0, n, "UTF-8"));
            in.close();
        }
        h.disconnect();
        if (code != 201) throw new Exception("HTTP " + code);
        return new JSONObject(sb.toString()).optString("html_url");
    }

    // ---- Later compatibility update ------------------------------------------------------------

    public interface Found { void onCompatibilityUpdate(Updater.Release r); }

    /**
     * A unit marked unsupported, now online: looks (at most every 6 hours) whether a release's
     * unit rules know it now and have a build for it.
     */
    public static void checkLater(final Context c, final Found found) {
        if (!Prefs.bool(c, P_UNSUPPORTED, false) || !online(c)) return;
        retryPending(c);
        long last = Prefs.longVal(c, P_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < 6L * 3600 * 1000) return;
        Prefs.put(c, P_LAST_CHECK, System.currentTimeMillis());
        Updater.check(c, new Updater.Checked() {
            @Override
            public void onChecked(Updater.Release r, String error) {
                if (r == null || !recognised(c)) return;
                Prefs.put(c, P_UNSUPPORTED, false);
                if (found != null) found.onCompatibilityUpdate(r);
            }
        });
    }
}
