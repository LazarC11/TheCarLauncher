package com.thecarlauncher.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * hcn firmware (MediaTek MT8163, com.hcn.* apps). The FM sound is made by the factory radio's
 * background service (it records the tuner input and plays it, which needs system rights), so the
 * radio is driven through that service's public API, the one it offers launcher widgets:
 * {@code com.hcn.autoradio.IRadioServiceAPI} on {@code com.hcn.autoradio/.service.FMPlugService}.
 * Station name, radio text and the stored stations are read from the firmware's "fmradio" service
 * ({@code android.radio.IRadioPlayer}); who has the speakers is the "sourceinfo" service
 * ({@code android.sourceservice.ISourceService}). Bluetooth music goes through the hcn Bluetooth
 * music service ({@code com.hcn.bluetooth.service.IBluetoothMusicService}), which switches the
 * A2DP sink's sound on and controls the phone.
 */
public final class HcnService implements UnitBackend {
    public static final String PKG_RADIO = "com.hcn.autoradio";
    private static final ComponentName FM_SERVICE = new ComponentName(PKG_RADIO, "com.hcn.autoradio.service.FMPlugService");
    private static final String FM_ACTION = "com.hcn.autoradio.FM_PLUG_SERVICE";
    private static final String D_API = "com.hcn.autoradio.IRadioServiceAPI";
    private static final String D_PLAYER = "android.radio.IRadioPlayer";
    private static final String D_SOURCE = "android.sourceservice.ISourceService";

    // IRadioServiceAPI (frequencies in kHz: 87500 = 87.5 MHz)
    private static final int A_REGISTER_CLIENT = 0x1, A_LOCDX = 0x8, A_SEEK_DOWN = 0x9, A_SEEK_UP = 0xa,
            A_AS = 0x6, A_GOTO = 0xe, A_FREQ = 0x12, A_IS_AS = 0x13, A_IS_SCAN = 0x15, A_IS_SEEK = 0x16,
            A_IS_LOCAL = 0x18, A_PLAY_AUDIO = 0x19;
    // IRadioPlayer ("fmradio")
    private static final int P_RADIO_INFO = 0xb, P_PRESET = 0xf, P_AF = 0x15;
    // ISourceService ("sourceinfo")
    private static final int S_REQUEST_PLAY = 0x3;

    // IBluetoothMusicService (com.hcn.bluetoothservice): it alone may switch the A2DP sink's audio
    // on (BluetoothA2dpSink.controlAudioFocus, system only), so BT audio goes through it.
    private static final ComponentName BT_SERVICE = new ComponentName("com.hcn.bluetoothservice",
            "com.hcn.bluetooth.service.BluetoothMusicService");
    private static final String BT_ACTION = "com.hcn.bluetooth.service.BluetoothMusicService";
    private static final String D_BT = "com.hcn.bluetooth.service.IBluetoothMusicService";
    private static final int B_A2DP_PLAYING = 0x3, B_CMD = 0x5, B_REQUEST_A2DP = 0x6, B_RELEASE_A2DP = 0x7,
            B_REGISTER_CLIENT = 0x8, B_ID3 = 0xc;
    /** send_Avrcp_Cmd: 0 play, 1 pause, 2 play/pause, 3 stop, 4 next, 5 previous. */
    private static final int CMD_PLAY = 0, CMD_PAUSE = 1, CMD_TOGGLE = 2, CMD_NEXT = 4, CMD_PREV = 5;
    public static final String PKG_BT = "com.autochips.bluetooth";

    private static HcnService sInstance;
    private final Context app;
    private String error;
    /** Our token for the services' "client binder" (they watch it for our death). */
    private final Binder token = new Binder();
    private final Remote radio, music;

    public static synchronized HcnService get(Context c) {
        if (sInstance == null) sInstance = new HcnService(c.getApplicationContext());
        return sInstance;
    }

    private HcnService(Context c) {
        app = c;
        radio = new Remote(FM_SERVICE, FM_ACTION, D_API, A_REGISTER_CLIENT);
        music = new Remote(BT_SERVICE, BT_ACTION, D_BT, B_REGISTER_CLIENT);
    }

    @Override
    public String error() { return error; }

    // ---- Factory services (bound like the launcher widgets they were written for) ---------------

