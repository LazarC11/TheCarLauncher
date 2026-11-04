package com.thecarlauncher.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Device report": everything the launcher can see about a head unit it does not know yet, so
 * radio and Bluetooth music can be made to work on it. Plain text, saved where a file manager
 * finds it (Download/ and the launcher's own folder), and shown on screen.
 */
public final class DeviceReport {
    private DeviceReport() {}

    /** Packages / properties worth listing: factory apps for radio, Bluetooth, MCU, projection. */
    private static final Pattern INTERESTING = Pattern.compile(
            "(?i).*(radio|fm|tuner|bt|blue|music|mcu|can|nwd|tw|topway|syu|fyt|qf|microntek|hct|carplay|zlink|carlink|"
                    + "autokit|easyconn|audio|amp|eq|sound|car|vehicle|launcher|goc|xr8|allwinner|sunxi).*");

    /** Builds the report (blocking: a few seconds; call on a worker thread). */
    public static String build(Context c) {
        StringBuilder r = new StringBuilder();
        r.append("TheCarLauncher device report  ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())).append('\n');
        r.append("launcher ").append(Updater.installedName(c)).append(" (").append(Updater.installedCode(c)).append(")\n\n");

        r.append("== Build ==\n");
        r.append("manufacturer=").append(Build.MANUFACTURER).append(" brand=").append(Build.BRAND).append(" model=").append(Build.MODEL)
                .append(" device=").append(Build.DEVICE).append(" product=").append(Build.PRODUCT).append('\n');
        r.append("board=").append(Build.BOARD).append(" hardware=").append(Build.HARDWARE).append('\n');
        r.append("android=").append(Build.VERSION.RELEASE).append(" sdk=").append(Build.VERSION.SDK_INT).append('\n');
        r.append("display=").append(Build.DISPLAY).append('\n').append("fingerprint=").append(Build.FINGERPRINT).append('\n');
        r.append("kernel=").append(System.getProperty("os.version")).append('\n');
        r.append("launcher build=").append(com.thecarlauncher.unit.Unit.ID).append(" unit detected=").append(UnitKind.detect(c)).append("\n\n");

        r.append("== Framework classes ==\n");
        for (String k : new String[]{"android.tw.john.TWUtil", "android.tw.john.TWClient", "android.os.IMcuManager",
                "android.app.CarManager", "com.nwd.android.mcu.McuManager", "android.microntek.mtcser.BTServiceInf",
                "com.syu.ipc.RemoteModuleProxy", "android.car.Car"}) {
            boolean ok;
            try {
                Class.forName(k);
                ok = true;
            } catch (Throwable e) {
                ok = false;
            }
            r.append(ok ? "  [x] " : "  [ ] ").append(k).append('\n');
        }

        r.append("\n== System services ==\n");
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            String[] list = (String[]) sm.getMethod("listServices").invoke(null);
            if (list != null) for (String s : list) if (INTERESTING.matcher(s).matches()) r.append("  ").append(s).append('\n');
        } catch (Throwable e) {
            r.append("  (not readable: ").append(e.getClass().getSimpleName()).append(")\n");
        }

        r.append("\n== Devices (/dev) ==\n");
        File[] dev = new File("/dev").listFiles();
        if (dev != null) {
            for (File f : dev) {
                String n = f.getName();
                if (n.matches("(?i).*(tty|serial|goc|bt|blue|mcu|fm|radio|uart|can|i2c|snd|audio|tw).*") && !n.matches("tty\\d+"))
                    r.append("  ").append(n).append(f.canRead() ? " r" : "").append(f.canWrite() ? "w" : "").append('\n');
            }
        }

        r.append("\n== Properties (filtered) ==\n");
        for (String line : run("getprop")) if (INTERESTING.matcher(line).matches()) r.append("  ").append(line).append('\n');

        r.append("\n== Factory / car packages ==\n");
        try {
            List<PackageInfo> pkgs = c.getPackageManager().getInstalledPackages(
                    android.content.pm.PackageManager.GET_SERVICES | android.content.pm.PackageManager.GET_RECEIVERS);
            for (PackageInfo p : pkgs) {
                boolean sys = p.applicationInfo != null && (p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                String label = p.applicationInfo == null ? "" : String.valueOf(c.getPackageManager().getApplicationLabel(p.applicationInfo));
                if (!INTERESTING.matcher(p.packageName + " " + label).matches()) continue;
                if (p.packageName.startsWith("com.android.") && !p.packageName.contains("bluetooth") && !p.packageName.contains("fm")) continue;
                r.append("  ").append(p.packageName).append("  \"").append(label).append("\"  v").append(p.versionName)
                        .append(sys ? "  system" : "").append('\n');
                if (p.services != null) for (ServiceInfo s : p.services) {
                    r.append("      service ").append(s.name.replace(p.packageName, "")).append(s.exported ? " (exported)" : "").append('\n');
                }
                if (p.receivers != null) for (android.content.pm.ActivityInfo s : p.receivers) {
                    r.append("      receiver ").append(s.name.replace(p.packageName, "")).append(s.exported ? " (exported)" : "").append('\n');
                }
            }
        } catch (Throwable e) {
            r.append("  (").append(e.getClass().getSimpleName()).append(")\n");
        }

        r.append("\n== Audio ==\n");
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            r.append("  music active=").append(am.isMusicActive()).append(" mode=").append(am.getMode()).append('\n');
            r.append("  music volume=").append(am.getStreamVolume(AudioManager.STREAM_MUSIC)).append('/')
                    .append(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).append('\n');
        } catch (Throwable ignored) {
        }

        r.append("\n== Bluetooth (Android) ==\n");
        try {
            android.bluetooth.BluetoothAdapter a = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            r.append("  adapter=").append(a != null).append(a != null ? " enabled=" + a.isEnabled() : "").append('\n');
            if (a != null && a.getBondedDevices() != null) {
                for (android.bluetooth.BluetoothDevice d : a.getBondedDevices()) r.append("  bonded: ").append(d.getName()).append('\n');
            }
        } catch (Throwable e) {
            r.append("  (").append(e.getClass().getSimpleName()).append(")\n");
        }

        r.append("\n== Media sources seen by the launcher ==\n");
        try {
            com.thecarlauncher.media.MediaHub hub = com.thecarlauncher.media.MediaHub.get(c);
            r.append("  notification access=").append(hub.hasNotificationAccess()).append('\n');
            for (String s : hub.sources()) r.append("  ").append(s).append('\n');
            r.append("  current=").append(hub.packageName()).append(" title=").append(hub.title()).append(" playing=").append(hub.isPlaying()).append('\n');
        } catch (Throwable e) {
            r.append("  (").append(e.getClass().getSimpleName()).append(")\n");
        }
        return r.toString();
    }

    /** Factory apps worth studying for a unit build (radio, Bluetooth, MCU / car services). */
    private static final Pattern FACTORY = Pattern.compile(
            "(?i).*(radio|bluetooth|\\.bt|music|mcu|canbus|midware|gocsdk|goodocom|hzbhd|nwd|\\.tw\\.|qf\\.|syu|fyt|microntek).*");

    /**
     * Copies the unit's factory radio / Bluetooth / MCU apps (their APK files) to
     * Download/TheCarLauncher-apks/, so they can be studied for a build made for this unit.
     * Returns the folder and how many were copied. Blocking.
     */
    public static String exportApks(Context c) {
        File dir = new File(new File(android.os.Environment.getExternalStorageDirectory(), "Download"), "TheCarLauncher-apks");
        if (!dir.mkdirs() && !dir.isDirectory()) {
            File own = c.getExternalFilesDir("apks");
            if (own == null) return null;
            dir = own;
        }
        int n = 0;
        long total = 0;
        try {
            for (PackageInfo p : c.getPackageManager().getInstalledPackages(0)) {
                ApplicationInfo ai = p.applicationInfo;
                if (ai == null || (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || ai.sourceDir == null) continue;
                if (!FACTORY.matcher(p.packageName).matches()) continue;
                if (p.packageName.startsWith("com.android.") && !p.packageName.equals("com.android.bluetooth")) continue;
                File src = new File(ai.sourceDir);
                if (!src.canRead() || src.length() > 60L * 1024 * 1024 || total + src.length() > 400L * 1024 * 1024) continue;
                java.io.FileInputStream in = new java.io.FileInputStream(src);
                FileOutputStream o = new FileOutputStream(new File(dir, p.packageName + ".apk"));
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
                o.close();
                in.close();
                total += src.length();
                n++;
            }
        } catch (Exception ignored) {
        }
        return dir.getPath() + "  (" + n + " APK, " + (total / (1024 * 1024)) + " MB)";
    }

    /** Saves the report; returns the path shown to the user (or null). */
    public static String save(Context c, String text) {
        String name = "TheCarLauncher-report.txt";
        String shown = null;
        File[] dirs = {new File(android.os.Environment.getExternalStorageDirectory(), "Download"), c.getExternalFilesDir(null)};
        for (File d : dirs) {
            if (d == null) continue;
            try {
                d.mkdirs();
                File f = new File(d, name);
                FileOutputStream o = new FileOutputStream(f);
                o.write(text.getBytes("UTF-8"));
                o.close();
                if (shown == null) shown = f.getPath();
            } catch (Exception ignored) {
            }
        }
        return shown;
    }

    private static java.util.List<String> run(String cmd) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while ((l = in.readLine()) != null && out.size() < 4000) out.add(l);
            in.close();
            p.destroy();
        } catch (Exception ignored) {
        }
        return out;
    }
}
