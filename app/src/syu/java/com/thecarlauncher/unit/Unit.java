package com.thecarlauncher.unit;

/**
 * Build for SYU / FYT units (com.syu.* system apps, the MainServer com.syu.ms with its
 * com.syu.ipc toolkit). Radio, Bluetooth music and the audio source go through MainServer.
 */
public final class Unit {
    public static final String ID = "syu";
    public static final String NAME = "SYU / FYT";
    public static final boolean QF = false;
    /** Radio and Bluetooth music through the unit's android.car "car" service. */
    public static final boolean CAR = false;
    /** Radio and Bluetooth music through the SYU / FYT MainServer (com.syu.ms). */
    public static final boolean SYU = true;

    private Unit() {}
}
