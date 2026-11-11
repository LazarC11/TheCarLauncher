package com.thecarlauncher.radio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.thecarlauncher.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Online radio: internet stations from the free Radio Browser directory (radio-browser.info),
 * played by the launcher. Used by {@link RadioController} when Setup → Radio → Radio source is
 * "Online"; it has its own 18 presets, so the FM ones stay as they are.
 */
public final class OnlineRadio {
    public static final String P_MODE = "radio_mode", MODE_FM = "fm", MODE_ONLINE = "online";
    public static final String P_COUNTRY = "online_country";
    private static final String P_PRESETS = "online_presets", P_LAST = "online_last";
    private static final String[] SERVERS = {"https://de1.api.radio-browser.info", "https://de2.api.radio-browser.info",
            "https://fi1.api.radio-browser.info", "https://all.api.radio-browser.info"};

    /** An internet station. */
    public static final class Station {
        public final String id, name, url, logo, country, info;

        Station(String id, String name, String url, String logo, String country, String info) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name.trim();
            this.url = url == null ? "" : url;
            this.logo = logo == null ? "" : logo;
            this.country = country == null ? "" : country;
            this.info = info == null ? "" : info;
        }

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("url", url).put("logo", logo)
                    .put("country", country).put("info", info);
        }

        static Station of(JSONObject o) {
            return o == null ? null : new Station(o.optString("id"), o.optString("name"), o.optString("url"),
                    o.optString("logo"), o.optString("country"), o.optString("info"));
        }
    }

    public interface Listener { void onOnlineChanged(); }

    private static OnlineRadio sInstance;
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final Station[] presets = new Station[RadioController.PRESETS];
    private final List<Station> results = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private MediaPlayer player;
    private Station current;
    private boolean active, buffering, searching;
    private String status = "", query = "";
    private AudioFocusRequest focusRequest;
    private int searchGen;

    public static synchronized OnlineRadio get(Context c) {
        if (sInstance == null) sInstance = new OnlineRadio(c.getApplicationContext());
        return sInstance;
    }

    private OnlineRadio(Context c) {
        app = c;
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            JSONArray a = new JSONArray(Prefs.str(c, P_PRESETS, "[]"));
            for (int i = 0; i < presets.length && i < a.length(); i++) presets[i] = Station.of(a.optJSONObject(i));
        } catch (Exception ignored) {
        }
        try {
            current = Station.of(new JSONObject(Prefs.str(c, P_LAST, "{}")));
            if (current != null && current.url.length() == 0) current = null;
        } catch (Exception ignored) {
        }
    }

    /** True when Setup → Radio → Radio source is "Online". */
    public static boolean on(Context c) { return MODE_ONLINE.equals(Prefs.str(c, P_MODE, MODE_FM)); }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<>(listeners)) l.onOnlineChanged();
            }
        });
    }

    // ---- State ---------------------------------------------------------------------------------

    public boolean isActive() { return active; }

    public Station current() { return current; }

    /** "Buffering…", "No connection", country / format… */
    public String status() { return status; }

    public Station preset(int i) { return presets[i]; }

    public int presetIndexOfCurrent() {
        if (current == null) return -1;
        for (int i = 0; i < presets.length; i++) if (presets[i] != null && presets[i].id.equals(current.id)) return i;
        return -1;
    }

    public List<Station> results() { return results; }

    public boolean searching() { return searching; }

    public String query() { return query; }

    /** Country code of the station list: chosen in the list, else the unit's region. */
    public String country() {
        String c = Prefs.str(app, P_COUNTRY, null);
        if (c != null) return c;
        String lang = com.thecarlauncher.util.Lang.current(app);
        if ("sr".equals(lang)) return "RS";
        if ("pl".equals(lang)) return "PL";
        if ("tr".equals(lang)) return "TR";
        String l = Locale.getDefault().getCountry();
        return l.length() == 2 ? l : "";
    }

    // ---- Playback ------------------------------------------------------------------------------

    /** Plays the last station (or the first preset). False when there is nothing to play yet. */
    public boolean start() {
        if (active) return true;
        Station s = current;
        if (s == null) for (Station p : presets) if (p != null) { s = p; break; }
        if (s == null) {
            active = true;
            status = com.thecarlauncher.mib.Mib.t(app, "Izaberite stanicu u listi stanica", "Choose a station in the station list");
            changed();
            return true;
        }
        play(s);
        return true;
    }

    public void play(final Station s) {
        if (s == null || s.url.length() == 0) return;
        current = s;
        try {
            Prefs.put(app, P_LAST, s.json().toString());
        } catch (Exception ignored) {
        }
        active = true;
        buffering = true;
        status = com.thecarlauncher.mib.Mib.t(app, "Učitavanje…", "Buffering…");
        changed();
        requestFocus();
        claimUnitAudio();
        release();
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(s.url);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer p) {
                    if (p != player) return;
                    buffering = false;
                    status = s.info;
                    p.start();
                    changed();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer p, int what, int extra) {
                    if (p != player) return true;
                    buffering = false;
                    status = com.thecarlauncher.mib.Mib.t(app, "Stanica se ne može pustiti (veza?)",
                            "The station can't be played (connection?)");
                    release();
                    changed();
                    return true;
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer p) {
                    // A live stream ended (connection lost): try again in a moment.
                    if (p == player && active) {
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (active && current == s) play(s);
                            }
                        }, 3000);
                    }
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            buffering = false;
            status = com.thecarlauncher.mib.Mib.t(app, "Stanica se ne može pustiti", "The station can't be played");
            release();
            changed();
        }
        clickCount(s);
    }

    public void stop() {
        if (!active) return;
        active = false;
        buffering = false;
        release();
        abandonFocus();
        changed();
    }

    /** Next / previous preset (wraps around), else next / previous search result. */
    public void step(boolean up) {
        List<Station> ring = new ArrayList<>();
        for (Station p : presets) if (p != null) ring.add(p);
        if (ring.isEmpty()) ring.addAll(results);
        if (ring.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < ring.size(); i++) if (current != null && ring.get(i).id.equals(current.id)) at = i;
        int n = up ? at + 1 : at - 1;
        if (n < 0) n = ring.size() - 1;
        if (n >= ring.size()) n = 0;
        play(ring.get(n));
    }

    private void release() {
        MediaPlayer p = player;
        player = null;
        if (p != null) {
            try {
                p.reset();
                p.release();
            } catch (Exception ignored) {
            }
        }
    }

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int change) {
            MediaPlayer p = player;
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                stop();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (p != null && p.isPlaying()) p.setVolume(0f, 0f);
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (p != null) p.setVolume(0.25f, 0.25f);
            } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                if (p != null) p.setVolume(1f, 1f);
            }
        }
    };

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(focus, main).build();
            audio.requestAudioFocus(focusRequest);
        } else {
            audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        else audio.abandonAudioFocus(focus);
    }

    /** Units whose own service owns the speakers (SYU, android.car): Android media gets them. */
    private void claimUnitAudio() {
        final com.thecarlauncher.media.UnitBackend unit = com.thecarlauncher.media.UnitBackend.Get.of(app);
        if (unit == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                unit.claimForMedia();
            }
        }, "online-claim").start();
    }

    // ---- Presets -------------------------------------------------------------------------------

    public void savePreset(int i, Station s) {
        if (s == null) return;
        presets[i] = s;
        savePresets();
        changed();
    }

    /** Stores a station on the first free preset; returns its index or -1 when all are taken. */
    public int addToFirstFree(Station s) {
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] != null && presets[i].id.equals(s.id)) return i;
        }
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == null) {
                savePreset(i, s);
                return i;
            }
        }
        return -1;
    }

    public void clearPreset(int i) {
        presets[i] = null;
        savePresets();
        changed();
    }

    private void savePresets() {
        JSONArray a = new JSONArray();
        try {
            for (Station p : presets) a.put(p == null ? JSONObject.NULL : p.json());
        } catch (Exception ignored) {
        }
        Prefs.put(app, P_PRESETS, a.toString());
    }

    // ---- Directory -----------------------------------------------------------------------------

    /** Stations of the chosen country (most listened first), optionally filtered by name. */
    public void search(final String text) {
        query = text == null ? "" : text.trim();
        final String cc = country();
        final int gen = ++searchGen;
        searching = true;
        changed();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Station> found = new ArrayList<>();
                String err = null;
                try {
                    StringBuilder q = new StringBuilder("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=150");
                    if (cc.length() > 0) q.append("&countrycode=").append(cc);
                    if (query.length() > 0) q.append("&name=").append(URLEncoder.encode(query, "UTF-8"));
                    JSONArray a = new JSONArray(get(q.toString()));
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        if (o == null) continue;
                        String url = o.optString("url_resolved");
                        if (url.length() == 0) url = o.optString("url");
                        if (url.length() == 0 || o.optString("name").trim().length() == 0) continue;
                        String codec = o.optString("codec");
                        int br = o.optInt("bitrate");
                        String info = o.optString("countrycode") + (codec.length() > 0 ? " · " + codec : "")
                                + (br > 0 ? " " + br + " kbit/s" : "");
                        found.add(new Station(o.optString("stationuuid"), o.optString("name"), url, o.optString("favicon"),
                                o.optString("countrycode"), info));
                    }
                } catch (Exception e) {
                    err = e.getClass().getSimpleName();
                }
                final String fe = err;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != searchGen) return;
                        searching = false;
                        results.clear();
                        results.addAll(found);
                        if (fe != null && found.isEmpty()) {
                            status = com.thecarlauncher.mib.Mib.t(app, "Lista nije dostupna (internet?)",
                                    "List not available (internet?)");
                        }
                        for (Listener l : new ArrayList<>(listeners)) l.onOnlineChanged();
                    }
                });
            }
        }, "online-search").start();
    }

    /** {code, name, count} of countries with stations, most stations first (blocking). */
    public static List<String[]> countries() throws Exception {
        List<String[]> out = new ArrayList<>();
        JSONArray a = new JSONArray(get("/json/countries?order=stationcount&reverse=true&hidebroken=true"));
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String code = o.optString("iso_3166_1");
            if (code.length() != 2) continue;
            out.add(new String[]{code, o.optString("name"), String.valueOf(o.optInt("stationcount"))});
        }
        return out;
    }

    public void setCountry(String code) {
        Prefs.put(app, P_COUNTRY, code);
        search(query);
    }

    /** Radio Browser asks clients to count plays (helps its "most listened" order). */
    private void clickCount(final Station s) {
        if (s.id.length() == 0) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    get("/json/url/" + URLEncoder.encode(s.id, "UTF-8"));
                } catch (Exception ignored) {
                }
            }
        }, "online-click").start();
    }

    private static String get(String path) throws Exception {
        Exception last = null;
        for (String server : SERVERS) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(server + path).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(12000);
                con.setRequestProperty("User-Agent", "TheCarLauncher/1.0");
                if (con.getResponseCode() >= 400) throw new java.io.IOException("HTTP " + con.getResponseCode());
                InputStream in = con.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                return bo.toString("UTF-8");
            } catch (Exception e) {
                last = e;
            } finally {
                if (con != null) con.disconnect();
            }
        }
        throw last != null ? last : new java.io.IOException("no server");
    }
}
