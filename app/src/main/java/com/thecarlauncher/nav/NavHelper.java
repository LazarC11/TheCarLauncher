package com.thecarlauncher.nav;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.net.Uri;

import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hands destinations to whichever navigation app the driver prefers. */
public final class NavHelper {
    public static final String GOOGLE_MAPS = "com.google.android.apps.maps";
    public static final String WAZE = "com.waze";
    private static final String[] KNOWN = {
            GOOGLE_MAPS, WAZE, "com.here.app.maps", "net.osmand", "net.osmand.plus",
            "com.sygic.aura", "ru.yandex.yandexnavi", "app.organicmaps", "com.mapswithme.maps.pro"
    };

    private NavHelper() {}

    /** Packages that can open a geo: URI, in a stable order. */
    public static List<String> navApps(Context c) {
        PackageManager pm = c.getPackageManager();
        List<String> out = new ArrayList<>();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=test"));
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            String p = ri.activityInfo.packageName;
            if (!out.contains(p) && !p.equals(c.getPackageName())) out.add(p);
        }
        for (String k : KNOWN) {
            if (!out.contains(k) && installed(pm, k)) out.add(k);
        }
        return out;
    }

    public static String preferred(Context c) {
        PackageManager pm = c.getPackageManager();
        String p = Prefs.str(c, Prefs.NAV_PKG, null);
        if (p != null && installed(pm, p)) return p;
        for (String k : KNOWN) if (installed(pm, k)) return k;
        List<String> apps = navApps(c);
        return apps.isEmpty() ? null : apps.get(0);
    }

    public static String label(Context c, String pkg) {
        if (pkg == null) return "Maps";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Starts turn-by-turn guidance to a free-text address or "lat,lon". */
    public static void navigateTo(Context c, String destination) {
        if (Ui.isEmpty(destination)) return;
        String pkg = preferred(c);
        String q = Uri.encode(destination.trim());
        Intent i;
        if (GOOGLE_MAPS.equals(pkg)) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + q));
        } else if (WAZE.equals(pkg)) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=" + q + "&navigate=yes"));
        } else {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + q));
        }
        if (pkg != null) i.setPackage(pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Ui.start(c, i)) return;
        Intent generic = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + q));
        if (!Ui.start(c, generic)) Ui.toast(c, "No navigation app installed");
    }

    /** Opens the navigation app itself, centred on the current position when possible. */
    public static void openApp(Context c, Location here) {
        String pkg = preferred(c);
        if (pkg != null) {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null && Ui.start(c, launch)) return;
        }
        String geo = here == null ? "geo:0,0" : String.format(Locale.US, "geo:%.6f,%.6f",
                here.getLatitude(), here.getLongitude());
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(geo));
        if (pkg != null) i.setPackage(pkg);
        if (!Ui.start(c, i)) Ui.toast(c, "No navigation app installed");
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
