package com.thecarlauncher.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Album covers looked up online by artist + title, for songs that arrive without one
 * (the unit's Bluetooth module only sends text). Deezer search first, then iTunes; results
 * are kept in memory and on disk so a song is looked up only once.
 */
public final class CoverArt {
    public interface Listener { void onCover(); }

    private static final int MAX_MEM = 12;
    private static final long RETRY_MS = 10 * 60_000L;

    private static CoverArt instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> mem = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > MAX_MEM; }
    };
    private final Map<String, Long> missed = new LinkedHashMap<>();
    private final Set<String> loading = new HashSet<>();
    private Listener listener;

    public static synchronized CoverArt get(Context c) {
        if (instance == null) instance = new CoverArt(c.getApplicationContext());
        return instance;
    }

    private CoverArt(Context c) { app = c; }

    public void setListener(Listener l) { listener = l; }

    /** The cover if known; otherwise starts a lookup and returns null (listener fires later). */
    public synchronized Bitmap find(String artist, String title) {
        if (title == null || title.trim().isEmpty()) return null;
        final String key = key(artist, title);
        Bitmap b = mem.get(key);
        if (b != null) return b;
        Long miss = missed.get(key);
        if (miss != null && System.currentTimeMillis() - miss < RETRY_MS) return null;
        if (loading.contains(key)) return null;
        loading.add(key);
        final String a = artist, t = title;
        new Thread(new Runnable() {
            @Override
            public void run() { load(key, a, t); }
        }, "cover-art").start();
        return null;
    }

    private void load(String key, String artist, String title) {
        Bitmap b = null;
        File f = file(key);
        try {
            if (f.exists()) b = BitmapFactory.decodeFile(f.getPath());
            if (b == null) {
                String url = lookup(artist, title);
                if (url != null) {
                    byte[] data = download(url);
                    b = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (b != null) {
                        f.getParentFile().mkdirs();
                        FileOutputStream os = new FileOutputStream(f);
                        os.write(data);
                        os.close();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        final Bitmap res = b;
        synchronized (this) {
            loading.remove(key);
            if (res != null) mem.put(key, res);
            else missed.put(key, System.currentTimeMillis());
        }
        if (res != null) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) listener.onCover();
                }
            });
        }
    }

    /** Cover image URL: artist + title first, then the title alone (artist is often a channel name). */
    private static String lookup(String artist, String title) {
        String clean = title.replaceAll("(?i)\\(.*?(official|video|audio|lyrics|spot).*?\\)|\\[.*?\\]", " ").trim();
        String[] queries = artist != null && !artist.trim().isEmpty()
                ? new String[]{artist.trim() + " " + clean, clean} : new String[]{clean};
        for (String q : queries) {
            String u = deezer(q);
            if (u == null) u = itunes(q);
            if (u != null) return u;
        }
        return null;
    }

    private static String deezer(String q) {
        try {
            JSONArray d = new JSONObject(text("https://api.deezer.com/search?limit=1&q=" + enc(q))).optJSONArray("data");
            if (d == null || d.length() == 0) return null;
            JSONObject album = d.getJSONObject(0).optJSONObject("album");
            String u = album == null ? null : album.optString("cover_xl", null);
            return u == null || u.isEmpty() ? null : u;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String itunes(String q) {
        try {
            JSONArray r = new JSONObject(text("https://itunes.apple.com/search?entity=song&limit=1&term=" + enc(q))).optJSONArray("results");
            if (r == null || r.length() == 0) return null;
            String u = r.getJSONObject(0).optString("artworkUrl100", null);
            return u == null || u.isEmpty() ? null : u.replace("100x100bb", "600x600bb");
        } catch (Throwable e) {
            return null;
        }
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String text(String url) throws Exception { return new String(download(url), "UTF-8"); }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(6000);
            con.setReadTimeout(10000);
            con.setRequestProperty("User-Agent", "TheCarLauncher");
            if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } finally {
            con.disconnect();
        }
    }

    private static String key(String artist, String title) {
        return ((artist == null ? "" : artist.trim()) + "|" + title.trim()).toLowerCase(java.util.Locale.ROOT);
    }

    private File file(String key) {
        return new File(new File(app.getCacheDir(), "covers"), Integer.toHexString(key.hashCode()) + ".jpg");
    }
}
