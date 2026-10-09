package com.thecarlauncher.radio;

import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.lang.reflect.Method;

/**
 * Low-level FM tuner access for QuickFish ("QF") head units such as the Roco K706 (UIS7862S).
 *
 * The tuner chip hangs off the unit's MCU and is reached through the system service
 * {@code mcu_service} ({@code android.qf.mcu.IMcuManager}). Commands are MCU packets
 * {@code 0xA0 [sub, p1, p2]}; tuner state comes back through an {@code IMcuListener}.
 *
 * Protocol knowledge comes from OpenRadioFM by kapi21 (Apache License 2.0),
 * https://github.com/kapi21/OpenRadioFM — see NOTICE.
 */
final class K706Tuner {
    interface Listener {
        void onPacket(byte[] data);
    }

    private static final String SERVICE = "mcu_service";
    private static final String MANAGER = "android.qf.mcu.IMcuManager";
    private static final String LISTENER = "android.qf.mcu.IMcuListener";

    static final int CMD_TUNER = 0xA0;
    static final int CMD_RDS = 0xA2;
    static final byte SUB_TUNE = 0x00, SUB_SEEK_UP = 0x01, SUB_SEEK_DOWN = 0x02, SUB_FINE_UP = 0x03,
            SUB_FINE_DOWN = 0x04, SUB_BAND = 0x06, SUB_LOC = 0x07, SUB_AREA = 0x0A, SUB_SCAN_STOP = 0x0C;

    /** Audio routing channels of the MCU: 2 = FM tuner, 4 = Android media. */
    static final byte CHANNEL_FM = 2, CHANNEL_ANDROID = 4;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Object manager;
    private Method sendMsg, setMute, getChannel, setChannel;
    private Listener listener;
    private Binder listenerBinder;
    private String error;

    K706Tuner(Context c) {
        app = c.getApplicationContext();
    }

    String error() { return error; }

    boolean isConnected() { return manager != null && sendMsg != null; }

    /** Connects to mcu_service and registers for tuner callbacks. Safe to call repeatedly. */
    boolean connect(Listener l) {
        listener = l;
        if (isConnected()) return true;
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, SERVICE);
            if (binder == null) {
                error = "mcu_service not found";
                return false;
            }
            handshake(binder);
            Class<?> stub = Class.forName(MANAGER + "$Stub");
            manager = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> mc = manager.getClass();
            sendMsg = mc.getMethod("RPC_SendMcuMsgData", byte.class, byte[].class, int.class);
            setMute = find(mc, "RPC_SetVolumeMute", int.class, boolean.class);
            getChannel = find(mc, "RPC_GetChannel");
            setChannel = find(mc, "RPC_SetChannel", byte.class, int.class);
            try {
                registerListener();
            } catch (Throwable t) {
                // Tuning still works; only RDS names and frequency feedback are missing.
                listenerBinder = null;
            }
            error = null;
            return true;
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
            manager = null;
            sendMsg = null;
            return false;
        }
    }

    /** Transaction 1001 grants our package the MCU info callbacks (RDS, tuner state). */
    private void handshake(IBinder binder) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MANAGER);
            data.writeString(app.getPackageName());
            binder.transact(1001, data, reply, 0);
            reply.readException();
        } catch (Throwable ignored) {
            // Older firmwares don't need it.
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void registerListener() throws Exception {
        listenerBinder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == INTERFACE_TRANSACTION) {
                    if (reply != null) reply.writeString(LISTENER);
                    return true;
                }
                if (code == 1) { // onMcuInfoChanged(byte[])
                    data.enforceInterface(LISTENER);
                    final byte[] bytes = data.createByteArray();
                    if (reply != null) reply.writeNoException();
                    if (bytes != null && bytes.length > 0) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) listener.onPacket(bytes);
                            }
                        });
                    }
                    return true;
                }
                return false;
            }
        };
        listenerBinder.attachInterface(null, LISTENER);
        Class<?> lstub = Class.forName(LISTENER + "$Stub");
        Object proxy = lstub.getMethod("asInterface", IBinder.class).invoke(null, listenerBinder);
        Class<?> li = Class.forName(LISTENER);
        manager.getClass().getMethod("RPC_RequestMcuInfoChangedListener", li, String.class)
                .invoke(manager, proxy, app.getPackageName());
    }

    private static Method find(Class<?> c, String name, Class<?>... paramVariants) {
        if (paramVariants.length == 0) {
            try {
                return c.getMethod(name);
            } catch (Exception e) {
                return null;
            }
        }
        for (Class<?> p : paramVariants) {
            try {
                return c.getMethod(name, p);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ---- Commands ----------------------------------------------------------------------------

    void cmd(byte sub, int p1, int p2) {
        send(CMD_TUNER, new byte[]{sub, (byte) p1, (byte) p2});
    }

    void send(int command, byte[] payload) {
        if (!isConnected()) return;
        try {
            sendMsg.invoke(manager, (byte) command, payload, payload.length);
        } catch (Throwable ignored) {
        }
    }

    /** FM in MCU units (×100 kHz-tenths, e.g. 9220 = 92.20 MHz); AM in kHz. */
    void tune(int mcuFreq) {
        cmd(SUB_TUNE, (mcuFreq >> 8) & 0xFF, mcuFreq & 0xFF);
    }

    void mute(boolean on) {
        if (setMute == null) return;
        try {
            if (setMute.getParameterTypes()[0] == int.class) setMute.invoke(manager, on ? 1 : 0);
            else setMute.invoke(manager, on);
        } catch (Throwable ignored) {
        }
    }

    int channel() {
        if (getChannel == null) return -1;
        try {
            return ((Number) getChannel.invoke(manager)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    void route(byte ch) {
        if (setChannel == null) return;
        try {
            if (setChannel.getParameterTypes()[0] == byte.class) setChannel.invoke(manager, ch);
            else setChannel.invoke(manager, (int) ch);
        } catch (Throwable ignored) {
        }
    }
}
