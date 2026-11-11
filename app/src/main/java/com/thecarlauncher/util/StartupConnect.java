package com.thecarlauncher.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;

import com.thecarlauncher.media.BtDevices;
import com.thecarlauncher.media.UnitBackend;

import java.util.ArrayList;
import java.util.List;

/**
 * Start-up connections: when the launcher starts and when the unit wakes up (ignition on), switch
 * Bluetooth / Wi-Fi on, connect the chosen phone and network, and start the CarPlay / Android Auto
 * app, so the phone is ready without touching anything. Each step is optional (Setup → Start-up
 * connections); presets set the usual combinations.
 * Bluetooth goes through the unit's own system (QF Goodocom module, android.car car service,
 * SYU MainServer) or Android's Bluetooth on other units. Android 10+ lets only system apps switch
 * Wi-Fi; there Android itself reconnects saved networks once Wi-Fi is on.
 */
public final class StartupConnect {
    public static final String P_BT_ON = "start_bt_on", P_BT_CONNECT = "start_bt_connect", P_BT_DEVICE = "start_bt_device",
            P_WIFI_ON = "start_wifi_on", P_WIFI_SSID = "start_wifi_ssid", P_PROJECTION = "start_projection";
    /** The last phone seen connected ("address|name"), used when no device is chosen. */
    private static final String P_LAST_PHONE = "start_last_phone";
    private static final long WAKE_GAP_MS = 2 * 60 * 1000L, RETRY_MS = 15000L, RETRY_FOR_MS = 2 * 60 * 1000L;

    private static boolean installed;
    private static long screenOffAt, lastRun;

    private StartupConnect() {}

    /** Presets for Setup: name sr, name en, Bluetooth on, connect phone, Wi-Fi on, start projection app. */
    public static final Object[][] PRESETS = {
            {"Isključeno", "Off", false, false, false, false},
            {"Bluetooth telefon", "Bluetooth phone", true, true, false, false},
            {"CarPlay / Android Auto", "CarPlay / Android Auto", true, false, true, true},
    };

    public static void applyPreset(Context c, int i) {
        Object[] p = PRESETS[i];
        Prefs.of(c).edit().putBoolean(P_BT_ON, (Boolean) p[2]).putBoolean(P_BT_CONNECT, (Boolean) p[3])
                .putBoolean(P_WIFI_ON, (Boolean) p[4]).putBoolean(P_PROJECTION, (Boolean) p[5]).apply();
    }

    /** Index of the preset the current switches match, or -1 (custom). */
    public static int preset(Context c) {
        for (int i = 0; i < PRESETS.length; i++) {
            Object[] p = PRESETS[i];
            if (Prefs.bool(c, P_BT_ON, false) == (Boolean) p[2] && Prefs.bool(c, P_BT_CONNECT, false) == (Boolean) p[3]
                    && Prefs.bool(c, P_WIFI_ON, false) == (Boolean) p[4] && Prefs.bool(c, P_PROJECTION, false) == (Boolean) p[5]) {
                return i;
            }
        }
        return -1;
    }

    public static boolean anyOn(Context c) {
        return Prefs.bool(c, P_BT_ON, false) || Prefs.bool(c, P_BT_CONNECT, false) || Prefs.bool(c, P_WIFI_ON, false)
                || Prefs.bool(c, P_PROJECTION, false);
    }

