package com.thecarlauncher.media;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Looks for the head unit's Bluetooth daemon (e.g. Goodocom "gocsdk" on 127.0.0.1:3152 or a
 * /dev/socket entry) and listens to what it says for a few seconds, so we can see whether the
 * song title travels there. Read-only: nothing is sent to the daemon.
 */
public final class BtProbe {
    private static final int[] PORTS = {3153, 3150, 8888, 9999}; // 3152 is ZLink's, left alone
    private static final String[] SOCKETS = {"zj_bt_socket", "gocsdk", "goc_bt", "gocsdk_socket", "bt_socket", "qf_bt",
            "qfbt", "btservice", "bluetooth"};

    private BtProbe() {}

    /** Runs the probe (blocking, call off the main thread); returns readable report lines. */
    public static List<String> run() {
        List<String> out = new ArrayList<>();
        // 1. Listening loopback TCP ports (may be hidden on Android 10+).
        List<Integer> listening = new ArrayList<>();
        for (String f : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
            try {
                BufferedReader r = new BufferedReader(new FileReader(f));
                String line;
                r.readLine();
                while ((line = r.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length > 3 && "0A".equals(p[3])) {
                        int port = Integer.parseInt(p[1].substring(p[1].lastIndexOf(':') + 1), 16);
                        if (!listening.contains(port)) listening.add(port);
                    }
                }
                r.close();
            } catch (Exception e) {
                out.add(f + ": " + e.getClass().getSimpleName());
            }
        }
        if (!listening.isEmpty()) out.add("TCP listen: " + listening);
        // 2. /dev/socket entries.
        String[] entries = new File("/dev/socket").list();
        out.add("/dev/socket: " + (entries == null ? "nedostupno" : java.util.Arrays.toString(entries)));
        // 3. Connect and listen.
        List<Integer> ports = new ArrayList<>();
        for (int p : PORTS) ports.add(p);
        for (int p : listening) if (!ports.contains(p) && p > 1024 && p < 20000 && p != 3152) ports.add(p);
        for (int port : ports) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress("127.0.0.1", port), 400);
                s.setSoTimeout(3000);
                out.add("TCP " + port + " ✓ " + read(s.getInputStream()));
            } catch (Exception e) {
                if (listening.contains(port)) out.add("TCP " + port + " ✗ " + e.getClass().getSimpleName());
            } finally {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }
        List<String> names = new ArrayList<>();
        for (String n : SOCKETS) names.add(n);
        if (entries != null) {
            for (String e : entries) {
                String l = e.toLowerCase(Locale.ROOT);
                if ((l.contains("bt") || l.contains("blue") || l.contains("goc") || l.contains("qf") || l.contains("zj"))
                        && !names.contains(e)) names.add(e);
            }
        }
        for (String n : names) {
            for (LocalSocketAddress.Namespace ns : new LocalSocketAddress.Namespace[]{
                    LocalSocketAddress.Namespace.FILESYSTEM, LocalSocketAddress.Namespace.RESERVED,
                    LocalSocketAddress.Namespace.ABSTRACT}) {
                LocalSocket ls = new LocalSocket();
                try {
                    String path = ns == LocalSocketAddress.Namespace.FILESYSTEM ? "/dev/socket/" + n : n;
                    ls.connect(new LocalSocketAddress(path, ns));
                    ls.setSoTimeout(3000);
                    out.add("socket " + n + " (" + ns.name().toLowerCase(Locale.ROOT) + ") ✓ " + read(ls.getInputStream()));
                    break;
                } catch (Exception ignored) {
                } finally {
                    try {
                        ls.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return out;
    }

    /** Up to 3 s / 600 bytes of whatever the daemon sends, as text with hex for control bytes. */
    private static String read(InputStream in) {
        byte[] buf = new byte[600];
        int n = 0;
        long end = System.currentTimeMillis() + 3000;
        try {
            while (n < buf.length && System.currentTimeMillis() < end) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
        } catch (Exception ignored) {
        }
        if (n == 0) return "(tišina)";
        String s;
        try {
            s = new String(buf, 0, n, "UTF-8");
        } catch (Exception e) {
            s = "";
        }
        StringBuilder sb = new StringBuilder(n + " B: ");
        for (char c : s.toCharArray()) {
            if (c >= 32 && c != 0xFFFD) sb.append(c);
            else sb.append(String.format(Locale.US, "[%02X]", (int) c & 0xFF));
            if (sb.length() > 400) break;
        }
        return sb.toString();
    }
}
