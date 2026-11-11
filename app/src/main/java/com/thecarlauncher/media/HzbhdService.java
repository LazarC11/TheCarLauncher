package com.thecarlauncher.media;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import java.util.Locale;
import java.util.TreeSet;

/**
 * hzbhd firmware (Allwinner T3, com.hzbhd.* apps). Three of its system services are spoken to
 * directly, the same calls the factory apps make through their proxy classes:
 * <ul>
 *   <li>"hzbhd.radio" (IRadioManager): the tuner. Frequencies are MHz text ("92.20").</li>
 *   <li>"hzbhd.sourceswitch" (ISourceSwitchManager): who has the speakers. The factory radio asks
 *   for FM, its BT music screen for BTAUDIO, its Music app for NORMAL_SOURCE (Android sound).</li>
 *   <li>"hzbhd.bluetooth" (nForce UiServiceCommand): AVRCP controls, and a callback with the song,
 *   play state and position.</li>
 * </ul>
 * Each call checks the service's descriptor first, so nothing is sent to a service that is
 * something else; without a service the launcher keeps using the factory apps.
 */
public final class HzbhdService implements UnitBackend {
    private static final String RADIO = "hzbhd.radio", SWITCH = "hzbhd.sourceswitch", BT = "hzbhd.bluetooth";
    private static final String D_RADIO = "com.hzbhd.midwareproxy.radio.aidl.IRadioManager";
    private static final String D_SWITCH = "com.hzbhd.midwareproxy.binder.service.ISourceSwitchManager";
    private static final String D_BT = "nfore.android.bt.servicemanager.UiServiceCommand";
    private static final String D_BT_CB = "nfore.android.bt.servicemanager.UiA2dpCallback";

    // IRadioManager
    private static final int R_PS = 0x5, R_RT = 0x7, R_AUTO_STATUS = 0x8, R_SEEK_UP_STATUS = 0x9, R_SEEK_DOWN_STATUS = 0xa,
            R_AUTO_SEARCH = 0x15, R_BAND = 0x1a, R_RF_MODE = 0x1c, R_SET_FREQ = 0x1e, R_FREQ = 0x1f,
            R_SEEK_UP = 0x27, R_SEEK_DOWN = 0x28, R_VALID_LIST = 0x2f, R_PRESET_LIST = 0x31,
            R_RDS = 0x39, R_AF = 0x3d, R_FM1 = 0x6a;
    /** SearchType.SEARCH_ALL_BAND_FROM_MIN_FREQ. */
    private static final int SEARCH_ALL = 1;

    // ISourceSwitchManager
    private static final int S_REQUEST = 3, S_CURRENT = 0xb;
    /** channelManagerDef.MEDIA_SOURCE_ID ordinals. */
    static final int SRC_FM = 17, SRC_BTAUDIO = 20, SRC_NORMAL = 55;
    private static final int SEAT_FRONT = 0;

    // nForce UiServiceCommand
    private static final int B_REGISTER_SERVICE_CB = 0x1, B_SCAN = 0x6, B_PAIR = 0x7, B_UNPAIR = 0x8,
            B_PAIRED = 0xb, B_LOCAL_NAME = 0xd, B_SCANNING = 0x13, B_DISCONNECT_ALL = 0x19;
    private static final String D_SERVICE_CB = "nfore.android.bt.servicemanager.UiServiceCallback";
    // UiServiceCallback
    private static final int SC_PAIRED_LIST = 0x3, SC_FOUND = 0x5, SC_PAIRED = 0x6, SC_UNPAIRED = 0x7,
            SC_PAIR_FAILED = 0x8, SC_A2DP = 0xa, SC_HFP = 0xb, SC_FOUND_LIST = 0xe;
    private static final int B_SET_ENABLE = 0x3, B_REMOTE_NAME = 0xe, B_IS_ENABLED = 0x11, B_HFP_CONNECT = 0x1e,
            B_HFP_ADDR = 0x20, B_A2DP_CONNECT = 0x69;
    private static final int B_REGISTER_A2DP_AVRCP_CB = 0x67, B_A2DP_ADDR = 0x6b, B_PLAY = 0x73, B_PAUSE = 0x75,
            B_NEXT = 0x76, B_PREV = 0x77, B_GET_ELEMENTS = 0x83, B_GET_PLAY_STATUS = 0x84, B_A2DP_PLAYING = 0xd7;
    // UiA2dpCallback
    private static final int C_ELEMENTS = 0x9, C_PLAY_STATUS = 0xa, C_STATUS_CHANGED = 0xb, C_TRACK_CHANGED = 0xc,
            C_POS_CHANGED = 0xf, C_SONG_STATUS = 0x27;

