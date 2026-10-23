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

    private Unit() {}
}
