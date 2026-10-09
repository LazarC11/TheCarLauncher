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
import java.net.InetSocketAddress;
import java.net.Socket;
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
    private String endpoint, status = "—";
    private final List<String> lines = new ArrayList<>();
    // Song state
    private String title, artist, album;
    private int playing = -1;
    private long posMs = -1, lenMs = -1, posAt;
    private int signal = -1, battery = -1;
    private long updated;
    private volatile long received;
    private final StringBuilder rawHead = new StringBuilder();
    private volatile String lastError = "—";

    public static synchronized GocBluetooth get(Context c) {
        if (sInstance == null) sInstance = new GocBluetooth(c.getApplicationContext());
        return sInstance;
    }

    private GocBluetooth(Context c) { app = c; }

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
            byte[] buf = new byte[1024];
            byte[] line = new byte[2048];
            int n = 0;
            long lastAsk = System.currentTimeMillis();
            try {
                while (running) {
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

    private static final class Conn {
        InputStream in;
        OutputStream out;
        Socket tcp;
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

    /** Endpoints seen on Goodocom firmwares, most common first. */
    private Conn open() {
        Conn c = new Conn();
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", 3152), 4000);
            s.setSoTimeout(6000);
            c.tcp = s;
            c.in = s.getInputStream();
            c.out = s.getOutputStream();
            endpoint = "tcp 3152";
            return c;
        } catch (Exception ignored) {
        }
        File serial = new File("/dev/goc_serial");
        if (serial.canRead() && serial.canWrite()) {
            try {
                c.in = new FileInputStream(serial);
                c.out = new FileOutputStream(serial);
                endpoint = "/dev/goc_serial";
                return c;
            } catch (Exception ignored) {
            }
        }
        // Only the command channel. goc_control / goc_data carry the factory player's audio
        // stream; reading them could take data away from it.
        for (String name : new String[]{"goc_serial"}) {
            for (LocalSocketAddress.Namespace ns : new LocalSocketAddress.Namespace[]{
                    LocalSocketAddress.Namespace.RESERVED, LocalSocketAddress.Namespace.FILESYSTEM}) {
                try {
                    LocalSocket ls = new LocalSocket();
                    ls.connect(new LocalSocketAddress(ns == LocalSocketAddress.Namespace.FILESYSTEM ? "/dev/socket/" + name : name, ns));
                    ls.setSoTimeout(6000);
                    c.local = ls;
                    c.in = ls.getInputStream();
                    c.out = ls.getOutputStream();
                    endpoint = name;
                    return c;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
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
        synchronized (lines) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < f.size(); i++) sb.append(i == 0 ? "" : " | ").append(f.get(i));
            lines.add(0, sb.length() > 70 ? sb.substring(0, 70) + "…" : sb.toString());
            while (lines.size() > 10) lines.remove(lines.size() - 1);
        }
        if (first.length() < 2) return;
        String code = first.substring(0, 2);
        boolean changed = false;
        if ("MI".equals(code)) {
            List<String> v = new ArrayList<>(f);
            v.set(0, first.substring(2));
            if (v.size() >= 2) {
                title = v.get(0).trim();
                artist = v.get(1).trim();
                album = v.size() >= 6 ? v.get(2).trim() : null;
                // Numeric tail: the first number is usually the length in ms.
                try {
                    int idx = v.size() >= 6 ? 3 : 2;
                    if (v.size() > idx) {
                        long l = Long.parseLong(v.get(idx).trim());
                        if (l > 0) lenMs = l < 36000 ? l * 1000 : l;
                    }
                } catch (Exception ignored) {
                }
                updated = System.currentTimeMillis();
                changed = true;
            }
        } else if ("MB".equals(code) && first.length() == 2) {
            playing = 1;
            changed = true;
        } else if ("MA".equals(code) && first.length() == 2) {
            playing = 0;
            changed = true;
        } else if ("MP".equals(code) && first.length() >= 10) {
            try {
                posMs = Integer.parseInt(first.substring(2, 6), 16) * 1000L;
                long l = Integer.parseInt(first.substring(6, 10), 16) * 1000L;
                if (l > 0) lenMs = l;
                posAt = System.currentTimeMillis();
                changed = true;
            } catch (Exception ignored) {
            }
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
                    PhoneStatus.get(app).onGoc(signal, battery);
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

    public long positionMs() {
        if (posMs < 0) return -1;
        long p = posMs + (playing == 1 ? System.currentTimeMillis() - posAt : 0);
        return lenMs > 0 ? Math.min(p, lenMs) : p;
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
