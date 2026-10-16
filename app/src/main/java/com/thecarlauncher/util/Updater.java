package com.thecarlauncher.util;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.thecarlauncher.mib.LoadingBanner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Software updates from the project's GitHub releases. Builds of the main branch are published
 * as releases ("v4.5.1"); with "test builds" on, every build counts. The APK is downloaded with
 * the loading banner and handed to Android's package installer: as an ordinary app Android asks
 * to confirm the update, as a system app (Magisk module) it installs silently.
 */
public final class Updater {
    public static final String REPO = "LazarC11/TheCarLauncher";
    public static final String P_AUTO = "update_auto_check", P_BETA = "update_test_builds", P_LAST = "update_last_check";
    private static final String ACTION_DONE = "com.thecarlauncher.UPDATE_INSTALLED";

    public static final class Release {
        public final String tag, name, notes, apkUrl, date;
        public final long versionCode;
        public final String versionName;
        public final boolean test;

        Release(String tag, String name, String notes, String apkUrl, String date, long code, String vname, boolean test) {
            this.tag = tag;
            this.name = name;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.date = date;
            this.versionCode = code;
            this.versionName = vname;
            this.test = test;
        }
    }

    public interface Checked { void onChecked(Release latest, String error); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Release lastFound;
    private static boolean busy;
    private static volatile boolean cancelled;

    /** Steps of an update, shown like the factory "Software update in progress" list. */
    public static final String[] STEPS = {"launcher::download", "launcher::verify", "launcher::install"};
    /** Progress per step 0..100, -1 = not started; failed step index or -1. */
    private static final int[] progress = {-1, -1, -1};
    private static int failedStep = -1;
    private static String failMessage;

    public interface Progress { void onUpdateProgress(); }

    private static final java.util.List<Progress> watchers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addWatcher(Progress p) { if (!watchers.contains(p)) watchers.add(p); }

    public static void removeWatcher(Progress p) { watchers.remove(p); }

    public static int progress(int step) { return progress[step]; }

    public static int failedStep() { return failedStep; }

    public static String failMessage() { return failMessage; }

    /** True while a download / install runs or after one ended (until the screen resets it). */
    public static boolean started() { return progress[0] >= 0; }

    public static void cancel() { cancelled = true; }

    public static void reset() {
        if (busy) return;
        progress[0] = progress[1] = progress[2] = -1;
        failedStep = -1;
        failMessage = null;
        notifyWatchers();
    }

    private static void step(int i, int pct) {
        progress[i] = pct;
        notifyWatchers();
    }

    private static void fail(int i, String msg) {
        failedStep = i;
        failMessage = msg;
        busy = false;
        notifyWatchers();
    }

    private static void notifyWatchers() {
        MAIN.post(new Runnable() {
            public void run() { for (Progress p : watchers) p.onUpdateProgress(); }
        });
    }

    private Updater() {}

    public static long installedCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static Release lastFound() { return lastFound; }

    public static boolean isNewer(Context c, Release r) { return r != null && r.versionCode > installedCode(c); }

    public static boolean busy() { return busy; }

