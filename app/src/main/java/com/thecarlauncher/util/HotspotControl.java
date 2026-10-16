package com.thecarlauncher.util;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;

/**
 * Wi-Fi hotspot. Android keeps switching it to system apps, so the launcher shows its state and
 * opens the system hotspot page for turning it on or off.
 */
public final class HotspotControl {
    private HotspotControl() {}

    /** On / off, or null when the system does not say. */
    public static Boolean isOn(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Object v = WifiManager.class.getMethod("isWifiApEnabled").invoke(wm);
            return v instanceof Boolean ? (Boolean) v : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** The system's hotspot / tethering page (several names across firmwares). */
    public static boolean openSystem(Context c) {
        Intent[] tries = {
                new Intent().setClassName("com.android.settings", "com.android.settings.Settings$TetherSettingsActivity"),
                new Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
                new Intent().setClassName("com.android.settings", "com.android.settings.Settings$WifiTetherSettingsActivity"),
                new Intent("android.settings.TETHER_SETTINGS"),
                new Intent(Settings.ACTION_WIRELESS_SETTINGS),
        };
        for (Intent i : tries) if (Ui.start(c, i)) return true;
        return false;
    }
}
