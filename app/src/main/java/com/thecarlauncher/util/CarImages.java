package com.thecarlauncher.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.thecarlauncher.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * The car pictures used around the launcher (Vehicle status, ambient lighting). The owner can
 * replace either one with a picture from the unit's storage; otherwise the built-in ones are used.
 */
public final class CarImages {
    public static final int EXTERIOR = 0, INTERIOR = 1;
    private static final int MAX = 1600;

    private CarImages() {}

    private static File file(Context c, int which) {
        return new File(c.getFilesDir(), which == EXTERIOR ? "car_exterior.png" : "car_interior.jpg");
    }

    public static boolean isCustom(Context c, int which) { return file(c, which).exists(); }

    /** The owner's picture if set, else the built-in one. */
    public static Bitmap get(Context c, int which) {
        File f = file(c, which);
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) return b;
        }
        return BitmapFactory.decodeResource(c.getResources(), which == EXTERIOR ? R.drawable.car_exterior : R.drawable.car_interior);
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
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void reset(Context c, int which) {
        file(c, which).delete();
    }
}
