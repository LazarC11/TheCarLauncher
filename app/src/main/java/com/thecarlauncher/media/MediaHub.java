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
    private boolean btSelected, btPlaying, btRouted, playWhenConnected;
    /** BT audio was switched on since the launcher started (sound routed, play sent). */
    private boolean btActivated;
    private long btAutoAt;

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
        // Older builds learned the (empty) system Bluetooth session as the BT player; forget it.
        if (BluetoothSource.SYSTEM_PKG.equals(Prefs.str(c, Prefs.BT_SESSION_PKG, null))) Prefs.put(c, Prefs.BT_SESSION_PKG, (String) null);
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

    /** Opens Android's notification-access page (for our listener, where supported). */
    public void openNotificationAccess() {
        Intent i;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            i = new Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS")
                    .putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", listenerComponent.flattenToString());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Ui.start(app, i)) return;
        }
        i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!Ui.start(app, i)) Ui.toast(app, "Notification access settings not found");
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
        if (!pkg.equals(app.getPackageName()) && !pkg.equals(BluetoothSource.SYSTEM_PKG) && bt.looksLikeBluetooth(pkg)) {
            Prefs.put(app, Prefs.BT_SESSION_PKG, pkg);
        }
        MediaController.Callback cb = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) setActive(mc);
                notifyChanged();
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                if (btSelected) {
                    MediaController best = btController();
                    if (best != null) active = best;
                }
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
        // The Bluetooth app's session appeared while BT is the source: follow it.
        if (btSelected && bt.isBluetoothPackage(pkg)) {
            active = mc;
            if (playWhenConnected) {
                playWhenConnected = false;
                mc.getTransportControls().play();
            }
        }
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
        if (btSelected && !pkg.equals(app.getPackageName()) && !pkg.equals(BluetoothSource.SYSTEM_PKG)
                && !bt.isBluetoothPackage(pkg)) {
            // Something started playing while BT-audio is the source: that is the BT player.
            Prefs.put(app, Prefs.BT_SESSION_PKG, pkg);
        }
        btSelected = bt.isBluetoothPackage(pkg) || pkg.equals(Prefs.str(app, Prefs.BT_SESSION_PKG, null));
        if (active == mc) return;
        active = mc;
        Prefs.put(app, Prefs.LAST_SOURCE, btSelected ? BLUETOOTH : pkg);
    }

    /** Called once the system Bluetooth media browser is connected. */
    void onBluetoothController(String pkg, MediaController mc) {
        add(pkg, mc);
        if (btSelected) {
            active = mc;
            if (playWhenConnected) {
                playWhenConnected = false;
                mc.getTransportControls().play();
            }
        }
        notifyChanged();
    }

    /**
     * The Bluetooth player to show. Candidates in order: the user's choice, the learned
     * package, the system AVRCP browser, the BT app, then (while BT-audio is the source)
     * anything playing. A candidate that is playing or has a title wins over an empty one,
     * so the empty system session on units with their own Bluetooth module is skipped.
     */
    private MediaController btController() {
        List<String> order = new ArrayList<>();
        String chosen = Prefs.str(app, Prefs.BT_PKG, null);
        if (chosen != null) order.add(chosen.contains("/") ? chosen.substring(0, chosen.indexOf('/')) : chosen);
        String sp = Prefs.str(app, Prefs.BT_SESSION_PKG, null);
        if (sp != null) order.add(sp);
        String ap = bt.appPackage();
        if (ap != null) order.add(ap);
        order.add(BluetoothSource.SYSTEM_PKG);
        MediaController firstFound = null;
        for (String pkg : order) {
            MediaController mc = controllers.get(pkg);
            if (mc == null) continue;
            if (firstFound == null) firstFound = mc;
            if (isPlaying(mc) || hasTitle(mc)) return mc;
        }
        if (btSelected) {
            // BT-audio is the source but its player is published under another package
            // (e.g. a Bluetooth service instead of the screen): take whatever is playing.
            for (Map.Entry<String, MediaController> e : controllers.entrySet()) {
                if (e.getKey().equals(app.getPackageName()) || e.getKey().equals(BluetoothSource.SYSTEM_PKG)) continue;
                if (isPlaying(e.getValue()) || hasTitle(e.getValue())) {
                    Prefs.put(app, Prefs.BT_SESSION_PKG, e.getKey());
                    return e.getValue();
                }
            }
        }
        return firstFound;
    }

    private static boolean hasTitle(MediaController mc) {
        MediaMetadata m = mc.getMetadata();
        if (m == null) return false;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        return t != null && t.length() > 0;
    }

    /**
     * A "now playing" notification carries its media session token: use it, so the Bluetooth
     * app's player is found even when it is missing from the active-session list.
     */
    public void onMediaNotification(String pkg, MediaSession.Token token) {
        if (pkg == null || token == null || pkg.equals(app.getPackageName())) return;
        MediaController known = controllers.get(pkg);
        if (known != null && known.getSessionToken().equals(token)) return;
        try {
            add(pkg, new MediaController(app, token));
        } catch (Exception ignored) {
            return;
        }
        if (btSelected) active = btController();
        notifyChanged();
    }

    /** Text and picture of the Bluetooth app's notification, when it has no usable session. */
    private String notifTitle, notifText, notifPkg;
    private Bitmap notifArt;

    public void onPlayingNotification(String pkg, String title, String text, Bitmap art) {
        if (pkg == null || pkg.equals(app.getPackageName())) return;
        notifPkg = pkg;
        notifTitle = title;
        notifText = text;
        notifArt = art;
        if (btSelected) notifyChanged();
    }

    /** Diagnostics: what the last media notification said. */
    public String notificationInfo() {
        return notifPkg == null ? null : notifPkg + ": " + notifTitle + " / " + notifText + (notifArt != null ? " [cover]" : "");
    }

    private boolean notifUsable() {
        return btSelected && notifTitle != null && notifPkg != null
                && (bt.isBluetoothPackage(notifPkg) || notifPkg.equals(Prefs.str(app, Prefs.BT_SESSION_PKG, null))
                || bt.looksLikeBluetooth(notifPkg));
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
        if (app.getPackageName().equals(key)) {
            selectOwn();
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
        // Optional: only if a channel was set by hand in Setup (default off).
        int ch = radio().bluetoothChannel();
        if (ch > 0) btRouted = radio().routeChannel(ch);
        btSelected = true;
        btActivated = true;
        active = btController();
        Prefs.put(app, Prefs.LAST_SOURCE, BLUETOOTH);
        // Like the factory multimedia: start the unit's Bluetooth audio app, let it run in the
        // background, start playback, and show what it is playing.
        if (!Prefs.bool(app, Prefs.BT_OPEN_APP, false)) {
            // Never show the BT app: make sure it runs in the background, then send it "play"
            // through its control broadcast. Its play also takes the audio focus, which switches
            // the unit's sound to Bluetooth. The song itself is read from the BT module.
            bt.startInBackground(this, bt.hasQfApp() ? BluetoothSource.QF_PKG : bt.appPackage());
            playWhenConnected = true;
            main.postDelayed(playBluetooth, 500);
            main.postDelayed(playBluetoothRetry, 2500);
        } else {
            // The unit only plays BT audio once the app's screen has opened: open it briefly
            // and come back. Coming back needs "Display over other apps" on Android 10+.
            bt.startInBackground(this, bt.appPackage());
            playWhenConnected = true;
            if (!com.thecarlauncher.util.BringBack.canOverlay(app) && !Prefs.bool(app, "overlay_asked", false)) {
                // Without this permission Android keeps the BT app on top: ask once.
                Prefs.put(app, "overlay_asked", true);
                Ui.toast(app, com.thecarlauncher.mib.Mib.t(app, "Uključi \"Prikaz preko drugih aplikacija\" za Car Launcher, pa ponovo izaberi BT audio.",
                        "Turn on \"Display over other apps\" for Car Launcher, then choose BT audio again."));
                com.thecarlauncher.util.BringBack.requestOverlay(app);
            } else {
                wakeBluetoothApp();
            }
        }
        goc().send("MK");
        goc().send("VI");
        notifyChanged();
    }

    /**
     * Opening the media screen with BT audio as the last source switches the sound to Bluetooth
     * and starts it, as if BT audio had been chosen: once after the launcher starts, and again
     * after the phone was gone. A song the user paused on the phone is left alone.
     */
    public void resumeBluetooth() {
        if (!btSelected || isRadio()) return;
        long now = System.currentTimeMillis();
        if (now - btAutoAt < 8000) return;
        boolean idle = !goc().hasSong() && !isPlaying();
        if (btActivated && !idle) return;
        btAutoAt = now;
        selectBluetooth();
    }

    /** The phone just connected over Bluetooth: BT audio becomes the source, like its key. */
    public void onPhoneConnected() {
        BtDevices.get(app).known(); // remembers the phone
        if (!Prefs.bool(app, Prefs.BT_AUTO_SELECT, true)) return;
        btAutoAt = System.currentTimeMillis();
        select(BLUETOOTH);
    }

    /** Our own player (USB / flash) becomes the source: radio and Bluetooth stop. */
    public void selectOwn() {
        if (isRadio()) radio().stop();
        if (btSelected) {
            main.removeCallbacks(playBluetooth);
            main.removeCallbacks(playBluetoothRetry);
            playWhenConnected = false;
            pauseBluetooth();
            if (btRouted) {
                radio().routeChannel(4);
                btRouted = false;
            }
            btSelected = false;
        }
        MediaController mc = controllers.get(app.getPackageName());
        if (mc != null) active = mc;
        Prefs.put(app, Prefs.LAST_SOURCE, app.getPackageName());
        notifyChanged();
    }

    private void pauseBluetooth() {
        if (bt.qfCommand(BluetoothSource.QF_PAUSE)) {
            btPlaying = false;
            return;
        }
        if (goc().connected()) {
            goc().send("MJ");
            btPlaying = false;
            return;
        }
        MediaController mc = btController();
        if (mc != null) mc.getTransportControls().pause();
        else bt.sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        btPlaying = false;
    }

    /**
     * Some units only route Bluetooth audio to the speakers while their BT app has been opened.
     * Open it briefly, then come back to the launcher.
     */
    /**
     * Opens the Bluetooth music screen: through its media session first (works even for
     * screens the launcher may not start directly), then by package / hidden screen.
     */
    public boolean openBluetoothUi() {
        // A screen the user picked in Setup is opened as chosen.
        if (Prefs.str(app, Prefs.BT_PKG, null) != null && bt.openApp()) return true;
        MediaController mc = btController();
        if (mc != null && mc.getSessionActivity() != null) {
            try {
                mc.getSessionActivity().send();
                return true;
            } catch (Exception ignored) {
            }
        }
        return bt.openApp();
    }

    /** Package names of all media sessions currently visible (diagnostics). */
    public List<String> sessionPackages() {
        return new ArrayList<>(controllers.keySet());
    }

    /**
     * Starts the Bluetooth audio app (it only connects the phone's music when started), brings
     * the launcher back so the app keeps running behind it, then starts playback.
     */
    private void wakeBluetoothApp() {
        AppRepository.App screen = bt.appScreen();
        boolean started = false;
        if (screen != null) {
            started = Ui.start(app, new Intent(Intent.ACTION_MAIN).setComponent(screen.component())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
        if (!started) started = openBluetoothUi();
        int stay = Prefs.integer(app, Prefs.BT_WAKE_MS, 1200);
        if (started && Prefs.bool(app, Prefs.BT_WAKE, true)) {
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    com.thecarlauncher.util.BringBack.toMedia(app);
                }
            }, stay);
        }
        // Start playback once the app is up, and nudge it once more if it is still silent.
        main.postDelayed(playBluetooth, stay + 300);
        main.postDelayed(playBluetoothRetry, stay + 2500);
    }

    private final Runnable playBluetooth = new Runnable() {
        @Override
        public void run() {
            if (!btSelected) return;
            MediaController mc = btController();
            if (bt.qfCommand(BluetoothSource.QF_PLAY)) {
                if (mc != null) active = mc;
                playWhenConnected = false;
                goc().send("MK");
                notifyChanged();
                return;
            }
            if (goc().connected()) {
                if (goc().playing() != 1) goc().send("MS");
                if (mc != null) active = mc;
                playWhenConnected = false;
                notifyChanged();
                return;
            }
            if (mc != null) {
                active = mc;
                playWhenConnected = false;
                if (!isPlaying(mc)) mc.getTransportControls().play();
            } else {
                bt.sendKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                btPlaying = true;
            }
            notifyChanged();
        }
    };

    private final Runnable playBluetoothRetry = new Runnable() {
        @Override
        public void run() {
            if (!btSelected) return;
            MediaController mc = btController();
            boolean silent = goc().connected() ? goc().playing() != 1 : mc != null ? !isPlaying(mc) : qf().playing() != 1;
            if (silent) playBluetooth.run();
        }
    };

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
        if (btSelected && goc().connected() && goc().playing() >= 0) return goc().playing() == 1;
        // Song info is arriving from the BT module but no play state: it is playing.
        if (gocSong() && goc().playing() < 0) return true;
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

    private GocBluetooth goc() { return GocBluetooth.get(app); }

    /** BT-audio with the song coming straight from the Goodocom Bluetooth daemon. */
    private boolean gocSong() { return btSelected && goc().hasSong(); }

    public boolean hasTrackInfo() {
        if (isRadio()) return true;
        if (gocSong()) return true;
        // The BT module is the truth when we read it: no song there means the phone is gone
        // or stopped, so old details from other sources are not shown.
        if (btSelected && goc().connected()) return false;
        if (btSelected && qf().title() != null) return true;
        if (notifUsable()) return true;
        MediaMetadata m = meta();
        if (m == null) return false;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t != null && t.length() > 0;
    }

    public String title() {
        if (isRadio()) return radio().label();
        if (gocSong()) return goc().title();
        MediaMetadata m = meta();
        String t = null;
        if (m != null) {
            t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        if (btSelected && (t == null || t.length() == 0) && notifUsable()) t = notifTitle;
        if (btSelected && (t == null || t.length() == 0)) t = qf().title();
        if (btSelected && (t == null || t.length() == 0)) t = "Bluetooth audio";
        return t;
    }

    public String artist() {
        if (gocSong() && goc().artist() != null && goc().artist().length() > 0) return goc().artist();
        if (isRadio()) return radio().rt().length() > 0 ? radio().rt() : "FM " + RadioController.format(radio().freq()) + " MHz";
        MediaMetadata m = meta();
        String a = null;
        if (m != null) {
            a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        }
        if (btSelected && (a == null || a.length() == 0) && notifUsable()) a = notifText;
        if (btSelected && (a == null || a.length() == 0)) a = qf().artist();
        if (btSelected && (a == null || a.length() == 0)) a = "Music from your phone";
        return a;
    }

    public String album() {
        if (isRadio()) return null;
        if (gocSong()) return goc().album();
        MediaMetadata m = meta();
        return m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ALBUM);
    }

    /** Cover looked up online by artist + title (Setup can switch it off). */
    private Bitmap onlineCover(String artist, String title) {
        if (!Prefs.bool(app, Prefs.ONLINE_COVER, true)) return null;
        CoverArt ca = CoverArt.get(app);
        ca.setListener(new CoverArt.Listener() {
            @Override
            public void onCover() { notifyChanged(); }
        });
        return ca.find(artist, title);
    }

    public Bitmap art() {
        if (isRadio()) return null;
        // Song from the Bluetooth daemon: only a cover that belongs to that same song.
        if (gocSong()) {
            MediaMetadata gm = meta();
            String t = gm == null ? null : gm.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (t == null || !t.equals(goc().title())) {
                if (notifUsable() && notifArt != null) return notifArt;
                return onlineCover(goc().artist(), goc().title());
            }
        }
        MediaMetadata m = meta();
        Bitmap b = null;
        if (m != null) {
            b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            // Bluetooth (AVRCP) cover art often arrives as a content:// link instead of a bitmap.
            if (b == null) {
                for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI,
                        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
                    String u = m.getString(k);
                    if (u != null && u.length() > 0) {
                        b = artFromUri(u);
                        if (b != null) break;
                    }
                }
            }
        }
        if (b == null && notifUsable()) b = notifArt;
        if (b == null && btSelected) b = qf().art();
        return b;
    }

    private final Map<String, Bitmap> uriArt = new LinkedHashMap<>();
    private final java.util.Set<String> uriLoading = new java.util.HashSet<>();

    /** Loads a cover from a content:// or file link once, in the background. */
    private Bitmap artFromUri(final String uri) {
        Bitmap b = uriArt.get(uri);
        if (b != null || uriLoading.contains(uri)) return b;
        uriLoading.add(uri);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap loaded = null;
                try {
                    java.io.InputStream in = app.getContentResolver().openInputStream(android.net.Uri.parse(uri));
                    loaded = android.graphics.BitmapFactory.decodeStream(in);
                    if (in != null) in.close();
                } catch (Throwable ignored) {
                }
                final Bitmap fb = loaded;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        uriLoading.remove(uri);
                        if (fb == null) return;
                        if (uriArt.size() > 6) uriArt.remove(uriArt.keySet().iterator().next());
                        uriArt.put(uri, fb);
                        notifyChanged();
                    }
                });
            }
        }, "art-uri").start();
        return null;
    }

    public long duration() {
        if (isRadio()) return 0;
        if (gocSong()) return Math.max(0, goc().lengthMs());
        MediaMetadata m = meta();
        return m == null ? 0 : Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    /** Current position, extrapolated from the last state update while playing. */
    public long position() {
        if (gocSong()) return Math.max(0, goc().positionMs());
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
        if (btSelected && (bt.qfCommand(BluetoothSource.QF_PLAY_PAUSE) || goc().send("MA"))) {
            goc().send("VI");
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
        } else if (btSelected && (bt.qfCommand(BluetoothSource.QF_NEXT) || goc().send("MD"))) {
            goc().send("MK");
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
        } else if (btSelected && (bt.qfCommand(BluetoothSource.QF_PREVIOUS) || goc().send("ME"))) {
            goc().send("MK");
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
        if (btSelected) openBluetoothUi();
        else AppRepository.launchPackage(app, packageName());
    }

    public void seekTo(long ms) {
        if (active != null) active.getTransportControls().seekTo(ms);
    }
}
