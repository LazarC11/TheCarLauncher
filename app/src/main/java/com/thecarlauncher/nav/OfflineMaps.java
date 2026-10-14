package com.thecarlauncher.nav;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.thecarlauncher.util.Prefs;

import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline maps: whole countries / regions downloaded as OpenStreetMap vector files
 * (mapsforge format) and drawn on the unit in a factory-navigation style. Works with no
 * internet at all. Map files come from the free mapsforge download server.
 */
public final class OfflineMaps {
    public static final String SERVER = "https://download.mapsforge.org/maps/v5/";
    public static final String P_ON = "offline_maps_on";
    private static final String P_DOWNLOADS = "offline_downloads"; // "id|file;id|file"

    /** One entry of the download server's folder listing. */
    public static final class Region {
        public final String name, path, size;
        public final boolean folder;

        Region(String name, String path, String size, boolean folder) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.folder = folder;
        }
    }

    public interface Listed { void onListed(List<Region> regions, String error); }

    private static OfflineMaps instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<BoundingBox> boxes = new ArrayList<>();
    private MultiMapDataStore store;
    private DatabaseRenderer renderer;
    private RenderThemeFuture theme;
    private DisplayModel model;
    private int renderSize;
    private boolean graphicsReady;

    public static synchronized OfflineMaps get(Context c) {
        if (instance == null) instance = new OfflineMaps(c.getApplicationContext());
        return instance;
    }

    private OfflineMaps(Context c) {
        app = c;
        finishDownloads();
        reload();
        if (downloading()) watch();
    }

    public File dir() {
        File base = app.getExternalFilesDir(null);
        File d = new File(base != null ? base : app.getFilesDir(), "maps");
        d.mkdirs();
        return d;
    }

    private File downloadDir() {
        File base = app.getExternalFilesDir(null);
        File d = new File(base != null ? base : app.getFilesDir(), "maps-download");
        d.mkdirs();
        return d;
    }

    public List<File> files() {
        List<File> out = new ArrayList<>();
        File[] fs = dir().listFiles();
        if (fs != null) for (File f : fs) if (f.getName().endsWith(".map")) out.add(f);
        java.util.Collections.sort(out);
        return out;
    }

    public boolean enabled() { return Prefs.bool(app, P_ON, true); }

    public synchronized boolean available() { return store != null && !boxes.isEmpty(); }

    /** Re-opens all downloaded map files (after a download or a delete). */
    public synchronized void reload() {
        close();
        List<File> fs = files();
        if (fs.isEmpty()) return;
        MultiMapDataStore s = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);
        for (File f : fs) {
            try {
                MapFile mf = new MapFile(f);
                s.addMapDataStore(mf, false, false);
                boxes.add(mf.boundingBox());
            } catch (Throwable e) {
                // Broken or partial file: skip it.
            }
        }
        if (boxes.isEmpty()) {
            s.close();
            return;
        }
        store = s;
        tilesChanged();
    }

    private void tilesChanged() {
        main.post(new Runnable() {
            @Override
            public void run() { com.thecarlauncher.views.TileCache.get(app).clearOffline(); }
        });
    }

    private void close() {
        if (store != null) {
            try {
                store.close();
            } catch (Throwable ignored) {
            }
        }
        store = null;
        renderer = null;
        boxes.clear();
    }

    /** True when the tile lies (at least partly) inside a downloaded map. */
    public synchronized boolean covers(int z, int x, int y) {
        if (store == null) return false;
        BoundingBox b = new Tile(x, y, (byte) z, 256).getBoundingBox();
        for (BoundingBox m : boxes) if (m.intersects(b)) return true;
        return false;
    }

    /** Draws one map tile, size×size pixels. Blocking: call on a worker thread. */
    public synchronized Bitmap render(int z, int x, int y, int size, float density) {
        if (store == null) return null;
        try {
            if (!graphicsReady) {
                AndroidGraphicFactory.createInstance(app);
                graphicsReady = true;
            }
            if (renderer == null || renderSize != size) {
                DisplayModel.setDeviceScaleFactor(density);
                model = new DisplayModel();
                model.setFixedTileSize(size);
                theme = new RenderThemeFuture(AndroidGraphicFactory.INSTANCE,
                        new AssetsRenderTheme(app.getAssets(), "maptheme/", "stock_day.xml"), model);
                theme.run();
                renderer = new DatabaseRenderer(store, AndroidGraphicFactory.INSTANCE, new InMemoryTileCache(4),
                        null, true, false, null);
                renderSize = size;
            }
            Tile t = new Tile(x, y, (byte) z, size);
            RendererJob job = new RendererJob(t, store, theme, model, 1f, false, false);
            TileBitmap tb = renderer.executeJob(job);
            if (tb == null) return null;
            Bitmap src = AndroidGraphicFactory.getBitmap(tb);
            Bitmap copy = src == null ? null : src.copy(Bitmap.Config.ARGB_8888, false);
            tb.decrementRefCount();
            return copy;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Name of the street at a point, from the downloaded maps (works with no internet): the
     * nearest named road within ~40 m. A road running the way the car drives is preferred.
     * Blocking: call on a worker thread. Null when no map covers the point.
     */
    public synchronized String streetAt(double lat, double lon, float bearing, boolean hasBearing, String lang) {
        if (store == null) return null;
        try {
            final byte z = 16;
            double n = 1 << z, lr = Math.toRadians(lat);
            double tx = (lon + 180.0) / 360.0 * n;
            double ty = (1 - Math.log(Math.tan(lr) + 1 / Math.cos(lr)) / Math.PI) / 2 * n;
            int x0 = (int) Math.floor(tx), y0 = (int) Math.floor(ty);
            int x1 = tx - x0 < 0.5 ? x0 - 1 : x0 + 1, y1 = ty - y0 < 0.5 ? y0 - 1 : y0 + 1;
            String best = null;
            double bestScore = 40;
            double cosLat = Math.cos(Math.toRadians(lat));
            for (int[] t : new int[][]{{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}}) {
                org.mapsforge.map.datastore.MapReadResult r = store.readMapData(new Tile(t[0], t[1], z, 256));
                if (r == null || r.ways == null) continue;
                for (org.mapsforge.map.datastore.Way w : r.ways) {
                    String name = null;
                    boolean road = false;
                    for (org.mapsforge.core.model.Tag tag : w.tags) {
                        if ("name".equals(tag.key)) name = tag.value;
                        else if ("highway".equals(tag.key)) road = true;
                    }
                    if (!road || name == null || name.isEmpty() || w.latLongs == null) continue;
                    for (org.mapsforge.core.model.LatLong[] line : w.latLongs) {
                        for (int i = 0; i + 1 < line.length; i++) {
                            // Local metres around the car.
                            double ax = (line[i].longitude - lon) * 111320 * cosLat, ay = (line[i].latitude - lat) * 110540;
                            double bx = (line[i + 1].longitude - lon) * 111320 * cosLat, by = (line[i + 1].latitude - lat) * 110540;
                            double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
                            double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
                            double px = ax + u * dx, py = ay + u * dy;
                            double d = Math.sqrt(px * px + py * py);
                            if (hasBearing && len2 > 1) {
                                double seg = Math.toDegrees(Math.atan2(dx, dy));
                                double diff = Math.abs(((seg - bearing) % 180 + 270) % 180 - 90);
                                d += diff / 90.0 * 12; // crossing roads count as a bit further away
                            }
                            if (d < bestScore) {
                                bestScore = d;
                                best = name;
                            }
                        }
                    }
                }
            }
            return best == null ? null : localName(best, lang);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Map files keep translations as "default\rlang\bname…": pick the language when present. */
    private static String localName(String raw, String lang) {
        String[] parts = raw.split("\r");
        String def = parts[0];
        for (int i = 1; i < parts.length; i++) {
            int b = parts[i].indexOf('\b');
            if (b <= 0) continue;
            String l = parts[i].substring(0, b);
            if (lang != null && (l.equalsIgnoreCase(lang) || l.toLowerCase(Locale.ROOT).startsWith(lang + "-"))) {
                return parts[i].substring(b + 1);
            }
        }
        return def;
    }

    // ---- Download server ---------------------------------------------------------------------

    private static final Pattern ENTRY = Pattern.compile("href=\"([^\"]+)\"");
    private static final Pattern SIZE = Pattern.compile("(?<![\\w.:-])(\\d+(?:\\.\\d+)?\\s?[KMG])(?:i?B)?(?![\\w])");

    /** Lists a folder of the download server, e.g. "" or "europe/". */
    public void list(final String path, final Listed done) {
        com.thecarlauncher.mib.LoadingBanner.start("map-list",
                com.thecarlauncher.mib.LoadingBanner.t("Učitavanje liste mapa…", "Loading the list of maps…"));
        final Listed cb = new Listed() {
            @Override
            public void onListed(List<Region> regions, String error) {
                com.thecarlauncher.mib.LoadingBanner.stop("map-list");
                done.onListed(regions, error);
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Region> out = new ArrayList<>();
                String error = null;
                HttpURLConnection con = null;
                try {
                    con = (HttpURLConnection) new URL(SERVER + path).openConnection();
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(20000);
                    con.setRequestProperty("User-Agent", "TheCarLauncher (Android head unit launcher)");
                    if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) sb.append(line).append('\n');
                    in.close();
                    out = parse(path, sb.toString());
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                } finally {
                    if (con != null) con.disconnect();
                }
                final List<Region> res = out;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() { cb.onListed(res, err); }
                });
            }
        }, "offline-list").start();
    }

    static List<Region> parse(String path, String html) {
        List<Region> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        Matcher m = ENTRY.matcher(html);
        while (m.find()) {
            String href = m.group(1);
            if (href.startsWith("?") || href.startsWith("#") || href.contains("://") || href.startsWith("..")) continue;
            if (href.startsWith("/")) {
                // Absolute link: keep only children of the folder being listed.
                String base = "/maps/v5/" + path;
                if (!href.startsWith(base) || href.length() == base.length()) continue;
                href = href.substring(base.length());
            }
            boolean folder = href.endsWith("/");
            String last = folder ? href.substring(0, href.length() - 1) : href;
            if (last.isEmpty() || last.contains("/") || (!folder && !last.endsWith(".map"))) continue;
            if (!seen.add(last)) continue;
            String name = Uri.decode(folder ? last : last.substring(0, last.length() - 4)).replace('-', ' ').replace('_', ' ');
            if (name.length() > 0) name = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            String size = null;
            if (!folder) {
                // The size follows the link on the same row of the listing.
                int end = html.indexOf('\n', m.end());
                String rest = html.substring(m.end(), end < 0 ? html.length() : Math.min(end, m.end() + 300));
                rest = rest.replaceAll("<[^>]*>", " ");
                Matcher sm = SIZE.matcher(rest);
                if (sm.find()) size = sm.group(1).replace(" ", "") + "B";
            }
            out.add(new Region(name, path + last + (folder ? "/" : ""), size, folder));
        }
        return out;
    }

    // ---- Downloads ---------------------------------------------------------------------------

    /** Starts downloading a region; the unit shows progress in its notifications too. */
    public long download(Region r) {
        String file = r.path.substring(r.path.lastIndexOf('/') + 1);
        File target = new File(downloadDir(), file);
        if (target.exists()) target.delete();
        DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(SERVER + r.path))
                .setTitle("Map: " + r.name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(target))
                .addRequestHeader("User-Agent", "TheCarLauncher (Android head unit launcher)");
        long id = dm.enqueue(req);
        String list = Prefs.str(app, P_DOWNLOADS, "");
        Prefs.put(app, P_DOWNLOADS, list + id + "|" + file + ";");
        watch();
        return id;
    }

    /** Keeps the loading banner up with the download progress until all downloads finish. */
    private final Runnable watcher = new Runnable() {
        @Override
        public void run() {
            List<String> p = progress();
            if (p.isEmpty()) {
                com.thecarlauncher.mib.LoadingBanner.stop("map-download");
                return;
            }
            StringBuilder sb = new StringBuilder(com.thecarlauncher.mib.LoadingBanner.t("Preuzimanje mape: ", "Downloading map: "));
            for (int i = 0; i < p.size(); i++) sb.append(i == 0 ? "" : ", ").append(p.get(i));
            com.thecarlauncher.mib.LoadingBanner.start("map-download", sb.toString());
            main.postDelayed(this, 2000);
        }
    };

    public void watch() {
        main.removeCallbacks(watcher);
        main.post(watcher);
    }

    /** Running downloads: "name 34%" lines (and moves finished ones into place). */
    public List<String> progress() {
        finishDownloads();
        List<String> out = new ArrayList<>();
        DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        for (String e : Prefs.str(app, P_DOWNLOADS, "").split(";")) {
            if (e.isEmpty()) continue;
            String[] p = e.split("\\|");
            Cursor c = null;
            try {
                c = dm.query(new DownloadManager.Query().setFilterById(Long.parseLong(p[0])));
                if (c != null && c.moveToFirst()) {
                    long done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    String name = p[1].replace(".map", "");
                    out.add(total > 0 ? name + "  " + (done * 100 / total) + "%" : name + "  …");
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
        }
        return out;
    }

    public boolean downloading() { return !Prefs.str(app, P_DOWNLOADS, "").isEmpty(); }

    /** Moves completed downloads into the maps folder and drops failed ones. */
    public synchronized void finishDownloads() {
        String list = Prefs.str(app, P_DOWNLOADS, "");
        if (list.isEmpty()) return;
        DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        StringBuilder keep = new StringBuilder();
        boolean added = false;
        for (String e : list.split(";")) {
            if (e.isEmpty()) continue;
            String[] p = e.split("\\|");
            int status = -1;
            Cursor c = null;
            try {
                c = dm.query(new DownloadManager.Query().setFilterById(Long.parseLong(p[0])));
                if (c != null && c.moveToFirst()) status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
            File part = new File(downloadDir(), p[1]);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                File target = new File(dir(), p[1]);
                if (target.exists()) target.delete();
                if (part.renameTo(target)) added = true;
            } else if (status == DownloadManager.STATUS_FAILED || status == -1) {
                part.delete();
            } else {
                keep.append(e).append(';');
            }
        }
        Prefs.put(app, P_DOWNLOADS, keep.toString());
        if (added) reload();
    }

    public void delete(File f) {
        synchronized (this) {
            close();
            f.delete();
        }
        reload();
        tilesChanged();
    }

    public static String size(File f) {
        long b = f.length();
        if (b >= 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f GB", b / 1024f / 1024f / 1024f);
        return String.format(Locale.US, "%d MB", Math.max(1, b / 1024 / 1024));
    }
}