    /** Called when the launcher starts: runs once now and again whenever the unit wakes up. */
    public static synchronized void onLauncherStart(Context c) {
        final Context app = c.getApplicationContext();
        if (!installed) {
            installed = true;
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            try {
                app.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent i) {
                        if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                            screenOffAt = SystemClock.elapsedRealtime();
                        } else if (screenOffAt > 0 && SystemClock.elapsedRealtime() - screenOffAt >= WAKE_GAP_MS) {
                            screenOffAt = 0;
                            run(app);
                        }
                    }
                }, f);
            } catch (Exception ignored) {
            }
            run(app);
        }
    }

    /** "Try now" in Setup: runs the steps immediately. */
    public static synchronized void runNow(Context c) {
        lastRun = 0;
        run(c.getApplicationContext());
    }

    /** Runs the chosen steps in the background (at most once a minute). */
    public static synchronized void run(final Context app) {
        long now = SystemClock.elapsedRealtime();
        if (lastRun > 0 && now - lastRun < 60000L) return;
        lastRun = now;
        if (!anyOn(app)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                runSteps(app);
            }
        }, "startup-connect").start();
    }

    private static void runSteps(final Context app) {
        if (Prefs.bool(app, P_BT_ON, false)) bluetoothOn(app);
        if (Prefs.bool(app, P_WIFI_ON, false)) wifiOn(app);
        if (Prefs.bool(app, P_PROJECTION, false)) startProjection(app);
        if (Prefs.bool(app, P_BT_CONNECT, false)) {
            // The phone may not be in range yet (driver still walking up): retry for a while.
            long until = SystemClock.elapsedRealtime() + RETRY_FOR_MS;
            sleep(3000);
            while (SystemClock.elapsedRealtime() < until) {
                if (phoneConnected(app)) break;
                connectPhone(app);
                sleep(RETRY_MS);
            }
        }
        learnPhone(app);
    }

    // ---- Bluetooth -----------------------------------------------------------------------------

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private static void bluetoothOn(Context c) {
        UnitBackend unit = UnitBackend.Get.of(c);
        if (unit != null && unit.available()) {
            unit.btPowerOn();
            return;
        }
        if (com.thecarlauncher.unit.Unit.QF) return; // the Goodocom module is always on
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null && !a.isEnabled()) a.enable();
        } catch (Throwable ignored) {
        }
    }

    /** {address, name} of the phone to connect: the chosen one, else the last one seen. */
    public static String[] target(Context c) {
        String chosen = Prefs.str(c, P_BT_DEVICE, null);
        String v = chosen != null && chosen.length() > 0 ? chosen : Prefs.str(c, P_LAST_PHONE, null);
        if (v == null || v.indexOf('|') <= 0) return null;
        int bar = v.indexOf('|');
        return new String[]{v.substring(0, bar), v.substring(bar + 1)};
    }

    public static void choose(Context c, String address, String name) {
        Prefs.put(c, P_BT_DEVICE, address == null ? null : address + "|" + (name == null ? "" : name));
    }

    private static boolean phoneConnected(Context c) {
        UnitBackend unit = UnitBackend.Get.of(c);
        if (unit != null && unit.available()) return unit.btPhone() != null;
        if (com.thecarlauncher.unit.Unit.QF) return BtDevices.get(c).isConnected();
        return androidConnected() != null;
    }

    private static void connectPhone(Context c) {
        String[] t = target(c);
        if (t == null) return;
        UnitBackend unit = UnitBackend.Get.of(c);
        if (unit != null && unit.available()) {
            unit.btConnect(t[0], t[1]);
            return;
        }
        if (com.thecarlauncher.unit.Unit.QF) {
            for (BtDevices.Device d : BtDevices.get(c).known()) {
                if (BtDevices.norm(d.address).equals(BtDevices.norm(t[0]))) {
                    BtDevices.get(c).connect(d);
                    return;
                }
            }
            return;
        }
        androidConnect(c, t[0]);
    }

    /** Remembers the phone that is connected now, so "the last phone" can be reconnected. */
    public static void learnPhone(Context c) {
        String[] p = connectedPhone(c);
        if (p != null && p[0] != null && p[0].length() > 0) Prefs.put(c, P_LAST_PHONE, p[0] + "|" + (p[1] == null ? "" : p[1]));
    }

    /** {address, name} of the phone connected now, or null. Blocking (unit service calls). */
    public static String[] connectedPhone(Context c) {
        UnitBackend unit = UnitBackend.Get.of(c);
        if (unit != null && unit.available()) return unit.btPhone();
        if (com.thecarlauncher.unit.Unit.QF) {
            BtDevices d = BtDevices.get(c);
            return d.isConnected() && d.connectedAddress() != null ? new String[]{d.connectedAddress(), d.connectedName()} : null;
        }
        return androidConnected();
    }

    /** Phones to choose from: remembered by the unit (QF) or paired with Android, plus the last one seen. */
    @SuppressWarnings("MissingPermission")
    public static List<String[]> knownPhones(Context c) {
        List<String[]> out = new ArrayList<>();
        if (com.thecarlauncher.unit.Unit.QF || BtDevices.get(c).nforce()) {
            for (BtDevices.Device d : BtDevices.get(c).known()) out.add(new String[]{d.address, d.label()});
        } else if (UnitBackend.Get.of(c) == null) {
            try {
                BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                if (a != null && a.getBondedDevices() != null) {
                    for (BluetoothDevice d : a.getBondedDevices()) out.add(new String[]{d.getAddress(), d.getName()});
                }
            } catch (Throwable ignored) {
            }
        }
        String last = Prefs.str(c, P_LAST_PHONE, null);
        if (last != null && last.indexOf('|') > 0) {
            String addr = last.substring(0, last.indexOf('|'));
            boolean listed = false;
            for (String[] p : out) if (BtDevices.norm(p[0]).equals(BtDevices.norm(addr))) listed = true;
            if (!listed) out.add(new String[]{addr, last.substring(last.indexOf('|') + 1)});
        }
        return out;
    }

    // Android's own Bluetooth (units without a separate module): the head unit is the A2DP sink /
    // hands-free side. Connecting a profile is a hidden call that works for system apps.
    private static final int A2DP_SINK = 11, HEADSET_CLIENT = 16;

    @SuppressWarnings("MissingPermission")
    private static void androidConnect(Context c, final String address) {
        try {
            final BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) return;
            final BluetoothDevice dev = a.getRemoteDevice(address);
            for (int profile : new int[]{HEADSET_CLIENT, A2DP_SINK}) {
                a.getProfileProxy(c, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int p, BluetoothProfile proxy) {
                        try {
                            proxy.getClass().getMethod("connect", BluetoothDevice.class).invoke(proxy, dev);
                        } catch (Throwable ignored) {
                        }
                        try {
                            a.closeProfileProxy(p, proxy);
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int p) {}
                }, profile);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("MissingPermission")
    private static String[] androidConnected() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled() || a.getBondedDevices() == null) return null;
            for (BluetoothDevice d : a.getBondedDevices()) {
                Object on = d.getClass().getMethod("isConnected").invoke(d);
                if (Boolean.TRUE.equals(on)) return new String[]{d.getAddress(), d.getName()};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---- Wi-Fi ---------------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private static void wifiOn(Context c) {
        WifiControl w = WifiControl.get(c);
        if (!w.isOn()) w.setOn(true);
        final String ssid = Prefs.str(c, P_WIFI_SSID, null);
        if (ssid == null || ssid.length() == 0 || Build.VERSION.SDK_INT >= 29) return;
        // Below Android 10: prefer the chosen saved network once Wi-Fi is up.
        for (int i = 0; i < 20 && !w.isOn(); i++) sleep(500);
        if (ssid.equals(w.connectedSsid())) return;
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            List<WifiConfiguration> saved = wm.getConfiguredNetworks();
            if (saved == null) return;
            for (WifiConfiguration cfg : saved) {
                if (cfg.SSID != null && cfg.SSID.replace("\"", "").equals(ssid)) {
                    wm.enableNetwork(cfg.networkId, true);
                    wm.reconnect();
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ---- CarPlay / Android Auto ------------------------------------------------------------------

    /** Starts the projection app (it listens for the phone only once started) and comes back. */
    private static void startProjection(final Context c) {
        final String pkg = projectionPackage(c);
        if (pkg == null) return;
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(new Runnable() {
            @Override
            public void run() {
                if (com.thecarlauncher.apps.AppRepository.launchPackage(c, pkg)) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            BringBack.toLauncher(c);
                        }
                    }, 2500);
                }
            }
        });
    }

    /** The projection app chosen under App-Connect, else the first one found. */
    public static String projectionPackage(Context c) {
        String p = Prefs.str(c, Prefs.PROJECTION_PKG, null);
        if (p != null && com.thecarlauncher.apps.AppRepository.get(c).findPackage(p) != null) return p;
        java.util.regex.Pattern m = java.util.regex.Pattern.compile(
                "(?i).*(zlink|zjinnova|carplay|car ?link|autokit|android ?auto|easyconn|tlink).*");
        for (com.thecarlauncher.apps.AppRepository.App a : com.thecarlauncher.apps.AppRepository.get(c).all()) {
            if (m.matcher(a.pkg + " " + a.label).matches()) return a.pkg;
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