    private final class Remote implements ServiceConnection {
        final ComponentName cn;
        final String action, descriptor;
        final int registerCode;
        volatile IBinder binder;
        volatile CountDownLatch latch;
        boolean binding;
        String err;

        Remote(ComponentName cn, String action, String descriptor, int registerCode) {
            this.cn = cn;
            this.action = action;
            this.descriptor = descriptor;
            this.registerCode = registerCode;
        }

        boolean installed() {
            try {
                return app.getPackageManager().getServiceInfo(cn, 0) != null;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            try {
                if (!descriptor.equals(b.getInterfaceDescriptor())) {
                    err = "is " + b.getInterfaceDescriptor();
                    return;
                }
            } catch (Throwable t) {
                err = t.getClass().getSimpleName();
                return;
            }
            binder = b;
            err = null;
            CountDownLatch l = latch;
            if (l != null) l.countDown();
            final IBinder bb = b;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    call(bb, descriptor, registerCode, new Args() {
                        @Override
                        public void write(Parcel p) { p.writeStrongBinder(token); }
                    }, null, null);
                }
            }, "hcn-register").start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }

        synchronized void bind() {
            if (binder != null || binding) return;
            binding = true;
            latch = new CountDownLatch(1);
            try {
                if (!app.bindService(new Intent(action).setComponent(cn), this, Context.BIND_AUTO_CREATE)) {
                    err = "bind refused";
                    binding = false;
                }
            } catch (Throwable t) {
                err = t.getClass().getSimpleName();
                binding = false;
            }
        }

        /** The service's binder, waiting up to 2 s for the binding (never on the main thread). */
        IBinder get() {
            IBinder b = binder;
            if (b != null && b.isBinderAlive()) return b;
            if (b != null) {
                synchronized (this) {
                    binder = null;
                    binding = false;
                }
            }
            bind();
            CountDownLatch l = latch;
            if (l != null && Looper.myLooper() != Looper.getMainLooper()) {
                try {
                    l.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }
            return binder;
        }

        /** Installed (the binding then starts in the background). */
        boolean available() {
            if (binder != null) return true;
            if (!installed()) {
                err = "not installed";
                return false;
            }
            bind();
            return true;
        }

        String state() {
            return binder != null ? "connected" : !installed() ? "not installed" : "not connected (" + err + ")";
        }
    }

    private IBinder api() { return radio.get(); }

    /** The factory radio service is installed (the launcher binds to it). */
    @Override
    public boolean available() {
        boolean ok = radio.available();
        if (!ok) error = "no " + PKG_RADIO;
        return ok;
    }

    /** The hcn Bluetooth music service is installed (BT audio goes through it). */
    public boolean btAvailable() { return music.available(); }

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

    private static final Reader<Integer> INT = new Reader<Integer>() {
        @Override
        public Integer read(Parcel p) { return p.readInt(); }
    };

    private static Args ints(final int... v) {
        return new Args() {
            @Override
            public void write(Parcel p) {
                for (int i : v) p.writeInt(i);
            }
        };
    }

    private void apiCall(int code, Args a) { call(api(), D_API, code, a, null, null); }

    private boolean apiBool(int code) { return call(api(), D_API, code, null, INT, 0) != 0; }

    private static IBinder service(String name, String descriptor) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, name);
            return b != null && descriptor.equals(b.getInterfaceDescriptor()) ? b : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private IBinder player() { return service("fmradio", D_PLAYER); }

    // ---- Sources -------------------------------------------------------------------------------

    /** The package the unit plays now ("sys.sourcing.package"), or null. */
    public static String sourcePackage() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String s = (String) sp.getMethod("get", String.class).invoke(null, "sys.sourcing.package");
            return s == null || s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** True while the factory radio is the unit's source. */
    public static boolean radioIsSource() { return PKG_RADIO.equals(sourcePackage()); }

    /** Tells the source service a package is about to play (what the factory apps do). */
    private void requestSource(final String pkg) {
        call(service("sourceinfo", D_SOURCE), D_SOURCE, S_REQUEST_PLAY, new Args() {
            @Override
            public void write(Parcel p) { p.writeString(pkg); }
        }, null, null);
    }

    @Override
    public void claimForMedia() {
        if (radioIsSource()) {
            requestSource(app.getPackageName());
        } else {
            String src = sourcePackage();
            if (src != null && src.startsWith(PKG_BT)) {
                call(bt(), D_BT, B_RELEASE_A2DP, null, null, null);
                requestSource(app.getPackageName());
            }
        }
    }

    // ---- Radio ---------------------------------------------------------------------------------

    /**
     * The radio service makes the radio the source and plays. After it lost the audio focus to
     * another player it stays muted until its own screen comes up again: then the screen is opened
     * briefly (RadioController brings the launcher back through {@link #appOnTop}).
     */
    @Override
    public void enterRadio() {
        call(api(), D_API, A_PLAY_AUDIO, null, INT, 0);
        android.media.AudioManager am = (android.media.AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        for (int i = 0; i < 8; i++) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return;
            }
            if (am != null && am.isMusicActive() && radioIsSource()) return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(PKG_RADIO, "com.hcn.autoradio.RadioMain"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            app.startActivity(i);
        } catch (Throwable t) {
            error = "radio screen: " + t.getClass().getSimpleName();
        }
    }

    /**
     * Stops the factory radio: it mutes itself and lets go of the audio when another player takes
     * the audio focus, so the focus is taken for a moment and given back.
     */
    @Override
    public void exitRadio() {
        if (!radioIsSource()) return;
        requestSource(app.getPackageName());
        try {
            android.media.AudioManager am = (android.media.AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            android.media.AudioManager.OnAudioFocusChangeListener l = new android.media.AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {}
            };
            am.requestAudioFocus(l, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN);
            am.abandonAudioFocus(l);
        } catch (Throwable t) {
            error = "focus: " + t.getClass().getSimpleName();
        }
    }

    /** kHz → 10 kHz units; 0 outside FM. */
    static int fromKhz(int khz) {
        if (khz >= 64000 && khz <= 110000) return Math.round(khz / 10f);
        if (khz >= 6400 && khz <= 11000) return khz; // already 10 kHz units
        return 0;
    }

    @Override
    public int radioFreq() { return fromKhz(call(api(), D_API, A_FREQ, null, INT, 0)); }

    /** {PS name, radio text} from fmradio's RadioInfo, or null. */
    private String[] rds() {
        return call(player(), D_PLAYER, P_RADIO_INFO, null, new Reader<String[]>() {
            @Override
            public String[] read(Parcel p) {
                if (p.readInt() == 0) return null;
                p.readInt(); // freq
                p.readInt(); // scan type
                p.readInt(); // stereo
                p.readInt(); // local
                p.readString(); // ui band
                p.readInt(); // index
                return new String[]{p.readString(), p.readString()};
            }
        }, null);
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.length() == 0 ? null : s;
    }

    @Override
    public String radioName() {
        String[] r = rds();
        return r == null ? null : clean(r[0]);
    }

    @Override
    public String radioText() {
        String[] r = rds();
        return r == null ? null : clean(r[1]);
    }

    @Override
    public boolean radioSeeking() { return apiBool(A_IS_AS) || apiBool(A_IS_SEEK) || apiBool(A_IS_SCAN); }

    @Override
    public void radioTune(int freq) { apiCall(A_GOTO, ints(freq * 10)); }

    @Override
    public void radioSeek(boolean up) { apiCall(up ? A_SEEK_UP : A_SEEK_DOWN, null); }

    /** The API only toggles LOC/DX: toggled when it differs. */
    @Override
    public void radioLocal(boolean on) {
        if (api() == null) return;
        if (apiBool(A_IS_LOCAL) != on) apiCall(A_LOCDX, null);
    }

    @Override
    public void radioRds(boolean on) { call(player(), D_PLAYER, P_AF, ints(on ? 1 : 0), null, null); }

    @Override
    public boolean radioSearch() {
        if (api() == null) return false;
        apiCall(A_AS, null);
        return true;
    }

    /** The FM presets the factory radio keeps (FM1–FM3), in 10 kHz units. */
    @Override
    public int[] radioStations() {
        IBinder p = player();
        if (p == null) return null;
        TreeSet<Integer> set = new TreeSet<>();
        for (int band = 0; band < 4; band++) { // BAND value: band << 8 | index; FM is band 0
            int[] l = call(p, D_PLAYER, P_PRESET, ints(band), new Reader<int[]>() {
                @Override
                public int[] read(Parcel r) { return r.createIntArray(); }
            }, null);
            if (l == null) continue;
            for (int f : l) {
                int v = fromKhz(f);
                if (v > 0) set.add(v);
            }
        }
        int[] out = new int[set.size()];
        int i = 0;
        for (int f : set) out[i++] = f;
        return out;
    }

    // ---- Bluetooth music: the hcn Bluetooth music service (A2DP sink + AVRCP) ------------------

    private IBinder bt() { return music.get(); }

    private void cmd(int c) { call(bt(), D_BT, B_CMD, ints(c), null, null); }

    /** BT audio becomes the source and the A2DP sink's sound is switched on. */
    @Override
    public void enterBluetooth() { call(bt(), D_BT, B_REQUEST_A2DP, null, null, null); }

    @Override
    public void exitBluetooth() {
        String src = sourcePackage();
        if (src != null && src.startsWith(PKG_BT)) call(bt(), D_BT, B_RELEASE_A2DP, null, null, null);
    }

    /** {title, artist, album, length} from the service's ID3 info, or null. */
    private String[] id3() {
        return call(bt(), D_BT, B_ID3, null, new Reader<String[]>() {
            @Override
            public String[] read(Parcel p) { return p.createStringArray(); }
        }, null);
    }

    @Override
    public String[] btSong() {
        String[] s = id3();
        if (s == null || s.length == 0 || clean(s[0]) == null) return null;
        return new String[]{s[0], s.length > 1 ? s[1] : null, s.length > 2 ? s[2] : null};
    }

    @Override
    public int btPlaying() {
        IBinder b = bt();
        if (b == null) return -1;
        return call(b, D_BT, B_A2DP_PLAYING, null, INT, -1) > 0 ? 1 : 0;
    }

    /** Index 3 of the ID3 info: the song length (ms, or seconds when small). */
    @Override
    public long btDurationMs() {
        String[] s = id3();
        if (s == null || s.length < 4 || s[3] == null) return 0;
        try {
            long v = Long.parseLong(s[3].trim());
            return v <= 0 ? 0 : v < 10000 ? v * 1000 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The service keeps no position: the launcher counts it. */
    @Override
    public long btPositionMs() { return -1; }

    @Override
    public void btPlayPause() { cmd(CMD_TOGGLE); }

    @Override
    public void btPlay() { cmd(CMD_PLAY); }

    @Override
    public void btNext() { cmd(CMD_NEXT); }

    @Override
    public void btPrevious() { cmd(CMD_PREV); }

    @Override
    public void btPowerOn() {}

    @Override
    public void btConnect(String address, String name) {}

    @Override
    public String[] btPhone() { return null; }

    /** Package of the activity on screen ("sys.top.activity", set by the firmware), or "". */
    @Override
    public String appOnTop() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String s = (String) sp.getMethod("get", String.class).invoke(null, "sys.top.activity");
            if (s == null) return "";
            int slash = s.indexOf('/');
            return slash > 0 ? s.substring(0, slash) : s;
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public String radioPackage() { return PKG_RADIO; }

    @Override
    public String btPackage() { return PKG_BT; }

    /** A few lines for the device report. */
    @Override
    public String describe() {
        StringBuilder r = new StringBuilder();
        r.append("  radio app service: ").append(radio.state()).append('\n');
        if (radio.binder != null) {
            r.append("    freq=").append(call(radio.binder, D_API, A_FREQ, null, INT, -1)).append(" kHz seeking=").append(radioSeeking())
                    .append(" local=").append(apiBool(A_IS_LOCAL)).append('\n');
        }
        r.append("  fmradio: ").append(player() != null ? "ok" : "not reachable").append('\n');
        if (player() != null) {
            String[] rd = rds();
            int[] st = radioStations();
            r.append("    ps=").append(rd == null ? null : rd[0]).append(" rt=").append(rd == null ? null : rd[1])
                    .append(" presets=").append(st == null ? "null" : java.util.Arrays.toString(st)).append('\n');
        }
        r.append("  bt music service: ").append(music.state()).append('\n');
        if (music.binder != null) {
            String[] id = id3();
            r.append("    playing=").append(btPlaying()).append(" id3=").append(id == null ? "null" : java.util.Arrays.toString(id)).append('\n');
        }
        r.append("  sourceinfo: ").append(service("sourceinfo", D_SOURCE) != null ? "ok" : "not reachable")
                .append(", source=").append(sourcePackage()).append('\n');
        return r.toString();
    }
}
