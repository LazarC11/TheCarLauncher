package com.thecarlauncher.nav;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.thecarlauncher.util.Prefs;

import java.util.concurrent.CopyOnWriteArrayList;

/** Single GPS subscription shared by the status bar, dashboard and navigation screen. */
public final class LocationTracker implements LocationListener {
    public interface Listener { void onLocationUpdate(LocationTracker t); }

    private static final long FIX_TIMEOUT_MS = 6000;
    private static final String[] HEADINGS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static LocationTracker sInstance;

    private final Context app;
    private final LocationManager lm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private Object gnssCallback;
    private boolean running;

    private Location last;
    private long lastGpsElapsed;
    private boolean fix;
    private float speedMps, bearing;
    private int satsUsed, satsVisible;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            boolean stale = SystemClock.elapsedRealtime() - lastGpsElapsed > FIX_TIMEOUT_MS;
            if (fix && stale) {
                fix = false;
                speedMps = 0;
                notifyListeners();
            }
            main.postDelayed(this, 2000);
        }
    };

    public static synchronized LocationTracker get(Context c) {
        if (sInstance == null) sInstance = new LocationTracker(c.getApplicationContext());
        return sInstance;
    }

    private LocationTracker(Context c) {
        app = c;
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        float lat = Prefs.of(c).getFloat(Prefs.LAST_LAT, Float.NaN);
        float lon = Prefs.of(c).getFloat(Prefs.LAST_LON, Float.NaN);
        if (!Float.isNaN(lat)) {
            last = new Location("saved");
            last.setLatitude(lat);
            last.setLongitude(lon);
        }
    }

    public boolean hasPermission() {
        return app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
        l.onLocationUpdate(this);
    }

    public void removeListener(Listener l) { listeners.remove(l); }

    @SuppressWarnings("MissingPermission")
    public void start() {
        if (running || lm == null || !hasPermission()) return;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER}) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (last == null || "saved".equals(last.getProvider()) || l.getTime() > last.getTime())) {
                    last = l;
                }
            }
            if (lm.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
            }
            if (lm.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, this, Looper.getMainLooper());
            }
            if (Build.VERSION.SDK_INT >= 24) registerGnss();
            running = true;
            main.postDelayed(watchdog, 2000);
            notifyListeners();
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    @SuppressWarnings("MissingPermission")
    private void registerGnss() {
        GnssStatus.Callback cb = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                int used = 0;
                for (int i = 0; i < status.getSatelliteCount(); i++) if (status.usedInFix(i)) used++;
                satsUsed = used;
                satsVisible = status.getSatelliteCount();
            }
        };
        try {
            lm.registerGnssStatusCallback(cb, main);
            gnssCallback = cb;
        } catch (SecurityException ignored) {
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        main.removeCallbacks(watchdog);
        try {
            lm.removeUpdates(this);
            if (Build.VERSION.SDK_INT >= 24 && gnssCallback != null) {
                lm.unregisterGnssStatusCallback((GnssStatus.Callback) gnssCallback);
            }
        } catch (Exception ignored) {
        }
        if (last != null) {
            Prefs.of(app).edit().putFloat(Prefs.LAST_LAT, (float) last.getLatitude())
                    .putFloat(Prefs.LAST_LON, (float) last.getLongitude()).apply();
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        long now = SystemClock.elapsedRealtime();
        if (!gps && now - lastGpsElapsed < FIX_TIMEOUT_MS) return; // GPS is better, ignore network
        Location prev = last;
        if (gps) {
            lastGpsElapsed = now;
            fix = true;
        }
        if (l.hasSpeed()) {
            speedMps = l.getSpeed();
        } else if (prev != null && gps && l.getTime() > prev.getTime()) {
            speedMps = prev.distanceTo(l) / ((l.getTime() - prev.getTime()) / 1000f);
        } else if (!gps) {
            speedMps = 0;
        }
        if (speedMps > 1.5f) {
            if (l.hasBearing()) bearing = l.getBearing();
            else if (prev != null && prev.distanceTo(l) > 8) bearing = prev.bearingTo(l);
        }
        last = l;
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener l : listeners) l.onLocationUpdate(this);
    }

    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}

    public Location location() { return last; }
    public boolean hasFix() { return fix; }
    public float bearing() { return (bearing + 360f) % 360f; }

    public float speedMps() { return speedMps; }
    public int satsUsed() { return satsUsed; }
    public int satsVisible() { return satsVisible; }

    /** Speed in the user's chosen unit. */
    public float speed(Context c) {
        float kmh = speedMps * 3.6f;
        if (kmh < 2f) kmh = 0f; // GPS jitter while parked
        return Prefs.metric(c) ? kmh : kmh * 0.621371f;
    }

    public static String unit(Context c) {
        return Prefs.metric(c) ? "km/h" : "mph";
    }

    public static String heading(float deg) {
        return HEADINGS[(int) Math.floor(((deg + 22.5f) % 360f) / 45f)];
    }
}
