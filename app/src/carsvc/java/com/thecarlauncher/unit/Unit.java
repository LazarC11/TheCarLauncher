package com.thecarlauncher.unit;

/**
 * Build for units with the "android.car" firmware (android.car.app.radio / android.car.app.bt
 * system apps, the "car" system service android.car.server.ICarService). Radio, Bluetooth music
 * and the audio source are driven through that service.
 */
public final class Unit {
    public static final String ID = "carsvc";
    public static final String NAME = "android.car firmware";
    public static final boolean QF = false;
    /** Radio and Bluetooth music through the unit's android.car "car" service. */
    public static final boolean CAR = true;
    /** Radio and Bluetooth music through the SYU / FYT MainServer (com.syu.ms). */
    public static final boolean SYU = false;

    private Unit() {}
}
