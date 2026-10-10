package com.thecarlauncher.nav;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;

import com.thecarlauncher.util.Prefs;

/**
 * Trip data accumulated from GPS fixes, like the factory MIB driving-data pages:
 * "Od starta" (this drive), "Dugoročno" (long term, until reset) and the lifetime total that
 * feeds the odometer and the since-refuel figures.
 */
public final class TripComputer implements LocationTracker.Listener {
    private static TripComputer sInstance;

    private final Context app;
    private long startElapsed = SystemClock.elapsedRealtime();
    private long movingMs;
    private double meters;
    private Location last;
    private long lastElapsed;
    // Persisted totals (metres / moving milliseconds).
    private double lifeMeters, longMeters;
    private long longMs, lastSaved, lastMoveWall;
    private static final String P_LIFE = "trip_life_m", P_LONG = "trip_long_m", P_LONG_MS = "trip_long_ms",
            P_LAST_MOVE = "trip_last_move";
    /** A new drive starts after the car has been parked this long. */
    private static final long NEW_DRIVE_MS = 2 * 3600 * 1000L;

    public static synchronized TripComputer get(Context c) {
        if (sInstance == null) {
            sInstance = new TripComputer(c.getApplicationContext());
            LocationTracker.get(c).addListener(sInstance);
        }
        return sInstance;
    }

    private TripComputer(Context c) {
        app = c;
        lifeMeters = Double.longBitsToDouble(Prefs.of(c).getLong(P_LIFE, 0));
        longMeters = Double.longBitsToDouble(Prefs.of(c).getLong(P_LONG, 0));
        longMs = Prefs.of(c).getLong(P_LONG_MS, 0);
        lastMoveWall = Prefs.of(c).getLong(P_LAST_MOVE, 0);
    }

    private void save() {
        lastSaved = SystemClock.elapsedRealtime();
        Prefs.of(app).edit().putLong(P_LIFE, Double.doubleToLongBits(lifeMeters))
                .putLong(P_LONG, Double.doubleToLongBits(longMeters))
                .putLong(P_LONG_MS, longMs).putLong(P_LAST_MOVE, lastMoveWall).apply();
    }

    public void resetLongTerm() {
        longMeters = 0;
        longMs = 0;
        save();
    }

    /** Total GPS distance ever driven with the launcher running, in kilometres. */
    public double lifetimeKm() { return lifeMeters / 1000.0; }

    public double longTermKm() { return longMeters / 1000.0; }

    public long longTermMs() { return longMs; }

    public double movingKm() { return meters / 1000.0; }

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
                long wall = System.currentTimeMillis();
                if (lastMoveWall > 0 && wall - lastMoveWall > NEW_DRIVE_MS) reset();
                lastMoveWall = wall;
                meters += d;
                movingMs += dt;
                lifeMeters += d;
                longMeters += d;
                longMs += dt;
                if (now - lastSaved > 20000) save();
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
