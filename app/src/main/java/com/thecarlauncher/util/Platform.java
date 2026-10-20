package com.thecarlauncher.util;

import android.content.Context;

import java.io.File;

/**
 * Which kind of head unit this is. The launcher's direct radio and Bluetooth paths were built on
 * a UIS7862 unit with the QF firmware (MCU service, the QF Bluetooth app and the Goodocom
 * module on /dev/goc_serial). Other units (e.g. Allwinner T3 boards from NWD / TopWay) work
 * through their own factory apps: radio and Bluetooth music are opened there, and the launcher
 * shows what they play.
 */
public final class Platform {
    public static final String P_PLATFORM = "platform"; // "auto", "qf", "other"
    public static final int QF = 0, OTHER = 1;

    private Platform() {}

    /** What was detected, ignoring the setting. */
    public static int detected(Context c) {
        if (installed(c, "com.qf.bluetooth") || new File("/dev/goc_serial").exists()) return QF;
        return OTHER;
    }

    public static int current(Context c) {
        String p = Prefs.str(c, P_PLATFORM, "auto");
        if ("qf".equals(p)) return QF;
        if ("other".equals(p)) return OTHER;
        return detected(c);
    }

    public static boolean qf(Context c) { return current(c) == QF; }

    /** TopWay framework class used by T3 / MST768 units for the MCU (radio, keys). */
    public static boolean hasTopWay() {
        try {
            Class.forName("android.tw.john.TWUtil");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String name(Context c, int p) {
        return p == QF ? "UIS7862 / QF (MCU, Goodocom BT)"
                : hasTopWay() ? Mib_t(c, "Druga (TopWay / T3, fabričke aplikacije)", "Other (TopWay / T3, factory apps)")
                : Mib_t(c, "Druga (fabričke aplikacije)", "Other (factory apps)");
    }

    private static String Mib_t(Context c, String sr, String en) { return com.thecarlauncher.mib.Mib.t(c, sr, en); }

    static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
