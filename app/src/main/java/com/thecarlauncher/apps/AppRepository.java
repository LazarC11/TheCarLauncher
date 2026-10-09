package com.thecarlauncher.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Installed launchable apps plus the user's home-screen favourites. */
public final class AppRepository {
    public interface Listener { void onAppsChanged(); }

    public static final class App {
        public final String pkg, cls, label;
        public final Drawable icon;
        /** A screen without a launcher icon (e.g. the unit's Bluetooth music), found by scanning. */
        public boolean hidden;

        App(String pkg, String cls, String label, Drawable icon) {
            this.pkg = pkg;
            this.cls = cls;
            this.label = label;
            this.icon = icon;
        }

        public String key() { return pkg + "/" + cls; }
        public ComponentName component() { return new ComponentName(pkg, cls); }
    }

    private static final String[] DEFAULT_FAVOURITES = {
            "com.google.android.apps.maps", "com.waze", "com.spotify.music",
            "com.google.android.apps.youtube.music", "com.google.android.youtube", "com.android.chrome",
            "com.android.settings"
    };
    private static AppRepository sInstance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile List<App> apps = Collections.emptyList();
    private boolean loading;

    public static synchronized AppRepository get(Context c) {
        if (sInstance == null) sInstance = new AppRepository(c.getApplicationContext());
        return sInstance;
    }

    private AppRepository(Context c) {
        app = c;
        reload();
    }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public List<App> all() { return apps; }

