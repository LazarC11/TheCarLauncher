package com.thecarlauncher.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Bridge to the head unit's Bluetooth music (phone → car over A2DP/AVRCP).
 * The audio itself is always received by the unit's own Bluetooth stack; we act as its front panel:
 *  1. Standard Android units expose it as a MediaBrowserService inside com.android.bluetooth.
 *  2. Many vendor BT apps publish a media session (visible with notification access).
 *  3. Otherwise we send media-button events straight to the vendor's Bluetooth app.
 */
public final class BluetoothSource {
    public static final String SYSTEM_PKG = "com.android.bluetooth";
    static final ComponentName SYSTEM_BROWSER = new ComponentName(SYSTEM_PKG,
            "com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService");
    private static final Pattern BT_APP = Pattern.compile(
            "(?i).*(bluetooth|\\bbt\\b|btmusic|bt_music|bt\\.music|bt music|a2dp|蓝牙).*");
    private static final Pattern NOT_BT = Pattern.compile("(?i).*(settings|scanner|file|transfer|share).*");

    private final Context app;
    private MediaBrowser browser;
    private MediaController systemController;
    private boolean systemAvailable;

    BluetoothSource(Context c) {
        app = c;
    }

    /** Connects to the system Bluetooth media browser if this unit has one. */
    void connect(final MediaHub hub) {
        if (browser != null) return;
        Intent probe = new Intent("android.media.browse.MediaBrowserService").setComponent(SYSTEM_BROWSER);
        systemAvailable = !app.getPackageManager().queryIntentServices(probe, 0).isEmpty();
        if (!systemAvailable) return;
        browser = new MediaBrowser(app, SYSTEM_BROWSER, new MediaBrowser.ConnectionCallback() {
            @Override
            public void onConnected() {
                try {
                    systemController = new MediaController(app, browser.getSessionToken());
                    hub.onBluetoothController(systemController);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onConnectionSuspended() {
                systemController = null;
            }

            @Override
            public void onConnectionFailed() {
                systemController = null;
            }
        }, null);
        try {
            browser.connect();
        } catch (Exception ignored) {
        }
    }

    public boolean systemServiceFound() { return systemAvailable; }
    public boolean systemConnected() { return systemController != null; }

    /** The vendor Bluetooth music app: user's choice, else best guess. May be null. */
    public String appPackage() {
        String p = Prefs.str(app, Prefs.BT_PKG, null);
        if (p != null && p.contains("/")) p = p.substring(0, p.indexOf('/'));
        if (p != null && installed(p)) return p;
        AppRepository.App hidden = AppRepository.get(app).bluetoothMusic();
        if (hidden != null) return hidden.pkg;
        List<String> c = candidates(app);
        return c.isEmpty() ? null : c.get(0);
    }

    /** The exact screen to open: the user's choice, else the unit's hidden BT music screen. */
    public AppRepository.App appScreen() {
        AppRepository repo = AppRepository.get(app);
        String p = Prefs.str(app, Prefs.BT_PKG, null);
        if (p != null && p.contains("/")) {
            AppRepository.App a = repo.find(p);
            if (a != null) return a;
        }
        AppRepository.App hidden = repo.bluetoothMusic();
        if (hidden != null && (p == null || hidden.pkg.equals(p))) return hidden;
        return p == null ? null : repo.findPackage(p);
    }

    /** Launchable apps that look like a Bluetooth music app, best matches first. */
    public static List<String> candidates(Context c) {
        List<String> music = new ArrayList<>(), other = new ArrayList<>();
        // Hidden factory screens first (keys "package/class"), e.g. the unit's "Bluetooth Music".
        for (AppRepository.App a : AppRepository.get(c).all()) if (a.hidden) music.add(a.key());
        for (AppRepository.App a : AppRepository.get(c).all()) {
            if (a.hidden) continue;
            String hay = a.pkg + " " + a.label;
            if (!BT_APP.matcher(hay).matches() || NOT_BT.matcher(hay).matches()) continue;
            if (music.contains(a.pkg) || other.contains(a.pkg)) continue;
            String l = hay.toLowerCase(Locale.ROOT);
            if (l.contains("music") || l.contains("audio") || l.contains("音乐")) music.add(a.pkg);
            else other.add(a.pkg);
        }
        music.addAll(other);
        return music;
    }

    public boolean isBluetoothPackage(String pkg) {
        return pkg != null && (pkg.equals(SYSTEM_PKG) || pkg.equals(appPackage()));
    }

    /** Sends a media key to the Bluetooth app, which forwards it to the phone over AVRCP. */
    public void sendKey(int keyCode) {
        String pkg = appPackage();
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        if (pkg != null) {
            for (KeyEvent e : new KeyEvent[]{down, up}) {
                Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                        .putExtra(Intent.EXTRA_KEY_EVENT, e);
                try {
                    app.sendBroadcast(i);
                } catch (Exception ignored) {
                }
            }
        }
        if (pkg == null || Prefs.bool(app, Prefs.BT_SYSTEM_KEYS, false)) {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(down);
            am.dispatchMediaKeyEvent(up);
        }
    }

    public boolean openApp() {
        AppRepository.App screen = appScreen();
        if (screen != null && AppRepository.launch(app, screen)) return true;
        String pkg = appPackage();
        if (pkg != null && AppRepository.launchPackage(app, pkg)) return true;
        Ui.toast(app, "Choose your Bluetooth music app in Settings → Media");
        return false;
    }

    private boolean installed(String pkg) {
        try {
            app.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
