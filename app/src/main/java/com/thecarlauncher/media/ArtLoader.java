package com.thecarlauncher.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Async thumbnail loader for library rows. */
public final class ArtLoader {
    private static final Bitmap NONE = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
    private static final LruCache<Long, Bitmap> CACHE = new LruCache<Long, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(Long k, Bitmap b) {
            return Math.max(1, b.getByteCount() / 1024);
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ArtLoader() {}

    /** Shows album art in the view, or leaves the fallback icon if there is none. */
    public static void into(final Context c, final ImageView v, final long albumId, final int fallbackRes) {
        v.setTag(albumId);
        Bitmap cached = CACHE.get(albumId);
        if (cached != null) {
            show(v, cached, fallbackRes);
            return;
        }
        show(v, NONE, fallbackRes);
        final Context app = c.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap b = MusicLibrary.loadArt(app, albumId, 0, 128);
                final Bitmap result = b == null ? NONE : b;
                CACHE.put(albumId, result);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        Object tag = v.getTag();
                        if (tag instanceof Long && (Long) tag == albumId) show(v, result, fallbackRes);
                    }
                });
            }
        });
    }

    private static void show(ImageView v, Bitmap b, int fallbackRes) {
        if (b == NONE) {
            v.setImageResource(fallbackRes);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = (int) (8 * v.getResources().getDisplayMetrics().density);
            v.setPadding(pad, pad, pad, pad);
            v.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF9AA6B4));
        } else {
            v.setImageBitmap(b);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setPadding(0, 0, 0, 0);
            v.setImageTintList(null);
        }
    }
}
