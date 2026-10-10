package com.thecarlauncher.media;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads local music (internal storage, SD card, USB) from MediaStore. */
public final class MusicLibrary {
    public static final String CTX_ALL = "all";

    /** An album, artist or folder grouping in the browser. */
    public static final class Group {
        public final String key, title;
        public String subtitle;
        public long artAlbumId;
        public int count;

        Group(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }

    private static List<Track> sCache;

    private MusicLibrary() {}

    public static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    public static synchronized void invalidate() {
        sCache = null;
    }

    /** All tracks sorted by title. Blocking: call off the main thread. */
    public static synchronized List<Track> all(Context c) {
        if (sCache != null) return sCache;
        List<Track> out = new ArrayList<>();
        if (!hasPermission(c)) return out;
        String[] proj = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA
        };
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                    MediaStore.Audio.Media.IS_MUSIC + "!=0", null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    String data = cur.getString(7);
                    String folder = "";
                    if (data != null) {
                        int slash = data.lastIndexOf('/');
                        folder = slash > 0 ? data.substring(0, slash) : data;
                    }
                    out.add(new Track(cur.getLong(0), clean(cur.getString(1), "Unknown title"),
                            clean(cur.getString(2), "Unknown artist"), clean(cur.getString(3), "Unknown album"),
                            cur.getLong(4), cur.getLong(5), cur.getLong(6), folder));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        final Collator col = Collator.getInstance();
        Collections.sort(out, new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return col.compare(a.title, b.title);
            }
        });
        sCache = out;
        return out;
    }

    private static String clean(String s, String def) {
        if (s == null || s.length() == 0 || "<unknown>".equals(s)) return def;
        return s;
    }

    public static List<Group> albums(Context c) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (Track t : all(c)) {
            String k = "album:" + t.albumId;
            Group g = map.get(k);
            if (g == null) {
                g = new Group(k, t.album);
                g.subtitle = t.artist;
                g.artAlbumId = t.albumId;
                map.put(k, g);
            }
            g.count++;
        }
        return sorted(map);
    }

    public static List<Group> artists(Context c) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (Track t : all(c)) {
            String k = "artist:" + t.artistId;
            Group g = map.get(k);
            if (g == null) {
                g = new Group(k, t.artist);
                g.artAlbumId = t.albumId;
                map.put(k, g);
            }
            g.count++;
        }
        List<Group> out = sorted(map);
        for (Group g : out) g.subtitle = g.count + (g.count == 1 ? " song" : " songs");
        return out;
    }

    public static List<Group> folders(Context c) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (Track t : all(c)) {
            String k = "folder:" + t.folder;
            Group g = map.get(k);
            if (g == null) {
                int slash = t.folder.lastIndexOf('/');
                g = new Group(k, slash >= 0 ? t.folder.substring(slash + 1) : t.folder);
                g.subtitle = t.folder;
                g.artAlbumId = t.albumId;
                map.put(k, g);
            }
            g.count++;
        }
        return sorted(map);
    }

    private static List<Group> sorted(Map<String, Group> map) {
        List<Group> out = new ArrayList<>(map.values());
        final Collator col = Collator.getInstance();
        Collections.sort(out, new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                return col.compare(a.title, b.title);
            }
        });
        return out;
    }

    /** Resolves a queue context ("all", "album:12", "artist:4", "folder:/x") to tracks. */
    public static List<Track> resolve(Context c, String ctx) {
        List<Track> all = all(c);
        if (ctx == null || CTX_ALL.equals(ctx)) return all;
        List<Track> out = new ArrayList<>();
        for (Track t : all) {
            if (ctx.equals("album:" + t.albumId) || ctx.equals("artist:" + t.artistId)
                    || ctx.equals("folder:" + t.folder)) {
                out.add(t);
            }
        }
        if (ctx.startsWith("album:")) {
            // Album order is better than alphabetical for an album.
            Collections.sort(out, new Comparator<Track>() {
                @Override
                public int compare(Track a, Track b) {
                    return Long.compare(a.id, b.id);
                }
            });
        }
        return out;
    }

    /** Loads cover art for an album, or null. Blocking. */
    public static Bitmap loadArt(Context c, long albumId, long trackId, int size) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                Uri u = trackId > 0
                        ? ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
                        : ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId);
                return c.getContentResolver().loadThumbnail(u, new Size(size, size), null);
            }
            Uri art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(art);
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / (sample * 2) >= size) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = c.getContentResolver().openInputStream(art);
            if (in == null) return null;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
