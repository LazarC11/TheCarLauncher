package com.thecarlauncher.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/**
 * SYU / FYT units: the "MainServer" com.syu.ms (app.ToolkitService) owns the radio, the
 * Bluetooth module and the audio source. Its toolkit hands out modules (0 main, 1 radio, 2 BT);
 * each takes commands {@code cmd(code, ints, floats, strings)} and sends updates to registered
 * callbacks. Spoken over raw binder transactions in the com.syu.ipc format, so no firmware
 * classes are needed; nothing is sent unless the service is the SYU toolkit.
 */
public final class SyuService implements UnitBackend {
    private static final String PKG = "com.syu.ms", CLS = "app.ToolkitService";
    private static final String TOOLKIT = "com.syu.ipc.IRemoteToolkit", MODULE = "com.syu.ipc.IRemoteModule",
            CALLBACK = "com.syu.ipc.IModuleCallback";
    private static final int MOD_MAIN = 0, MOD_RADIO = 1, MOD_BT = 2;

    // Main module
    private static final int MAIN_C_APP_ID = 0, MAIN_U_APP_ID = 0;
    /** Source ids ("app ids"). */
    public static final int APP_RADIO = 1, APP_BTAV = 3, APP_AUDIO_PLAYER = 8;
    // Radio module
    private static final int R_C_FREQ_UP = 3, R_C_SEEK_UP = 5, R_C_SEEK_DOWN = 6, R_C_FREQ = 13, R_C_RDS_AF = 20,
            R_C_LOC = 18;
    private static final int R_FREQ_DIRECT_NEW = 3;
    private static final int R_U_BAND = 0, R_U_FREQ = 1, R_U_RDS_TEXT = 13, R_U_SEARCH_STATE = 22, R_U_PS_TEXT = 26;
    // Bluetooth module
    private static final int BT_C_PREV = 0, BT_C_NEXT = 1, BT_C_PLAYPAUSE = 2, BT_C_PLAY = 27;
    private static final int BT_U_TITLE = 0, BT_U_ARTIST = 1, BT_U_PLAY_STATE = 13;

    public static final String PKG_RADIO = "com.syu.radio", PKG_BT = "com.syu.bt";

    private static SyuService sInstance;
    private final Context app;
    private IBinder toolkit;
    private final IBinder[] modules = new IBinder[3];
    /** bindService succeeded: Android keeps (re)connecting it, so it is never bound twice. */
    private boolean bound;
    private String error = "not connected yet";

    private volatile int appId = -1, band = -1, freq, searchState;
    private volatile String ps, rdsText, btTitle, btArtist;
    private volatile int btState = -1;

    public static synchronized SyuService get(Context c) {
        if (sInstance == null) sInstance = new SyuService(c.getApplicationContext());
        return sInstance;
    }

