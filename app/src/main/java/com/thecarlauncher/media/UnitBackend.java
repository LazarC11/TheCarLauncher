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

    void enterBluetooth();

    void exitBluetooth();

    /** {title, artist} of the phone's song, or null. */
    String[] btSong();

    /** 1 playing, 0 not playing, -1 unknown. */
    int btPlaying();

    void btPlayPause();

    void btPlay();

    void btNext();

    void btPrevious();

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
