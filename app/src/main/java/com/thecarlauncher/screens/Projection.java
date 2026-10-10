package com.thecarlauncher.screens;

import android.content.Context;

import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Phone projection apps (ZLink, CarPlay/Android Auto boxes) found on the head unit. */
final class Projection {
    private static final Pattern MATCH = Pattern.compile(
            "(?i).*(zlink|zjinnova|carplay|car ?link|autokit|android ?auto|gearhead|easyconn|tlink).*");

    private Projection() {}

    static List<String> candidates(Context c) {
        List<String> out = new ArrayList<>();
        for (AppRepository.App a : AppRepository.get(c).all()) {
            if (MATCH.matcher(a.pkg + " " + a.label).matches() && !out.contains(a.pkg)) out.add(a.pkg);
        }
        return out;
    }

    /** The user's chosen projection app, else the first one found, else null. */
    static String find(Context c) {
        String p = Prefs.str(c, Prefs.PROJECTION_PKG, null);
        List<String> found = candidates(c);
        if (p != null && (found.contains(p) || AppRepository.get(c).findPackage(p) != null)) return p;
        return found.isEmpty() ? null : found.get(0);
    }

    static String label(Context c, String pkg) {
        AppRepository.App a = AppRepository.get(c).findPackage(pkg);
        return a == null ? "CarPlay / Auto" : a.label;
    }
}
