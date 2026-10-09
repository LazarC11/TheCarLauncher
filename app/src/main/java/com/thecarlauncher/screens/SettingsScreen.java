package com.thecarlauncher.screens;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.media.MusicLibrary;
import com.thecarlauncher.nav.NavHelper;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Theme;
import com.thecarlauncher.util.Ui;

import java.util.List;

/** Two-pane settings: categories on the left, options on the right. */
public class SettingsScreen extends Screen {
    private static final String[] CATEGORIES = {"Display", "Vehicle", "Navigation", "Media", "System", "About"};
    private static final int[] CATEGORY_ICONS = {
            R.drawable.ic_palette, R.drawable.ic_car, R.drawable.ic_navigation,
            R.drawable.ic_music, R.drawable.ic_settings, R.drawable.ic_info
    };

    private LinearLayout categories, content;
    private ScrollView scroll;
    private static int selected; // survives recreate() after theme changes

    public SettingsScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return "Settings"; }

    @Override
    protected View create(ViewGroup parent) {
        View v = LayoutInflater.from(act).inflate(R.layout.screen_settings, parent, false);
        categories = v.findViewById(R.id.settings_categories);
        content = v.findViewById(R.id.settings_content);
        scroll = v.findViewById(R.id.settings_scroll);
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            View row = categoryRow(CATEGORY_ICONS[i], CATEGORIES[i]);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    select(idx);
                }
            });
            categories.addView(row);
        }
        select(selected);
        return v;
    }

    @Override
    public void onShow() {
        // Permission states may have changed while away.
        if (content != null) select(selected);
    }

    private View categoryRow(int icon, String label) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(act, 16);
        row.setPadding(p, 0, p, 0);
        ImageView iv = new ImageView(act);
        iv.setImageResource(icon);
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(act, 22), Ui.dp(act, 22)));
        TextView tv = new TextView(act);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(act, 14);
        row.addView(tv, lp);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 54));
        int m = Ui.dp(act, 6);
        rl.setMargins(m, Ui.dp(act, 2), m, Ui.dp(act, 2));
        row.setLayoutParams(rl);
        return row;
    }

    private void select(int idx) {
        selected = idx;
        for (int i = 0; i < categories.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) categories.getChildAt(i);
            boolean on = i == idx;
            Theme.tint((ImageView) row.getChildAt(0), on ? accent : Theme.TEXT_SECONDARY);
            ((TextView) row.getChildAt(1)).setTextColor(on ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
            if (on) {
                GradientDrawable d = Theme.round(Theme.alpha(accent, 0x22), Ui.dp(act, 10));
                d.setStroke(Ui.dp(act, 1), Theme.alpha(accent, 0x66));
                row.setBackground(d);
            } else {
                row.setBackgroundResource(R.drawable.ripple_rect);
            }
        }
        content.removeAllViews();
        switch (idx) {
            case 0: buildDisplay(); break;
            case 1: buildVehicle(); break;
            case 2: buildNavigation(); break;
            case 3: buildMedia(); break;
            case 4: buildSystem(); break;
            default: buildAbout(); break;
        }
        scroll.scrollTo(0, 0);
    }

    // ---- Sections -------------------------------------------------------------------------

    private void buildDisplay() {
        header("Accent colour");
        swatches();
        header("Appearance");
        final String[] wallNames = {"Aurora", "Pure black", "Carbon"};
        final String[] wallKeys = {Prefs.WALL_AURORA, Prefs.WALL_BLACK, Prefs.WALL_CARBON};
        String wall = Prefs.str(act, Prefs.WALLPAPER, Prefs.WALL_AURORA);
        action("Background", wallNames[indexOf(wallKeys, wall)], new Runnable() {
            @Override
            public void run() {
                Dialogs.choose(act, "Background", wallNames,
                        indexOf(wallKeys, Prefs.str(act, Prefs.WALLPAPER, Prefs.WALL_AURORA)), new Dialogs.Chosen() {
                            @Override
                            public void onChosen(int i) {
                                Prefs.put(act, Prefs.WALLPAPER, wallKeys[i]);
                                act.restartUi();
                            }
                        });
            }
        });
        toggle("24-hour clock", null, Prefs.use24h(act), new Toggle() {
            @Override
            public void set(boolean on) {
                Prefs.put(act, Prefs.CLOCK_24, on);
            }
        });
        toggle("Full screen", "Hide Android's status and navigation bars", Prefs.bool(act, Prefs.IMMERSIVE, false),
                new Toggle() {
                    @Override
                    public void set(boolean on) {
                        Prefs.put(act, Prefs.IMMERSIVE, on);
                        act.restartUi();
                    }
                });
        action("Screen brightness", "Android display settings", intent(Settings.ACTION_DISPLAY_SETTINGS));
    }

    private void buildVehicle() {
        header("Vehicle");
        action("Vehicle name", Prefs.vehicle(act), new Runnable() {
            @Override
            public void run() {
                Dialogs.text(act, "Vehicle name", "e.g. Opel Corsa C", Prefs.vehicle(act), new Dialogs.TextEntered() {
                    @Override
                    public void onText(String text) {
                        Prefs.put(act, Prefs.VEHICLE, text);
                        act.restartUi();
                    }
                });
            }
        });
        final String[] units = {"Kilometres (km/h)", "Miles (mph)"};
        action("Speed units", units[Prefs.metric(act) ? 0 : 1], new Runnable() {
            @Override
            public void run() {
                Dialogs.choose(act, "Speed units", units, Prefs.metric(act) ? 0 : 1, new Dialogs.Chosen() {
                    @Override
                    public void onChosen(int i) {
                        Prefs.put(act, Prefs.UNITS, i == 0 ? "kmh" : "mph");
                        act.restartUi();
                    }
                });
            }
        });
        String phone = Prefs.str(act, Prefs.PHONE_PKG, null);
        action("Phone app", phone == null ? "Ask / system dialer" : NavHelper.label(act, phone), new Runnable() {
            @Override
            public void run() {
                Dialogs.pickApp(act, "Phone app", "System dialer", new Dialogs.AppPicked() {
                    @Override
                    public void onPicked(String pkg) {
                        Prefs.put(act, Prefs.PHONE_PKG, pkg);
                        select(selected);
                    }
                });
            }
        });
        note("Speed and heading come from the head unit's GPS antenna. Speed is shown from 2 km/h upwards "
                + "to hide GPS drift while parked.");
    }

    private void buildNavigation() {
        header("Navigation app");
        String pkg = NavHelper.preferred(act);
        action("Preferred app", pkg == null ? "None installed" : NavHelper.label(act, pkg), new Runnable() {
            @Override
            public void run() {
                List<String> apps = NavHelper.navApps(act);
                if (apps.isEmpty()) {
                    Ui.toast(act, "Install Google Maps, Waze or another navigation app first");
                    return;
                }
                Dialogs.pickPackage(act, "Navigation app", null, apps, new Dialogs.AppPicked() {
                    @Override
                    public void onPicked(String p) {
                        Prefs.put(act, Prefs.NAV_PKG, p);
                        select(selected);
                    }
                });
            }
        });
        header("Saved places");
        address("Home", Prefs.HOME_ADDR);
        address("Work", Prefs.WORK_ADDR);
        header("Map");
        final String[] names = {"Night (CARTO dark)", "OpenStreetMap, dimmed", "Off (offline grid)"};
        final String[] keys = {Prefs.TILES_DARK, Prefs.TILES_OSM, Prefs.TILES_OFF};
        action("Map style", names[indexOf(keys, Prefs.tiles(act))], new Runnable() {
            @Override
            public void run() {
                Dialogs.choose(act, "Map style", names, indexOf(keys, Prefs.tiles(act)), new Dialogs.Chosen() {
                    @Override
                    public void onChosen(int i) {
                        Prefs.put(act, Prefs.MAP_TILES, keys[i]);
                        act.restartUi();
                    }
                });
            }
        });
        note("Map tiles are downloaded over the head unit's internet connection (Wi-Fi hotspot or SIM) and "
                + "cached for 30 days, so places you drive often keep working offline.");
    }

    private void buildMedia() {
        header("Playback");
        toggle("Resume music on start", "Continues the last song when the car is switched on",
                Prefs.bool(act, Prefs.RESUME_MEDIA, true), new Toggle() {
                    @Override
                    public void set(boolean on) {
                        Prefs.put(act, Prefs.RESUME_MEDIA, on);
                    }
                });
        action("Equalizer", "Bass, mid and treble for the built-in player", new Runnable() {
            @Override
            public void run() {
                Dialogs.equalizer(act, accent);
            }
        });
        header("Access");
        boolean lib = MusicLibrary.hasPermission(act);
        action("Music library", lib ? "Allowed" : "Not allowed — tap to allow", new Runnable() {
            @Override
            public void run() {
                if (MusicLibrary.hasPermission(act)) {
                    MusicLibrary.invalidate();
                    Ui.toast(act, "Library will be re-scanned");
                } else {
                    act.requestMissingPermissions(true);
                }
            }
        });
        boolean ext = MediaHub.get(act).hasNotificationAccess();
        action("Other media apps", ext ? "Connected (Spotify, YouTube Music, Bluetooth…)"
                : "Not connected — tap to allow notification access", intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        action("Rescan library", "Pick up music just copied to USB or SD card", new Runnable() {
            @Override
            public void run() {
                MusicLibrary.invalidate();
                Ui.toast(act, "Library will be re-scanned");
            }
        });
    }

    private void buildSystem() {
        header("Launcher");
        action("Set as default launcher", "Make this the home screen", new Runnable() {
            @Override
            public void run() {
                if (!Ui.start(act, new Intent(Settings.ACTION_HOME_SETTINGS))) {
                    Ui.start(act, new Intent(Settings.ACTION_SETTINGS));
                }
            }
        });
        action("Permissions", "Location, music and notifications", new Runnable() {
            @Override
            public void run() {
                act.requestMissingPermissions(true);
            }
        });
        header("Android");
        action("Wi-Fi", null, intent(Settings.ACTION_WIFI_SETTINGS));
        action("Bluetooth", null, intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        action("Sound", null, intent(Settings.ACTION_SOUND_SETTINGS));
        action("Date & time", null, intent(Settings.ACTION_DATE_SETTINGS));
        action("Language", null, intent(Settings.ACTION_LOCALE_SETTINGS));
        action("All Android settings", null, intent(Settings.ACTION_SETTINGS));
    }

    private void buildAbout() {
        header("About");
        String version = "1.0";
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception ignored) {
        }
        info("Car Launcher", "Version " + version);
        info("Head unit", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        info("Screen", act.getResources().getDisplayMetrics().widthPixels + " × "
                + act.getResources().getDisplayMetrics().heightPixels + " px · "
                + act.getResources().getDisplayMetrics().densityDpi + " dpi");
        note("Map data © OpenStreetMap contributors (openstreetmap.org/copyright). Night map style © CARTO.");
    }

    // ---- Row builders ---------------------------------------------------------------------

    private interface Toggle { void set(boolean on); }

    private void header(String text) {
        TextView t = new TextView(act);
        t.setText(text.toUpperCase());
        t.setTextSize(12);
        t.setLetterSpacing(0.14f);
        t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        t.setTextColor(accent);
        int p = Ui.dp(act, 14);
        t.setPadding(p, Ui.dp(act, content.getChildCount() == 0 ? 10 : 22), p, Ui.dp(act, 6));
        content.addView(t);
    }

    private LinearLayout baseRow(String title, String subtitle) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(act, 60));
        int p = Ui.dp(act, 14);
        row.setPadding(p, Ui.dp(act, 8), p, Ui.dp(act, 8));
        row.setBackgroundResource(R.drawable.ripple_rect);
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(Theme.TEXT_PRIMARY);
        texts.addView(t);
        if (subtitle != null) {
            TextView s = new TextView(act);
            s.setText(subtitle);
            s.setTextSize(13);
            s.setTextColor(Theme.TEXT_SECONDARY);
            s.setPadding(0, Ui.dp(act, 2), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void action(String title, String subtitle, final Runnable r) {
        LinearLayout row = baseRow(title, subtitle);
        ImageView chev = new ImageView(act);
        chev.setImageResource(R.drawable.ic_chevron_right);
        Theme.tint(chev, Theme.TEXT_DIM);
        row.addView(chev, new LinearLayout.LayoutParams(Ui.dp(act, 22), Ui.dp(act, 22)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
    }

    private void info(String title, String subtitle) {
        baseRow(title, subtitle).setBackground(null);
    }

    private void toggle(String title, String subtitle, boolean value, final Toggle t) {
        LinearLayout row = baseRow(title, subtitle);
        final Switch sw = new Switch(act);
        sw.setChecked(value);
        sw.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{accent, 0xFFB0BAC5}));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Theme.alpha(accent, 0x88), 0xFF3A4654}));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                t.set(on);
            }
        });
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
    }

    private void address(final String name, final String key) {
        String addr = Prefs.str(act, key, null);
        action(name, Ui.isEmpty(addr) ? "Not set" : addr, new Runnable() {
            @Override
            public void run() {
                Dialogs.text(act, name + " address", "Street, city", Prefs.str(act, key, ""), new Dialogs.TextEntered() {
                    @Override
                    public void onText(String text) {
                        Prefs.put(act, key, Ui.isEmpty(text) ? null : text);
                        select(selected);
                    }
                });
            }
        });
    }

    private void note(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Theme.TEXT_DIM);
        t.setLineSpacing(0, 1.15f);
        int p = Ui.dp(act, 14);
        t.setPadding(p, Ui.dp(act, 14), p, Ui.dp(act, 4));
        content.addView(t);
    }

    private void swatches() {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(act, 14);
        row.setPadding(p, Ui.dp(act, 6), p, Ui.dp(act, 4));
        int size = Ui.dp(act, 40);
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            final int color = Theme.ACCENTS[i];
            View dot = new View(act);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            if (color == accent) d.setStroke(Ui.dp(act, 3), 0xFFFFFFFF);
            else d.setStroke(Ui.dp(act, 1), 0x33FFFFFF);
            dot.setBackground(d);
            dot.setContentDescription(Theme.ACCENT_NAMES[i]);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (color == accent) return;
                    Prefs.put(act, Prefs.ACCENT, color);
                    act.restartUi();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = Ui.dp(act, 12);
            row.addView(dot, lp);
        }
        content.addView(row);
        TextView name = new TextView(act);
        int idx = 0;
        for (int i = 0; i < Theme.ACCENTS.length; i++) if (Theme.ACCENTS[i] == accent) idx = i;
        name.setText(Theme.ACCENT_NAMES[idx]);
        name.setTextColor(Theme.TEXT_SECONDARY);
        name.setTextSize(13);
        name.setPadding(p, Ui.dp(act, 4), p, 0);
        content.addView(name);
    }

    private Runnable intent(final String action) {
        return new Runnable() {
            @Override
            public void run() {
                if (!Ui.start(act, new Intent(action))) Ui.start(act, new Intent(Settings.ACTION_SETTINGS));
            }
        };
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }
}
