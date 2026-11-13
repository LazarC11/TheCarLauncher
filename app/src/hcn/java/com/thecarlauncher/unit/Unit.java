package com.thecarlauncher.unit;

/**
 * Build for MediaTek MT8163 units with the hcn firmware (com.hcn.* system apps: autoradio,
 * AutoMediaPlayer, bluetoothservice; com.autochips.bluetooth). Bluetooth music plays through
 * Android's own A2DP-sink music service; the radio opens the factory com.hcn.autoradio app until
 * its "fmradio" service is mapped.
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
