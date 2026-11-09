package com.thecarlauncher.util;

import android.content.Context;

/**
 * Which build fits the unit the launcher runs on, judged from what is installed: Software update
 * installs that build ("TheCarLauncher_<id>.apk"; the QF build is plain "TheCarLauncher.apk").
 */
public final class UnitKind {
    private UnitKind() {}

    /** Build id for this unit: "hzbhd", "carsvc" or "qf" (the default). */
    public static String detect(Context c) {
        for (String p : new String[]{"com.hzbhd.midware.service", "com.hzbhd.radio", "com.hzbhd.bluetooth"}) {
            if (installed(c, p)) return "hzbhd";
        }
        for (String p : new String[]{"android.car.service.bt", "android.car.app.radio", "android.car.app.bt"}) {
            if (installed(c, p)) return "carsvc";
        }
        return "qf";
    }

    /** Release asset holding the build for this unit. */
    public static String assetName(String id) {
        // "_" sorts after ".", so older launchers (which took the first APK) still pick the QF build.
        return "qf".equals(id) ? "TheCarLauncher.apk" : "TheCarLauncher_" + id + ".apk";
    }

    public static String name(String id) {
        if ("hzbhd".equals(id)) return "Allwinner T3 / hzbhd";
        if ("carsvc".equals(id)) return "android.car firmware";
        return "UIS7862 / QF";
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