    public static final String PKG_RADIO = "com.hzbhd.radio", PKG_BT = "com.hzbhd.bluetooth";

    private static HzbhdService sInstance;
    private final Context app;
    private IBinder radio, sw, bt;
    private String error;
    private boolean cbRegistered;

    // Song from the BT callback.
    private volatile String title, artist, album;
    private volatile long durMs, posMs = -1;
    private volatile int playStatus = -1;
    private volatile long songAt, askedAt;

    public static synchronized HzbhdService get(Context c) {
        if (sInstance == null) sInstance = new HzbhdService(c.getApplicationContext());
        return sInstance;
    }

    private HzbhdService(Context c) {
        app = c;
    }

    @Override
    public String error() { return error; }

    private IBinder lookup(String name, String descriptor) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, name);
            if (b == null) {
                error = "no \"" + name + "\" service";
                return null;
            }
            String d = b.getInterfaceDescriptor();
            if (!descriptor.equals(d)) {
                error = "\"" + name + "\" is " + d;
                return null;
            }
            return b;
        } catch (Throwable t) {
            error = name + ": " + t.getClass().getSimpleName();
            return null;
        }
    }

    private synchronized IBinder radio() {
        if (radio == null || !radio.isBinderAlive()) radio = lookup(RADIO, D_RADIO);
        return radio;
    }

    private synchronized IBinder sw() {
        if (sw == null || !sw.isBinderAlive()) sw = lookup(SWITCH, D_SWITCH);
        return sw;
    }

    private synchronized IBinder bt() {
        if (bt == null || !bt.isBinderAlive()) {
            bt = lookup(BT, D_BT);
            cbRegistered = false;
        }
        if (bt != null && !cbRegistered) {
            cbRegistered = true;
            Parcel d = Parcel.obtain(), r = Parcel.obtain();
            try {
                d.writeInterfaceToken(D_BT);
                d.writeStrongBinder(callback);
                bt.transact(B_REGISTER_A2DP_AVRCP_CB, d, r, 0);
                r.readException();
                d.recycle();
                r.recycle();
                d = Parcel.obtain();
                r = Parcel.obtain();
                d.writeInterfaceToken(D_BT);
                d.writeStrongBinder(serviceCallback);
                bt.transact(B_REGISTER_SERVICE_CB, d, r, 0);
                r.readException();
            } catch (Throwable t) {
                error = "bt callback: " + t.getClass().getSimpleName();
            } finally {
                d.recycle();
                r.recycle();
            }
        }
        return bt;
    }

    /**
     * The radio service is what makes this a usable unit backend; Bluetooth is checked on its
     * own (see {@link #btAvailable}).
     */
    @Override
    public boolean available() { return radio() != null; }

    public boolean btAvailable() { return bt() != null; }

    // ---- Raw calls -----------------------------------------------------------------------------

    private interface Args {
        void write(Parcel p);
    }

    private interface Reader<T> {
        T read(Parcel p);
    }

    private <T> T call(IBinder b, String descriptor, int code, Args args, Reader<T> reader, T def) {
        if (b == null) return def;
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(descriptor);
            if (args != null) args.write(d);
            if (!b.transact(code, d, r, 0)) return def;
            r.readException();
            return reader == null ? def : reader.read(r);
        } catch (Throwable t) {
            error = Integer.toHexString(code) + ": " + t.getClass().getSimpleName();
            return def;
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    private static final Reader<String> STRING = new Reader<String>() {
        @Override
        public String read(Parcel p) { return p.readString(); }
    };
    private static final Reader<Integer> INT = new Reader<Integer>() {
        @Override
        public Integer read(Parcel p) { return p.readInt(); }
    };
    private static final Reader<String[]> STRINGS = new Reader<String[]>() {
        @Override
        public String[] read(Parcel p) { return p.createStringArray(); }
    };

    private static Args ints(final int... v) {
        return new Args() {
            @Override
            public void write(Parcel p) {
                for (int i : v) p.writeInt(i);
            }
        };
    }

    private static Args str(final String s) {
        return new Args() {
            @Override
            public void write(Parcel p) { p.writeString(s); }
        };
    }

    private void radioCall(int code, Args a) { call(radio(), D_RADIO, code, a, null, null); }

    private String radioString(int code) { return call(radio(), D_RADIO, code, null, STRING, null); }

    private boolean radioBool(int code) { return call(radio(), D_RADIO, code, null, INT, 0) != 0; }

    // ---- Sources -------------------------------------------------------------------------------

    /** Asks the unit for an audio source (MEDIA_SOURCE_ID ordinal) on the front seat. */
    public boolean request(final int source) {
        return call(sw(), D_SWITCH, S_REQUEST, new Args() {
            @Override
            public void write(Parcel p) {
                p.writeInt(source);
                p.writeInt(SEAT_FRONT);
                p.writeInt(0); // no Bundle
            }
        }, INT, 0) != 0;
    }

    /** The source on the front seat (MEDIA_SOURCE_ID ordinal), -1 when unknown. */
    public int currentSource() {
        return call(sw(), D_SWITCH, S_CURRENT, ints(SEAT_FRONT), INT, -1);
    }

    @Override
    public void claimForMedia() {
        // Android sound is NORMAL_SOURCE (what the factory Music app asks for).
        if (sw() != null && currentSource() != SRC_NORMAL) request(SRC_NORMAL);
    }

    // ---- Radio ---------------------------------------------------------------------------------

    @Override
    public void enterRadio() {
        request(SRC_FM);
        String band = radioString(R_BAND);
        if (band != null && band.toUpperCase(Locale.US).startsWith("AM")) radioCall(R_FM1, null);
    }

    @Override
    public void exitRadio() {
        if (currentSource() == SRC_FM) request(SRC_NORMAL);
    }

    /** "92.20" (MHz) → 9220; AM (kHz) and garbage → 0. */
    static int parseFreq(String s) {
        if (s == null) return 0;
        try {
            float f = Float.parseFloat(s.trim().replace(',', '.'));
            if (f >= 64 && f <= 110) return Math.round(f * 100);
            if (f >= 6400 && f <= 11000) return Math.round(f); // already 10 kHz units
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    @Override
    public int radioFreq() { return parseFreq(radioString(R_FREQ)); }

    @Override
    public String radioName() { return clean(radioString(R_PS)); }

    @Override
    public String radioText() { return clean(radioString(R_RT)); }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.length() == 0 ? null : s;
    }

    @Override
    public boolean radioSeeking() {
        return radioBool(R_AUTO_STATUS) || radioBool(R_SEEK_UP_STATUS) || radioBool(R_SEEK_DOWN_STATUS);
    }

    @Override
    public void radioTune(int freq) {
        radioCall(R_SET_FREQ, str(String.format(Locale.US, "%.2f", freq / 100f)));
    }

    @Override
    public void radioSeek(boolean up) { radioCall(up ? R_SEEK_UP : R_SEEK_DOWN, null); }

    /** RF input mode: LOC 0, DX 1. */
    @Override
    public void radioLocal(boolean on) { radioCall(R_RF_MODE, ints(on ? 0 : 1)); }

    @Override
    public void radioRds(boolean on) {
        radioCall(R_RDS, ints(on ? 1 : 0));
        radioCall(R_AF, ints(on ? 1 : 0));
    }

    @Override
    public boolean radioSearch() {
        if (radio() == null) return false;
        call(radio(), D_RADIO, R_AUTO_SEARCH, ints(SEARCH_ALL), INT, 0);
        return true;
    }

    /** What the unit's search stored: the valid and preset lists of FM1–FM3. */
    @Override
    public int[] radioStations() {
        if (radio() == null) return null;
        TreeSet<Integer> set = new TreeSet<>();
        for (int band = 0; band <= 2; band++) {
            for (int code : new int[]{R_VALID_LIST, R_PRESET_LIST}) {
                String[] l = call(radio(), D_RADIO, code, ints(band), STRINGS, null);
                if (l == null) continue;
                for (String s : l) {
                    int f = parseFreq(s);
                    if (f > 0) set.add(f);
                }
            }
        }
        int[] out = new int[set.size()];
        int i = 0;
        for (int f : set) out[i++] = f;
        return out;
    }

    // ---- Bluetooth music -------------------------------------------------------------------------

    private String btAddr() {
        String a = call(bt(), D_BT, B_A2DP_ADDR, null, STRING, null);
        return a == null ? "" : a;
    }

    private void btCall(int code) {
        final String addr = btAddr();
        call(bt(), D_BT, code, str(addr), INT, 0);
    }

    @Override
    public void enterBluetooth() { request(SRC_BTAUDIO); }

    @Override
    public void exitBluetooth() {
        if (currentSource() == SRC_BTAUDIO) request(SRC_NORMAL);
    }

    /** Asks the module for the song and play status (answers arrive on the callback). */
    private void askSong() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (askedAt != 0 && now - askedAt < 3000) return;
        askedAt = now;
        btCall(B_GET_ELEMENTS);
        btCall(B_GET_PLAY_STATUS);
    }

    @Override
    public String[] btSong() {
        if (bt() == null) return null;
        askSong();
        String t = title;
        return t == null || t.length() == 0 ? null : new String[]{t, artist, album};
    }

    @Override
    public int btPlaying() {
        if (bt() == null) return -1;
        // AVRCP play status: 0 stopped, 1 playing, 2 paused, 3/4 seeking, 0xff error.
        int s = playStatus;
        if (s >= 0 && s != 0xff) return s == 1 || s == 3 || s == 4 ? 1 : 0;
        Integer p = call(bt(), D_BT, B_A2DP_PLAYING, null, INT, -1);
        return p < 0 ? -1 : p != 0 ? 1 : 0;
    }

    @Override
    public long btDurationMs() { return durMs; }

    @Override
    public long btPositionMs() { return posMs; }

    @Override
    public void btPlayPause() { btCall(btPlaying() == 1 ? B_PAUSE : B_PLAY); }

    @Override
    public void btPlay() {
        if (btPlaying() != 1) btCall(B_PLAY);
    }

    @Override
    public void btNext() { btCall(B_NEXT); }

    @Override
    public void btPrevious() { btCall(B_PREV); }

    @Override
    public void btPowerOn() {
        if (bt() == null || call(bt, D_BT, B_IS_ENABLED, null, INT, 1) != 0) return;
        call(bt, D_BT, B_SET_ENABLE, ints(1), INT, 0);
    }

    @Override
    public void btConnect(String address, String name) {
        if (address == null || bt() == null) return;
        address = colon(address);
        call(bt, D_BT, B_HFP_CONNECT, str(address), INT, 0);
        call(bt, D_BT, B_A2DP_CONNECT, str(address), INT, 0);
    }

    /** "AABBCCDDEEFF" → "AA:BB:CC:DD:EE:FF" (the module's form); other text unchanged. */
    static String colon(String a) {
        if (a == null) return null;
        String n = a.trim();
        if (n.length() != 12 || n.indexOf(':') >= 0) return n;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i += 2) b.append(i == 0 ? "" : ":").append(n, i, i + 2);
        return b.toString().toUpperCase(Locale.ROOT);
    }

    private static boolean realAddr(String a) {
        return a != null && a.length() >= 12 && !a.replace(":", "").matches("0+");
    }

    @Override
    public String[] btPhone() {
        if (bt() == null) return null;
        String a = call(bt, D_BT, B_HFP_ADDR, null, STRING, null);
        if (!realAddr(a)) a = btAddr();
        if (!realAddr(a)) return null;
        return new String[]{a, call(bt, D_BT, B_REMOTE_NAME, str(a), STRING, null)};
    }

    /** The factory apps are never opened here. */
    @Override
    public String appOnTop() { return null; }

    @Override
    public String radioPackage() { return PKG_RADIO; }

    @Override
    public String btPackage() { return PKG_BT; }

    // ---- Phones (the launcher's Bluetooth devices screen) -----------------------------------------

    /** Paired phones and search results from the module; called on a binder thread. */
    public interface PhoneListener {
        /** The module's paired list (addresses "AA:BB:..", names may be null). */
        void onPairedList(String[] addresses, String[] names);

        void onFound(String address, String name);

        /** A phone connected, disconnected, paired or was removed. */
        void onPhonesChanged();
    }

    private volatile PhoneListener phoneListener;

    public void setPhoneListener(PhoneListener l) { phoneListener = l; }

    /** Asks for the paired list (answer on {@link PhoneListener#onPairedList}). */
    public void requestPaired() { if (bt() != null) call(bt, D_BT, B_PAIRED, null, INT, 0); }

    public void scan(boolean on) { if (bt() != null) call(bt, D_BT, B_SCAN, ints(on ? 1 : 0), INT, 0); }

    public boolean scanning() { return bt() != null && call(bt, D_BT, B_SCANNING, null, INT, 0) != 0; }

    public void pair(String address) { if (bt() != null) call(bt, D_BT, B_PAIR, str(colon(address)), INT, 0); }

    public void unpair(String address) { if (bt() != null) call(bt, D_BT, B_UNPAIR, str(colon(address)), INT, 0); }

    public void disconnectAll() { if (bt() != null) call(bt, D_BT, B_DISCONNECT_ALL, null, INT, 0); }

    public String localName() { return bt() == null ? null : clean(call(bt, D_BT, B_LOCAL_NAME, null, STRING, null)); }

    /** UiServiceCallback: paired list, search results, connection changes. */
    private final Binder serviceCallback = new Binder() {
        {
            attachInterface(null, D_SERVICE_CB);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(D_SERVICE_CB);
                return true;
            }
            PhoneListener l = phoneListener;
            try {
                data.enforceInterface(D_SERVICE_CB);
                switch (code) {
                    case SC_PAIRED_LIST:
                    case SC_FOUND_LIST: {
                        data.readInt(); // count
                        String[] a = data.createStringArray(), n = data.createStringArray();
                        if (a == null || l == null) break;
                        if (code == SC_PAIRED_LIST) {
                            l.onPairedList(a, n);
                        } else {
                            for (int i = 0; i < a.length; i++) l.onFound(a[i], n != null && i < n.length ? n[i] : null);
                        }
                        break;
                    }
                    case SC_FOUND: {
                        String a = data.readString(), n = data.readString();
                        if (l != null && a != null) l.onFound(a, n);
                        break;
                    }
                    case SC_PAIRED:
                    case SC_UNPAIRED:
                    case SC_PAIR_FAILED:
                    case SC_A2DP:
                    case SC_HFP:
                        if (l != null) l.onPhonesChanged();
                        break;
                    default:
                        break;
                }
            } catch (Throwable ignored) {
            }
            if (reply != null && (flags & FLAG_ONEWAY) == 0) reply.writeNoException();
            return true;
        }
    };

    /** Receives the module's AVRCP answers (UiA2dpCallback, one-way calls). */
    private final Binder callback = new Binder() {
        {
            attachInterface(null, D_BT_CB);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(D_BT_CB);
                return true;
            }
            try {
                data.enforceInterface(D_BT_CB);
                data.readString(); // device address
                switch (code) {
                    case C_ELEMENTS: {
                        // AVRCP attribute ids: 1 title, 2 artist, 3 album, 7 playing time (ms).
                        int[] ids = data.createIntArray();
                        String[] vals = data.createStringArray();
                        if (ids != null && vals != null) {
                            for (int i = 0; i < ids.length && i < vals.length; i++) {
                                String v = vals[i];
                                if (ids[i] == 1) title = v;
                                else if (ids[i] == 2) artist = v;
                                else if (ids[i] == 3) album = v;
                                else if (ids[i] == 7) durMs = parseLong(v);
                            }
                            songAt = android.os.SystemClock.elapsedRealtime();
                        }
                        break;
                    }
                    case C_PLAY_STATUS: {
                        // nForce order: song length, position (ms); swapped if they look reversed.
                        long len = data.readLong(), pos = data.readLong();
                        if (len > 0 && pos > len) {
                            long t = len;
                            len = pos;
                            pos = t;
                        }
                        int st = data.readByte() & 0xff;
                        if (len > 0) durMs = len;
                        posMs = pos;
                        playStatus = st;
                        break;
                    }
                    case C_STATUS_CHANGED:
                        playStatus = data.readByte() & 0xff;
                        break;
                    case C_POS_CHANGED:
                        posMs = data.readLong();
                        break;
                    case C_TRACK_CHANGED:
                        askedAt = 0; // the next poll asks for the new song
                        break;
                    case C_SONG_STATUS: {
                        // hzbhd extension: artist, album, title.
                        String a = data.readString(), al = data.readString(), t = data.readString();
                        if (t != null && t.length() > 0) title = t;
                        if (a != null) artist = a;
                        if (al != null) album = al;
                        break;
                    }
                    default:
                        break;
                }
            } catch (Throwable ignored) {
            }
            // Calls that expect an answer (none here) get an empty one.
            if (reply != null && (flags & FLAG_ONEWAY) == 0) reply.writeNoException();
            return true;
        }
    };

    private static long parseLong(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A few lines for the device report. */
    @Override
    public String describe() {
        StringBuilder r = new StringBuilder();
        r.append("  radio: ").append(radio() != null ? "ok" : error).append('\n');
        if (radio != null) {
            r.append("    band=").append(radioString(R_BAND)).append(" freq=").append(radioString(R_FREQ))
                    .append(" ps=").append(radioName()).append('\n');
            int[] st = radioStations();
            r.append("    stations=").append(st == null ? "null" : java.util.Arrays.toString(st)).append('\n');
        }
        r.append("  sourceswitch: ").append(sw() != null ? "ok, current=" + currentSource() : error).append('\n');
        r.append("  bluetooth: ").append(bt() != null ? "ok" : error).append('\n');
        if (bt != null) {
            r.append("    a2dp addr=").append(btAddr()).append(" playing=").append(btPlaying())
                    .append(" song=").append(title).append(" / ").append(artist).append('\n');
        }
        return r.toString();
    }
}
