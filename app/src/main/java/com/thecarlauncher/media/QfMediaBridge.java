package com.thecarlauncher.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * QuickFish (QF001 / Roco K706) firmware: the factory launcher's music widget is fed by
 * com.qf.* broadcasts rather than media sessions. We listen to the same broadcasts so the
 * unit's own Bluetooth music shows its song here too. Extra names differ between firmware
 * builds, so several common spellings are accepted; the last broadcast is kept for diagnostics.
 */
public final class QfMediaBridge {
    public static final String[] ACTIONS = {
            "com.qf.action.UPDATE_MEDIA_INFO",
            "com.qf.action.UPDATE_MEDIA_STATE",
            "com.qf.musicplayer.action.UPDATE_ACTION",
            "com.qf.action.BT_STATE",
            "com.qf.action.BT_MUSIC_INFO",
            "com.qf.bluetooth.action.MUSIC_INFO",
    };
    private static QfMediaBridge sInstance;

    private final Context app;
    private String title, artist, album, source, last;
    private int playing = -1; // -1 unknown, 0 paused, 1 playing
    private int btState = -1;
    private long updated;

    public static synchronized QfMediaBridge get(Context c) {
        if (sInstance == null) sInstance = new QfMediaBridge(c.getApplicationContext());
        return sInstance;
    }

    private QfMediaBridge(Context c) {
        app = c;
        IntentFilter f = new IntentFilter();
        for (String a : ACTIONS) f.addAction(a);
        try {
            if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
            else c.registerReceiver(receiver, f);
        } catch (Exception ignored) {
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Bundle b = i.getExtras();
            last = describe(i.getAction(), b);
            if (b == null) return;
            if ("com.qf.action.BT_STATE".equals(i.getAction())) {
                btState = b.getInt("state", -1);
                com.thecarlauncher.radio.RadioController.get(app).learnBluetoothChannel();
                MediaHub.get(app).radioChanged();
                return;
            }
            String pkg = str(b, "packageName", "pkg", "app", "source", "from");
            String t = str(b, "title", "trackName", "name", "song", "songName", "musicName", "track");
            String ar = str(b, "artist", "artistName", "singer", "author");
            String al = str(b, "album", "albumName");
            int p = playState(b);
            // Ignore our own echoes and the OEM radio; keep everything else (Bluetooth, music).
            if (pkg != null && (pkg.equals(app.getPackageName()) || pkg.contains("radio"))) return;
            if (t != null) title = t;
            if (ar != null) artist = ar;
            if (al != null) album = al;
            if (p >= 0) playing = p;
            if (pkg != null) source = pkg;
            updated = System.currentTimeMillis();
            // The factory Bluetooth app is playing: note which MCU channel it uses.
            if (playing == 1 && (pkg == null || pkg.contains("bluetooth") || pkg.contains(".bt"))) {
                com.thecarlauncher.radio.RadioController.get(app).learnBluetoothChannel();
            }
            MediaHub.get(app).radioChanged(); // generic "media changed" notification
        }
    };

    private static String str(Bundle b, String... keys) {
        for (String k : keys) {
            Object v = b.get(k);
            if (v instanceof CharSequence && ((CharSequence) v).length() > 0) return v.toString();
        }
        return null;
    }

    private static int playState(Bundle b) {
        for (String k : new String[]{"isPlaying", "playing", "play"}) {
            Object v = b.get(k);
            if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        }
        for (String k : new String[]{"playState", "play_state", "state", "status"}) {
            Object v = b.get(k);
            if (v instanceof Integer) return ((Integer) v) == 1 || ((Integer) v) == 3 ? 1 : 0;
        }
        return -1;
    }

    private static String describe(String action, Bundle b) {
        StringBuilder sb = new StringBuilder(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()))
                .append("  ").append(action.replace("com.qf.", ""));
        if (b != null) {
            for (String k : b.keySet()) {
                Object v = b.get(k);
                String s = v instanceof byte[] || v instanceof android.graphics.Bitmap ? "<data>" : String.valueOf(v);
                if (s.length() > 40) s = s.substring(0, 40) + "…";
                sb.append("\n  ").append(k).append(" = ").append(s);
            }
        }
        return sb.toString();
    }

    /** Fresh info (last 10 minutes) from the unit's own broadcasts, else null. */
    public boolean fresh() { return title != null && System.currentTimeMillis() - updated < 600000; }
    public String title() { return fresh() ? title : null; }
    public String artist() { return fresh() ? artist : null; }
    public String album() { return fresh() ? album : null; }
    public String source() { return source; }
    /** 1 playing, 0 paused, -1 unknown. */
    public int playing() { return fresh() ? playing : -1; }
    public int btState() { return btState; }
    public String lastBroadcast() { return last; }
}