    /** Looks for the newest release (in the background); the answer comes on the main thread. */
    public static void check(final Context c, final Checked done) {
        final boolean beta = Prefs.bool(c, P_BETA, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Release found = null;
                String err = null;
                try {
                    String url = "https://api.github.com/repos/" + REPO + (beta ? "/releases?per_page=15" : "/releases/latest");
                    String json = new String(get(url), "UTF-8");
                    if (beta) {
                        JSONArray a = new JSONArray(json);
                        for (int i = 0; i < a.length(); i++) {
                            Release r = parse(a.getJSONObject(i));
                            if (r != null && (found == null || r.versionCode > found.versionCode)) found = r;
                        }
                    } else {
                        found = parse(new JSONObject(json));
                    }
                    if (found == null) err = "no release with an APK";
                } catch (Exception e) {
                    err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                }
                Prefs.put(c, P_LAST, System.currentTimeMillis());
                final Release r = found;
                final String er = err;
                MAIN.post(new Runnable() {
                    public void run() {
                        if (r != null) lastFound = r;
                        if (done != null) done.onChecked(r, er);
                    }
                });
            }
        }, "update-check").start();
    }

    private static final Pattern CODE = Pattern.compile("versionCode[:= ]+(\\d+)");
    private static final Pattern NAME = Pattern.compile("versionName[:= ]+([0-9][0-9A-Za-z.\\-]*)");

    private static Release parse(JSONObject o) {
        String apk = null;
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && a.optString("name").endsWith(".apk")) {
                    apk = a.optString("browser_download_url");
                    break;
                }
            }
        }
        if (apk == null || apk.isEmpty()) return null;
        String body = o.optString("body", "");
        Matcher m = CODE.matcher(body);
        if (!m.find()) return null; // only releases made by the build workflow say which version they hold
        long code = Long.parseLong(m.group(1));
        Matcher n = NAME.matcher(body);
        String vname = n.find() ? n.group(1) : o.optString("tag_name").replaceFirst("^v", "");
        // Notes: the body without the workflow's version lines.
        String notes = body.replaceAll("(?m)^.*version(Code|Name)[:= ].*$", "").replaceAll("(?m)^Download .*$", "").trim();
        String date = o.optString("published_at", "");
        if (date.length() >= 10) date = date.substring(0, 10);
        return new Release(o.optString("tag_name"), o.optString("name"), notes, apk, date, code, vname, o.optBoolean("prerelease"));
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setRequestProperty("User-Agent", "TheCarLauncher-updater");
            int code = con.getResponseCode();
            if (code >= 400) throw new java.io.IOException("HTTP " + code);
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            return bo.toByteArray();
        } finally {
            con.disconnect();
        }
    }

    // ---- Install -------------------------------------------------------------------------------

    /** True when the launcher installs without asking (system app with INSTALL_PACKAGES). */
    public static boolean silent(Context c) {
        return c.checkSelfPermission("android.permission.INSTALL_PACKAGES") == PackageManager.PERMISSION_GRANTED;
    }

    /** Ordinary app on Android 8+: "Install unknown apps" must be allowed once. */
    public static boolean mayInstall(Context c) {
        return silent(c) || Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    public static void askInstallPermission(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        Ui.start(c, new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())));
    }

    /** Downloads the release (banner shows progress) and installs it. */
    public static void install(final Context c, final Release r) {
        if (busy || r == null) return;
        if (!mayInstall(c)) {
            askInstallPermission(c);
            LoadingBanner.flash("update", LoadingBanner.t("Dozvoli instaliranje aplikacija za Car Launcher, pa ponovo",
                    "Allow installing apps for Car Launcher, then try again"), 3500);
            return;
        }
        busy = true;
        cancelled = false;
        progress[0] = 0;
        progress[1] = progress[2] = -1;
        failedStep = -1;
        failMessage = null;
        notifyWatchers();
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File f = new File(app.getCacheDir(), "update.apk");
                int at = 0;
                try {
                    download(r.apkUrl, f);
                    if (cancelled) throw new java.io.InterruptedIOException("cancelled");
                    step(0, 100);
                    at = 1;
                    step(1, 0);
                    verify(app, f);
                    step(1, 100);
                    at = 2;
                    step(2, 10);
                    LoadingBanner.start("update", LoadingBanner.t("Instaliranje ", "Installing ") + r.versionName + "…");
                    commit(app, f);
                    step(2, 60);
                } catch (Exception e) {
                    LoadingBanner.stop("update");
                    String msg = cancelled ? LoadingBanner.t("Otkazano", "Cancelled") : e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    fail(at, msg);
                    LoadingBanner.flash("update-fail", LoadingBanner.t("Ažuriranje nije uspelo: ", "Update failed: ") + msg, 4000);
                }
            }
        }, "update-install").start();
    }

    private static void download(String url, File out) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", "TheCarLauncher-updater");
        try {
            if (con.getResponseCode() >= 400) throw new java.io.IOException("HTTP " + con.getResponseCode());
            long total = con.getContentLength();
            InputStream in = con.getInputStream();
            OutputStream o = new FileOutputStream(out);
            byte[] buf = new byte[16384];
            long got = 0;
            int r, lastPct = -1;
            while ((r = in.read(buf)) > 0) {
                o.write(buf, 0, r);
                got += r;
                if (cancelled) break;
                int pct = total > 0 ? (int) (got * 100 / total) : -1;
                if (pct != lastPct) {
                    lastPct = pct;
                    if (pct >= 0) step(0, pct);
                    LoadingBanner.start("update", LoadingBanner.t("Preuzimanje ažuriranja… ", "Downloading the update… ")
                            + (pct >= 0 ? pct + " %" : (got / 1024) + " KB"));
                }
            }
            o.close();
            in.close();
        } finally {
            con.disconnect();
        }
    }

    /** The download must be this launcher, newer than the installed one. */
    private static void verify(Context c, File apk) throws Exception {
        PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        if (pi == null) throw new java.io.IOException(LoadingBanner.t("Preuzeti fajl nije ispravan", "The download is not a valid APK"));
        if (!c.getPackageName().equals(pi.packageName)) throw new java.io.IOException(LoadingBanner.t("Pogrešan paket", "Wrong package"));
        long code = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        if (code <= installedCode(c)) throw new java.io.IOException(LoadingBanner.t("Verzija nije novija", "Not a newer version"));
    }

    private static void commit(final Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(c.getPackageName());
        int id = pi.createSession(p);
        PackageInstaller.Session s = pi.openSession(id);
        InputStream in = new FileInputStream(apk);
        OutputStream o = s.openWrite("update.apk", 0, apk.length());
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
        s.fsync(o);
        o.close();
        in.close();
        IntentFilter f = new IntentFilter(ACTION_DONE);
        BroadcastReceiver done = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    // Ordinary app: Android's own "Update this app?" dialog.
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            ctx.startActivity(confirm);
                        } catch (Exception ignored) {
                        }
                    }
                    LoadingBanner.stop("update");
                    step(2, 80);
                    return;
                }
                busy = false;
                LoadingBanner.stop("update");
                try {
                    ctx.unregisterReceiver(this);
                } catch (Exception ignored) {
                }
                if (status == PackageInstaller.STATUS_SUCCESS) step(2, 100);
                else fail(2, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                if (status != PackageInstaller.STATUS_SUCCESS) {
                    String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    LoadingBanner.flash("update-fail", LoadingBanner.t("Ažuriranje nije instalirano", "Update not installed")
                            + (msg != null ? ": " + msg : ""), 4000);
                }
                // On success the launcher is restarted by Android in the new version.
            }
        };
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(done, f);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent pend = PendingIntent.getBroadcast(c, id, new Intent(ACTION_DONE).setPackage(c.getPackageName()), flags);
        s.commit(pend.getIntentSender());
        s.close();
    }

    /** Once a day (when allowed): look for an update and mention it in the banner. */
    public static void autoCheck(final Context c) {
        if (!Prefs.bool(c, P_AUTO, true)) return;
        long last = Prefs.longVal(c, P_LAST, 0);
        if (System.currentTimeMillis() - last < 24L * 3600 * 1000) return;
        check(c, new Checked() {
            public void onChecked(Release r, String error) {
                if (isNewer(c, r)) {
                    LoadingBanner.flash("update-available", LoadingBanner.t("Dostupna je nova verzija " + r.versionName
                            + " — Podešavanje → Ažuriranje softvera", "Version " + r.versionName
                            + " is available — Setup → Software update"), 6000);
                }
            }
        });
    }
}