    public void reload() {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<App> list = query();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        apps = list;
                        loading = false;
                        for (Listener l : listeners) l.onAppsChanged();
                    }
                });
            }
        }, "app-loader").start();
    }

    private List<App> query() {
        PackageManager pm = app.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            if (ri.activityInfo.packageName.equals(app.getPackageName())) continue;
            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Exception e) {
                icon = pm.getDefaultActivityIcon();
            }
            out.add(new App(ri.activityInfo.packageName, ri.activityInfo.name,
                    String.valueOf(ri.loadLabel(pm)), icon));
        }
        addHidden(pm, out);
        final Collator col = Collator.getInstance();
        Collections.sort(out, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return col.compare(a.label, b.label);
            }
        });
        return out;
    }

    /**
     * Head units keep some of their own screens (Bluetooth music, Bluetooth phone) inside system
     * packages without a launcher icon; their factory launcher opens them directly. Find those
     * exported screens by name so they can be listed and opened here too.
     */
    private static final java.util.regex.Pattern HIDDEN = java.util.regex.Pattern.compile(
            "(?i).*((bt|bluetooth|a2dp|blue)[ ._]*(music|audio|player|media|phone|dial|call)"
                    + "|(music|audio|player|media)[ ._]*(bt|bluetooth|a2dp)|蓝牙).*");

    private void addHidden(PackageManager pm, List<App> out) {
        java.util.Set<String> have = new java.util.HashSet<>();
        for (App a : out) have.add(a.key());
        List<android.content.pm.ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(0);
        } catch (Exception e) {
            return;
        }
        for (android.content.pm.ApplicationInfo ai : apps) {
            if (ai.packageName.equals(app.getPackageName())) continue;
            android.content.pm.PackageInfo pi;
            try {
                pi = pm.getPackageInfo(ai.packageName, PackageManager.GET_ACTIVITIES);
            } catch (Throwable t) {
                continue;
            }
            if (pi.activities == null) continue;
            for (android.content.pm.ActivityInfo a : pi.activities) {
                if (!a.exported || !a.enabled || a.permission != null) continue;
                String simple = a.name.substring(a.name.lastIndexOf('.') + 1);
                String label;
                try {
                    label = String.valueOf(a.loadLabel(pm));
                } catch (Exception e) {
                    label = simple;
                }
                boolean match = HIDDEN.matcher(simple + " " + label + " " + a.name).matches();
                // e.g. com.qf.bluetooth/.ui.MusicActivity: Bluetooth package, music screen.
                String lp = ai.packageName.toLowerCase(java.util.Locale.ROOT), ls = simple.toLowerCase(java.util.Locale.ROOT);
                if (!match && (lp.contains("bluetooth") || lp.matches(".*\\bbt\\b.*|.*\\.bt.*"))
                        && (ls.contains("music") || ls.contains("audio") || ls.contains("a2dp") || ls.contains("player"))) match = true;
                if (!match) continue;
                if (have.contains(ai.packageName + "/" + a.name)) continue;
                if (simple.toLowerCase(java.util.Locale.ROOT).contains("setting")) continue;
                // Use a readable name when the label is just the package's or the class name.
                if (label.equals(simple) || label.length() == 0) label = simple.replaceAll("(?i)activity", "")
                        .replaceAll("([a-z])([A-Z])", "$1 $2");
                Drawable icon;
                try {
                    icon = a.loadIcon(pm);
                } catch (Exception e) {
                    icon = pm.getDefaultActivityIcon();
                }
                App h = new App(ai.packageName, a.name, label, icon);
                h.hidden = true;
                out.add(h);
                have.add(h.key());
            }
        }
    }

    /** The unit's Bluetooth music screen, if a hidden one was found. */
    public App bluetoothMusic() {
        // QuickFish firmware packages (com.qf.*) first: that is the unit's own Bluetooth app.
        for (App a : apps) {
            String l = (a.label + " " + a.cls).toLowerCase(java.util.Locale.ROOT);
            if (a.hidden && a.pkg.startsWith("com.qf") && (l.contains("music") || l.contains("audio") || l.contains("a2dp")
                    || l.contains("player"))) return a;
        }
        for (App a : apps) {
            String l = (a.label + " " + a.cls).toLowerCase(java.util.Locale.ROOT);
            if (a.hidden && (l.contains("music") || l.contains("audio") || l.contains("a2dp") || l.contains("player"))) return a;
        }
        return null;
    }

    public App find(String key) {
        for (App a : apps) if (a.key().equals(key)) return a;
        return null;
    }

    public App findPackage(String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg) && !a.hidden) return a;
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    public List<String> favouriteKeys() {
        String raw = Prefs.str(app, Prefs.FAVOURITES, null);
        if (raw != null) {
            List<String> out = new ArrayList<>();
            for (String s : raw.split("\\|")) if (s.length() > 0) out.add(s);
            return out;
        }
        // First run: seed with popular in-car apps that are actually installed.
        List<String> seeded = new ArrayList<>();
        for (String pkg : DEFAULT_FAVOURITES) {
            App a = findPackage(pkg);
            if (a != null && seeded.size() < 4) seeded.add(a.key());
        }
        return seeded;
    }

    public List<App> favourites() {
        List<App> out = new ArrayList<>();
        for (String k : favouriteKeys()) {
            App a = find(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    public boolean isFavourite(App a) {
        return favouriteKeys().contains(a.key());
    }

    public void toggleFavourite(App a) {
        List<String> keys = new ArrayList<>(favouriteKeys());
        if (!keys.remove(a.key())) {
            if (keys.size() >= 4) keys.remove(0);
            keys.add(a.key());
        }
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(k).append('|');
        Prefs.put(app, Prefs.FAVOURITES, sb.toString());
        for (Listener l : listeners) l.onAppsChanged();
    }

    public static boolean launch(Context c, App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.component())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        boolean ok = Ui.start(c, i);
        if (!ok) Ui.toast(c, "Can't open " + a.label);
        return ok;
    }

    public static boolean launchPackage(Context c, String pkg) {
        if (pkg == null) return false;
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        return i != null && Ui.start(c, i);
    }

    public static void showInfo(Context c, String pkg) {
        Ui.start(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    public static void uninstall(Context c, String pkg) {
        Ui.start(c, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
    }

}
