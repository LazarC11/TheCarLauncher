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
        publish();
    }

    @Override
    public void onListenerDisconnected() {
        sInstance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        publish();
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
