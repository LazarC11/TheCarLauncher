package com.thecarlauncher.radio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FM radio inside the launcher: tuner state, presets, station list and audio routing.
 * Drives the K706/QuickFish tuner through {@link K706Tuner}.
 */
public final class RadioController implements K706Tuner.Listener {
    public interface Listener { void onRadioChanged(); }

    /** A stored or discovered station: FM frequency in MCU units (9220 = 92.2 MHz) plus RDS name. */
    public static final class Station {
        public final int freq;
        public final String name;

        Station(int freq, String name) {
            this.freq = freq;
            this.name = name == null ? "" : name;
        }
    }

    public static final int FM_MIN = 8750, FM_MAX = 10800, PRESETS = 18;
    private static final String P_FREQ = "radio_freq", P_PRESETS = "radio_presets", P_STATIONS = "radio_stations";
    private static RadioController sInstance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final K706Tuner tuner;
    private final AudioManager audio;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Station[] presets = new Station[PRESETS];
    private final List<Station> stations = new ArrayList<>();

    private int freq;
    private String ps = "", rt = "";
    private boolean stereo, seeking, active, focusPaused;
    private boolean scanning;
    private int scanLast, scanSteps;
    private long scanSettleAt;
    private AudioFocusRequest focusRequest;

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int change) {
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                // Another source took over (USB player, Bluetooth, Spotify…): hand audio back.
                stop();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // Navigation prompt or call: mute the tuner and come back afterwards.
                if (active) {
                    focusPaused = true;
                    tuner.mute(true);
                }
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && focusPaused) {
                focusPaused = false;
                if (tuner.channel() != K706Tuner.CHANNEL_FM) tuner.route(K706Tuner.CHANNEL_FM);
                tuner.mute(false);
            }
        }
    };

    public static synchronized RadioController get(Context c) {
        if (sInstance == null) sInstance = new RadioController(c.getApplicationContext());
        return sInstance;
    }

    private RadioController(Context c) {
        app = c;
        tuner = new K706Tuner(c);
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        freq = Prefs.integer(c, P_FREQ, FM_MIN);
        loadList(Prefs.str(c, P_PRESETS, ""), null);
        loadList(Prefs.str(c, P_STATIONS, ""), stations);
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
        l.onRadioChanged();
    }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        for (Listener l : listeners) l.onRadioChanged();
        MediaHub.get(app).radioChanged();
    }

    // ---- Availability / audio ----------------------------------------------------------------

    /** True when this head unit's FM tuner can be driven from the launcher. */
    public boolean available() {
        if (!Prefs.bool(app, Prefs.RADIO_INTERNAL, true)) return false;
        return tuner.connect(this);
    }

    public String error() { return tuner.error(); }

    // ---- MCU audio switch (shared with Bluetooth music) ----------------------------------------

    /** Current MCU audio channel (2 = FM, 4 = Android media, others = e.g. Bluetooth), -1 unknown. */
    public int mcuChannel() {
        return tuner.connect(this) ? tuner.channel() : -1;
    }

    /** Switches the MCU audio channel (used to put the Bluetooth module on the speakers). */
    public boolean routeChannel(int ch) {
        if (!tuner.connect(this)) return false;
        if (active && ch != K706Tuner.CHANNEL_FM) stop();
        tuner.mute(true);
        tuner.route((byte) ch);
        tuner.mute(false);
        return true;
    }

    /**
     * Remembers the channel the MCU is on while the factory Bluetooth app plays, so the
     * launcher can switch to it later on its own.
     */
    public void learnBluetoothChannel() {
        int ch = mcuChannel();
        if (ch > 0 && ch != K706Tuner.CHANNEL_FM && ch != K706Tuner.CHANNEL_ANDROID) {
            Prefs.put(app, Prefs.BT_CHANNEL_LEARNED, ch);
        }
    }

    /** The channel to use for Bluetooth music, or 0 to leave the switch alone. */
    public int bluetoothChannel() {
        // Off unless a channel was chosen by hand in Setup ("Automatic" uses the learned one).
        int pref = Prefs.integer(app, Prefs.BT_CHANNEL, 0);
        if (pref >= 0) return pref;
        return Prefs.integer(app, Prefs.BT_CHANNEL_LEARNED, 0);
    }

    public boolean isActive() { return active; }

    /** Makes FM the audio source (like pressing Radio on the factory unit) and tunes the last station. */
    public boolean start() {
        if (!available()) return false;
        if (active) return true;
        MediaHub.get(app).onRadioStarting();
        tuner.mute(true);
        tuner.cmd(K706Tuner.SUB_AREA, 1, 0);
        requestFocus();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (tuner.channel() != K706Tuner.CHANNEL_FM) tuner.route(K706Tuner.CHANNEL_FM);
                try {
                    audio.setParameters("fm_radio_on=1;fm_mute=0");
                } catch (Exception ignored) {
                }
                tuner.mute(false);
                setRds(Prefs.bool(app, Prefs.RADIO_RDS, true));
                if (Prefs.bool(app, Prefs.RADIO_LOC, false)) setLocal(true);
                tuner.tune(freq);
            }
        }, 150);
        active = true;
        changed();
        return true;
    }

    /** Hands audio back to Android (another source was chosen). */
    public void stop() {
        stopScan();
        if (!active) return;
        active = false;
        focusPaused = false;
        tuner.mute(true);
        try {
            audio.setParameters("fm_radio_on=0;fm_mute=1");
        } catch (Exception ignored) {
        }
        tuner.route(K706Tuner.CHANNEL_ANDROID);
        tuner.mute(false);
        abandonFocus();
        changed();
    }

    public void toggle() {
        if (active) stop();
        else start();
    }

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(focus, main)
                    .build();
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

    // ---- Tuning ------------------------------------------------------------------------------

    public void tune(int f) {
        f = Math.max(FM_MIN, Math.min(FM_MAX, f));
        if (!active) start();
        setFreq(f);
        tuner.tune(f);
    }

    public void seekUp() {
        if (!active) start();
        tuner.cmd(K706Tuner.SUB_SEEK_UP, 1, 0);
    }

    public void seekDown() {
        if (!active) start();
        tuner.cmd(K706Tuner.SUB_SEEK_DOWN, 2, 0);
    }

    public void stepUp() {
        tune(freq + 10 > FM_MAX ? FM_MIN : freq + 10);
    }

    public void stepDown() {
        tune(freq - 10 < FM_MIN ? FM_MAX : freq - 10);
    }

    public void setLocal(boolean local) {
        tuner.cmd(K706Tuner.SUB_LOC, local ? 1 : 0, 0);
    }

    public void setRds(boolean on) {
        tuner.send(K706Tuner.CMD_RDS, new byte[]{(byte) (on ? 1 : 0)});
    }

    private void setFreq(int f) {
        if (f == freq) return;
        freq = f;
        ps = "";
        rt = "";
        Prefs.put(app, P_FREQ, f);
        changed();
    }

    // ---- State -------------------------------------------------------------------------------

    public int freq() { return freq; }
    public String ps() { return ps; }
    public String rt() { return rt; }
    public boolean stereo() { return stereo; }
    public boolean scanning() { return scanning; }

    public static String format(int f) {
        return String.format(Locale.US, "%.1f", f / 100.0);
    }

    /** Station name if RDS gave one, else the frequency. */
    public String label() {
        return ps.length() > 0 ? ps : "FM " + format(freq) + " MHz";
    }

    // ---- Presets / station list ---------------------------------------------------------------

    public Station preset(int i) { return presets[i]; }

    public int presetIndexOfCurrent() {
        for (int i = 0; i < PRESETS; i++) if (presets[i] != null && presets[i].freq == freq) return i;
        return -1;
    }

    public void savePreset(int i) {
        presets[i] = new Station(freq, ps);
        Prefs.put(app, P_PRESETS, serialize(java.util.Arrays.asList(presets)));
        changed();
    }

    public void clearPresets() {
        java.util.Arrays.fill(presets, null);
        Prefs.put(app, P_PRESETS, "");
        changed();
    }

    public void recallPreset(int i) {
        if (presets[i] != null) tune(presets[i].freq);
    }

    public List<Station> stations() { return stations; }

    /** Software scan: seek upward through the band and list every station that stops the seek. */
    public void startScan() {
        if (!active) start();
        stations.clear();
        scanning = true;
        scanSteps = 0;
        scanLast = FM_MIN;
        changed();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                tuner.tune(FM_MIN);
                setFreq(FM_MIN);
                main.postDelayed(scanStep, 600);
            }
        }, 400);
    }

    public void stopScan() {
        if (!scanning) return;
        scanning = false;
        main.removeCallbacks(scanStep);
        main.removeCallbacks(scanWatch);
        Prefs.put(app, P_STATIONS, serialize(stations));
        changed();
    }

    private final Runnable scanStep = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
            scanLast = freq;
            scanSettleAt = 0;
            tuner.cmd(K706Tuner.SUB_SEEK_UP, 1, 0);
            main.postDelayed(scanWatch, 700);
        }
    };

    /** Waits for the seek to land, records the station (with RDS name if it arrives), then seeks on. */
    private final Runnable scanWatch = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
            long now = SystemClock.elapsedRealtime();
            if (seeking || freq == scanLast) {
                if (++scanSteps > 120) {
                    stopScan();
                    return;
                }
                main.postDelayed(this, 300);
                return;
            }
            if (freq < scanLast) { // wrapped around the band: done
                stopScan();
                return;
            }
            if (scanSettleAt == 0) {
                scanSettleAt = now; // give RDS a moment to name the station
                main.postDelayed(this, 1500);
                return;
            }
            stations.add(new Station(freq, ps));
            changed();
            if (stations.size() >= 40) {
                stopScan();
                return;
            }
            main.post(scanStep);
        }
    };

    private void loadList(String raw, List<Station> into) {
        if (raw == null || raw.length() == 0) return;
        String[] parts = raw.split(";", -1);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            int bar = p.indexOf('|');
            if (bar <= 0) continue;
            try {
                Station s = new Station(Integer.parseInt(p.substring(0, bar)), p.substring(bar + 1));
                if (into != null) into.add(s);
                else if (i < PRESETS) presets[i] = s;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static String serialize(List<Station> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Station s = list.get(i);
            if (i > 0) sb.append(';');
            if (s != null) sb.append(s.freq).append('|').append(s.name.replace(";", " ").replace("|", " "));
        }
        return sb.toString();
    }

    // ---- MCU packets -------------------------------------------------------------------------

    @Override
    public void onPacket(byte[] d) {
        int type = d[0] & 0xFF;
        switch (type) {
            case 0xB0: // tuner flags
                if (d.length > 1) {
                    int f = d[1] & 0xFF;
                    stereo = (f & 0x10) != 0;
                    seeking = (f & 0x08) != 0;
                    changed();
                }
                break;
            case 0xB1: { // [B1, band, idx, f0hi, f0lo, …]: current frequency is the slot at idx
                if (d.length < 5) break;
                int idx = d[2] & 0xFF;
                int off = 3 + idx * 2;
                int f = off + 1 < d.length ? ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF)
                        : ((d[3] & 0xFF) << 8) | (d[4] & 0xFF);
                if (f >= FM_MIN && f <= FM_MAX) setFreq(((f + 5) / 10) * 10);
                break;
            }
            case 0xB6: { // RDS PS (station name)
                String s = text(d, 1, Math.min(8, d.length - 1));
                if (s.length() > 0 && !s.equals(ps)) {
                    ps = s;
                    changed();
                }
                break;
            }
            case 0xB3: // RDS radio text on some firmwares (flags on others)
            case 0xB7: { // RDS radio text
                int start = d.length > 2 && d[1] >= 0 && d[1] < 10 ? 2 : 1;
                String s = text(d, start, d.length - start);
                if (type == 0xB3 && s.length() < 3) break;
                if (s.length() > 0 && !s.equals(rt)) {
                    rt = s;
                    changed();
                }
                break;
            }
            default:
                break;
        }
    }

    private static String text(byte[] d, int start, int len) {
        if (len <= 0) return "";
        try {
            String s = new String(d, start, len, "ISO-8859-1").replace('\u0000', ' ');
            s = s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s{2,}", " ").trim();
            return s;
        } catch (Exception e) {
            return "";
        }
    }
}
