package com.thecarlauncher.media;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Fallback for firmware that still opens its own radio / Bluetooth app when that source is switched
 * on (current android.car firmware does not, see CarService.enterSource): as soon as that app is on
 * top, the launcher's media screen comes back (checked every 150 ms for three seconds).
 */
public final class UnitReturn {
    private UnitReturn() {}

    /**
     * Coming back over the factory app needs "Display over other apps" (Android 10+): asked once,
     * on the main thread, before a source that opens a factory app is switched on.
     */
    public static void askOverlayOnce(Context c) {
        if (com.thecarlauncher.util.BringBack.canOverlay(c) || com.thecarlauncher.util.Prefs.bool(c, "overlay_asked", false)) return;
        com.thecarlauncher.util.Prefs.put(c, "overlay_asked", true);
        com.thecarlauncher.util.Ui.toast(c, com.thecarlauncher.mib.Mib.t(c,
                "Uključi \"Prikaz preko drugih aplikacija\" za Car Launcher, da se launcher vrati preko fabričke aplikacije.",
                "Turn on \"Display over other apps\" for Car Launcher, so it can come back over the factory app."));
        com.thecarlauncher.util.BringBack.requestOverlay(c);
    }

    /** Call on a worker thread right after switching the source. */
    public static void watch(final Context app, UnitBackend unit, String pkg) {
        // Units that never open their own apps (SYU) report no app on top: nothing to watch.
        if (pkg == null || unit.appOnTop() == null) return;
        final Handler main = new Handler(Looper.getMainLooper());
        for (int i = 0; i < 20; i++) {
            if (pkg.equals(unit.appOnTop())) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        com.thecarlauncher.util.BringBack.toMedia(app);
                        // This firmware does open its app: coming back over it needs "Display over
                        // other apps" on Android 10+, so ask for it now (once).
                        askOverlayOnce(app);
                    }
                });
                return;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
