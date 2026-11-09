package com.thecarlauncher.unit;

/**
 * Build for UIS7862 units with the QF firmware (MCU tuner through mcu_service, the QF Bluetooth
 * app and the Goodocom module on /dev/goc_serial). Every head-unit family gets its own build;
 * Setup → Software update installs the one that matches the unit.
 */
public final class Unit {
    /** Release asset / build id. */
    public static final String ID = "qf";
    public static final String NAME = "UIS7862 / QF";
    /** QF-specific paths: MCU tuner, QF Bluetooth app control, Goodocom serial line, BT devices. */
    public static final boolean QF = true;

    /** Radio and Bluetooth music through the unit's android.car "car" service. */
    public static final boolean CAR = false;
    /** Radio and Bluetooth music through the SYU / FYT MainServer (com.syu.ms). */
    public static final boolean SYU = false;

    private Unit() {}
}
