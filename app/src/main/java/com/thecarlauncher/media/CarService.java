package com.thecarlauncher.media;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

/**
 * The "car" system service of units with the android.car firmware
 * ({@code android.car.server.ICarService}). Everything goes through two calls: a named request
 * with a Bundle ("request.radio.seek_next", "request.app.enter_app", ...) and the data manager
 * ("request.dm.get_value" for keys such as "state.app.cur_source" or "state.bt.avrcp_info").
 * The binder is spoken to directly, so no firmware classes are needed; nothing is sent unless the
 * service really is ICarService.
 */
public final class CarService {
    private static final String SERVICE = "car";
    private static final String DESCRIPTOR = "android.car.server.ICarService";
    private static final int TX_REQUEST_SYNC = 5, TX_REQUEST_ONEWAY = 6;

    /** Data manager value types (CarDataManager.DataType ordinals). */
    public static final int T_INT = 2, T_STRING = 6, T_BUNDLE = 7, T_STRING_ARRAY = 15;

    /** Source ids ("app ids") of the car service. */
    public static final int SRC_RADIO = 0x10000, SRC_BT = 0x10001, SRC_EMPTY = 0x10003, SRC_MUSIC = 0x10013;

    public static final String PKG_RADIO = "android.car.app.radio", PKG_BT = "android.car.app.bt";

    private static CarService sInstance;
    private final Context app;
    private IBinder binder;
    private String error;

    public static synchronized CarService get(Context c) {
        if (sInstance == null) sInstance = new CarService(c.getApplicationContext());
        return sInstance;
    }

    private CarService(Context c) {
        app = c;
    }

    public String error() { return error; }

    /** True when the unit has the car service (looked up again after it restarts). */
    public synchronized boolean available() {
        if (binder != null && binder.isBinderAlive()) return true;
        binder = null;
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, SERVICE);
            if (b == null) {
                error = "no \"car\" service";
                return false;
            }
            String d = b.getInterfaceDescriptor();
            if (!DESCRIPTOR.equals(d)) {
                error = "\"car\" is " + d;
                return false;
            }
            binder = b;
            error = null;
            return true;
        } catch (Throwable t) {
            error = t.getClass().getSimpleName();
            return false;
        }
    }

    /** A named request that answers; values come back in {@code b}. Returns the result code or -1. */
    public int request(String key, Bundle b) {
        if (!available()) return -1;
        if (b == null) b = new Bundle();
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            write(data, key, b);
            if (!binder.transact(TX_REQUEST_SYNC, data, reply, 0)) return -1;
            reply.readException();
            int r = reply.readInt();
            if (reply.readInt() != 0) b.readFromParcel(reply);
            return r;
        } catch (Throwable t) {
            error = key + ": " + t.getClass().getSimpleName();
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** A named request without an answer (radio and Bluetooth commands). */
    public void send(String key, Bundle b) {
        if (!available()) return;
        Parcel data = Parcel.obtain();
        try {
            write(data, key, b);
            binder.transact(TX_REQUEST_ONEWAY, data, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable t) {
            error = key + ": " + t.getClass().getSimpleName();
        } finally {
            data.recycle();
        }
    }

    public void send(String key) { send(key, null); }

    private static void write(Parcel p, String key, Bundle b) {
        p.writeInterfaceToken(DESCRIPTOR);
        p.writeString(key);
        p.writeInt(0);
        p.writeInt(0);
        p.writeString(null);
        if (b != null) {
            p.writeInt(1);
            b.writeToParcel(p, 0);
        } else {
            p.writeInt(0);
        }
        p.writeInt(0);
    }

    // ---- Data manager ------------------------------------------------------------------------

    /** The stored value of a key, or null. */
    public Object value(String key, int type) {
        Bundle b = new Bundle();
        b.putString("key", key);
        b.putInt("type", type);
        if (request("request.dm.get_value", b) < 0) return null;
        try {
            return b.get("value");
        } catch (Throwable t) {
            return null;
        }
    }

    public int getInt(String key, int def) {
        Object v = value(key, T_INT);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public String getString(String key) {
        Object v = value(key, T_STRING);
        if (v instanceof CharSequence) return v.toString();
        if (v instanceof byte[]) {
            try {
                return new String((byte[]) v, "UTF-8").trim();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public String[] getStringArray(String key) {
        Object v = value(key, T_STRING_ARRAY);
        return v instanceof String[] ? (String[]) v : null;
    }

    // ---- Sources -------------------------------------------------------------------------------

    /** The source the unit is playing (SRC_*), 0 when unknown. */
    public int currentSource() { return getInt("state.app.cur_source", 0); }

    /** Package of the app on screen, as the car service sees it. */
    public String currentAppPackage() { return getString("state.app.cur_app_pack"); }

    /** Makes a source current (the car service switches the audio to it and may open its app). */
    public int enterSource(int id) {
        Bundle b = new Bundle();
        b.putInt("app_id", id);
        b.putString("reason", app.getPackageName());
        return request("request.app.enter_app", b);
    }

    /** Leaves a source: the unit's sound goes back to Android media. */
    public int exitSource(int id) {
        Bundle b = new Bundle();
        b.putInt("app_id", id);
        b.putString("reason", app.getPackageName());
        return request("request.app.exit_app", b);
    }

    /**
     * The launcher's own player is about to play: a radio, Bluetooth or other unit source still
     * holding the speakers is left, so Android media is heard.
     */
    public void claimForMedia() {
        if (!available()) return;
        int cur = currentSource();
        // Source ids are 0x1xxxx; 0x10003 is "no source".
        if ((cur & 0xFFFF0000) == 0x10000 && cur != SRC_EMPTY && cur != SRC_MUSIC) exitSource(cur);
    }

    // ---- Radio ---------------------------------------------------------------------------------

    /** Current FM frequency in 10 kHz units (9220 = 92.20 MHz), 0 when not FM / unknown. */
    public int radioFreq() {
        int c = getInt("data.radio.composite_main_freq", 0);
        int band = (c >> 24) & 0xFF;
        return band == 0 || band == 1 ? c & 0xFFFF : 0;
    }

    public void radioTune(int freq) {
        Bundle b = new Bundle();
        b.putInt("band", 0);
        b.putInt("freq", freq);
        send("request.radio.set_main_freq", b);
    }

    public void radioOn(String request, boolean on) {
        Bundle b = new Bundle();
        b.putBoolean("on", on);
        send(request, b);
    }

    // ---- Bluetooth music -------------------------------------------------------------------------

    /** 0 idle, 1 playing, 2 paused, 3 stopped; -1 unknown. */
    public int btMusicState() { return getInt("state.bt.a2dp_state", -1); }

    /** {title, artist, ...} of the phone's song, or null. */
    public String[] btSong() { return getStringArray("state.bt.avrcp_info"); }

    /** A few lines for the device report. */
    public String describe() {
        if (!available()) return "  not available (" + error + ")\n";
        StringBuilder r = new StringBuilder();
        r.append("  cur_source=0x").append(Integer.toHexString(currentSource()))
                .append(" cur_app=").append(currentAppPackage()).append('\n');
        r.append("  radio freq=").append(radioFreq()).append(" ps=").append(getString("data.radio.rds_ps_text")).append('\n');
        String[] s = btSong();
        r.append("  bt a2dp_state=").append(btMusicState()).append(" hfp_state=").append(getInt("state.bt.hfp_state", -1))
                .append(" song=").append(s == null ? "null" : java.util.Arrays.toString(s)).append('\n');
        return r.toString();
    }
}
