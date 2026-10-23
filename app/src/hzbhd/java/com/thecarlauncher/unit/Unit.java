package com.thecarlauncher.unit;

/**
 * Build for Allwinner T3 units with the hzbhd firmware (com.hzbhd.* system apps, hzbhd.radio /
 * hzbhd.bluetooth services, Goodocom gocsdk, EasyConnection). Radio and Bluetooth music go
 * through the unit's own apps until their interfaces are mapped.
 */
public final class Unit {
    public static final String ID = "hzbhd";
    public static final String NAME = "Allwinner T3 / hzbhd";
    public static final boolean QF = false;

    private Unit() {}
}
