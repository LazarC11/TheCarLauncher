package com.thecarlauncher.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
    private final Context app;
    /** Offline tiles are drawn one at a time (the map renderer is not thread-safe). */
    private final ExecutorService offlinePool = Executors.newSingleThreadExecutor();
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
        app = c;
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

    /**
     * Tile address. Since 2026 CARTO's basemaps need a (free) API key and draw "API KEY
     * REQUIRED" over every tile without one, so without a key the day map uses
     * OpenStreetMap's own tiles instead.
     */
    public static String url(String source, int z, int x, int y, boolean hiDpi, String cartoKey) {
        boolean carto = cartoKey != null && !cartoKey.isEmpty();
        if ("osm_dark".equals(source) || !carto) {
            return String.format(Locale.US, "https://tile.openstreetmap.org/%d/%d/%d.png", z, x, y);
        }
        String style = "mib_day".equals(source) ? "rastertiles/voyager" : "dark_all";
        return String.format(Locale.US, "https://basemaps.cartocdn.com/%s/%d/%d/%d%s.png?key=%s",
                style, z, x, y, hiDpi ? "@2x" : "", Uri.encode(cartoKey.trim()));
    }

    private String cartoKey() {
        return com.thecarlauncher.util.Prefs.str(app, com.thecarlauncher.util.Prefs.CARTO_KEY, null);
    }

    /** Cache name of a tile; the tile server is part of it so old watermarked tiles are not reused. */
    private String keyOf(String source, int z, int x, int y, boolean hiDpi) {
        String k = cartoKey();
        String server = "osm_dark".equals(source) || k == null || k.isEmpty() ? "o" : "c";
        return source + "_" + server + (hiDpi ? "h" : "l") + "_" + z + "_" + x + "_" + y;
    }

    /** True when the map has something to draw: an online style, or a downloaded offline map. */
    public boolean draws(String source) {
        return !com.thecarlauncher.util.Prefs.TILES_OFF.equals(source) || offlineReady();
    }

    private boolean offlineReady() {
        com.thecarlauncher.nav.OfflineMaps om = com.thecarlauncher.nav.OfflineMaps.get(app);
        return om.enabled() && om.available();
    }

    private static String offlineKey(int z, int x, int y, boolean hiDpi) {
        return "offline_" + (hiDpi ? "h" : "l") + "_" + z + "_" + x + "_" + y;
    }

    /** Tiles inside a downloaded map are drawn on the unit, without internet. */
    private Bitmap getOffline(final int z, final int x, final int y, final boolean hiDpi) {
        final String key = offlineKey(z, x, y, hiDpi);
        Bitmap b = memory.get(key);
        if (b != null || pending.contains(key) || failed.contains(key)) return b;
        pending.add(key);
        offlinePool.execute(new Runnable() {
            @Override
            public void run() {
                // Drawn tiles are kept on disk too: drawing is the slow part on a head unit.
                File f = new File(dir, key + ".png");
                Bitmap d = f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
                if (d == null) {
                    d = com.thecarlauncher.nav.OfflineMaps.get(app).render(z, x, y, hiDpi ? 512 : 256, hiDpi ? 2f : 1f);
                    if (d != null) {
                        try {
                            FileOutputStream fo = new FileOutputStream(f);
                            d.compress(Bitmap.CompressFormat.PNG, 100, fo);
                            fo.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                final Bitmap drawn = d;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove(key);
                        if (drawn != null) {
                            memory.put(key, drawn);
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

    /** Drops drawn offline tiles (after a map was added or removed). */
    public void clearOffline() {
        for (String k : memory.snapshot().keySet()) if (k.startsWith("offline_")) memory.remove(k);
        failed.clear();
        offlinePool.execute(new Runnable() {
            @Override
            public void run() {
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) if (f.getName().startsWith("offline_")) f.delete();
            }
        });
    }

    /** Returns the tile if cached in memory; otherwise schedules a load and returns null. */
    public Bitmap get(final String source, final int z, final int x, final int y, final boolean hiDpi) {
        if (offlineReady() && com.thecarlauncher.nav.OfflineMaps.get(app).covers(z, x, y)) {
            return getOffline(z, x, y, hiDpi);
        }
        if (com.thecarlauncher.util.Prefs.TILES_OFF.equals(source)) return null;
        final String key = keyOf(source, z, x, y, hiDpi);
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
                final Bitmap loaded = load(key, url(source, z, x, y, hiDpi, cartoKey()));
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
        Bitmap off = memory.get(offlineKey(z, x, y, hiDpi));
        if (off != null) return off;
        return memory.get(keyOf(source, z, x, y, hiDpi));
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
