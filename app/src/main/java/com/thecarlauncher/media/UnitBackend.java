package com.thecarlauncher.media;

import android.content.Context;

/**
 * A head unit's own radio / Bluetooth music / audio-source service, for unit builds whose
 * firmware routes the speakers itself (android.car "car" service, SYU/FYT "com.syu.ms").
 * Null for the QF build (MCU tuner and QF Bluetooth app) and builds that use the factory apps.
 * Calls may block briefly: radio and media code call them from a worker thread.
 */
public interface UnitBackend {
    boolean available();

    String error();

    /** The launcher's own player is about to play: the speakers go to Android media. */
    void claimForMedia();

    void enterRadio();

    void exitRadio();

    /** FM frequency in 10 kHz units (9220 = 92.20 MHz), 0 when unknown. */
    int radioFreq();

    String radioName();

    String radioText();

    boolean radioSeeking();

    void radioTune(int freq);

    void radioSeek(boolean up);

    void radioLocal(boolean on);

    void radioRds(boolean on);

    /**
     * Starts the unit's own station search, if it has one (it stores what it finds in its station
     * slots, see {@link #radioStations}); false when the launcher has to scan by seeking.
     */
    boolean radioSearch();

    /** FM stations the unit's search stored (10 kHz units), or null when it keeps no such list. */
    int[] radioStations();

    void enterBluetooth();

    void exitBluetooth();

    /** {title, artist} of the phone's song, or null. */
    String[] btSong();

    /** 1 playing, 0 not playing, -1 unknown. */
    int btPlaying();

    /** Length of the phone's song in ms, 0 when the unit does not tell. */
    long btDurationMs();

    /** Position in the phone's song in ms, -1 when the unit does not tell. */
    long btPositionMs();

    void btPlayPause();

    void btPlay();

    void btNext();

    void btPrevious();

    /** Switches the unit's Bluetooth module on (best effort). */
    void btPowerOn();

    /** Connects a phone by its Bluetooth address. */
    void btConnect(String address, String name);

    /** {address, name} of the connected phone, or null when none is connected / unknown. */
    String[] btPhone();

    /** Package of the unit app that may come up on top when a source is entered, or null. */
    String appOnTop();

    String radioPackage();

    String btPackage();

    /** A few lines for the device report. */
    String describe();

    final class Get {
        private Get() {}

        public static UnitBackend of(Context c) {
            if (com.thecarlauncher.unit.Unit.CAR) return CarService.get(c);
            if (com.thecarlauncher.unit.Unit.SYU) return SyuService.get(c);
            return null;
        }
    }
}
