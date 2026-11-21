package com.thecarlauncher.car;

import android.content.Context;

import com.thecarlauncher.mib.LoadingBanner;
import com.thecarlauncher.screens.VehicleScreen;
import com.thecarlauncher.util.Prefs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vehicle events (oil change, service, registration, tyres, low fuel) drop the top banner down
 * once when they come up, with their factory warning light, and once more when they turn urgent.
 * A tap opens the Vehicle screen. Events that are over are forgotten, so they can come up again.
 */
public final class CarAlerts {
    static final String P_SHOWN = "car_alerts_shown";
    private static final int SHOW_MS = 7000;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private CarAlerts() {}

    /** Compares the current notices with the ones already announced; announces the new ones. */
    public static void check(Context c, Runnable openVehicle) {
        List<CarData.Notice> notices = CarData.get(c).notices();
        Set<String> before = split(Prefs.str(c, P_SHOWN, ""));
        Set<String> now = new HashSet<>();
        int n = 0;
        for (final CarData.Notice x : notices) {
            final String key = x.kind + (x.urgent ? "!" : "");
            now.add(key);
            if (before.contains(key)) continue;
            // Several at once: one after the other, each for a few seconds.
            MAIN.postDelayed(new Runnable() {
                public void run() {
                    LoadingBanner.notice("car-" + key, VehicleScreen.lamp(x), x.text, SHOW_MS, openVehicle);
                }
            }, (long) SHOW_MS * n++);
        }
        Prefs.put(c, P_SHOWN, join(now));
    }

    private static Set<String> split(String s) {
        Set<String> out = new HashSet<>();
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static String join(Set<String> s) {
        StringBuilder b = new StringBuilder();
        for (String p : s) b.append(b.length() == 0 ? "" : ",").append(p);
        return b.toString();
    }
}
