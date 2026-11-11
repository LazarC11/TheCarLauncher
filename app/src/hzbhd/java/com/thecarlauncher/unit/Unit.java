package com.thecarlauncher.unit;

/**
 * Build for Allwinner T3 units with the hzbhd firmware (com.hzbhd.* system apps, hzbhd.radio /
 * hzbhd.bluetooth services, Goodocom gocsdk, EasyConnection). Radio, Bluetooth music and the
 * audio source go through the hzbhd services (media.HzbhdService); the factory apps are the
 * fallback when a service cannot be reached.
 */
public final class Unit {
    public static final String ID = "hzbhd";
    public static final String NAME = "Allwinner T3 / hzbhd";
    public static final boolean QF = false;

    /** Radio and Bluetooth music through the unit's android.car "car" service. */
    public static final boolean CAR = false;
    /** Radio and Bluetooth music through the SYU / FYT MainServer (com.syu.ms). */
    public static final boolean SYU = false;

    private Unit() {}
}
