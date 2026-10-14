package com.thecarlauncher.nav;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.thecarlauncher.util.Prefs;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Current street name for the status bar. First from the downloaded offline maps (no internet
 * needed, checked every few seconds), then Android's Geocoder when the unit has one, otherwise
 * OpenStreetMap Nominatim (rate-limited: only after moving and at most every 10 s).
 * The last known street is kept so something sensible is always shown.
 */
public final class StreetName implements LocationTracker.Listener {
    public interface Listener { void onStreet(String street); }

    private static final String PREF = "last_street";
    private static final long MIN_INTERVAL_MS = 10000, OFFLINE_INTERVAL_MS = 2500;
    private static final float OFFLINE_MOVE_M = 8f;
    private static final float MIN_MOVE_M = 25f;
    private static StreetName sInstance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private String street;
    private Location lastQueried;
    private long lastQueryAt = -MIN_INTERVAL_MS;
    private boolean busy;

    public static synchronized StreetName get(Context c) {
        if (sInstance == null) {
            sInstance = new StreetName(c.getApplicationContext());
            LocationTracker.get(c).addListener(sInstance);
        }
        return sInstance;
    }

    private StreetName(Context c) {
        app = c;
        street = Prefs.str(c, PREF, "");
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
        l.onStreet(street);
    }

    public void removeListener(Listener l) { listeners.remove(l); }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        final Location l = t.location();
        if (l == null || busy) return;
        long now = SystemClock.elapsedRealtime();
        final boolean offline = OfflineMaps.get(app).available();
        if (now - lastQueryAt < (offline ? OFFLINE_INTERVAL_MS : MIN_INTERVAL_MS)) return;
        if (lastQueried != null && lastQueried.distanceTo(l) < (offline ? OFFLINE_MOVE_M : MIN_MOVE_M) && street.length() > 0) return;
        busy = true;
        lastQueryAt = now;
        lastQueried = l;
        final float bearing = t.bearing();
        final boolean moving = t.speedMps() > 1.5f;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String o = offline ? OfflineMaps.get(app).streetAt(l.getLatitude(), l.getLongitude(), bearing, moving,
                        "en".equals(Prefs.str(app, Prefs.LANGUAGE, "sr")) ? "en" : "sr") : null;
                // Online only now and then (rate limits), and only when the maps have no name.
                final String s = o != null ? o
                        : SystemClock.elapsedRealtime() - lastOnlineAt >= MIN_INTERVAL_MS ? online(l) : null;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (s == null || s.length() == 0) return;
                        street = s;
                        Prefs.put(app, PREF, s);
                        for (Listener x : listeners) x.onStreet(s);
                    }
                });
            }
        }, "street-lookup").start();
    }

    private volatile long lastOnlineAt = -MIN_INTERVAL_MS;

    private String online(Location l) {
        lastOnlineAt = SystemClock.elapsedRealtime();
        return lookup(l.getLatitude(), l.getLongitude());
    }

    private String lookup(double lat, double lon) {
        String s = null;
        try {
            if (Geocoder.isPresent()) {
                @SuppressWarnings("deprecation")
                List<Address> r = new Geocoder(app, locale()).getFromLocation(lat, lon, 1);
                if (r != null && !r.isEmpty()) {
                    Address a = r.get(0);
                    s = a.getThoroughfare();
                    if (s == null) s = a.getSubLocality();
                }
            }
        } catch (Exception ignored) {
        }
        if (s == null) s = nominatim(lat, lon);
        return s;
    }

    private String nominatim(double lat, double lon) {
        HttpURLConnection con = null;
        try {
            String lang = "en".equals(Prefs.str(app, Prefs.LANGUAGE, "sr")) ? "en" : "sr-Latn,sr,hr,bs,en";
            URL u = new URL(String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&addressdetails=1&lat=%.6f&lon=%.6f",
                    lat, lon));
            con = (HttpURLConnection) u.openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User-Agent", "TheCarLauncher/2 (personal Android head unit launcher)");
            con.setRequestProperty("Accept-Language", lang);
            if (con.getResponseCode() != 200) return null;
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            JSONObject addr = new JSONObject(sb.toString()).optJSONObject("address");
            if (addr == null) return null;
            for (String k : new String[]{"road", "pedestrian", "street", "residential", "footway", "path",
                    "neighbourhood", "suburb", "village", "town", "city"}) {
                String v = addr.optString(k, "");
                if (v.length() > 0) return v;
            }
        } catch (Exception ignored) {
        } finally {
            if (con != null) con.disconnect();
        }
        return null;
    }

    private Locale locale() {
        return "en".equals(Prefs.str(app, Prefs.LANGUAGE, "sr")) ? Locale.ENGLISH : new Locale("sr", "RS");
    }
}
