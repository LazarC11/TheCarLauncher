package com.thecarlauncher.unit;

/**
 * The universal build (MibDroid.apk), the one download for every unit. It runs everywhere with
 * the factory apps for radio and Bluetooth; on first start the setup assistant recognises the
 * unit and installs the build made for it over this one (same app, nothing left behind). On a
 * unit no rule knows yet it stays installed as it is.
 */
public final class Unit {
    public static final String ID = "universal";
    public static final String NAME = "MibDroid (universal)";
    public static final boolean QF = false;
    /** Radio and Bluetooth music through the unit's android.car "car" service. */
    public static final boolean CAR = false;
    /** Radio and Bluetooth music through the SYU / FYT MainServer (com.syu.ms). */
    public static final boolean SYU = false;

    private Unit() {}
}
