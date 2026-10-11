package com.thecarlauncher.radio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.thecarlauncher.mib.LoadingBanner;
import com.thecarlauncher.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Station logos by RDS name, from the free Radio Browser community database
 * (radio-browser.info). Logos are downloaded once and kept on the unit; nothing is bundled.
 */
public final class RadioLogos {
    public static final String P_ON = "radio_logos_on";
    private static final String[] SERVERS = {"https://de1.api.radio-browser.info", "https://de2.api.radio-browser.info",
            "https://fi1.api.radio-browser.info", "https://all.api.radio-browser.info"};
    /** Countries tried first when several stations share a name (the region the unit is used in). */
    private static final Set<String> NEAR = new HashSet<>(java.util.Arrays.asList(
            "RS", "ME", "BA", "HR", "MK", "SI", "XK", "AL", "BG", "RO", "HU", "AT", "DE", "IT", "GR"));

    public interface Listener { void onLogo(); }

    private static RadioLogos instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory = new LruCache<>(40);
    private final Set<String> loading = new HashSet<>();
    private final Set<String> missed = new HashSet<>();
    private final List<Listener> listeners = new ArrayList<>();

    public static synchronized RadioLogos get(Context c) {
        if (instance == null) instance = new RadioLogos(c.getApplicationContext());
        return instance;
    }

    private RadioLogos(Context c) { app = c; }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private File dir() {
        File d = new File(app.getFilesDir(), "radiologos");
        d.mkdirs();
        return d;
    }

    static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public int count() {
        File[] f = dir().listFiles();
        return f == null ? 0 : f.length;
    }

    /** The logo if it is on the unit; otherwise starts a lookup (when allowed) and returns null. */
    public Bitmap logo(String name) {
        final String k = key(name);
        if (k.length() < 2) return null;
        Bitmap b = memory.get(k);
        if (b != null) return b;
        File f = new File(dir(), k + ".png");
        if (f.exists()) {
            b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) {
                memory.put(k, b);
                return b;
            }
        }
        if (Prefs.bool(app, P_ON, true) && !missed.contains(k) && loading.add(k)) {
            final String n = name.trim();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final boolean ok = fetch(n);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            loading.remove(k);
                            if (!ok) missed.add(k);
                            else for (Listener l : new ArrayList<>(listeners)) l.onLogo();
                        }
                    });
                }
            }, "radio-logo").start();
        }
        return null;
    }

    /** Downloads logos for every named station found by the scan and every preset. */
    public void downloadAll(RadioController radio, final Runnable done) {
        final Set<String> names = new LinkedHashSet<>();
        for (RadioController.Station s : radio.stations()) if (s.name.trim().length() > 1) names.add(s.name.trim());
        for (int i = 0; i < RadioController.PRESETS; i++) {
            RadioController.Station s = radio.preset(i);
            if (s != null && s.name.trim().length() > 1) names.add(s.name.trim());
        }
        if (names.isEmpty()) {
            LoadingBanner.flash("radio-logos", LoadingBanner.t("Nema stanica sa imenom — prvo ažuriraj listu stanica",
                    "No named stations — update the station list first"), 2500);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int i = 0, got = 0;
                for (String n : names) {
                    i++;
                    LoadingBanner.start("radio-logos", LoadingBanner.t("Preuzimanje logotipa stanica… ", "Downloading station logos… ")
                            + i + "/" + names.size());
                    if (new File(dir(), key(n) + ".png").exists() || fetch(n)) got++;
                }
                final int ok = got;
                LoadingBanner.stop("radio-logos");
                LoadingBanner.flash("radio-logos-done", LoadingBanner.t("Logotipi: ", "Logos: ") + ok + "/" + names.size(), 2500);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        missed.clear();
                        for (Listener l : new ArrayList<>(listeners)) l.onLogo();
                        if (done != null) done.run();
                    }
                });
            }
        }, "radio-logos").start();
    }

    /** Looks the station up by name and stores its logo; true when one was saved. */
    private boolean fetch(String name) {
        String k = key(name);
        for (String server : SERVERS) {
            try {
                String json = text(server + "/json/stations/byname/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                        + "?limit=25&hidebroken=true&order=votes&reverse=true");
                JSONArray arr = new JSONArray(json);
                String best = null, any = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String fav = o.optString("favicon", "");
                    if (fav.isEmpty() || !key(o.optString("name")).contains(k)) continue;
                    if (any == null) any = fav;
                    if (best == null && NEAR.contains(o.optString("countrycode", ""))) best = fav;
                }
                String url = best != null ? best : any;
                if (url == null) return false;
                byte[] data = bytes(url);
                Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (b == null) return false;
                FileOutputStream fo = new FileOutputStream(new File(dir(), k + ".png"));
                b.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                memory.put(k, b);
                return true;
            } catch (Exception e) {
                // next server
            }
        }
        return false;
    }

    private static String text(String url) throws Exception { return new String(bytes(url), "UTF-8"); }

    private static byte[] bytes(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(7000);
            con.setReadTimeout(10000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "TheCarLauncher/3 (Android head unit launcher)");
            if (con.getResponseCode() >= 400) throw new java.io.IOException("HTTP " + con.getResponseCode());
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 2_000_000) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } finally {
            con.disconnect();
        }
    }
}
