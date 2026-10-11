package com.thecarlauncher.util;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.nav.LocationTracker;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Outside temperature for the status bar. The car has no temperature feed here, so it is the
 * current weather at the car's position from Open-Meteo (free, no key), refreshed every 15 minutes
 * or after driving 10 km. The last value is kept, so it shows right away after a restart.
 */
public final class Weather {
    private static final long EVERY_MS = 15 * 60_000L;
    private static final float MOVE_M = 10_000f;
    private static final String P_TEMP = "weather_temp", P_AT = "weather_at";

    private static Weather instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LauncherActivity activity;
    private long lastFetch;
    private Location lastWhere;
    private boolean busy;

    public static synchronized Weather get(LauncherActivity a) {
        if (instance == null) instance = new Weather(a.getApplicationContext());
        instance.activity = a;
        return instance;
    }

    private Weather(Context c) { app = c; }

    /** Shows the stored value, then refreshes when it is old or the car moved far. */
    public void update(LocationTracker t) {
        long at = Prefs.of(app).getLong(P_AT, 0);
        float stored = Prefs.of(app).getFloat(P_TEMP, Float.NaN);
        if (!Float.isNaN(stored) && System.currentTimeMillis() - at < 3 * 3600_000L) show(stored);
        Location here = t.location();
        double lat, lon;
        if (here != null) {
            lat = here.getLatitude();
            lon = here.getLongitude();
        } else {
            float la = Prefs.of(app).getFloat(Prefs.LAST_LAT, Float.NaN), lo = Prefs.of(app).getFloat(Prefs.LAST_LON, Float.NaN);
            if (Float.isNaN(la) || Float.isNaN(lo)) return;
            lat = la;
            lon = lo;
        }
        long now = System.currentTimeMillis();
        boolean moved = here != null && lastWhere != null && here.distanceTo(lastWhere) > MOVE_M;
        if (busy || (now - lastFetch < EVERY_MS && !moved)) return;
        busy = true;
        lastFetch = now;
        if (here != null) lastWhere = new Location(here);
        fetch(lat, lon);
    }

    private void fetch(final double lat, final double lon) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Float temp = null;
                HttpURLConnection con = null;
                try {
                    String url = String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m", lat, lon);
                    con = (HttpURLConnection) new URL(url).openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(10000);
                    con.setRequestProperty("User-Agent", "TheCarLauncher (Android head unit launcher)");
                    if (con.getResponseCode() == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) sb.append(line);
                        in.close();
                        temp = (float) new JSONObject(sb.toString()).getJSONObject("current").getDouble("temperature_2m");
                    }
                } catch (Exception ignored) {
                } finally {
                    if (con != null) con.disconnect();
                }
                final Float t = temp;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (t == null) {
                            lastFetch -= EVERY_MS - 60_000L; // try again in a minute
                            return;
                        }
                        Prefs.of(app).edit().putFloat(P_TEMP, t).putLong(P_AT, System.currentTimeMillis()).apply();
                        show(t);
                    }
                });
            }
        }, "weather").start();
    }

    private void show(float celsius) {
        if (activity == null) return;
        boolean metric = Prefs.metric(app);
        int v = Math.round(metric ? celsius : celsius * 9f / 5f + 32f);
        activity.onTemperature(v + (metric ? " °C" : " °F"));
    }
}
