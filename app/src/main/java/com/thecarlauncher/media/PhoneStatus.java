package com.thecarlauncher.media;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The phone as the factory status bar shows it: Bluetooth connected, battery level, and
 * whether notifications are waiting. Battery comes from the Bluetooth hands-free link
 * (the phone reports it to the car) or the device battery broadcast, whichever the unit sends.
 */
public final class PhoneStatus {
    public interface Listener { void onPhoneStatusChanged(); }

    // Broadcasts of the Android Bluetooth stack (hidden constants, stable strings).
    private static final String ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL";
    private static final String ACTION_HF_EVENT = "android.bluetooth.headsetclient.profile.action.AG_EVENT";
    private static final String EXTRA_HF_BATTERY = "android.bluetooth.headsetclient.extra.BATTERY_LEVEL";
    private static final String EXTRA_HF_SIGNAL = "android.bluetooth.headsetclient.extra.NETWORK_SIGNAL_STRENGTH";
    private static final String EXTRA_HF_NETWORK = "android.bluetooth.headsetclient.extra.NETWORK_STATUS";
    private static final String ACTION_HF_STATE = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED";
    private static final String ACTION_A2DP_SINK_STATE = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";
    private static final int STATE_CONNECTED = 2;

    private static PhoneStatus sInstance;
    private final Context app;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> connected = new ArrayList<>();
    private int battery = -1;
    /** Mobile signal 0–5 bars; -1 unknown, -2 no service. */
    private int signal = -1;
    private final List<StatusBarNotification> notifications = new ArrayList<>();

    public static synchronized PhoneStatus get(Context c) {
        if (sInstance == null) sInstance = new PhoneStatus(c.getApplicationContext());
        return sInstance;
    }

    private PhoneStatus(Context c) {
        app = c;
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(ACTION_BATTERY);
        f.addAction(ACTION_HF_EVENT);
        f.addAction(ACTION_HF_STATE);
        f.addAction(ACTION_A2DP_SINK_STATE);
        try {
            if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
            else c.registerReceiver(receiver, f);
        } catch (Exception ignored) {
        }
        refresh();
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
        l.onPhoneStatusChanged();
    }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        for (Listener l : listeners) l.onPhoneStatusChanged();
    }

    public boolean connected() { return !connected.isEmpty() || gocPhone; }

    private boolean gocPhone;

    /** The Goodocom daemon reports the phone disconnected. */
    void onGocGone() {
        if (!gocPhone) return;
        gocPhone = false;
        changed();
    }

    /** Phone signal / battery reported by the Goodocom Bluetooth daemon (0–5 scales). */
    void onGoc(int sig, int batt) {
        if (sig < 0 && batt < 0) return;
        gocPhone = true;
        if (sig >= 0) signal = Math.min(5, sig);
        if (batt >= 0) battery = batt <= 5 ? batt * 20 : Math.min(100, batt);
        changed();
    }

    /** Phone battery 0–100, or -1 when the phone does not report it. */
    public int battery() { return connected() ? battery : -1; }

    /** Phone signal 0–5 bars, -2 for no service, -1 when the phone does not report it. */
    public int signal() { return connected() ? signal : -1; }

    private boolean allowed() {
        return Build.VERSION.SDK_INT < 31
                || app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** Reads the current state from bonded devices (after start-up or a permission grant). */
    @SuppressWarnings("MissingPermission")
    public void refresh() {
        connected.clear();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null && a.isEnabled() && allowed()) {
                Method isConn = BluetoothDevice.class.getMethod("isConnected");
                Method level = null;
                try {
                    level = BluetoothDevice.class.getMethod("getBatteryLevel");
                } catch (Exception ignored) {
                }
                for (BluetoothDevice d : a.getBondedDevices()) {
                    if (!(Boolean) isConn.invoke(d)) continue;
                    connected.add(d.getAddress());
                    if (level != null) {
                        int b = (Integer) level.invoke(d);
                        if (b >= 0) battery = b;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        changed();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String action = i.getAction();
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String addr = d == null ? null : d.getAddress();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                if (addr != null && !connected.contains(addr)) connected.add(addr);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                if (addr != null) connected.remove(addr);
                if (connected.isEmpty()) {
                    battery = -1;
                    signal = -1;
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                refresh();
                return;
            } else if (ACTION_BATTERY.equals(action)) {
                int b = i.getIntExtra(EXTRA_BATTERY, -1);
                if (b >= 0) battery = b;
            } else if (ACTION_HF_EVENT.equals(action)) {
                // Hands-free indicator: 0–5 bars.
                if (i.hasExtra(EXTRA_HF_BATTERY)) battery = Math.max(0, Math.min(5, i.getIntExtra(EXTRA_HF_BATTERY, 0))) * 20;
                if (i.hasExtra(EXTRA_HF_SIGNAL)) signal = Math.max(0, Math.min(5, i.getIntExtra(EXTRA_HF_SIGNAL, 0)));
                if (i.hasExtra(EXTRA_HF_NETWORK) && i.getIntExtra(EXTRA_HF_NETWORK, 1) == 0) signal = -2;
                if (addr != null && !connected.contains(addr)) connected.add(addr);
            } else if (ACTION_HF_STATE.equals(action) || ACTION_A2DP_SINK_STATE.equals(action)) {
                int st = i.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                if (addr != null) {
                    if (st == STATE_CONNECTED && !connected.contains(addr)) connected.add(addr);
                }
            }
            changed();
        }
    };

    // ---- Notifications (fed by MediaListenerService) -----------------------------------------

    /** Messages and other dismissable notifications worth an envelope in the status bar. */
    static boolean counts(Context c, StatusBarNotification n) {
        if (n.isOngoing() || !n.isClearable()) return false;
        if (n.getPackageName().equals(c.getPackageName())) return false;
        android.app.Notification no = n.getNotification();
        if ((no.flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        CharSequence title = no.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        return title != null && title.length() > 0;
    }

    synchronized void setNotifications(StatusBarNotification[] all) {
        notifications.clear();
        if (all != null) for (StatusBarNotification n : all) if (counts(app, n)) notifications.add(n);
        changed();
    }

    public synchronized List<StatusBarNotification> notifications() {
        return new ArrayList<>(notifications);
    }
}
