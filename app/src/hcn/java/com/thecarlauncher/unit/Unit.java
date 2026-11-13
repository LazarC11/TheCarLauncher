package com.thecarlauncher.unit;

/**
 * Build for MediaTek MT8163 units with the hcn firmware (com.hcn.* system apps: autoradio,
 * AutoMediaPlayer, bluetoothservice; com.autochips.bluetooth). Radio and Bluetooth music go
 * through the factory radio and Bluetooth music services (media.HcnService).
 */
public final class Unit {
    public static final String ID = "hcn";
    public static final String NAME = "MediaTek MT8163 / hcn";
    public static final boolean QF = false;
    /** Radio and Bluetooth music through the unit's android.car "car" service. */
    public static final boolean CAR = false;
    /** Radio and Bluetooth music through the SYU / FYT MainServer (com.syu.ms). */
    public static final boolean SYU = false;

    private Unit() {}
}
