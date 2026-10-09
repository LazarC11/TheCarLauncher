package com.thecarlauncher.screens;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.media.BluetoothSource;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.media.MusicLibrary;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.nav.NavHelper;
import com.thecarlauncher.radio.RadioController;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** MIB "Podešavanje sistema": title, blue rule, light bars, nested pages. */
public class SettingsScreen extends Screen {
    public static final int PAGE_ROOT = 0, PAGE_NAV = 1, PAGE_MEDIA = 2, PAGE_RADIO = 3;

    private interface Page { void build(); }

    private static final class Entry {
        final String title;
        final Page page;

        Entry(String title, Page page) {
            this.title = title;
            this.page = page;
        }
    }

    private final List<Entry> stack = new ArrayList<>();
    private TextView title;
    private View back, thumb;
    private LinearLayout content;
    private ScrollView scroll;

    public SettingsScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return s("Podešavanje sistema", "System settings"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Mib.px(act, 14), 0, 0);

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Mib.px(act, 30), 0, Mib.px(act, 30), Mib.px(act, 10));
        FrameLayout backBox = new FrameLayout(act);
        backBox.setBackground(Mib.box(act));
        backBox.addView(Mib.icon(act, R.drawable.vw_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 30), Mib.px(act, 30), Gravity.CENTER));
        backBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack();
            }
        });
        back = backBox;
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Mib.px(act, 62), Mib.px(act, 54));
        bl.rightMargin = Mib.px(act, 18);
        head.addView(backBox, bl);
        title = Mib.text(act, "", 40, Mib.semibold(act), Mib.WHITE);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(head);

        View rule = new View(act);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Mib.shade(Mib.BLUE, 0.8f, 0xFF), Mib.BLUE, Mib.BLUE, Mib.shade(Mib.BLUE, 0.8f, 0x80)}));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(2, Mib.px(act, 2.4f)));
        rl.leftMargin = Mib.px(act, 12);
        rl.rightMargin = Mib.px(act, 12);
        r.addView(rule, rl);

        FrameLayout body = new FrameLayout(act);
        scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Mib.px(act, 12), 0, Mib.px(act, 12));
        scroll.addView(content);
        body.addView(scroll);
        thumb = new View(act);
        thumb.setBackgroundColor(Mib.BLUE);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Math.max(2, Mib.px(act, 5)), 0, Gravity.RIGHT);
        tl.rightMargin = Mib.px(act, 30);
        body.addView(thumb, tl);
        scroll.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                updateThumb();
            }
        });
        r.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (stack.isEmpty()) push(title(), new Page() {
            public void build() { buildRoot(); }
        });
        else render();
        return r;
    }

    // ---- Navigation between pages ------------------------------------------------------------

    private void push(String t, Page p) {
        stack.add(new Entry(t, p));
        render();
    }

    private void render() {
        if (content == null || stack.isEmpty()) return;
        Entry e = stack.get(stack.size() - 1);
        title.setText(e.title);
        back.setVisibility(stack.size() > 1 ? View.VISIBLE : View.GONE);
        content.removeAllViews();
        e.page.build();
        scroll.scrollTo(0, 0);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                updateThumb();
            }
        });
    }

    private void refresh() { render(); }

    @Override
    public boolean onBack() {
        if (stack.size() <= 1) return false;
        stack.remove(stack.size() - 1);
        render();
        return true;
    }

    @Override
    public void onShow() {
        render();
    }

    /** Jumps straight to a page (used by the Setup soft keys). */
    public void open(int page) {
        while (stack.size() > 1) stack.remove(stack.size() - 1);
        if (stack.isEmpty()) stack.add(new Entry(title(), new Page() {
            public void build() { buildRoot(); }
        }));
        if (page == PAGE_NAV) push(s("Navigacija", "Navigation"), new Page() {
            public void build() { buildNav(); }
        });
        else if (page == PAGE_MEDIA) push(s("Mediji", "Media"), new Page() {
            public void build() { buildMedia(); }
        });
        else if (page == PAGE_RADIO) push("Radio", new Page() {
            public void build() { buildRadio(); }
        });
        else render();
    }

    private void updateThumb() {
        int h = scroll.getHeight(), ch = content.getHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) thumb.getLayoutParams();
        if (ch <= h || h == 0) {
            thumb.setVisibility(View.INVISIBLE);
            return;
        }
        thumb.setVisibility(View.VISIBLE);
        int th = Math.max(Mib.px(act, 40), h * h / ch);
        int top = (int) ((h - th) * (scroll.getScrollY() / (float) (ch - h)));
        if (lp.height != th) {
            lp.height = th;
            thumb.setLayoutParams(lp);
        }
        thumb.setTranslationY(top);
    }

    // ---- Pages -------------------------------------------------------------------------------

    private void buildRoot() {
        sub(s("Ekran", "Screen"), new Page() { public void build() { buildDisplay(); } });
        LinearLayout amb = sub(s("Ambijentalno osvetljenje", "Ambient lighting"), new Page() { public void build() { buildAmbient(); } });
        // The factory-style page with the interior picture and colour slider replaces the list.
        amb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.AMBIENT, true);
            }
        });
        sub(s("Vreme i datum", "Time and date"), new Page() { public void build() { buildTime(); } });
        LinearLayout lang = sub("Jezik/Language", new Page() { public void build() { buildLanguage(); } });
        addLeading(lang, flag());
        sub(s("Jedinice", "Units"), new Page() { public void build() { buildUnits(); } });
        sub(s("Navigacija", "Navigation"), new Page() { public void build() { buildNav(); } });
        LinearLayout sound = sub(s("Zvuk", "Sound"), new Page() { public void build() { buildMedia(); } });
        sound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.SOUND, true);
            }
        });
        sub(s("Mediji", "Media"), new Page() { public void build() { buildMedia(); } });
        sub("Radio", new Page() { public void build() { buildRadio(); } });
        sub("Bluetooth audio", new Page() { public void build() { buildBluetooth(); } });
        sub("App-Connect", new Page() { public void build() { buildAppConnect(); } });
        sub(s("Telefon i radio", "Phone and radio"), new Page() { public void build() { buildPhoneRadio(); } });
        sub(s("Vozilo", "Vehicle"), new Page() { public void build() { buildVehicle(); } });
        sub(s("Android sistem", "Android system"), new Page() { public void build() { buildAndroid(); } });
        sub(s("Informacije o sistemu", "System information"), new Page() { public void build() { buildAbout(); } });
    }

    private void buildDisplay() {
        toggle(s("Pun ekran (sakrij Android trake)", "Full screen (hide Android bars)"), Prefs.IMMERSIVE, false, true);
        action(s("Osvetljenost", "Brightness"), null, intent(Settings.ACTION_DISPLAY_SETTINGS));
    }

    /** 30 colours for the UI accent ("ambient lighting"); the whole UI follows the choice. */
    private void buildAmbient() {
        int cur = Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT);
        boolean en = "en".equals(Prefs.str(act, Prefs.LANGUAGE, "sr"));
        for (Object[] a : Mib.AMBIENT) {
            final int color = (Integer) a[2];
            LinearLayout row = choice((String) (en ? a[1] : a[0]), color == cur, new Runnable() {
                public void run() {
                    Prefs.put(act, Prefs.AMBIENT, color);
                    act.restartUi();
                }
            });
            View sw = new View(act);
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(Mib.px(act, 3));
            d.setStroke(Math.max(1, Mib.px(act, 1)), 0xFF3A3B44);
            sw.setBackground(d);
            addLeading(row, sw);
        }
    }

    private void buildTime() {
        boolean h24 = Prefs.use24h(act);
        LinearLayout row = bar(s("Format 24 časa", "24-hour format"), null);
        final View box = checkbox(row, h24);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.use24h(act);
                Prefs.put(act, Prefs.CLOCK_24, on);
                setChecked(box, on);
            }
        });
        action(s("Datum i vreme (Android)", "Date and time (Android)"), null, intent(Settings.ACTION_DATE_SETTINGS));
    }

    private void buildLanguage() {
        String cur = Prefs.str(act, Prefs.LANGUAGE, "sr");
        choice("Srpski (latinica)", "sr".equals(cur), new Runnable() {
            public void run() { setLanguage("sr"); }
        });
        choice("English", "en".equals(cur), new Runnable() {
            public void run() { setLanguage("en"); }
        });
    }

    private void setLanguage(String l) {
        Prefs.put(act, Prefs.LANGUAGE, l);
        stack.clear();
        act.restartUi();
    }

    private void buildUnits() {
        final boolean metric = Prefs.metric(act);
        choice(s("Kilometri (km/h)", "Kilometres (km/h)"), metric, new Runnable() {
            public void run() {
                Prefs.put(act, Prefs.UNITS, "kmh");
                refresh();
            }
        });
        choice(s("Milje (mph)", "Miles (mph)"), !metric, new Runnable() {
            public void run() {
                Prefs.put(act, Prefs.UNITS, "mph");
                refresh();
            }
        });
    }

    private void buildNav() {
        String pkg = NavHelper.preferred(act);
        action(s("Aplikacija za navigaciju", "Navigation app"), pkg == null ? s("Nije pronađena", "None found")
                : appLabel(pkg), new Runnable() {
            public void run() {
                appChoice(s("Aplikacija za navigaciju", "Navigation app"), NavHelper.navApps(act), Prefs.NAV_PKG, false);
            }
        });
        address(s("Kuća", "Home"), Prefs.HOME_ADDR);
        address(s("Posao", "Work"), Prefs.WORK_ADDR);
        final String[] names = {s("Dan (MIB)", "Day (MIB)"), s("Noć", "Night"), s("OpenStreetMap (zatamnjena)", "OpenStreetMap (dimmed)"),
                s("Isključena (bez interneta)", "Off (no internet)")};
        final String[] keys = {Prefs.TILES_DAY, Prefs.TILES_DARK, Prefs.TILES_OSM, Prefs.TILES_OFF};
        action(s("Izgled mape", "Map style"), names[indexOf(keys, Prefs.tiles(act))], new Runnable() {
            public void run() {
                push(s("Izgled mape", "Map style"), new Page() {
                    public void build() {
                        for (int i = 0; i < keys.length; i++) {
                            final int idx = i;
                            choice(names[i], keys[i].equals(Prefs.tiles(act)), new Runnable() {
                                public void run() {
                                    Prefs.put(act, Prefs.MAP_TILES, keys[idx]);
                                    act.restartUi();
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void buildMedia() {
        toggle(s("Nastavi reprodukciju pri pokretanju", "Resume playback on start"), Prefs.RESUME_MEDIA, true, false);
        action(s("Duboki - Srednji - Visoki", "Bass - Mid - Treble"), toneSummary(), new Runnable() {
            public void run() { act.show(LauncherActivity.SOUND, true); }
        });
        action(s("Muzička biblioteka", "Music library"), MusicLibrary.hasPermission(act)
                ? s("Ponovo skeniraj", "Rescan") : s("Dozvoli pristup", "Allow access"), new Runnable() {
            public void run() {
                if (MusicLibrary.hasPermission(act)) {
                    MusicLibrary.invalidate();
                    Ui.toast(act, s("Biblioteka će biti ponovo skenirana", "Library will be rescanned"));
                } else {
                    act.requestMissingPermissions(true);
                }
            }
        });
        action(s("Druge aplikacije za medije", "Other media apps"), MediaHub.get(act).hasNotificationAccess()
                        ? s("Povezano", "Connected") : s("Nije povezano", "Not connected"),
                intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    }

    private String toneSummary() {
        com.thecarlauncher.media.Tone t = com.thecarlauncher.media.Tone.get(act);
        return sign(t.bass()) + " / " + sign(t.mid()) + " / " + sign(t.treble());
    }

    private static String sign(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    private void buildBluetooth() {
        String pkg = MediaHub.get(act).bluetooth().appPackage();
        action(s("Aplikacija za BT audio", "BT audio app"), pkg == null ? s("Nije pronađena", "Not found") : appLabel(pkg),
                new Runnable() {
                    public void run() {
                        appChoice(s("Aplikacija za BT audio", "BT audio app"), BluetoothSource.candidates(act), Prefs.BT_PKG, true);
                    }
                });
        toggle(s("Probudi BT aplikaciju pri izboru izvora", "Wake BT app when selecting the source"), Prefs.BT_WAKE, false, false);
        toggle(s("Šalji komande sistemski", "Send controls system-wide"), Prefs.BT_SYSTEM_KEYS, false, false);
    }

    private void buildAppConnect() {
        String pkg = Projection.find(act);
        action(s("Aplikacija (ZLink, CarPlay…)", "App (ZLink, CarPlay…)"), pkg == null ? s("Nije pronađena", "Not found")
                : appLabel(pkg), new Runnable() {
            public void run() {
                appChoice("App-Connect", Projection.candidates(act), Prefs.PROJECTION_PKG, true);
            }
        });
    }

    private void buildPhoneRadio() {
        String phone = Prefs.str(act, Prefs.PHONE_PKG, null);
        action(s("Aplikacija za telefon", "Phone app"), phone == null ? s("Sistemski telefon", "System dialer") : appLabel(phone),
                new Runnable() {
                    public void run() {
                        appChoice(s("Aplikacija za telefon", "Phone app"), new ArrayList<String>(), Prefs.PHONE_PKG, true);
                    }
                });
        String radio = LauncherActivity.radioPackage(act);
        action(s("Aplikacija za radio", "Radio app"), radio == null ? s("Nije pronađena", "Not found") : appLabel(radio),
                new Runnable() {
                    public void run() {
                        appChoice(s("Aplikacija za radio", "Radio app"), LauncherActivity.radioCandidates(act), Prefs.RADIO_PKG, true);
                    }
                });
    }

    private void buildRadio() {
        final RadioController radio = RadioController.get(act);
        boolean ok = radio.available();
        info(s("FM tjuner", "FM tuner"), ok ? "K706 / QuickFish MCU" : radio.error());
        toggle(s("Ugrađeni radio u launcheru", "Built-in launcher radio"), Prefs.RADIO_INTERNAL, true, new Runnable() {
            public void run() {
                if (!Prefs.bool(act, Prefs.RADIO_INTERNAL, true)) radio.stop();
                refresh();
            }
        });
        toggle(s("Samo jake stanice (LOC)", "Strong stations only (LOC)"), Prefs.RADIO_LOC, false, new Runnable() {
            public void run() { radio.setLocal(Prefs.bool(act, Prefs.RADIO_LOC, false)); }
        });
        toggle("RDS", Prefs.RADIO_RDS, true, new Runnable() {
            public void run() { radio.setRds(Prefs.bool(act, Prefs.RADIO_RDS, true)); }
        });
        action(s("Obriši memorisane stanice", "Clear presets"), null, new Runnable() {
            public void run() {
                radio.clearPresets();
                Ui.toast(act, s("Memorisane stanice obrisane", "Presets cleared"));
            }
        });
        if (radio.isActive()) action(s("Isključi radio", "Turn radio off"), null, new Runnable() {
            public void run() {
                radio.stop();
                refresh();
            }
        });
        String app = LauncherActivity.radioPackage(act);
        action(s("Fabrička radio aplikacija", "Factory radio app"), app == null ? s("Nije pronađena", "Not found") : appLabel(app),
                new Runnable() {
                    public void run() {
                        appChoice(s("Aplikacija za radio", "Radio app"), LauncherActivity.radioCandidates(act), Prefs.RADIO_PKG, true);
                    }
                });
    }

    private void buildVehicle() {
        action(s("Naziv vozila", "Vehicle name"), Prefs.vehicle(act), new Runnable() {
            public void run() {
                Dialogs.text(act, s("Naziv vozila", "Vehicle name"), "Opel Corsa C", Prefs.vehicle(act), new Dialogs.TextEntered() {
                    public void onText(String text) {
                        Prefs.put(act, Prefs.VEHICLE, text);
                        refresh();
                    }
                });
            }
        });
    }

    private void buildAndroid() {
        action("Wi-Fi", null, intent(Settings.ACTION_WIFI_SETTINGS));
        action("Bluetooth", null, intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        action(s("Zvuk", "Sound"), null, intent(Settings.ACTION_SOUND_SETTINGS));
        action(s("Podrazumevani pokretač", "Default launcher"), null, new Runnable() {
            public void run() {
                if (!Ui.start(act, new Intent(Settings.ACTION_HOME_SETTINGS))) Ui.start(act, new Intent(Settings.ACTION_SETTINGS));
            }
        });
        action(s("Dozvole", "Permissions"), null, new Runnable() {
            public void run() { act.requestMissingPermissions(true); }
        });
        action(s("Sva Android podešavanja", "All Android settings"), null, intent(Settings.ACTION_SETTINGS));
    }

    private void buildAbout() {
        String version = "";
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception ignored) {
        }
        info("Car Launcher", version);
        info(s("Uređaj", "Head unit"), Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        info(s("Ekran", "Screen"), act.getResources().getDisplayMetrics().widthPixels + "×"
                + act.getResources().getDisplayMetrics().heightPixels + " · " + act.getResources().getDisplayMetrics().densityDpi + " dpi");
        MediaHub hub = MediaHub.get(act);
        BluetoothSource bt = hub.bluetooth();
        info(s("BT servis za medije", "BT media service"), bt.systemServiceFound()
                ? (bt.systemConnected() ? s("Povezan", "Connected") : s("Povezivanje…", "Connecting…"))
                : s("Nije dostupan", "Not available"));
        String btPkg = bt.appPackage();
        info(s("BT audio aplikacija", "BT audio app"), btPkg == null ? "—" : btPkg);
        info(s("Pristup medijima", "Media access"), hub.hasNotificationAccess() ? s("Da", "Yes") : s("Ne", "No"));
        StringBuilder sb = new StringBuilder();
        for (String k : hub.sources()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k.equals(MediaHub.BLUETOOTH) ? "BT" : k);
        }
        info(s("Izvori", "Sources"), sb.toString());
        String proj = Projection.find(act);
        info("App-Connect", proj == null ? "—" : proj);
        info(s("Podaci mape", "Map data"), "© OpenStreetMap, © CARTO");
    }

    // ---- Choice helpers ----------------------------------------------------------------------

    private void appChoice(String t, final List<String> preferred, final String prefKey, final boolean allApps) {
        push(t, new Page() {
            public void build() {
                List<String> pkgs = new ArrayList<>(preferred);
                if (allApps || pkgs.isEmpty()) {
                    for (AppRepository.App a : AppRepository.get(act).all()) if (!pkgs.contains(a.pkg)) pkgs.add(a.pkg);
                }
                String cur = Prefs.str(act, prefKey, null);
                if (Prefs.PHONE_PKG.equals(prefKey)) {
                    choice(s("Sistemski telefon", "System dialer"), cur == null, new Runnable() {
                        public void run() {
                            Prefs.put(act, prefKey, (String) null);
                            onBack();
                        }
                    });
                }
                for (final String p : pkgs) {
                    LinearLayout row = choice(appLabel(p), p.equals(cur), new Runnable() {
                        public void run() {
                            Prefs.put(act, prefKey, p);
                            onBack();
                        }
                    });
                    AppRepository.App a = AppRepository.get(act).findPackage(p);
                    Drawable d = a != null ? a.icon : null;
                    if (d == null) {
                        try {
                            d = act.getPackageManager().getApplicationIcon(p);
                        } catch (Exception ignored) {
                        }
                    }
                    if (d != null) {
                        ImageView iv = new ImageView(act);
                        iv.setImageDrawable(d);
                        addLeading(row, iv);
                    }
                }
            }
        });
    }

    private void address(final String name, final String key) {
        String addr = Prefs.str(act, key, null);
        action(name, Ui.isEmpty(addr) ? s("Nije podešeno", "Not set") : addr, new Runnable() {
            public void run() {
                Dialogs.text(act, name, s("Ulica, grad", "Street, city"), Prefs.str(act, key, ""), new Dialogs.TextEntered() {
                    public void onText(String text) {
                        Prefs.put(act, key, Ui.isEmpty(text) ? null : text);
                        refresh();
                    }
                });
            }
        });
    }

    // ---- Bar builders ------------------------------------------------------------------------

    private LinearLayout bar(String text, String value) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Mib.settingsBar(act));
        row.setPadding(Mib.px(act, 26), 0, Mib.px(act, 22), 0);
        TextView t = Mib.text(act, text, 30, Mib.regular(act), Mib.BAR_TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = Mib.text(act, value, 24, Mib.regular(act), Mib.BAR_VALUE);
            v.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            vl.leftMargin = Mib.px(act, 16);
            v.setMaxWidth(Mib.px(act, 380));
            row.addView(v, vl);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 78));
        lp.leftMargin = Mib.px(act, 26);
        lp.rightMargin = Mib.px(act, 58);
        lp.bottomMargin = Mib.px(act, 8);
        content.addView(row, lp);
        return row;
    }

    private LinearLayout sub(String text, final Page p) {
        final String t = text;
        LinearLayout row = bar(text, null);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                push(t, p);
            }
        });
        return row;
    }

    private void action(String text, String value, final Runnable r) {
        LinearLayout row = bar(text, value);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
    }

    private void info(String text, String value) {
        LinearLayout row = bar(text, value == null || value.length() == 0 ? "—" : value);
        row.setClickable(false);
    }

    private LinearLayout choice(String text, boolean selected, final Runnable r) {
        LinearLayout row = bar(text, null);
        checkbox(row, selected);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        return row;
    }

    private void toggle(String text, final String key, final boolean def, final Runnable after) {
        LinearLayout row = bar(text, null);
        final View box = checkbox(row, Prefs.bool(act, key, def));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.bool(act, key, def);
                Prefs.put(act, key, on);
                setChecked(box, on);
                after.run();
            }
        });
    }

    private void toggle(String text, final String key, final boolean def, final boolean restart) {
        LinearLayout row = bar(text, null);
        final View box = checkbox(row, Prefs.bool(act, key, def));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.bool(act, key, def);
                Prefs.put(act, key, on);
                setChecked(box, on);
                if (restart) act.restartUi();
            }
        });
    }

    /** Factory checkbox glyph, drawn dark on the light bars. */
    private View checkbox(LinearLayout row, boolean on) {
        ImageView box = Mib.icon(act, R.drawable.vw_ic_check_off, 0xFF1C1C22);
        row.addView(box, new LinearLayout.LayoutParams(Mib.px(act, 46), Mib.px(act, 46)));
        setChecked(box, on);
        return box;
    }

    private static void setChecked(View box, boolean on) {
        ((ImageView) box).setImageResource(on ? R.drawable.vw_ic_check_on : R.drawable.vw_ic_check_off);
    }

    private void addLeading(LinearLayout row, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 30));
        lp.rightMargin = Mib.px(act, 18);
        row.addView(v, 0, lp);
    }

    /** Serbian tricolour, as the factory language bar shows the country flag. */
    private View flag() {
        ImageView f = new ImageView(act);
        f.setImageResource(R.drawable.vw_ic_flag_rs);
        f.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return f;
    }

    private String appLabel(String pkg) {
        AppRepository.App a = AppRepository.get(act).findPackage(pkg);
        return a != null ? a.label : NavHelper.label(act, pkg);
    }

    private Runnable intent(final String action) {
        return new Runnable() {
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
