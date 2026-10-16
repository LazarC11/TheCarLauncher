package com.thecarlauncher.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

/**
 * Screen brightness and colour profiles.
 * Brightness is the unit's own (Android's system brightness), which needs "Modify system settings"
 * for the launcher; until that is allowed it applies while the launcher is on screen.
 * Colour profiles (saturation, contrast, warmth) colour the launcher's picture; changing the
 * colours of other apps needs system rights an ordinary app does not have.
 */
public final class DisplayControl {
    public static final String P_SAT = "disp_saturation", P_CONTRAST = "disp_contrast", P_WARMTH = "disp_warmth",
            P_PROFILE = "disp_profile", P_WINDOW_BRIGHTNESS = "disp_window_brightness";

    /** name sr, name en, saturation %, contrast %, warmth (-5..5). */
    public static final Object[][] PROFILES = {
            {"Standardno", "Standard", 100, 100, 0},
            {"Živo", "Vivid", 135, 108, 0},
            {"Meko", "Soft", 80, 94, 1},
            {"Toplo", "Warm", 100, 100, 3},
            {"Hladno", "Cool", 100, 100, -3},
            {"Noć", "Night", 70, 90, 4},
    };

    private DisplayControl() {}

    public static boolean canWrite(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(c);
    }

    public static void requestWrite(Context c) {
        if (Build.VERSION.SDK_INT < 23) return;
        Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName()));
        if (!Ui.start(c, i)) Ui.start(c, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
    }

    /** Brightness 0..100 %. */
    public static int brightness(Context c) {
        int w = Prefs.integer(c, P_WINDOW_BRIGHTNESS, -1);
        if (!canWrite(c) && w >= 0) return w;
        try {
            int v = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return Math.round(v * 100f / 255f);
        } catch (Exception e) {
            return w >= 0 ? w : 70;
        }
    }

    public static boolean autoBrightness(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Exception e) {
            return false;
        }
    }

    /** Sets the unit's brightness (or the launcher window's, without the permission). */
    public static void setBrightness(Activity a, int pct) {
        pct = Math.max(5, Math.min(100, pct));
        if (canWrite(a)) {
            try {
                Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.round(pct * 255f / 100f));
                Prefs.put(a, P_WINDOW_BRIGHTNESS, -1);
                applyWindow(a);
                return;
            } catch (Exception ignored) {
            }
        }
        Prefs.put(a, P_WINDOW_BRIGHTNESS, pct);
        applyWindow(a);
    }

    public static void setAuto(Context c, boolean on) {
        if (!canWrite(c)) return;
        try {
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    on ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        } catch (Exception ignored) {
        }
    }

    /** Window-only brightness (used until system brightness may be changed). */
    public static void applyWindow(Activity a) {
        int w = Prefs.integer(a, P_WINDOW_BRIGHTNESS, -1);
        WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        lp.screenBrightness = w >= 0 && !canWrite(a) ? w / 100f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        a.getWindow().setAttributes(lp);
    }

    public static int saturation(Context c) { return Prefs.integer(c, P_SAT, 100); }

    public static int contrast(Context c) { return Prefs.integer(c, P_CONTRAST, 100); }

    public static int warmth(Context c) { return Prefs.integer(c, P_WARMTH, 0); }

    /** Index into PROFILES, or -1 for own values. */
    public static int profile(Context c) {
        for (int i = 0; i < PROFILES.length; i++) {
            if ((Integer) PROFILES[i][2] == saturation(c) && (Integer) PROFILES[i][3] == contrast(c)
                    && (Integer) PROFILES[i][4] == warmth(c)) return i;
        }
        return -1;
    }

    public static void setProfile(Context c, int i) {
        Prefs.put(c, P_SAT, (Integer) PROFILES[i][2]);
        Prefs.put(c, P_CONTRAST, (Integer) PROFILES[i][3]);
        Prefs.put(c, P_WARMTH, (Integer) PROFILES[i][4]);
    }

    public static void set(Context c, int sat, int contrast, int warmth) {
        Prefs.put(c, P_SAT, Math.max(0, Math.min(200, sat)));
        Prefs.put(c, P_CONTRAST, Math.max(60, Math.min(150, contrast)));
        Prefs.put(c, P_WARMTH, Math.max(-5, Math.min(5, warmth)));
    }

    /** Colours the launcher's whole picture with the chosen profile (nothing when neutral). */
    public static void applyColors(View root) {
        Context c = root.getContext();
        int sat = saturation(c), con = contrast(c), warm = warmth(c);
        if (sat == 100 && con == 100 && warm == 0) {
            root.setLayerType(View.LAYER_TYPE_NONE, null);
            return;
        }
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(sat / 100f);
        float k = con / 100f, t = 128f * (1 - k);
        m.postConcat(new ColorMatrix(new float[]{
                k, 0, 0, 0, t,
                0, k, 0, 0, t,
                0, 0, k, 0, t,
                0, 0, 0, 1, 0}));
        float w = warm * 0.03f;
        m.postConcat(new ColorMatrix(new float[]{
                1 + w, 0, 0, 0, 0,
                0, 1 + w * 0.3f, 0, 0, 0,
                0, 0, 1 - w * 1.2f, 0, 0,
                0, 0, 0, 1, 0}));
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(m));
        root.setLayerType(View.LAYER_TYPE_HARDWARE, p);
    }
}
