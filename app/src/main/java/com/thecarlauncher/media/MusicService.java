package com.thecarlauncher.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.radio.RadioController;
import com.thecarlauncher.R;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The launcher's own music player. Exposes a MediaSession so steering-wheel keys,
 * the dashboard tile and the system all drive it the same way as any other player.
 */
public class MusicService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;
    private static final String ACTION_TOGGLE = "com.thecarlauncher.TOGGLE";
    private static final String ACTION_NEXT = "com.thecarlauncher.NEXT";
    private static final String ACTION_PREV = "com.thecarlauncher.PREV";
    private static final String CHANNEL = "playback";
    private static final int NOTIFICATION_ID = 7;

    private static MusicService sInstance;
    private static boolean sResumeChecked;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<Integer> history = new ArrayList<>();
    private MediaSession session;
    private MediaPlayer player;
    private AudioManager audio;

    private List<Track> queue = new ArrayList<>();
    private String queueCtx = MusicLibrary.CTX_ALL;
    private int index = -1;
    private boolean prepared, playWhenReady, shuffle, focusPaused, foreground;
    private int repeat = REPEAT_ALL;
    private int pendingSeek;
    private int errorStreak;
    private Bitmap art;

    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            saveState();
            main.postDelayed(this, 15000);
        }
    };

    public static MusicService get() { return sInstance; }

    public static void start(Context c) {
        try {
            c.startService(new Intent(c, MusicService.class));
        } catch (Exception ignored) {
            // Background start not allowed; the activity will retry when visible.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        shuffle = Prefs.bool(this, Prefs.SHUFFLE, false);
        repeat = Prefs.integer(this, Prefs.REPEAT, REPEAT_ALL);

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(MediaPlayer mp, int w, int h) {
                videoW = w;
                videoH = h;
                updateState();
            }
        });

        session = new MediaSession(this, "CarLauncher");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            // While the built-in FM radio is the source, steering-wheel keys drive the tuner.
            @Override public void onPlay() { if (!radio()) play(); }
            @Override public void onPause() { if (radio()) RadioController.get(MusicService.this).stop(); else pause(); }
            @Override public void onStop() { if (radio()) RadioController.get(MusicService.this).stop(); else pause(); }
            @Override public void onSkipToNext() { if (radio()) RadioController.get(MusicService.this).seekUp(); else next(); }
            @Override public void onSkipToPrevious() { if (radio()) RadioController.get(MusicService.this).seekDown(); else previous(); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
        });
        Intent open = new Intent(this, LauncherActivity.class).putExtra(LauncherActivity.EXTRA_SCREEN, "media");
        session.setSessionActivity(PendingIntent.getActivity(this, 1, open, pendingFlags()));
        MediaHub.get(this).setOwnSession(session.getSessionToken());
        Tone.get(this).attach(player.getAudioSessionId());
        updateState();

        restoreState();
        main.postDelayed(saver, 15000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(a)) toggle();
        else if (ACTION_NEXT.equals(a)) next();
        else if (ACTION_PREV.equals(a)) previous();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        saveState();
        main.removeCallbacksAndMessages(null);
        audio.abandonAudioFocus(this);
        Tone.get(this).detach(player.getAudioSessionId());
        player.release();
        session.release();
        sInstance = null;
        super.onDestroy();
    }

    // ---- Queue -------------------------------------------------------------------------

    /** Replaces the queue and starts playing at {@code start}. */
    public void playQueue(List<Track> tracks, int start, String ctx) {
        if (tracks == null || tracks.isEmpty()) return;
        queue = new ArrayList<>(tracks);
        queueCtx = ctx;
        history.clear();
        errorStreak = 0;
        load(Math.max(0, Math.min(start, queue.size() - 1)), 0, true);
    }

    /** Shuffles the whole library: the "just play something" button. */
    public void playEverything() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Track> all = MusicLibrary.all(MusicService.this);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (all.isEmpty()) return;
                        playQueue(all, random.nextInt(all.size()), MusicLibrary.CTX_ALL);
                    }
                });
            }
        }).start();
    }

    public List<Track> queue() { return queue; }
    public Track current() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }

    // ---- Music videos: the picture goes to whichever surface the media screen offers --------

    private android.view.Surface videoSurface;
    private int videoW, videoH;

    public void setVideoSurface(android.view.Surface s) {
        videoSurface = s;
        try {
            player.setSurface(s);
        } catch (Exception ignored) {
        }
    }

    /** Width / height of the playing video, or 0 / 0. */
    public int videoWidth() { return videoW; }

    public int videoHeight() { return videoH; }
    public boolean hasQueue() { return !queue.isEmpty(); }
    public boolean isShuffle() { return shuffle; }
    public int repeatMode() { return repeat; }

    public void setShuffle(boolean on) {
        shuffle = on;
        history.clear();
        Prefs.put(this, Prefs.SHUFFLE, on);
        updateState();
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        Prefs.put(this, Prefs.REPEAT, repeat);
        updateState();
    }

    private void load(int i, int seek, boolean autoplay) {
        index = i;
        prepared = false;
        pendingSeek = seek;
        playWhenReady = autoplay;
        final Track t = queue.get(i);
        try {
            player.reset();
            videoW = videoH = 0;
            player.setDataSource(this, t.uri());
            if (videoSurface != null && t.video) player.setSurface(videoSurface);
            player.prepareAsync();
        } catch (Exception e) {
            onError(player, 0, 0);
            return;
        }
        art = null;
        updateMetadata(t);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = MusicLibrary.loadArt(MusicService.this, t.albumId, t.id, 512);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (t == current()) {
                            art = b;
                            updateMetadata(t);
                            if (foreground || isPlaying()) postNotification();
                        }
                    }
                });
            }
        }).start();
        updateState();
    }

    // ---- Transport ---------------------------------------------------------------------

    public boolean isPlaying() {
        return prepared && player.isPlaying();
    }

    public void play() {
        if (queue.isEmpty()) {
            playEverything();
            return;
        }
        if (!requestFocus()) return;
        session.setActive(true);
        if (!prepared) {
            playWhenReady = true;
            if (index < 0) load(0, 0, true);
            return;
        }
        player.setVolume(1f, 1f);
        player.start();
        updateState();
        goForeground();
    }

    public void pause() {
        playWhenReady = false;
        if (prepared && player.isPlaying()) player.pause();
        updateState();
        leaveForeground();
        saveState();
        // Paused by the user or another source: give the audio back, so other players (the factory
        // radio, Bluetooth) are not kept quiet ("ducked") by a launcher that is not playing.
        if (!focusPaused) audio.abandonAudioFocus(this);
    }

    public void toggle() {
        if (isPlaying()) pause();
        else play();
    }

    public void next() {
        if (queue.isEmpty()) return;
        load(pickNext(true), 0, true);
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (prepared && player.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }
        int target;
        if (shuffle && !history.isEmpty()) {
            target = history.remove(history.size() - 1);
        } else {
            target = index - 1;
            if (target < 0) target = queue.size() - 1;
        }
        load(target, 0, true);
    }

    public void seekTo(int ms) {
        if (prepared) player.seekTo(ms);
        else pendingSeek = ms;
        updateState();
    }

    private int pickNext(boolean userInitiated) {
        if (shuffle && queue.size() > 1) {
            history.add(index);
            if (history.size() > 200) history.remove(0);
            int n;
            do {
                n = random.nextInt(queue.size());
            } while (n == index);
            return n;
        }
        int n = index + 1;
        if (n >= queue.size()) n = (repeat == REPEAT_OFF && !userInitiated) ? -1 : 0;
        return n;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        prepared = true;
        errorStreak = 0;
        if (pendingSeek > 0) mp.seekTo(pendingSeek);
        pendingSeek = 0;
        if (playWhenReady) play();
        else updateState();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (repeat == REPEAT_ONE) {
            mp.seekTo(0);
            mp.start();
            return;
        }
        int n = pickNext(false);
        if (n < 0) {
            playWhenReady = false;
            prepared = false;
            load(0, 0, false);
            leaveForeground();
            return;
        }
        load(n, 0, true);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        prepared = false;
        errorStreak++;
        if (errorStreak < Math.min(10, queue.size())) {
            final int n = (index + 1) % queue.size();
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    load(n, 0, playWhenReady);
                }
            }, 300);
        } else {
            playWhenReady = false;
            updateState();
        }
        return true;
    }

    private boolean radio() {
        return RadioController.get(this).isActive();
    }

    // ---- Audio focus -------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private boolean requestFocus() {
        return audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                focusPaused = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Phone call or navigation prompt: pause and come back afterwards.
                if (isPlaying()) {
                    focusPaused = true;
                    player.pause();
                    updateState();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (isPlaying()) player.setVolume(0.25f, 0.25f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (prepared) player.setVolume(1f, 1f);
                if (focusPaused) {
                    focusPaused = false;
                    play();
                }
                break;
        }
    }

    // ---- Session / notification ---------------------------------------------------------

    private void updateMetadata(Track t) {
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, t.duration);
        if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(b.build());
    }

    private void updateState() {
        int state;
        if (isPlaying()) state = PlaybackState.STATE_PLAYING;
        else if (!prepared && playWhenReady) state = PlaybackState.STATE_BUFFERING;
        else if (index >= 0) state = PlaybackState.STATE_PAUSED;
        else state = PlaybackState.STATE_NONE;
        long pos = prepared ? player.getCurrentPosition() : pendingSeek;
        android.os.Bundle extras = new android.os.Bundle();
        extras.putBoolean("shuffle", shuffle);
        extras.putInt("repeat", repeat);
        PlaybackState ps = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(state, pos, state == PlaybackState.STATE_PLAYING ? 1f : 0f, SystemClock.elapsedRealtime())
                .setExtras(extras)
                .build();
        session.setPlaybackState(ps);
        if (foreground) postNotification();
    }

    private void goForeground() {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
            foreground = true;
        } catch (Exception e) {
            // Some Android 12+ builds refuse background FGS starts; playback continues regardless.
            postNotification();
        }
    }

    private void leaveForeground() {
        if (foreground) {
            stopForeground(false);
            foreground = false;
        }
        if (index >= 0) postNotification();
    }

    private void postNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try {
            nm.notify(NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Track t = current();
        boolean playing = isPlaying();
        b.setSmallIcon(R.drawable.ic_music)
                .setContentTitle(t == null ? getString(R.string.app_name) : t.title)
                .setContentText(t == null ? "" : t.artist)
                .setLargeIcon(art)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(session.getController().getSessionActivity())
                .addAction(new Notification.Action(R.drawable.ic_prev, "Previous", service(ACTION_PREV, 2)))
                .addAction(new Notification.Action(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? "Pause" : "Play", service(ACTION_TOGGLE, 3)))
                .addAction(new Notification.Action(R.drawable.ic_next, "Next", service(ACTION_NEXT, 4)))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private PendingIntent service(String action, int code) {
        Intent i = new Intent(this, MusicService.class).setAction(action);
        return PendingIntent.getService(this, code, i, pendingFlags());
    }

    private static int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    // ---- Tone (bass / mid / treble) ------------------------------------------------------

    // ---- Persistence -------------------------------------------------------------------

    private void saveState() {
        Track t = current();
        if (t == null) return;
        Prefs.of(this).edit()
                .putString(Prefs.LAST_QUEUE, queueCtx)
                .putLong(Prefs.LAST_TRACK, t.id)
                .putInt(Prefs.LAST_POS, prepared ? player.getCurrentPosition() : pendingSeek)
                .apply();
    }

    /** Reloads the last queue; auto-resumes once per boot if the driver enabled it. */
    private void restoreState() {
        final String ctx = Prefs.str(this, Prefs.LAST_QUEUE, null);
        if (ctx == null || !MusicLibrary.hasPermission(this)) return;
        final long trackId = Prefs.of(this).getLong(Prefs.LAST_TRACK, -1);
        final int pos = Prefs.integer(this, Prefs.LAST_POS, 0);
        // Only resume our own player if it was the last thing playing (not e.g. Spotify).
        final boolean autoplay = !sResumeChecked && Prefs.bool(this, Prefs.RESUME_MEDIA, true)
                && getPackageName().equals(Prefs.str(this, Prefs.LAST_SOURCE, getPackageName()));
        sResumeChecked = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Track> tracks = MusicLibrary.resolve(MusicService.this, ctx);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (tracks.isEmpty() || !queue.isEmpty()) return;
                        int at = 0;
                        for (int i = 0; i < tracks.size(); i++) {
                            if (tracks.get(i).id == trackId) {
                                at = i;
                                break;
                            }
                        }
                        queue = new ArrayList<>(tracks);
                        queueCtx = ctx;
                        load(at, pos, false);
                        if (autoplay) {
                            // Give the head unit's audio stack a moment after ignition.
                            main.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    play();
                                }
                            }, 2500);
                        }
                    }
                });
            }
        }).start();
    }
}
