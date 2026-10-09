package com.thecarlauncher.media;

import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Notification access: lets us see other apps' media sessions (Spotify, YouTube Music,
 * Bluetooth audio, ...) and keeps the status-bar envelope in step with waiting notifications.
 */
public class MediaListenerService extends NotificationListenerService {
    private static MediaListenerService sInstance;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** The connected listener, or null; used to dismiss a notification after opening it. */
    public static MediaListenerService get() { return sInstance; }

    @Override
    public void onListenerConnected() {
        sInstance = this;
        MediaHub.get(this).connectExternal();
        try {
            for (StatusBarNotification n : getActiveNotifications()) media(n);
        } catch (Exception ignored) {
        }
        publish();
    }

    @Override
    public void onListenerDisconnected() {
        sInstance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        media(sbn);
        publish();
    }

    /** "Now playing" notifications: take their session token, or their text and picture. */
    private void media(StatusBarNotification sbn) {
        try {
            final String pkg = sbn.getPackageName();
            android.app.Notification n = sbn.getNotification();
            android.os.Bundle x = n.extras;
            final android.media.session.MediaSession.Token token =
                    x.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION);
            boolean transport = android.app.Notification.CATEGORY_TRANSPORT.equals(n.category) || token != null;
            CharSequence t = x.getCharSequence(android.app.Notification.EXTRA_TITLE);
            CharSequence s = x.getCharSequence(android.app.Notification.EXTRA_TEXT);
            if (s == null) s = x.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT);
            android.graphics.Bitmap art = null;
            Object li = x.get(android.app.Notification.EXTRA_LARGE_ICON);
            if (li instanceof android.graphics.Bitmap) art = (android.graphics.Bitmap) li;
            else if (android.os.Build.VERSION.SDK_INT >= 23 && n.getLargeIcon() != null) {
                android.graphics.drawable.Drawable d = n.getLargeIcon().loadDrawable(this);
                if (d instanceof android.graphics.drawable.BitmapDrawable) art = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            }
            final String title = t == null ? null : t.toString(), text = s == null ? null : s.toString();
            final android.graphics.Bitmap picture = art;
            final boolean isMedia = transport || String.valueOf(x.getString(android.app.Notification.EXTRA_TEMPLATE)).contains("MediaStyle");
            main.post(new Runnable() {
                @Override
                public void run() {
                    MediaHub hub = MediaHub.get(MediaListenerService.this);
                    if (token != null) hub.onMediaNotification(pkg, token);
                    if (isMedia || hub.bluetooth().looksLikeBluetooth(pkg)) hub.onPlayingNotification(pkg, title, text, picture);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        publish();
    }

    private void publish() {
        StatusBarNotification[] all;
        try {
            all = getActiveNotifications();
        } catch (Exception e) {
            all = null;
        }
        final StatusBarNotification[] snapshot = all;
        main.post(new Runnable() {
            @Override
            public void run() {
                PhoneStatus.get(MediaListenerService.this).setNotifications(snapshot);
            }
        });
    }
}
