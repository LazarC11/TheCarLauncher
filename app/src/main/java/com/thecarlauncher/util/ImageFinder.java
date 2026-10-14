package com.thecarlauncher.util;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pictures on the unit and on USB sticks, for choosing the car pictures inside the launcher.
 * Head-unit firmwares often have no working system picker (or one that never returns a result
 * to a home app), so the launcher lists the pictures itself.
 */
public final class ImageFinder {
    public static final class Image {
        public final Uri uri;
        public final String name, folder;
        public final long date;

        Image(Uri uri, String name, String folder, long date) {
            this.uri = uri;
            this.name = name;
            this.folder = folder;
            this.date = date;
        }
    }

    private static final int MAX = 400;

    private ImageFinder() {}

    public static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED || Storage.hasAllFiles();
    }

    /** All pictures found, newest first. Blocking: call on a worker thread. */
    public static List<Image> find(Context c) {
        List<Image> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Android's media index (phone photos, downloads, screenshots).
        try {
            Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] cols = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATA};
            Cursor q = c.getContentResolver().query(base, cols, null, null,
                    MediaStore.Images.Media.DATE_MODIFIED + " DESC");
            if (q != null) {
                while (q.moveToNext() && out.size() < MAX) {
                    String path = q.getString(3);
                    if (path != null) seen.add(path);
                    String folder = path == null ? "" : new File(path).getParentFile() == null ? "" : new File(path).getParentFile().getName();
                    out.add(new Image(ContentUris.withAppendedId(base, q.getLong(0)), q.getString(1), folder, q.getLong(2) * 1000));
                }
                q.close();
            }
        } catch (Exception ignored) {
        }
        // USB sticks and folders the index skips.
        List<File> roots = new ArrayList<>(Storage.removableRoots(c));
        File ext = Environment.getExternalStorageDirectory();
        for (String d : new String[]{"DCIM", "Pictures", "Download", "Wallpaper", "Wallpapers"}) roots.add(new File(ext, d));
        for (File r : roots) walk(r, 0, out, seen);
        Collections.sort(out, new Comparator<Image>() {
            @Override
            public int compare(Image a, Image b) { return Long.compare(b.date, a.date); }
        });
        return out;
    }

    private static void walk(File dir, int depth, List<Image> out, Set<String> seen) {
        if (out.size() >= MAX || depth > 5 || dir == null || !dir.canRead()) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (out.size() >= MAX) return;
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (!n.equalsIgnoreCase("Android")) walk(f, depth + 1, out, seen);
                continue;
            }
            String l = n.toLowerCase(Locale.ROOT);
            if (!(l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp"))) continue;
            if (!seen.add(f.getPath())) continue;
            out.add(new Image(Uri.fromFile(f), n, dir.getName(), f.lastModified()));
        }
    }

    /** A small preview, about px wide. Blocking. */
    public static Bitmap thumb(Context c, Uri uri, int px) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= px) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            in = c.getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
