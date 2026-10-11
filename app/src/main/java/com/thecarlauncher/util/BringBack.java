package com.thecarlauncher.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.thecarlauncher.LauncherActivity;

/**
 * Brings the launcher back on top after another app (the factory BT music app) was opened.
 * Android 10+ blocks activity starts from an app in the background, so a plain startActivity
 * silently does nothing. With "Display over other apps" granted, a 1-pixel overlay window
 * makes the launcher count as visible for a moment, which allows the start.
 */
public final class BringBack {
    /** Task id of the launcher, recorded by LauncherActivity. */
    public static volatile int launcherTask = -1;

    private BringBack() {}

    public static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    /** Opens the "Display over other apps" page for this launcher. */
    public static void requestOverlay(Context c) {
        if (Build.VERSION.SDK_INT < 23) return;
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + c.getPackageName()));
        if (!Ui.start(c, i)) Ui.start(c, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
    }

    public static void toMedia(Context c) {
        final Context app = c.getApplicationContext();
        final View pixel = canOverlay(app) ? addPixel(app) : null;
        Intent back = new Intent(app, LauncherActivity.class)
                .putExtra(LauncherActivity.EXTRA_SCREEN, "media")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Ui.start(app, back);
        // Second route: move our task to the front (allowed in the same situations).
        if (launcherTask >= 0) {
            try {
                ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                am.moveTaskToFront(launcherTask, ActivityManager.MOVE_TASK_NO_USER_ACTION);
            } catch (Throwable ignored) {
            }
        }
        if (pixel != null) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(pixel);
                    } catch (Throwable ignored) {
                    }
                }
            }, 1500);
        }
    }

    @SuppressWarnings("deprecation")
    private static View addPixel(Context app) {
        try {
            View v = new View(app);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).addView(v, lp);
            return v;
        } catch (Throwable e) {
            return null;
        }
    }
}
