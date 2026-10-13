package com.thecarlauncher.media;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.thecarlauncher.mib.LoadingBanner;
import com.thecarlauncher.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phones known to the unit's Bluetooth module, connecting to one, and pairing a new one, all
 * through the module's AT commands (the factory BT app keeps working alongside):
 * {@code AT#MX} paired list ("MX" + slot + 12-hex address + name), {@code AT#CC<addr>} connect,
 * {@code AT#CD} disconnect, {@code AT#SD}/{@code AT#ST} search start/stop ("SF" + type + address
 * + name per device, "SH" when done), {@code AT#DB<addr>} pair. Whether a phone is connected and
 * which one comes from the factory app's properties {@code sys.qf.bt.is.connect} and
 * {@code persist.sys.qf.bt.connect.addr}.
 */
public final class BtDevices {
    public static final class Device {
        public final String address;
        public String name;

        Device(String address, String name) {
            this.address = address;
            this.name = name;
        }

        public String label() { return name != null && !name.trim().isEmpty() ? name.trim() : pretty(address); }
    }

    public interface Listener { void onDevicesChanged(); }

    private static final String P_KNOWN = "bt_known_devices";
    private static final Pattern MX = Pattern.compile("MX(\\d)([0-9A-Fa-f]{12})(.*)");
    private static final Pattern SF = Pattern.compile("SF(\\d)([0-9A-Fa-f]{12})(.*)");

    private static BtDevices instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Device> known = new LinkedHashMap<>();
    private final Map<String, Device> found = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean searching;
    private String connecting;
    private long connectStarted;

    public static synchronized BtDevices get(Context c) {
        if (instance == null) instance = new BtDevices(c.getApplicationContext());
        return instance;
    }

    private BtDevices(Context c) {
        app = c;
        load();
        goc().addLineListener(new GocBluetooth.LineListener() {
            @Override
            public void onLine(final String line) {
                main.post(new Runnable() {
                    public void run() { parse(line); }
                });
            }
        });
    }

    private GocBluetooth goc() { return GocBluetooth.get(app); }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onDevicesChanged();
    }

    // ---- State ---------------------------------------------------------------------------------

    public static String norm(String a) {
        return a == null ? "" : a.replace(":", "").replace("-", "").trim().toUpperCase(Locale.ROOT);
    }

    static String pretty(String a) {
        String n = norm(a);
        if (n.length() != 12) return n;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i += 2) b.append(i == 0 ? "" : ":").append(n, i, i + 2);
        return b.toString();
    }

    /** False when the connection property cannot be read (then nothing is decided from it). */
    public boolean knowsState() {
        String v = prop("sys.qf.bt.is.connect");
        return v != null && !v.isEmpty();
    }

    public boolean isConnected() { return "true".equals(prop("sys.qf.bt.is.connect")); }

    /** Address of the connected phone, or null. */
    public String connectedAddress() {
        if (!isConnected()) return null;
        String a = norm(prop("persist.sys.qf.bt.connect.addr"));
        return a.length() == 12 && !a.equals("000000000000") ? a : null;
    }

    public String connecting() { return connecting; }

    public boolean searching() { return searching; }

    /** Remembered phones, the connected one first. */
    public List<Device> known() {
        String c = connectedAddress();
        if (c != null && (!known.containsKey(c) || known.get(c).name == null)) remember(c, connectedName());
        List<Device> l = new ArrayList<>(known.values());
        if (c != null) {
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i).address.equals(c)) {
                    l.add(0, l.remove(i));
                    break;
                }
            }
        }
        return l;
    }

    /** Name of the connected phone from the factory BT service (its "btBinderPool" service). */
    public String connectedName() {
        android.os.Parcel d = null, r = null;
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            android.os.IBinder pool = (android.os.IBinder) sm.getMethod("getService", String.class).invoke(null, "btBinderPool");
            if (pool == null) return null;
            d = android.os.Parcel.obtain();
            r = android.os.Parcel.obtain();
            d.writeInterfaceToken("com.qf.btsdk.IBTBinderPool");
            d.writeString("btSettingBinder");
            pool.transact(512, d, r, 0);
            r.readException();
            android.os.IBinder setting = r.readStrongBinder();
            d.recycle();
            r.recycle();
            d = r = null;
            if (setting == null) return null;
            d = android.os.Parcel.obtain();
            r = android.os.Parcel.obtain();
            d.writeInterfaceToken("com.qf.btsdk.IBTSettingBinderServer");
            setting.transact(2048, d, r, 0);
            r.readException();
            String n = r.readString();
            return n == null || n.trim().isEmpty() ? null : n.trim();
        } catch (Throwable e) {
            return null;
        } finally {
            if (d != null) d.recycle();
            if (r != null) r.recycle();
        }
    }

    /** Phones found by the last search that are not remembered yet. */
    public List<Device> found() {
        List<Device> l = new ArrayList<>();
        for (Device d : found.values()) if (!known.containsKey(d.address)) l.add(d);
        return l;
    }

    /** The unit's own Bluetooth name, as phones see it. */
    public String localName() {
        String n = prop("persist.sys.qf.bt.local.name");
        return n == null || n.isEmpty() ? null : n;
    }

    // ---- Module lines --------------------------------------------------------------------------

    private void parse(String line) {
        Matcher m = MX.matcher(line);
        if (m.matches()) {
            remember(m.group(2), clean(m.group(3)));
            changed();
            return;
        }
        m = SF.matcher(line);
        if (m.matches()) {
            String a = norm(m.group(2));
            found.put(a, new Device(a, clean(m.group(3))));
            changed();
            return;
        }
        if (line.startsWith("SH") && searching) {
            searching = false;
            LoadingBanner.stop("bt-search");
            changed();
        }
    }

    private static String clean(String n) {
        if (n == null) return null;
        n = n.replace("|", " ").trim();
        return n.isEmpty() ? null : n;
    }

    private void remember(String address, String name) {
        String a = norm(address);
        if (a.length() != 12) return;
        Device d = known.get(a);
        if (d == null) known.put(a, new Device(a, name));
        else if (name != null) d.name = name;
        save();
    }

    public void forget(String address) {
        String a = norm(address);
        known.remove(a);
        goc().send("CV" + a);
        save();
        changed();
    }

    // ---- Actions -------------------------------------------------------------------------------

    /** Asks the module for its paired list (a few times: we only see part of the line). */
    public void refresh() {
        goc().start();
        goc().listen(4000);
        for (int i = 0; i < 3; i++) {
            main.postDelayed(new Runnable() {
                public void run() { goc().send("MX"); }
            }, 150 + i * 1100L);
        }
    }

    public void search() {
        if (isConnected()) {
            LoadingBanner.flash("bt-search", LoadingBanner.t("Prvo odspoji telefon", "Disconnect the phone first"), 2500);
            return;
        }
        goc().start();
        found.clear();
        searching = true;
        changed();
        LoadingBanner.start("bt-search", LoadingBanner.t("Traženje uređaja…", "Searching for devices…"));
        goc().listen(16000);
        main.postDelayed(new Runnable() {
            public void run() { goc().send("SD"); }
        }, 150);
        main.postDelayed(new Runnable() {
            public void run() {
                if (!searching) return;
                goc().send("ST");
                searching = false;
                LoadingBanner.stop("bt-search");
                changed();
            }
        }, 15000);
    }

    public void connect(Device d) { connect(d, false); }

    /** Pairs a phone found by the search, then connects to it. */
    public void pair(Device d) { connect(d, true); }

    private void connect(final Device d, final boolean pair) {
        if (connecting != null) return;
        goc().start();
        if (searching) {
            goc().send("ST");
            searching = false;
            LoadingBanner.stop("bt-search");
        }
        final String cur = connectedAddress();
        if (d.address.equals(cur)) return;
        connecting = d.address;
        connectStarted = System.currentTimeMillis();
        changed();
        LoadingBanner.start("bt-connect", LoadingBanner.t((pair ? "Uparivanje sa " : "Povezivanje sa ") + d.label() + "…",
                (pair ? "Pairing with " : "Connecting to ") + d.label() + "…"));
        if (cur != null) goc().send("CD");
        main.postDelayed(new Runnable() {
            public void run() {
                if (pair) {
                    goc().send("DB" + d.address);
                    main.postDelayed(new Runnable() {
                        public void run() { if (!d.address.equals(connectedAddress())) goc().send("CC" + d.address); }
                    }, 6000);
                } else {
                    goc().send("CC" + d.address);
                }
            }
        }, cur != null ? 1500 : 100);
        main.postDelayed(watch, 1000);
    }

    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            if (connecting == null) return;
            String a = connectedAddress();
            if (connecting.equals(a)) {
                Device d = known.get(a);
                if (d == null) d = found.get(a);
                remember(a, d != null ? d.name : null);
                String label = known.get(a).label();
                connecting = null;
                LoadingBanner.stop("bt-connect");
                LoadingBanner.flash("bt-connected", LoadingBanner.t("Povezano: ", "Connected: ") + label, 2500);
                // BT audio becomes the source (done already when "BT audio on connect" is on).
                if (!Prefs.bool(app, Prefs.BT_AUTO_SELECT, true)) MediaHub.get(app).select(MediaHub.BLUETOOTH);
                changed();
                return;
            }
            if (System.currentTimeMillis() - connectStarted > 30000) {
                connecting = null;
                LoadingBanner.stop("bt-connect");
                LoadingBanner.flash("bt-connect-fail", LoadingBanner.t("Povezivanje nije uspelo — uključi Bluetooth na telefonu",
                        "Could not connect — turn on Bluetooth on the phone"), 3500);
                changed();
                return;
            }
            main.postDelayed(this, 700);
        }
    };

    public void disconnect() {
        goc().start();
        goc().send("CD");
        main.postDelayed(new Runnable() {
            public void run() { changed(); }
        }, 1500);
    }

    // ---- Storage -------------------------------------------------------------------------------

    private void load() {
        try {
            JSONArray a = new JSONArray(Prefs.str(app, P_KNOWN, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String ad = norm(o.optString("a"));
                if (ad.length() == 12) known.put(ad, new Device(ad, o.has("n") ? o.optString("n") : null));
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            JSONArray a = new JSONArray();
            for (Device d : known.values()) {
                JSONObject o = new JSONObject();
                o.put("a", d.address);
                if (d.name != null) o.put("n", d.name);
                a.put(o);
            }
            Prefs.put(app, P_KNOWN, a.toString());
        } catch (Exception ignored) {
        }
    }

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable e) {
            return null;
        }
    }
}
