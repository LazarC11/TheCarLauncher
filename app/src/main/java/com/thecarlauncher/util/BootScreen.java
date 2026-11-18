package com.thecarlauncher.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Optional start picture (Setup → Screen → Boot screen, off by default): shown full screen when
 * the launcher starts after the unit boots, then it fades into the launcher. The Android boot
 * animation itself belongs to the firmware and is not changed.
 */
public final class BootScreen {
    public static final String P_ON = "boot_screen_on", P_SECONDS = "boot_screen_seconds";
    private static final int MAX = 1920;
    /** Once per process: not again when the launcher's screen is only rebuilt. */
    private static boolean shown;

    private BootScreen() {}

    public static File file(Context c) { return new File(c.getFilesDir(), "boot_screen.jpg"); }

    public static boolean on(Context c) { return Prefs.bool(c, P_ON, false); }

    public static boolean hasPicture(Context c) { return file(c).exists(); }

    public static int seconds(Context c) { return Math.max(1, Math.min(10, Prefs.integer(c, P_SECONDS, 3))); }

    public static void setSeconds(Context c, int s) { Prefs.put(c, P_SECONDS, Math.max(1, Math.min(10, s))); }

    /** Copies the chosen picture (scaled to the screen) into the launcher's own files. */
    public static boolean set(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (o.outWidth <= 0) return false;
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = c.getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (b == null) return false;
            FileOutputStream fo = new FileOutputStream(file(c));
            b.compress(Bitmap.CompressFormat.JPEG, 92, fo);
            fo.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void clear(Context c) {
        file(c).delete();
        Prefs.put(c, P_ON, false);
    }

    /** Shows the picture over {@code root} on the first start of this process, if switched on. */
    public static void showOnStart(Context c, final ViewGroup root) {
        if (shown) return;
        shown = true;
        if (!on(c) || !hasPicture(c)) return;
        Bitmap b = BitmapFactory.decodeFile(file(c).getAbsolutePath());
        if (b == null) return;
        final ImageView v = new ImageView(c);
        v.setBackgroundColor(0xFF000000);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageBitmap(b);
        v.setClickable(true); // nothing underneath reacts while it is shown
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) { fade(root, v); }
        });
        root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.postDelayed(new Runnable() {
            @Override
            public void run() { fade(root, v); }
        }, seconds(c) * 1000L);
    }

    private static void fade(final ViewGroup root, final View v) {
        if (v.getParent() == null || v.getTag() != null) return;
        v.setTag("fading");
        v.animate().alpha(0f).setDuration(600).withEndAction(new Runnable() {
            @Override
            public void run() { root.removeView(v); }
        }).start();
    }
}
