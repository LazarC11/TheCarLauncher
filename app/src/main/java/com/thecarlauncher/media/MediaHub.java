package com.thecarlauncher.media;

import android.content.ComponentName;
import android.content.Context;
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
            if (!seen.contains(pkg) && !pkg.equals(app.getPackageName())) remove(pkg);
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
            active = controllers.get(app.getPackageName());
            for (MediaController other : controllers.values()) if (isPlaying(other)) active = other;
        }
    }

    private void setActive(MediaController mc) {
        if (active == mc) return;
        active = mc;
        Prefs.put(app, Prefs.LAST_SOURCE, packageOf(mc));
    }

    /** Lets the driver switch the dashboard to another app's player. */
    public void select(String pkg) {
        MediaController mc = controllers.get(pkg);
        if (mc != null) {
            setActive(mc);
            notifyChanged();
        }
    }

    public List<String> sources() {
        return new ArrayList<>(controllers.keySet());
    }

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
        return active == null || app.getPackageName().equals(packageOf(active));
    }

    public String packageName() {
        return active == null ? app.getPackageName() : packageOf(active);
    }

    private static boolean isPlaying(MediaController mc) {
        PlaybackState s = mc.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING
                || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    public boolean isPlaying() {
        return active != null && isPlaying(active);
    }

    private MediaMetadata meta() {
        return active == null ? null : active.getMetadata();
    }

    public boolean hasContent() {
        MediaMetadata m = meta();
        return m != null && title() != null;
    }

    public String title() {
        MediaMetadata m = meta();
        if (m == null) return null;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t;
    }

    public String artist() {
        MediaMetadata m = meta();
        if (m == null) return null;
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return a;
    }

    public String album() {
        MediaMetadata m = meta();
        return m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ALBUM);
    }

    public Bitmap art() {
        MediaMetadata m = meta();
        if (m == null) return null;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    public long duration() {
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
        if (pkg == null || pkg.equals(app.getPackageName())) return "Music";
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
        if (isOwn()) {
            MusicService s = MusicService.get();
            if (s != null) s.next();
        } else {
            active.getTransportControls().skipToNext();
        }
    }

    public void previous() {
        if (isOwn()) {
            MusicService s = MusicService.get();
            if (s != null) s.previous();
        } else {
            active.getTransportControls().skipToPrevious();
        }
    }

    public void seekTo(long ms) {
        if (active != null) active.getTransportControls().seekTo(ms);
    }
}
