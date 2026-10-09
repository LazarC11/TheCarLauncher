package com.thecarlauncher.nav;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;

import com.thecarlauncher.util.Prefs;

/** "Od početka" trip data (time, distance, average speed) accumulated from GPS fixes. */
public final class TripComputer implements LocationTracker.Listener {
    private static TripComputer sInstance;

    private final Context app;
    private long startElapsed = SystemClock.elapsedRealtime();
    private long movingMs;
    private double meters;
    private Location last;
    private long lastElapsed;

    public static synchronized TripComputer get(Context c) {
        if (sInstance == null) {
            sInstance = new TripComputer(c.getApplicationContext());
            LocationTracker.get(c).addListener(sInstance);
        }
        return sInstance;
    }

    private TripComputer(Context c) {
        app = c;
    }

    public void reset() {
        startElapsed = SystemClock.elapsedRealtime();
        movingMs = 0;
        meters = 0;
        last = null;
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        Location l = t.location();
        long now = SystemClock.elapsedRealtime();
        if (l == null || !t.hasFix()) {
            last = null;
            return;
        }
        if (last != null && l != last) {
            float d = last.distanceTo(l);
            long dt = now - lastElapsed;
            // Ignore GPS jitter while parked and implausible jumps.
            if (d > 3 && dt > 0 && d / (dt / 1000f) < 70) {
                meters += d;
                movingMs += dt;
            }
        }
        last = l;
        lastElapsed = now;
    }

    public long elapsedMs() { return SystemClock.elapsedRealtime() - startElapsed; }

    /** Distance in the user's unit (km or mi). */
    public double distance() {
        double km = meters / 1000.0;
        return Prefs.metric(app) ? km : km * 0.621371;
    }

    /** Average speed over the whole trip time (like the factory "Od početka" page). */
    public double averageSpeed() {
        double hours = elapsedMs() / 3600000.0;
        return hours < 0.005 ? 0 : distance() / hours;
    }
}
