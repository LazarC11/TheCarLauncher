package com.thecarlauncher.util;

import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.widget.Toast;

import java.util.Locale;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    public static void toast(Context c, CharSequence msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /** Starts an activity, returning false instead of crashing if nothing handles it. */
    public static boolean start(Context c, Intent i) {
        try {
            if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEmpty(CharSequence s) {
        return s == null || s.toString().trim().length() == 0;
    }
}
