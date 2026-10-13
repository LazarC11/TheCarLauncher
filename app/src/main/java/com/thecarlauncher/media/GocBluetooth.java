package com.thecarlauncher.media;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Talks to the head unit's Goodocom Bluetooth daemon ("gocsdk"), which runs the phone link on
 * units with their own Bluetooth module (the phone is not on Android's Bluetooth there).
 *
 * Protocol (Goodocom AT style): commands "AT#" + code + CR LF; replies are lines ending in CR,
 * fields separated by the byte 0xFF. Used here:
 *   MK  ask for the playing song   → MI title FF artist FF [album FF] n FF n FF n
 *   VI  ask for the play state      → MB playing / MA stopped
 *   MA / MD / ME / MS / MJ          play-pause / next / previous / play / pause
 *   MP  position: 4 hex digits pos + 4 hex digits length (seconds)
 *   PS  phone signal (2) + battery (2)
 */
public final class GocBluetooth {
    private static GocBluetooth sInstance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile OutputStream out;
    private volatile boolean running;
    private String endpoint, status = "isključeno";
    private final List<String> lines = new ArrayList<>();
    // Song state
    private String title, artist, album;
    private int playing = -1;
    private long posMs = -1, lenMs = -1, posAt;
    private volatile String lastMi, lastMp;
    /** Last song broadcast / progress update from the factory BT app (it owns the serial line). */
    private volatile long appFedAt;
    private volatile String appSong;
    private volatile int appSongs;
    private volatile long serialPosAt;
    private volatile boolean phoneGone;
    private int signal = -1, battery = -1;
    private long updated;
    private volatile long received;
    private final StringBuilder rawHead = new StringBuilder();
    private volatile String lastError = "—";

    public static synchronized GocBluetooth get(Context c) {
        if (sInstance == null) sInstance = new GocBluetooth(c.getApplicationContext());
        return sInstance;
    }

    private GocBluetooth(Context c) {
        app = c;
        // The factory BT app reads the same serial line and may take the module's song lines
        // before we see them. It republishes the song as a broadcast and the progress as a
        // system property, so both are read as well.
        try {
            android.content.IntentFilter f = new android.content.IntentFilter("com.qf.action.BT.MUSIC.INFO");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                c.registerReceiver(appInfo, f, Context.RECEIVER_EXPORTED);
            } else {
                c.registerReceiver(appInfo, f);
            }
        } catch (Exception ignored) {
        }
        main.postDelayed(progressPoll, 1000);
    }

    private final android.content.BroadcastReceiver appInfo = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context c, android.content.Intent i) {
            String t = i.getStringExtra("songName");
            if (t == null || t.trim().isEmpty()) return;
            String a = i.getStringExtra("songSinger"), al = i.getStringExtra("songAlbum");
            synchronized (GocBluetooth.this) {
                if (!t.trim().equals(title)) {
                    posMs = 0;
                    posAt = System.currentTimeMillis();
                    lenMs = -1;
                }
                title = t.trim();
                artist = a == null ? "" : a.trim();
                album = al == null || al.trim().isEmpty() ? null : al.trim();
                updated = System.currentTimeMillis();
            }
            appFedAt = System.currentTimeMillis();
            appSong = t.trim() + (a != null && !a.trim().isEmpty() ? " — " + a.trim() : "");
            appSongs++;
            MediaHub.get(app).radioChanged();
        }
    };

    /**
     * The factory BT app is a persistent system app that reads the same serial line, so our own
     * reads only get pieces of its lines. While it publishes the song itself, its data wins.
     */
    /** Raw module lines ("MX0A1B2C3D4E5F6Phone"), for the device list and search. */
    public interface LineListener { void onLine(String line); }

    private final List<LineListener> lineListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile long listenUntil;

    public void addLineListener(LineListener l) { if (!lineListeners.contains(l)) lineListeners.add(l); }

    public void removeLineListener(LineListener l) { lineListeners.remove(l); }

    private void dispatch(String line) {
        for (LineListener l : lineListeners) {
            try {
                l.onLine(line);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Reads the module's lines for a short while (device list, search) even when the line is
     * otherwise left to the factory BT app. Short windows only: the app misses what we read.
     */
    public void listen(long ms) {
        final long until = System.currentTimeMillis() + ms;
        if (until <= listenUntil) return;
        boolean running = listenUntil > System.currentTimeMillis();
        listenUntil = until;
        if (running || enabled()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                java.io.FileInputStream in = null;
                try {
                    in = new java.io.FileInputStream("/dev/goc_serial");
                    byte[] buf = new byte[1024];
                    byte[] line = new byte[2048];
                    int n = 0;
                    while (System.currentTimeMillis() < listenUntil) {
                        int r = in.read(buf);
                        if (r < 0) break;
                        for (int i = 0; i < r; i++) {
                            byte b = buf[i];
                            if (b == '\n') continue;
                            if (b == '\r') {
                                if (n > 0) dispatch(decode(line, n));
                                n = 0;
                            } else if (n < line.length) {
                                line[n++] = b;
                            }
                        }
                    }
                } catch (Exception e) {
                    lastError = "listen: " + e.getClass().getSimpleName();
                } finally {
                    try {
                        if (in != null) in.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "goc-listen").start();
        // A harmless question at the end wakes the reader so it can stop.
        main.postDelayed(new Runnable() {
            public void run() {
                if (System.currentTimeMillis() >= listenUntil) send("CY");
            }
        }, ms + 100);
    }

    private static String decode(byte[] b, int n) {
        try {
            for (int i = 0; i < n; i++) if ((b[i] & 0xFF) == 0xFF) b[i] = '|';
            return new String(b, 0, n, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean appFeeds() { return appFedAt > 0 && System.currentTimeMillis() - appFedAt < 30_000; }

    private String lastProgress = "";
    private String lastConnect;
    private long progressChangedAt;

    /** "position,length" in ms, published by the factory BT app while a song plays. */
    private final Runnable progressPoll = new Runnable() {
        @Override
        public void run() {
            main.postDelayed(this, 1000);
            String v = prop("sys.qf.bt.music.progress");
            long now = System.currentTimeMillis();
            // The BT app's own connection flag: phone gone → no song; phone back → BT audio.
            String conn = prop("sys.qf.bt.is.connect");
            if ("true".equals(conn) && !"true".equals(lastConnect)) {
                // Also when the phone is already connected as the launcher starts.
                main.postDelayed(new Runnable() {
                    public void run() { MediaHub.get(app).onPhoneConnected(); }
                }, 1500);
            }
            if (conn != null) lastConnect = conn;
            if ("false".equals(conn) && title != null) {
                synchronized (GocBluetooth.this) {
                    title = artist = album = null;
                    posMs = lenMs = -1;
                    playing = 0;
                }
                MediaHub.get(app).radioChanged();
            }
            if (v == null) return;
            if (v.isEmpty()) {
                // The BT app clears it when the phone disconnects: forget the song too.
                if (!lastProgress.isEmpty()) {
                    lastProgress = "";
                    synchronized (GocBluetooth.this) {
                        title = artist = album = null;
                        posMs = lenMs = -1;
                        playing = 0;
                    }
                    MediaHub.get(app).radioChanged();
                }
                return;
            }
            if (!v.equals(lastProgress)) {
                lastProgress = v;
                progressChangedAt = now;
                appFedAt = now;
                String[] pl = v.split(",");
                if (pl.length == 2) {
                    long p = number(pl[0]), l = songLength(number(pl[1]));
                    synchronized (GocBluetooth.this) {
                        if (p >= 0) {
                            posMs = p;
                            posAt = now;
                        }
                        if (l > 0) lenMs = l;
                        playing = 1;
                    }
                    MediaHub.get(app).radioChanged();
                }
            } else if (playing == 1 && progressChangedAt > 0 && now - progressChangedAt > 3000
                    && now - serialPosAt > 3000) {
                // No progress for a while and no module lines of our own: treat as paused.
                freezePosition();
                playing = 0;
                MediaHub.get(app).radioChanged();
            }
        }
    };

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Starts the background connection (no-op on units without the daemon). */
    public synchronized void start() {
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "goc-bt").start();
    }

    private void loop() {
        int fails = 0;
        while (running) {
            if (!enabled()) {
                writeOnly();
                continue;
            }
            Conn c = open();
            if (c == null) {
                if (++fails > 5) {
                    status = "nije pronađen";
                    running = false;
                    return;
                }
                sleep(4000);
                continue;
            }
            fails = 0;
            out = c.out;
            status = "povezan: " + endpoint;
            send("MK");
            send("VI");
            final Conn cur = c;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running && out == cur.out) {
                        sleep(5000);
                        if (out == cur.out) send("MK");
                    }
                }
            }, "goc-ask").start();
            byte[] buf = new byte[1024];
            byte[] line = new byte[2048];
            int n = 0;
            long lastAsk = System.currentTimeMillis();
            try {
                while (running && enabled()) {
                    int r;
                    try {
                        r = c.in.read(buf);
                    } catch (java.net.SocketTimeoutException quiet) {
                        // The daemon only talks when something happens: silence is normal.
                        if (System.currentTimeMillis() - lastAsk > 5000) {
                            send("MK");
                            lastAsk = System.currentTimeMillis();
                        }
                        continue;
                    }
                    if (r < 0) {
                        lastError = "zatvorio servis (EOF)";
                        break;
                    }
                    received += r;
                    if (rawHead.length() < 120) {
                        for (int i = 0; i < r && rawHead.length() < 120; i++) rawHead.append(String.format(Locale.US, "%02X ", buf[i] & 0xFF));
                    }
                    for (int i = 0; i < r; i++) {
                        byte b = buf[i];
                        if (b == '\n') continue;
                        if (b == '\r') {
                            if (n > 0) handle(line, n);
                            n = 0;
                        } else if (n < line.length) {
                            line[n++] = b;
                        }
                    }
                    // Keep the song fresh even if the daemon only answers questions.
                    if (System.currentTimeMillis() - lastAsk > 5000) {
                        send("MK");
                        lastAsk = System.currentTimeMillis();
                    }
                }
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            }
            out = null;
            c.close();
            status = "veza prekinuta, ponovo… (" + endpoint + ")";
            sleep(3000);
        }
    }

    /** Only sends commands (song info requests); the factory BT app reads the answers. */
    private void writeOnly() {
        File serial = new File("/dev/goc_serial");
        OutputStream o = null;
        try {
            if (serial.canWrite()) o = new FileOutputStream(serial);
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName();
        }
        if (o == null) {
            status = "samo slanje: /dev/goc_serial nije dostupan";
            sleep(10000);
            return;
        }
        endpoint = "/dev/goc_serial";
        out = o;
        status = "samo slanje (liniju čita BT aplikacija)";
        while (running && !enabled() && out == o) {
            if (!send("MK")) break;
            sleep(5000);
        }
        if (out == o) out = null;
        try {
            o.close();
        } catch (Exception ignored) {
        }
    }

    /** True while we read the module's lines ourselves (not only write commands). */
    public boolean reading() { return out != null && enabled(); }

    private static final class Conn {
        InputStream in;
        OutputStream out;
        java.net.Socket tcp;
        LocalSocket local;

        void close() {
            try {
                if (tcp != null) tcp.close();
                if (local != null) local.close();
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * The Goodocom command channel: the virtual serial port /dev/goc_serial (or a goc_serial
     * socket). Port 3152 is NOT used: on this unit it speaks ZLink's binary protocol.
     */
    private Conn open() {
        Conn c = new Conn();
        File serial = new File("/dev/goc_serial");
        if (serial.canRead() && serial.canWrite()) {
            try {
                c.in = new FileInputStream(serial);
                c.out = new FileOutputStream(serial);
                endpoint = "/dev/goc_serial";
                return c;
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName();
            }
        }
        for (LocalSocketAddress.Namespace ns : new LocalSocketAddress.Namespace[]{
                LocalSocketAddress.Namespace.RESERVED, LocalSocketAddress.Namespace.FILESYSTEM}) {
            try {
                LocalSocket ls = new LocalSocket();
                ls.connect(new LocalSocketAddress(ns == LocalSocketAddress.Namespace.FILESYSTEM ? "/dev/socket/goc_serial" : "goc_serial", ns));
                ls.setSoTimeout(6000);
                c.local = ls;
                c.in = ls.getInputStream();
                c.out = ls.getOutputStream();
                endpoint = "goc_serial";
                return c;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Opt-in: reading the shared serial port may take messages away from the factory BT app. */
    public static final String PREF = "goc_read";

    /**
     * Reading the line: off by default when the factory BT app is installed. It is a persistent
     * system app that owns the line, and every byte we read is lost to it (it then thinks the
     * phone is gone, ignores play / next, and publishes no song). Commands are still written.
     */
    public boolean enabled() { return com.thecarlauncher.util.Prefs.bool(app, PREF, !factoryApp()); }

    private boolean factoryApp() {
        try {
            app.getPackageManager().getPackageInfo("com.qf.bluetooth", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        status = "isključeno";
        OutputStream o = out;
        out = null;
        try {
            if (o != null) o.close();
        } catch (Exception ignored) {
        }
    }

    /** Sends one command, e.g. "MK" → "AT#MK\r\n". */
    public boolean send(String code) {
        OutputStream o = out;
        if (o == null) return false;
        try {
            o.write(("AT#" + code + "\r\n").getBytes("US-ASCII"));
            o.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean connected() { return out != null; }

    private void handle(byte[] b, int n) {
        // Split on 0xFF; decode each field as UTF-8.
        List<String> f = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= n; i++) {
            if (i == n || (b[i] & 0xFF) == 0xFF) {
                try {
                    f.add(new String(b, start, i - start, "UTF-8"));
                } catch (Exception e) {
                    f.add("");
                }
                start = i + 1;
            }
        }
        String first = f.get(0);
        if (!lineListeners.isEmpty()) dispatch(decode(b.clone(), n));
        synchronized (lines) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < f.size(); i++) sb.append(i == 0 ? "" : " | ").append(f.get(i));
            lines.add(0, sb.length() > 70 ? sb.substring(0, 70) + "…" : sb.toString());
            // Full song lines for the diagnostics: the numeric tail decides the length.
            if (first.startsWith("MI")) {
                StringBuilder t = new StringBuilder();
                for (int i = 2; i < f.size(); i++) t.append(i == 2 ? "" : " | ").append(f.get(i));
                lastMi = t.toString();
            } else if (first.startsWith("MP") || first.startsWith("MT") || first.startsWith("ML")) {
                lastMp = sb.toString();
            }
            while (lines.size() > 10) lines.remove(lines.size() - 1);
        }
        if (first.length() < 2) return;
        String code = first.substring(0, 2);
        boolean changed = false;
        if ("MT".equals(code) || "MP".equals(code) || "ML".equals(code)) serialPosAt = System.currentTimeMillis();
        // Song and position lines may be fragments while the factory app shares the line.
        if (appFeeds() && ("MI".equals(code) || "MP".equals(code) || "MT".equals(code) || "ML".equals(code))) return;
        if ("MI".equals(code)) {
            List<String> v = new ArrayList<>(f);
            v.set(0, first.substring(2));
            if (v.size() >= 2) {
                String newTitle = v.get(0).trim();
                if (!newTitle.equals(title)) {
                    // New song: our own clock starts at 0:00 until the daemon reports a position.
                    posMs = 0;
                    posAt = System.currentTimeMillis();
                    lenMs = -1;
                }
                title = newTitle;
                artist = v.get(1).trim();
                album = v.size() >= 6 ? v.get(2).trim() : null;
                // Numeric tail (length, track no., total…): the largest value that reads as a
                // believable song length. Track numbers and odd values are skipped.
                long best = -1;
                // Only millisecond values count here: small numbers are track no. / total.
                for (int i = 2; i < v.size(); i++) {
                    long ms = number(v.get(i));
                    if (ms >= 10_000 && ms <= MAX_SONG) best = Math.max(best, ms);
                }
                if (best > 0) lenMs = best;
                updated = System.currentTimeMillis();
                changed = true;
            }
        } else if ("MB".equals(code) && first.length() == 2) {
            freezePosition();
            playing = 1;
            changed = true;
        } else if ("MA".equals(code) && first.length() == 2) {
            freezePosition();
            playing = 0;
            changed = true;
        } else if ("MP".equals(code)) {
            long[] pl = parsePosition(first.substring(2), f);
            if (pl != null) {
                if (pl[0] >= 0) {
                    posMs = pl[0];
                    posAt = System.currentTimeMillis();
                }
                long l = songLength(pl[1]);
                if (l > 0) lenMs = l;
                changed = true;
            }
        } else if (("MT".equals(code) || "ML".equals(code)) && first.length() > 2) {
            // This unit: "MT" + 8-digit position + 8-digit length, both decimal ms,
            // e.g. MT0001146900214000 = 0:11 of 3:34.
            long[] pl = parsePosition(first.substring(2), f);
            if (pl != null) {
                if (pl[0] >= 0 && pl[1] > 0) {
                    posMs = pl[0];
                    posAt = System.currentTimeMillis();
                }
                long l = songLength(pl[1]);
                if (l > 0) lenMs = l;
                changed = true;
            }
        } else if (("MY".equals(code) || "IA".equals(code)) && first.length() == 2) {
            // Phone gone (A2DP / HFP disconnected): forget the song until it plays again.
            title = artist = album = null;
            posMs = lenMs = -1;
            playing = 0;
            if ("IA".equals(code)) {
                signal = battery = -1;
                phoneGone = true;
            }
            changed = true;
        } else if ("IB".equals(code) && first.length() == 2) {
            // Phone connected (hands-free): BT audio becomes the source.
            main.postDelayed(new Runnable() {
                public void run() { MediaHub.get(app).onPhoneConnected(); }
            }, 1500);
        } else if ("PS".equals(code) && first.length() >= 6) {
            try {
                signal = Integer.parseInt(first.substring(2, 4));
                battery = Integer.parseInt(first.substring(4, 6));
                changed = true;
            } catch (Exception ignored) {
            }
        }
        if (changed) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    MediaHub.get(app).radioChanged();
                    if (phoneGone) {
                        phoneGone = false;
                        PhoneStatus.get(app).onGocGone();
                    } else {
                        PhoneStatus.get(app).onGoc(signal, battery);
                    }
                }
            });
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    // ---- State for the media screen ----------------------------------------------------------

    public boolean hasSong() { return title != null && title.length() > 0; }
    public String title() { return title; }
    public String artist() { return artist; }
    public String album() { return album; }
    /** 1 playing, 0 paused, -1 unknown. */
    public int playing() { return playing; }
    public long lengthMs() { return lenMs; }

    /**
     * Position / length in the formats seen on Goodocom firmwares:
     * "pppp" + "llll" (hex seconds), "pppppppp" + "llllllll" (hex ms), or two decimal fields
     * separated by 0xFF or a comma (ms, or seconds when small).
     */
    private static long[] parsePosition(String head, List<String> f) {
        try {
            String a = null, b = null;
            if (f.size() >= 2) {
                a = head;
                b = f.get(1);
            } else if (head.contains(",")) {
                a = head.substring(0, head.indexOf(','));
                b = head.substring(head.indexOf(',') + 1);
            }
            if (a != null) return new long[]{toMs(number(a)), toMs(number(b))};
            if (head.matches("[0-9A-Fa-f]{8}")) {
                return new long[]{Long.parseLong(head.substring(0, 4), 16) * 1000L, Long.parseLong(head.substring(4, 8), 16) * 1000L};
            }
            if (head.matches("\\d{16}")) {
                return new long[]{Long.parseLong(head.substring(0, 8)), Long.parseLong(head.substring(8, 16))};
            }
            if (head.matches("[0-9A-Fa-f]{16}")) {
                return new long[]{Long.parseLong(head.substring(0, 8), 16), Long.parseLong(head.substring(8, 16), 16)};
            }
            if (head.matches("\\d+")) return new long[]{toMs(Long.parseLong(head)), -1};
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final long MAX_SONG = 3 * 3600_000L;

    /**
     * A song length in ms, or -1 when the value cannot be one. Small values are seconds,
     * larger ones ms; anything over three hours is not a song length.
     */
    static long songLength(long v) {
        if (v <= 0) return -1;
        if (v < 36000) return v >= 10 ? v * 1000 : -1;
        if (v <= MAX_SONG) return v;
        return -1;
    }

    public String lastMi() { return lastMi; }

    /** Diagnostics: what the factory BT app last published. */
    public String appSongInfo() {
        if (appSong == null) return null;
        return appSong + "  (" + appSongs + "×, " + (System.currentTimeMillis() - appFedAt) / 1000 + " s)";
    }

    public String connectProp() { return prop("sys.qf.bt.is.connect"); }

    public String progressProp() {
        String v = prop("sys.qf.bt.music.progress");
        return v == null ? null : v;
    }
    public String lastMp() { return lastMp; }

    private static long number(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static long toMs(long v) {
        if (v < 0) return -1;
        return v < 36000 ? v * 1000 : v;
    }

    /** Song position: last reported (or song start) plus the time played since. */
    public long positionMs() {
        if (posMs < 0) return -1;
        long p = posMs + (playing != 0 ? System.currentTimeMillis() - posAt : 0);
        return lenMs > 0 ? Math.min(p, lenMs) : p;
    }

    /** Keeps the clock right across play / pause changes. */
    private void freezePosition() {
        if (posMs < 0) return;
        posMs = positionMs();
        posAt = System.currentTimeMillis();
    }

    /** Diagnostics: bytes received, first raw bytes, last error. */
    public String traffic() {
        return received + " B" + (rawHead.length() > 0 ? "  " + rawHead.toString().trim() : "") + "  · " + lastError;
    }

    public String serialInfo() {
        File serial = new File("/dev/goc_serial");
        return serial.exists() ? (serial.canRead() ? "r" : "-") + (serial.canWrite() ? "w" : "-") : "ne postoji";
    }

    public String status() {
        return status;
    }

    public List<String> lastLines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }
}
