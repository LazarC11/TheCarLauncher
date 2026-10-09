package com.thecarlauncher.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Memory + disk cache for slippy-map tiles, shared by every map view. */
public final class TileCache {
    public interface Listener { void onTileLoaded(); }

    private static final long MAX_AGE_MS = 30L * 24 * 3600 * 1000;
    private static final long DISK_LIMIT = 120L * 1024 * 1024;
    private static TileCache sInstance;

    private final File dir;
    private final LruCache<String, Bitmap> memory;
    private final Set<String> pending = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new HashSet<>();
    private long lastFailureClear;

    public static synchronized TileCache get(Context c) {
        if (sInstance == null) sInstance = new TileCache(c.getApplicationContext());
        return sInstance;
    }

    private TileCache(Context c) {
        dir = new File(c.getCacheDir(), "tiles");
        dir.mkdirs();
        int kb = (int) Math.min(48 * 1024, Runtime.getRuntime().maxMemory() / 1024 / 6);
        memory = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String k, Bitmap b) {
                return b.getByteCount() / 1024;
            }
        };
        pool.execute(new Runnable() {
            @Override
            public void run() {
                trimDisk();
            }
        });
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public static String url(String source, int z, int x, int y, boolean hiDpi) {
        if ("osm_dark".equals(source)) {
            return String.format(Locale.US, "https://tile.openstreetmap.org/%d/%d/%d.png", z, x, y);
        }
        char sub = "abcd".charAt(Math.abs(x + y) % 4);
        return String.format(Locale.US, "https://%c.basemaps.cartocdn.com/dark_all/%d/%d/%d%s.png",
                sub, z, x, y, hiDpi ? "@2x" : "");
    }

    /** Returns the tile if cached in memory; otherwise schedules a load and returns null. */
    public Bitmap get(final String source, final int z, final int x, final int y, final boolean hiDpi) {
        final String key = source + "_" + (hiDpi ? "h" : "l") + "_" + z + "_" + x + "_" + y;
        Bitmap b = memory.get(key);
        if (b != null) return b;
        long now = System.currentTimeMillis();
        if (now - lastFailureClear > 30000) {
            failed.clear();
            lastFailureClear = now;
        }
        if (pending.contains(key) || failed.contains(key)) return null;
        pending.add(key);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap loaded = load(key, url(source, z, x, y, hiDpi));
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove(key);
                        if (loaded != null) {
                            memory.put(key, loaded);
                            for (Listener l : listeners.toArray(new Listener[0])) l.onTileLoaded();
                        } else {
                            failed.add(key);
                        }
                    }
                });
            }
        });
        return null;
    }

    /** Memory-only lookup used for drawing lower-zoom placeholders. */
    public Bitmap peek(String source, int z, int x, int y, boolean hiDpi) {
        return memory.get(source + "_" + (hiDpi ? "h" : "l") + "_" + z + "_" + x + "_" + y);
    }

    private Bitmap load(String key, String url) {
        File f = new File(dir, key + ".png");
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return b;
        }
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            con.setRequestProperty("User-Agent", "TheCarLauncher/1.0 (Android head unit launcher)");
            if (con.getResponseCode() != 200) return staleOrNull(f);
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            byte[] data = out.toByteArray();
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (b != null) {
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(data);
                fo.close();
            }
            return b;
        } catch (Exception e) {
            return staleOrNull(f);
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** Offline: an expired tile is still far better than nothing. */
    private static Bitmap staleOrNull(File f) {
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    private void trimDisk() {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= DISK_LIMIT) return;
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (File f : files) {
            if (total <= DISK_LIMIT * 3 / 4) break;
            total -= f.length();
            f.delete();
        }
    }
}
