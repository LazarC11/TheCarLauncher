package com.thecarlauncher.media;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lets us see other apps' media sessions
 * (Spotify, YouTube Music, Bluetooth audio, ...). Notifications themselves are ignored.
 */
public class MediaListenerService extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        MediaHub.get(this).connectExternal();
    }
}
