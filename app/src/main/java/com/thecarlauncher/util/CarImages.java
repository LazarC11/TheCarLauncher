package com.thecarlauncher.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.thecarlauncher.R;
import com.thecarlauncher.mib.Mib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * The car pictures used around the launcher (Vehicle status, ambient lighting). Until one is
 * chosen in Setup → Vehicle a "no picture selected" placeholder is shown. The choice is either a
 * built-in set (Corsa C) or a picture imported from the unit's storage.
 */
public final class CarImages {
    public static final int EXTERIOR = 0, INTERIOR = 1;
    private static final int MAX = 1600;

    private CarImages() {}

    private static File file(Context c, int which) {
        return new File(c.getFilesDir(), which == EXTERIOR ? "car_exterior.png" : "car_interior.jpg");
    }

    public static final String NONE = "none", BUILTIN = "corsa_c", CUSTOM = "custom";

    private static String key(int which) { return which == EXTERIOR ? "car_img_exterior" : "car_img_interior"; }

    /** NONE, BUILTIN or CUSTOM. */
    public static String mode(Context c, int which) {
        String m = Prefs.str(c, key(which), null);
        if (m == null) m = file(c, which).exists() ? CUSTOM : NONE; // pictures imported before this setting
        if (CUSTOM.equals(m) && !file(c, which).exists()) m = NONE;
        return m;
    }

    public static boolean isCustom(Context c, int which) { return CUSTOM.equals(mode(c, which)); }

    /** Short description for the settings row. */
    public static String label(Context c, int which) {
        String m = mode(c, which);
        if (BUILTIN.equals(m)) return Mib.t(c, "Corsa C (ugrađena)", "Corsa C (built-in)");
        if (CUSTOM.equals(m)) return Mib.t(c, "Uvezena slika", "Imported picture");
        return Mib.t(c, "Nije izabrana", "Not selected");
    }

    /** The chosen picture, or the "no picture selected" placeholder. */
    public static Bitmap get(Context c, int which) {
        String m = mode(c, which);
        if (CUSTOM.equals(m)) {
            Bitmap b = BitmapFactory.decodeFile(file(c, which).getPath());
            if (b != null) return b;
        } else if (BUILTIN.equals(m)) {
            return builtin(c, which);
        }
        return placeholder(c, which);
    }

    public static Bitmap builtin(Context c, int which) {
        return BitmapFactory.decodeResource(c.getResources(), which == EXTERIOR ? R.drawable.car_exterior : R.drawable.car_interior);
    }

    public static void useBuiltin(Context c, int which) {
        file(c, which).delete();
        Prefs.put(c, key(which), BUILTIN);
    }

    /** Amber warning triangle with "No picture selected" and where to choose one. */
    public static Bitmap placeholder(Context c, int which) {
        int w = 1200, h = 720;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        if (which == INTERIOR) cv.drawColor(0xFF0B0B0E);
        float cx = which == EXTERIOR ? w * 0.55f : w / 2f, top = which == EXTERIOR ? h * 0.30f : h * 0.24f;
        float side = 190f, th = side * 0.866f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFF2A516);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(14f);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path tri = new Path();
        tri.moveTo(cx, top);
        tri.lineTo(cx + side / 2f, top + th);
        tri.lineTo(cx - side / 2f, top + th);
        tri.close();
        cv.drawPath(tri, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.ROUND);
        cv.drawRoundRect(new android.graphics.RectF(cx - 8, top + th * 0.32f, cx + 8, top + th * 0.70f), 8, 8, p);
        cv.drawCircle(cx, top + th * 0.84f, 10f, p);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextAlign(Paint.Align.CENTER);
        t.setColor(0xFFFFFFFF);
        t.setTextSize(54f);
        try {
            t.setTypeface(Mib.semibold(c));
        } catch (Exception ignored) {
        }
        cv.drawText(Mib.t(c, "Nije izabrana slika", "No picture selected"), cx, top + th + 90f, t);
        t.setColor(0xFFB9BBC3);
        t.setTextSize(36f);
        cv.drawText(Mib.t(c, "Podešavanje → Vozilo", "Setup → Vehicle"), cx, top + th + 145f, t);
        return b;
    }

    /** Copies a picked image (scaled down to at most 1600 px); true when it was usable. */
    public static boolean set(Context c, int which, Uri uri) {
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
            FileOutputStream fo = new FileOutputStream(file(c, which));
            // PNG keeps a cut-out car's transparent background; the cabin photo is a JPEG.
            b.compress(which == EXTERIOR ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 92, fo);
            fo.close();
            Prefs.put(c, key(which), CUSTOM);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Back to "no picture selected". */
    public static void reset(Context c, int which) {
        file(c, which).delete();
        Prefs.put(c, key(which), NONE);
    }
}
