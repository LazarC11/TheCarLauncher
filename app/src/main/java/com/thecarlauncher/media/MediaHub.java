package com.thecarlauncher.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.radio.RadioController;
import com.thecarlauncher.util.Ui;

import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One place that knows "what is playing": our own player or any other media app
 * (Spotify, YouTube Music, the head unit's Bluetooth audio app, ...).
 */
public final class MediaHub {
    public interface Listener { void onMediaChanged(); }

    /** Pseudo source key for the head unit's Bluetooth music. */
    public static final String BLUETOOTH = "bluetooth";
    /** Pseudo source key for the launcher's built-in FM radio. */
    public static final String RADIO = "radio";
    private static MediaHub sInstance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionManager msm;
    private final ComponentName listenerComponent;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, MediaController> controllers = new LinkedHashMap<>();
    private final Map<String, MediaController.Callback> callbacks = new LinkedHashMap<>();
    private MediaController active;
    private boolean externalConnected;
    private final BluetoothSource bt;
    private boolean btSelected, btPlaying, btRouted;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> list) {
                    sync(list);
                }
            };

    private final Runnable notifier = new Runnable() {
        @Override
        public void run() {
            for (Listener l : listeners) l.onMediaChanged();
        }
    };

    public static synchronized MediaHub get(Context c) {
        if (sInstance == null) sInstance = new MediaHub(c.getApplicationContext());
        return sInstance;
    }

    private MediaHub(Context c) {
        app = c;
        msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(c, MediaListenerService.class);
        bt = new BluetoothSource(c);
        btSelected = BLUETOOTH.equals(Prefs.str(c, Prefs.LAST_SOURCE, null));
    }

    public BluetoothSource bluetooth() { return bt; }

    // ---- Built-in radio -----------------------------------------------------------------

    private RadioController radio() { return RadioController.get(app); }

    /** True while the FM tuner is the audio source. */
    public boolean isRadio() { return radio().isActive(); }

    /** Called by the radio when it changes state. */
    public void radioChanged() { notifyChanged(); }

    /** The radio is taking over the speakers: pause whatever was playing. */
    public void onRadioStarting() {
        MusicService own = MusicService.get();
        if (own != null && own.isPlaying()) own.pause();
        if (btSelected) pauseBluetooth();
        else if (active != null && !isOwn() && isPlaying(active)) active.getTransportControls().pause();
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
        l.onMediaChanged();
    }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyChanged() {
        main.removeCallbacks(notifier);
        main.post(notifier);
    }

    // ---- Session discovery --------------------------------------------------------------

    public void setOwnSession(MediaSession.Token token) {
        add(app.getPackageName(), new MediaController(app, token));
        if (active == null) active = controllers.get(app.getPackageName());
        notifyChanged();
    }

    public boolean hasNotificationAccess() {
        String flat = Settings.Secure.getString(app.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(listenerComponent.flattenToString());
    }

    /** Starts tracking other apps' sessions. Needs notification access. */
    public void connectExternal() {
        bt.connect(this);
        if (externalConnected || !hasNotificationAccess()) return;
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main);
            externalConnected = true;
            sync(msm.getActiveSessions(listenerComponent));
        } catch (SecurityException ignored) {
        }
    }

    private void sync(List<MediaController> list) {
        if (list == null) return;
        List<String> seen = new ArrayList<>();
        for (MediaController mc : list) {
            String pkg = mc.getPackageName();
            if (pkg == null) continue;
            seen.add(pkg);
            if (!controllers.containsKey(pkg)) add(pkg, mc);
        }
        for (String pkg : new ArrayList<>(controllers.keySet())) {
            if (!seen.contains(pkg) && !pkg.equals(app.getPackageName())
                    && !pkg.equals(BluetoothSource.SYSTEM_PKG)) {
                remove(pkg);
            }
        }
        for (MediaController mc : controllers.values()) {
            if (isPlaying(mc)) {
                setActive(mc);
                break;
            }
        }
        notifyChanged();
    }

    private void add(final String pkg, final MediaController mc) {
        if (controllers.containsKey(pkg)) remove(pkg);
        MediaController.Callback cb = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) setActive(mc);
                notifyChanged();
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                notifyChanged();
            }

            @Override
            public void onSessionDestroyed() {
                if (!pkg.equals(app.getPackageName())) {
                    remove(pkg);
                    notifyChanged();
                }
            }
        };
        controllers.put(pkg, mc);
        callbacks.put(pkg, cb);
        mc.registerCallback(cb, main);
    }

    private void remove(String pkg) {
        MediaController mc = controllers.remove(pkg);
        MediaController.Callback cb = callbacks.remove(pkg);
        if (mc != null && cb != null) mc.unregisterCallback(cb);
        if (mc != null && mc == active) {
            if (btSelected) {
                active = btController();
            } else {
                active = controllers.get(app.getPackageName());
                for (MediaController other : controllers.values()) if (isPlaying(other)) active = other;
            }
        }
    }

    private void setActive(MediaController mc) {
        String pkg = packageOf(mc);
        btSelected = bt.isBluetoothPackage(pkg);
        if (active == mc) return;
        active = mc;
        Prefs.put(app, Prefs.LAST_SOURCE, btSelected ? BLUETOOTH : pkg);
    }

    /** Called once the system Bluetooth media browser is connected. */
    void onBluetoothController(MediaController mc) {
        add(BluetoothSource.SYSTEM_PKG, mc);
        if (btSelected) active = mc;
        notifyChanged();
    }

    private MediaController btController() {
        MediaController mc = controllers.get(BluetoothSource.SYSTEM_PKG);
        if (mc == null) {
            String pkg = bt.appPackage();
            if (pkg != null) mc = controllers.get(pkg);
        }
        return mc;
    }

    /** Switches source like the factory unit: the old source pauses, the new one plays. */
    public void select(String key) {
        if (RADIO.equals(key)) {
            radio().start();
            return;
        }
        if (isRadio()) radio().stop();
        // Leaving Bluetooth: give the MCU audio switch back to Android media.
        if (btSelected && !BLUETOOTH.equals(key) && btRouted) {
            radio().routeChannel(4);
            btRouted = false;
        }
        if (BLUETOOTH.equals(key)) {
            selectBluetooth();
            return;
        }
        MediaController mc = controllers.get(key);
        if (mc == null) return;
        if (btSelected && !bt.isBluetoothPackage(key)) pauseBluetooth();
        setActive(mc);
        notifyChanged();
    }

    private void selectBluetooth() {
        MusicService own = MusicService.get();
        if (own != null && own.isPlaying()) own.pause();
        // QF / K706: the Bluetooth module has its own input on the MCU audio switch.
        int ch = radio().bluetoothChannel();
        if (ch > 0) btRouted = radio().routeChannel(ch);
        btSelected = true;
        active = btController();
        Prefs.put(app, Prefs.LAST_SOURCE, BLUETOOTH);
        if (Prefs.bool(app, Prefs.BT_WAKE, false)) wakeBluetoothApp();
        if (active != null) {
            active.getTransportControls().play();
        } else {
            bt.sendKey(KeyEvent.KEYCODE_MEDIA_PLAY);
            btPlaying = true;
        }
        notifyChanged();
    }

    private void pauseBluetooth() {
        MediaController mc = btController();
        if (mc != null) mc.getTransportControls().pause();
        else bt.sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        btPlaying = false;
    }

    /**
     * Some units only route Bluetooth audio to the speakers while their BT app has been opened.
     * Open it briefly, then come back to the launcher.
     */
    private void wakeBluetoothApp() {
        if (!bt.openApp()) return;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent back = new Intent(app, LauncherActivity.class)
                        .putExtra(LauncherActivity.EXTRA_SCREEN, "media")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                Ui.start(app, back);
            }
        }, 1500);
    }

    /** Source keys for the selector: our player, Bluetooth, then other media apps. */
    public List<String> sources() {
        List<String> out = new ArrayList<>();
        out.add(app.getPackageName());
        out.add(BLUETOOTH);
        for (String k : controllers.keySet()) {
            if (!k.equals(app.getPackageName()) && !bt.isBluetoothPackage(k)) out.add(k);
        }
        return out;
    }

    public boolean isBluetooth() { return btSelected; }

    /** Bluetooth selected but no session to read: controls go out as media keys. */
    private boolean btFallback() { return btSelected && active == null; }

    // ---- State --------------------------------------------------------------------------

    /** Package a controller is registered under (never null). */
    private String packageOf(MediaController mc) {
        for (Map.Entry<String, MediaController> e : controllers.entrySet()) {
            if (e.getValue() == mc) return e.getKey();
        }
        String p = mc.getPackageName();
        return p == null ? app.getPackageName() : p;
    }

    public boolean isOwn() {
        return !btSelected && (active == null || app.getPackageName().equals(packageOf(active)));
    }

    /** Key of the current source: our package, {@link #BLUETOOTH}, or another app's package. */
    public String packageName() {
        if (isRadio()) return RADIO;
        if (btSelected) return BLUETOOTH;
        return active == null ? app.getPackageName() : packageOf(active);
    }

    private static boolean isPlaying(MediaController mc) {
        PlaybackState s = mc.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING
                || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    public boolean isPlaying() {
        if (isRadio()) return true;
        if (btFallback()) return qf().playing() >= 0 ? qf().playing() == 1 : btPlaying;
        return active != null && isPlaying(active);
    }

    private MediaMetadata meta() {
        return active == null ? null : active.getMetadata();
    }

    public boolean hasContent() {
        if (btSelected) return true;
        MediaMetadata m = meta();
        return m != null && title() != null;
    }

    /** True when the current source reports a real track title (not our placeholder). */
    private QfMediaBridge qf() { return QfMediaBridge.get(app); }

    public boolean hasTrackInfo() {
        if (isRadio()) return true;
        if (btSelected && qf().title() != null) return true;
        MediaMetadata m = meta();
        if (m == null) return false;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t != null && t.length() > 0;
    }

    public String title() {
        if (isRadio()) return radio().label();
        MediaMetadata m = meta();
        String t = null;
        if (m != null) {
            t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        if (btSelected && (t == null || t.length() == 0)) t = qf().title();
        if (btSelected && (t == null || t.length() == 0)) t = "Bluetooth audio";
        return t;
    }

    public String artist() {
        if (isRadio()) return radio().rt().length() > 0 ? radio().rt() : "FM " + RadioController.format(radio().freq()) + " MHz";
        MediaMetadata m = meta();
        String a = null;
        if (m != null) {
            a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        }
        if (btSelected && (a == null || a.length() == 0)) a = qf().artist();
        if (btSelected && (a == null || a.length() == 0)) a = "Music from your phone";
        return a;
    }

    public String album() {
        if (isRadio()) return null;
        MediaMetadata m = meta();
        return m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ALBUM);
    }

    public Bitmap art() {
        if (isRadio()) return null;
        MediaMetadata m = meta();
        if (m == null) return null;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    public long duration() {
        if (isRadio()) return 0;
        MediaMetadata m = meta();
        return m == null ? 0 : Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    /** Current position, extrapolated from the last state update while playing. */
    public long position() {
        if (active == null) return 0;
        PlaybackState s = active.getPlaybackState();
        if (s == null) return 0;
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        long d = duration();
        return d > 0 ? Math.min(pos, d) : Math.max(0, pos);
    }

    public boolean canSeek() {
        if (active == null) return false;
        PlaybackState s = active.getPlaybackState();
        return s != null && (s.getActions() & PlaybackState.ACTION_SEEK_TO) != 0 && duration() > 0;
    }

    public boolean shuffle() {
        Bundle e = ownExtras();
        return e != null && e.getBoolean("shuffle");
    }

    public int repeat() {
        Bundle e = ownExtras();
        return e == null ? MusicService.REPEAT_ALL : e.getInt("repeat");
    }

    private Bundle ownExtras() {
        MediaController own = controllers.get(app.getPackageName());
        PlaybackState s = own == null ? null : own.getPlaybackState();
        return s == null ? null : s.getExtras();
    }

    public String sourceLabel(String pkg) {
        if (pkg == null || pkg.equals(app.getPackageName())) return "USB / Music";
        if (BLUETOOTH.equals(pkg)) return "Bluetooth";
        if (RADIO.equals(pkg)) return "Radio FM";
        try {
            PackageManager pm = app.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    public Drawable sourceIcon(String pkg) {
        try {
            return app.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Controls -----------------------------------------------------------------------

    public void playPause() {
        if (isRadio()) {
            radio().stop();
            return;
        }
        if (btFallback()) {
            bt.sendKey(btPlaying ? KeyEvent.KEYCODE_MEDIA_PAUSE : KeyEvent.KEYCODE_MEDIA_PLAY);
            btPlaying = !btPlaying;
            notifyChanged();
            return;
        }
        if (isOwn()) {
            MusicService s = MusicService.get();
            if (s != null) s.toggle();
            else MusicService.start(app);
            return;
        }
        if (isPlaying()) active.getTransportControls().pause();
        else active.getTransportControls().play();
    }

    public void next() {
        if (isRadio()) {
            radio().seekUp();
        } else if (btFallback()) {
            bt.sendKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        } else if (isOwn()) {
            MusicService s = MusicService.get();
            if (s != null) s.next();
        } else {
            active.getTransportControls().skipToNext();
        }
    }

    public void previous() {
        if (isRadio()) {
            radio().seekDown();
        } else if (btFallback()) {
            bt.sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if (isOwn()) {
            MusicService s = MusicService.get();
            if (s != null) s.previous();
        } else {
            active.getTransportControls().skipToPrevious();
        }
    }

    /** Opens the app behind the current source. */
    public void openCurrentApp() {
        if (isRadio()) return;
        if (btSelected) bt.openApp();
        else AppRepository.launchPackage(app, packageName());
    }

    public void seekTo(long ms) {
        if (active != null) active.getTransportControls().seekTo(ms);
    }
}
