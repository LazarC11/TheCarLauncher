package com.thecarlauncher.media;

import android.content.Context;
import android.media.audiofx.Equalizer;

import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Bass / mid / treble like the factory "Duboki - Srednji - Visoki" page, each -9…+9.
 * Mapped onto Android's band equalizer for the built-in player and, where the head unit
 * allows it, the global output mix (session 0) so other apps and Bluetooth audio follow too.
 */
public final class Tone {
    public static final int MIN = -9, MAX = 9;
    /** One step = 1 dB, in millibels. */
    private static final int STEP_MB = 100;
    private static final String P_BASS = "tone_bass", P_MID = "tone_mid", P_TREBLE = "tone_treble";
    private static Tone sInstance;

    private final Context app;
    private final List<Equalizer> eqs = new ArrayList<>();
    private final List<Integer> sessions = new ArrayList<>();
    private int bass, mid, treble;
    private boolean globalTried;

    public static synchronized Tone get(Context c) {
        if (sInstance == null) sInstance = new Tone(c.getApplicationContext());
        return sInstance;
    }

    private Tone(Context c) {
        app = c;
        bass = Prefs.integer(c, P_BASS, 0);
        mid = Prefs.integer(c, P_MID, 0);
        treble = Prefs.integer(c, P_TREBLE, 0);
    }

    public int bass() { return bass; }
    public int mid() { return mid; }
    public int treble() { return treble; }

    /** True once at least one equalizer accepted the settings. */
    public boolean available() {
        attachGlobal();
        return !eqs.isEmpty();
    }

    public void set(int bass, int mid, int treble) {
        this.bass = clamp(bass);
        this.mid = clamp(mid);
        this.treble = clamp(treble);
        Prefs.of(app).edit().putInt(P_BASS, this.bass).putInt(P_MID, this.mid).putInt(P_TREBLE, this.treble).apply();
        apply();
    }

    public void reset() { set(0, 0, 0); }

    private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }

    /** Hooks the tone control onto an audio session (the built-in player's). */
    public void attach(int sessionId) {
        try {
            Equalizer e = new Equalizer(0, sessionId);
            eqs.add(e);
            sessions.add(sessionId);
            apply(e);
        } catch (Throwable ignored) {
            // No equalizer effect in this audio HAL.
        }
    }

    public void detach(int sessionId) {
        for (int i = eqs.size() - 1; i >= 0; i--) {
            if (sessions.get(i) != sessionId) continue;
            try {
                eqs.get(i).release();
            } catch (Throwable ignored) {
            }
            eqs.remove(i);
            sessions.remove(i);
        }
    }

    /** The global output mix: deprecated by Android but still honoured by most head-unit ROMs. */
    @SuppressWarnings("deprecation")
    public void attachGlobal() {
        if (globalTried) return;
        globalTried = true;
        try {
            Equalizer e = new Equalizer(0, 0);
            eqs.add(e);
            sessions.add(0);
            apply(e);
        } catch (Throwable ignored) {
        }
    }

    private void apply() {
        for (int i = eqs.size() - 1; i >= 0; i--) {
            try {
                apply(eqs.get(i));
            } catch (Throwable t) {
                eqs.remove(i); // released by the system
                sessions.remove(i);
            }
        }
    }

    /**
     * Each band gets a blend of bass (anchored at 80 Hz), mid (1 kHz) and treble (10 kHz),
     * interpolated on a log-frequency scale.
     */
    private void apply(Equalizer e) {
        short[] range = e.getBandLevelRange();
        boolean flat = bass == 0 && mid == 0 && treble == 0;
        for (short b = 0; b < e.getNumberOfBands(); b++) {
            double f = Math.log10(Math.max(20, e.getCenterFreq(b) / 1000.0));
            double lb = Math.log10(80), lm = Math.log10(1000), lt = Math.log10(10000);
            double level;
            if (f <= lb) level = bass;
            else if (f <= lm) level = bass + (mid - bass) * (f - lb) / (lm - lb);
            else if (f <= lt) level = mid + (treble - mid) * (f - lm) / (lt - lm);
            else level = treble;
            int mb = (int) Math.round(level * STEP_MB);
            e.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], mb)));
        }
        e.setEnabled(!flat);
    }
}