    private SyuService(Context c) {
        app = c;
        bind();
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, final IBinder service) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    attach(service);
                }
            }, "syu-attach").start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (SyuService.this) {
                toolkit = null;
                java.util.Arrays.fill(modules, null);
                error = "MainServer disconnected";
            }
        }
    };

    private synchronized void bind() {
        if (bound) return;
        try {
            bound = app.bindService(new Intent().setClassName(PKG, CLS), connection, Context.BIND_AUTO_CREATE);
            if (!bound) error = "com.syu.ms not found";
        } catch (Throwable t) {
            error = "bind: " + t.getClass().getSimpleName();
        }
    }

    private void attach(IBinder service) {
        if (service == null) {
            error = "MainServer gave no binder";
            return;
        }
        try {
            String d = service.getInterfaceDescriptor();
            if (!TOOLKIT.equals(d)) {
                error = "com.syu.ms is " + d;
                return;
            }
            IBinder main = module(service, MOD_MAIN), radio = module(service, MOD_RADIO), bt = module(service, MOD_BT);
            synchronized (this) {
                toolkit = service;
                modules[MOD_MAIN] = main;
                modules[MOD_RADIO] = radio;
                modules[MOD_BT] = bt;
                error = main == null ? "no main module" : null;
            }
            register(MOD_MAIN, MAIN_U_APP_ID);
            for (int u : new int[]{R_U_BAND, R_U_FREQ, R_U_RDS_TEXT, R_U_SEARCH_STATE, R_U_PS_TEXT}) register(MOD_RADIO, u);
            for (int u : new int[]{BT_U_TITLE, BT_U_ARTIST, BT_U_PLAY_STATE}) register(MOD_BT, u);
        } catch (Throwable t) {
            error = "attach: " + t.getClass().getSimpleName();
        }
    }

    /** IRemoteToolkit.getRemoteModule(code). */
    private static IBinder module(IBinder toolkit, int code) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(TOOLKIT);
            data.writeInt(code);
            toolkit.transact(1, data, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** IRemoteModule.register(callback, updateCode, 1 = send the current value now). */
    private void register(int module, int code) {
        IBinder m = modules[module];
        if (m == null) return;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE);
            data.writeStrongBinder(callbacks[module]);
            data.writeInt(code);
            data.writeInt(1);
            m.transact(3, data, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable ignored) {
        } finally {
            data.recycle();
        }
    }

    /** IRemoteModule.cmd(code, ints, null, null). */
    private void cmd(int module, int code, int... ints) {
        if (!available()) return;
        IBinder m = modules[module];
        if (m == null) return;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE);
            data.writeInt(code);
            data.writeIntArray(ints.length == 0 ? null : ints);
            data.writeFloatArray(null);
            data.writeStringArray(null);
            m.transact(1, data, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable t) {
            error = "cmd " + module + "/" + code + ": " + t.getClass().getSimpleName();
        } finally {
            data.recycle();
        }
    }

    private final Binder[] callbacks = {new Callback(MOD_MAIN), new Callback(MOD_RADIO), new Callback(MOD_BT)};

    /** IModuleCallback: update(code, ints, floats, strings). */
    private final class Callback extends Binder {
        private final int module;

        Callback(int module) {
            this.module = module;
            attachInterface(null, CALLBACK);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CALLBACK);
                return true;
            }
            if (code != 1) return false;
            try {
                data.enforceInterface(CALLBACK);
                int u = data.readInt();
                int[] ints = data.createIntArray();
                data.createFloatArray();
                String[] strs = data.createStringArray();
                onUpdate(module, u, ints, strs);
                if (reply != null) reply.writeNoException();
            } catch (Throwable ignored) {
            }
            return true;
        }
    }

    void onUpdate(int module, int code, int[] ints, String[] strs) {
        int i = ints != null && ints.length > 0 ? ints[0] : Integer.MIN_VALUE;
        String s = strs != null && strs.length > 0 ? strs[0] : null;
        if (module == MOD_MAIN && code == MAIN_U_APP_ID && i != Integer.MIN_VALUE) appId = i;
        if (module == MOD_RADIO) {
            if (code == R_U_BAND && i != Integer.MIN_VALUE) band = i;
            else if (code == R_U_FREQ && i != Integer.MIN_VALUE) freq = i;
            else if (code == R_U_PS_TEXT) ps = s;
            else if (code == R_U_RDS_TEXT) rdsText = s;
            else if (code == R_U_SEARCH_STATE && i != Integer.MIN_VALUE) searchState = i;
        }
        if (module == MOD_BT) {
            if (code == BT_U_TITLE) btTitle = s;
            else if (code == BT_U_ARTIST) btArtist = s;
            else if (code == BT_U_PLAY_STATE && i != Integer.MIN_VALUE) btState = i;
        }
    }

    // ---- UnitBackend ---------------------------------------------------------------------------

    @Override
    public boolean available() {
        if (toolkit != null && toolkit.isBinderAlive()) return true;
        bind();
        return false;
    }

    @Override
    public String error() { return error; }

    /** The unit's current source (APP_*), -1 when not known yet. */
    public int currentApp() { return appId; }

    private void requestApp(int id) { cmd(MOD_MAIN, MAIN_C_APP_ID, id); }

    @Override
    public void claimForMedia() {
        if (available() && appId != APP_AUDIO_PLAYER) requestApp(APP_AUDIO_PLAYER);
    }

    @Override
    public void enterRadio() { requestApp(APP_RADIO); }

    @Override
    public void exitRadio() {
        if (appId == APP_RADIO) requestApp(APP_AUDIO_PLAYER);
    }

    @Override
    public int radioFreq() {
        int f = freq;
        return f >= 6000 && f <= 11000 ? f : 0;
    }

    @Override
    public String radioName() { return ps; }

    @Override
    public String radioText() { return rdsText; }

    @Override
    public boolean radioSeeking() { return searchState != 0; }

    @Override
    public void radioTune(int f) { cmd(MOD_RADIO, R_C_FREQ, R_FREQ_DIRECT_NEW, f); }

    @Override
    public void radioSeek(boolean up) { cmd(MOD_RADIO, up ? R_C_SEEK_UP : R_C_SEEK_DOWN); }

    @Override
    public void radioLocal(boolean on) { cmd(MOD_RADIO, R_C_LOC, on ? 1 : 0); }

    @Override
    public void radioRds(boolean on) { cmd(MOD_RADIO, R_C_RDS_AF, on ? 1 : 0); }

    @Override
    public void enterBluetooth() { requestApp(APP_BTAV); }

    @Override
    public void exitBluetooth() {
        if (appId == APP_BTAV) requestApp(APP_AUDIO_PLAYER);
    }

    @Override
    public String[] btSong() {
        String t = btTitle;
        return t == null || t.trim().length() == 0 ? null : new String[]{t, btArtist};
    }

    @Override
    public int btPlaying() {
        int s = btState;
        return s < 0 ? -1 : s == 1 ? 1 : 0;
    }

    @Override
    public void btPlayPause() { cmd(MOD_BT, BT_C_PLAYPAUSE); }

    @Override
    public void btPlay() { cmd(MOD_BT, BT_C_PLAY); }

    @Override
    public void btNext() { cmd(MOD_BT, BT_C_NEXT); }

    @Override
    public void btPrevious() { cmd(MOD_BT, BT_C_PREV); }

    /** Switching the source does not open the SYU apps. */
    @Override
    public String appOnTop() { return null; }

    @Override
    public String radioPackage() { return PKG_RADIO; }

    @Override
    public String btPackage() { return PKG_BT; }

    @Override
    public String describe() {
        if (!available()) return "  not available (" + error + ")\n";
        return "  app_id=" + appId + " radio band=" + band + " freq=" + freq + " ps=" + ps + " search=" + searchState + "\n"
                + "  bt title=" + btTitle + " artist=" + btArtist + " state=" + btState + "\n";
    }
}
