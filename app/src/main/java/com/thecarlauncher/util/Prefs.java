package com.thecarlauncher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

/** Typed access to every user setting the launcher stores. */
public final class Prefs {
    public static final String ACCENT = "accent";
    public static final String WALLPAPER = "wallpaper";
    public static final String CLOCK_24 = "clock24";
    public static final String IMMERSIVE = "immersive";
    public static final String UNITS = "units";
    public static final String VEHICLE = "vehicle_name";
    public static final String NAV_PKG = "nav_pkg";
    public static final String PHONE_PKG = "phone_pkg";
    public static final String HOME_ADDR = "home_addr";
    public static final String WORK_ADDR = "work_addr";
    public static final String MAP_TILES = "map_tiles";
    public static final String MAP_ZOOM = "map_zoom";
    public static final String RESUME_MEDIA = "resume_media";
    public static final String FAVOURITES = "favourites";
    public static final String LAST_QUEUE = "last_queue";
    public static final String LAST_TRACK = "last_track";
    public static final String LAST_POS = "last_pos";
    public static final String LAST_SOURCE = "last_source";
    public static final String LANGUAGE = "language";
    public static final String RADIO_PKG = "radio_pkg";
    public static final String BT_PKG = "bt_pkg";
    public static final String BT_WAKE = "bt_wake";
    public static final String BT_SYSTEM_KEYS = "bt_system_keys";
    public static final String PROJECTION_PKG = "projection_pkg";
    public static final String SHUFFLE = "shuffle";
    public static final String REPEAT = "repeat";
    public static final String EQ_LEVELS = "eq_levels";
    public static final String EQ_ENABLED = "eq_enabled";
    public static final String ASKED_PERMS = "asked_perms";
    public static final String LAST_LAT = "last_lat";
    public static final String LAST_LON = "last_lon";

    public static final String WALL_AURORA = "aurora";
    public static final String WALL_BLACK = "black";
    public static final String WALL_CARBON = "carbon";

    public static final String TILES_DAY = "mib_day";
    public static final String TILES_DARK = "carto_dark";
    public static final String TILES_OSM = "osm_dark";
    public static final String TILES_OFF = "off";

    private Prefs() {}

    public static SharedPreferences of(Context c) {
        return c.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    public static String str(Context c, String key, String def) {
        return of(c).getString(key, def);
    }

    public static void put(Context c, String key, String value) {
        of(c).edit().putString(key, value).apply();
    }

    public static boolean bool(Context c, String key, boolean def) {
        return of(c).getBoolean(key, def);
    }

    public static void put(Context c, String key, boolean value) {
        of(c).edit().putBoolean(key, value).apply();
    }

    public static int integer(Context c, String key, int def) {
        return of(c).getInt(key, def);
    }

    public static void put(Context c, String key, int value) {
        of(c).edit().putInt(key, value).apply();
    }

    public static boolean use24h(Context c) {
        return of(c).getBoolean(CLOCK_24, DateFormat.is24HourFormat(c));
    }

    public static boolean metric(Context c) {
        return !"mph".equals(str(c, UNITS, "kmh"));
    }

    public static String vehicle(Context c) {
        return str(c, VEHICLE, "Opel Corsa C");
    }

    public static String tiles(Context c) {
        return str(c, MAP_TILES, TILES_DAY);
    }
}
