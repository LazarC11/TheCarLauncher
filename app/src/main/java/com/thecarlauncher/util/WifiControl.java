package com.thecarlauncher.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wi-Fi from inside the launcher: on/off, nearby networks, the current connection and joining a
 * network. Android limits ordinary apps here: from Android 10 an app may not switch Wi-Fi itself
 * (the system's Wi-Fi panel opens instead), and new networks are handed to the system as a
 * "suggestion" (Android 11+ asks once in a system dialog). Everything else stays in the launcher.
 */
public final class WifiControl {
    public static final class Network {
        public final String ssid;
        public final int level; // 0..4
        public final boolean secure, connected;
        final String caps;

        Network(String ssid, int level, String caps, boolean connected) {
            this.ssid = ssid;
            this.level = level;
            this.caps = caps == null ? "" : caps;
            this.secure = this.caps.contains("WEP") || this.caps.contains("PSK") || this.caps.contains("SAE") || this.caps.contains("EAP");
            this.connected = connected;
        }

        boolean wpa3() { return caps.contains("SAE"); }

        boolean wep() { return caps.contains("WEP"); }
    }

    public interface Listener { void onWifiChanged(); }

    private static WifiControl instance;
    private final Context app;
    private final WifiManager wm;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean registered;

    public static synchronized WifiControl get(Context c) {
        if (instance == null) instance = new WifiControl(c.getApplicationContext());
        return instance;
    }

    private WifiControl(Context c) {
        app = c;
        wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            for (Listener l : new ArrayList<>(listeners)) l.onWifiChanged();
        }
    };

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        if (!registered && wm != null) {
            IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            f.addAction(WifiManager.RSSI_CHANGED_ACTION);
            try {
                app.registerReceiver(receiver, f);
                registered = true;
            } catch (Exception ignored) {
            }
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
        if (listeners.isEmpty() && registered) {
            try {
                app.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            registered = false;
        }
    }

    public boolean available() { return wm != null; }

    public boolean isOn() { return wm != null && wm.isWifiEnabled(); }

    /**
     * Turns Wi-Fi on or off. Returns false when Android does not let the launcher do it
     * (Android 10+); the caller then opens the system Wi-Fi panel.
     */
    @SuppressWarnings("deprecation")
    public boolean setOn(boolean on) {
        if (wm == null) return false;
        try {
            return wm.setWifiEnabled(on) && Build.VERSION.SDK_INT < 29;
        } catch (Exception e) {
            return false;
        }
    }

    /** The system's quick Wi-Fi panel (Android 10+) or its Wi-Fi page. */
    public Intent systemPanel() {
        return Build.VERSION.SDK_INT >= 29 ? new Intent(Settings.Panel.ACTION_WIFI) : new Intent(Settings.ACTION_WIFI_SETTINGS);
    }

    @SuppressWarnings("deprecation")
    public void scan() {
        try {
            if (wm != null) wm.startScan();
        } catch (Exception ignored) {
        }
    }

    /** SSID of the current connection, or null. */
    @SuppressWarnings("deprecation")
    public String connectedSsid() {
        if (wm == null) return null;
        try {
            WifiInfo i = wm.getConnectionInfo();
            if (i == null || i.getNetworkId() == -1) return null;
            String s = i.getSSID();
            if (s == null || s.equals("<unknown ssid>")) return null;
            return s.replace("\"", "");
        } catch (Exception e) {
            return null;
        }
    }

    /** Signal of the current connection 0..4, or -1. */
    @SuppressWarnings("deprecation")
    public int connectedLevel() {
        try {
            WifiInfo i = wm.getConnectionInfo();
            return i == null || i.getNetworkId() == -1 ? -1 : WifiManager.calculateSignalLevel(i.getRssi(), 5);
        } catch (Exception e) {
            return -1;
        }
    }

    /** "192.168.1.20 · 72 Mb/s" for the connected network. */
    @SuppressWarnings("deprecation")
    public String connectionDetails() {
        try {
            WifiInfo i = wm.getConnectionInfo();
            if (i == null || i.getNetworkId() == -1) return null;
            int ip = i.getIpAddress();
            String a = String.format(Locale.US, "%d.%d.%d.%d", ip & 0xFF, (ip >> 8) & 0xFF, (ip >> 16) & 0xFF, (ip >> 24) & 0xFF);
            return a + " · " + i.getLinkSpeed() + " Mb/s";
        } catch (Exception e) {
            return null;
        }
    }

    /** Nearby networks, strongest first, one entry per name; the connected one on top. */
    public List<Network> networks() {
        List<Network> out = new ArrayList<>();
        if (wm == null) return out;
        String cur = connectedSsid();
        Map<String, ScanResult> best = new LinkedHashMap<>();
        try {
            for (ScanResult r : wm.getScanResults()) {
                if (r.SSID == null || r.SSID.isEmpty()) continue;
                ScanResult b = best.get(r.SSID);
                if (b == null || r.level > b.level) best.put(r.SSID, r);
            }
        } catch (Exception ignored) {
        }
        for (ScanResult r : best.values()) {
            out.add(new Network(r.SSID, WifiManager.calculateSignalLevel(r.level, 5), r.capabilities, r.SSID.equals(cur)));
        }
        Collections.sort(out, new Comparator<Network>() {
            @Override
            public int compare(Network a, Network b) {
                if (a.connected != b.connected) return a.connected ? -1 : 1;
                return b.level - a.level;
            }
        });
        return out;
    }

    /** What happened when joining. */
    public enum Join { CONNECTING, ASKS_SYSTEM, FAILED }

    /**
     * Joins a network. Below Android 10 the launcher connects directly; on Android 10 the network is
     * suggested to the system (it connects on its own); on Android 11+ the system saves it after a
     * one-time confirmation dialog, which needs the activity (see {@link #addNetworksIntent}).
     */
    @SuppressWarnings("deprecation")
    @android.annotation.TargetApi(29)
    public Join join(Network n, String password) {
        if (wm == null) return Join.FAILED;
        if (Build.VERSION.SDK_INT < 29) {
            try {
                WifiConfiguration c = new WifiConfiguration();
                c.SSID = "\"" + n.ssid + "\"";
                if (!n.secure) c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                else if (n.wep()) {
                    c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    c.wepKeys[0] = "\"" + password + "\"";
                    c.wepTxKeyIndex = 0;
                } else c.preSharedKey = "\"" + password + "\"";
                int id = wm.addNetwork(c);
                if (id == -1) return Join.FAILED;
                wm.disconnect();
                boolean ok = wm.enableNetwork(id, true);
                wm.reconnect();
                return ok ? Join.CONNECTING : Join.FAILED;
            } catch (Exception e) {
                return Join.FAILED;
            }
        }
        if (Build.VERSION.SDK_INT >= 30) return Join.ASKS_SYSTEM;
        try {
            List<android.net.wifi.WifiNetworkSuggestion> l = new ArrayList<>();
            l.add(suggestion(n, password));
            int r = wm.addNetworkSuggestions(l);
            return r == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
                    || r == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE ? Join.CONNECTING : Join.FAILED;
        } catch (Exception e) {
            return Join.FAILED;
        }
    }

    /** Android 11+: the system dialog that saves the network for good (with internet). */
    @android.annotation.TargetApi(30)
    public Intent addNetworksIntent(Network n, String password) {
        if (Build.VERSION.SDK_INT < 30) return null;
        ArrayList<android.net.wifi.WifiNetworkSuggestion> l = new ArrayList<>();
        l.add(suggestion(n, password));
        Intent i = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
        i.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, l);
        return i;
    }

    @android.annotation.TargetApi(29)
    private static android.net.wifi.WifiNetworkSuggestion suggestion(Network n, String password) {
        android.net.wifi.WifiNetworkSuggestion.Builder b = new android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(n.ssid);
        if (n.secure && password != null && !password.isEmpty()) {
            if (n.wpa3()) b.setWpa3Passphrase(password);
            else b.setWpa2Passphrase(password);
        }
        return b.build();
    }
}
