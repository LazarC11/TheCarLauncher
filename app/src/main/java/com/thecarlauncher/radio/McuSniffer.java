package com.thecarlauncher.radio;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records MCU packets other than the tuner's, to find where this unit's Bluetooth module
 * reports the playing song (title / artist often travel through the MCU on QF units).
 * Keeps the latest packet per type plus any readable text found inside.
 */
public final class McuSniffer {
    private static final McuSniffer INSTANCE = new McuSniffer();
    private final Map<Integer, String> latest = new LinkedHashMap<>();
    private final Map<Integer, Integer> counts = new LinkedHashMap<>();
    private final List<String> texts = new ArrayList<>();

    public static McuSniffer get() { return INSTANCE; }

    synchronized void record(byte[] d) {
        if (d == null || d.length == 0) return;
        int type = d[0] & 0xFF;
        if (type >= 0xB0 && type <= 0xB7) return; // tuner: already understood
        Integer c = counts.get(type);
        counts.put(type, c == null ? 1 : c + 1);
        latest.put(type, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + hex(d, 24));
        for (String t : strings(d)) {
            String line = String.format(Locale.US, "%02X: %s", type, t);
            texts.remove(line);
            texts.add(0, line);
            while (texts.size() > 12) texts.remove(texts.size() - 1);
        }
    }

    public synchronized List<String> summary() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : latest.entrySet()) {
            out.add(String.format(Locale.US, "0x%02X ×%d  %s", e.getKey(), counts.get(e.getKey()), e.getValue()));
        }
        return out;
    }

    /** Readable strings (UTF-8 or UTF-16LE, 3+ letters) found in the packets. */
    public synchronized List<String> texts() { return new ArrayList<>(texts); }

    private static String hex(byte[] d, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(d.length, max); i++) sb.append(String.format(Locale.US, "%02X ", d[i] & 0xFF));
        if (d.length > max) sb.append("… (").append(d.length).append(')');
        return sb.toString().trim();
    }

    private static List<String> strings(byte[] d) {
        List<String> out = new ArrayList<>();
        try {
            for (String part : new String(d, 1, d.length - 1, "UTF-8").split("[\\x00-\\x1F\\uFFFD]+")) {
                if (part.replaceAll("[^\\p{L}]", "").length() >= 3) out.add(part.trim());
            }
            if (d.length > 6) {
                for (int off = 1; off <= 2; off++) {
                    String u = new String(d, off, (d.length - off) & ~1, "UTF-16LE");
                    for (String part : u.split("[\\x00-\\x1F\\uFFFD]+")) {
                        String p = part.trim();
                        if (p.replaceAll("[^\\p{L}]", "").length() >= 3 && p.matches("[\\p{IsLatin}\\p{IsCyrillic}\\p{N}\\p{P}\\p{Zs}]+")
                                && !out.contains(p)) out.add(p + "  (UTF-16)");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
