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
    public static final int PAGE_ROOT = 0, PAGE_NAV = 1, PAGE_MEDIA = 2, PAGE_RADIO = 3, PAGE_VEHICLE = 4;

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
        backBox.addView(Mib.icon(act, R.drawable.ui_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 30), Mib.px(act, 30), Gravity.CENTER));
        backBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!onBack()) act.goBack();
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
        act.updateBackArrow();
        content.removeAllViews();
        e.page.build();
        // Pages fade in like the rest of the launcher (not when only refreshing values).
        if (!quietRender) {
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(180).start();
        }
        quietRender = false;
        scroll.scrollTo(0, 0);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                updateThumb();
            }
        });
    }

    private void refresh() {
        quietRender = true;
        render();
    }

    @Override
    public boolean hasOwnBack() { return stack.size() > 1; }

    @Override
    public boolean onBack() {
        if (stack.size() <= 1) return false;
        // Opened straight on a page from another screen: back returns to that screen.
        if (jumped && stack.size() == 2) {
            jumped = false;
            stack.remove(stack.size() - 1);
            quietRender = true;
            render();
            return false;
        }
        stack.remove(stack.size() - 1);
        render();
        return true;
    }

    @Override
    public void onShow() {
        jumped = false;
        render();
    }

    /** True while the page was opened directly from another screen (Setup soft keys). */
    private boolean jumped;

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
        else if (page == PAGE_VEHICLE) push(s("Vozilo", "Vehicle"), new Page() {
            public void build() { buildVehicle(); }
        });
        else render();
        jumped = stack.size() == 2;
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
        final com.thecarlauncher.nav.OfflineMaps om = com.thecarlauncher.nav.OfflineMaps.get(act);
        int nMaps = om.files().size();
        action(s("Offline mape", "Offline maps"), nMaps == 0 ? s("Nijedna preuzeta", "None downloaded")
                : nMaps + s(" preuzeto", " downloaded"), new Runnable() {
            public void run() {
                push(s("Offline mape", "Offline maps"), new Page() {
                    public void build() { buildOfflineMaps(); }
                });
            }
        });
        address(s("Kuća", "Home"), Prefs.HOME_ADDR);
        address(s("Posao", "Work"), Prefs.WORK_ADDR);
        toggle(s("Glasovne poruke navigacije", "Voice guidance"), com.thecarlauncher.nav.Guidance.P_VOICE, true, false);
        action(s("Obriši poslednja odredišta", "Clear recent destinations"), null, new Runnable() {
            public void run() {
                Prefs.put(act, com.thecarlauncher.nav.Guidance.P_RECENT, "");
                Ui.toast(act, s("Obrisano", "Cleared"));
            }
        });
        String ck = Prefs.str(act, Prefs.CARTO_KEY, null);
        action(s("CARTO ključ za online mapu", "CARTO key for the online map"),
                ck == null || ck.trim().isEmpty() ? s("Nije unet – koristi se OpenStreetMap", "Not set – OpenStreetMap is used")
                        : s("Unet", "Set"), new Runnable() {
            public void run() {
                Dialogs.text(act, s("CARTO API ključ (besplatan: carto.com/basemaps/apikey)", "CARTO API key (free: carto.com/basemaps/apikey)"),
                        "key", Prefs.str(act, Prefs.CARTO_KEY, ""), new Dialogs.TextEntered() {
                            public void onText(String text) {
                                Prefs.put(act, Prefs.CARTO_KEY, text == null ? "" : text.trim());
                                act.restartUi();
                            }
                        });
            }
        });
        final String[] names = {s("Dan", "Day"), s("Noć", "Night"), s("OpenStreetMap (zatamnjena)", "OpenStreetMap (dimmed)"),
                s("Isključena (bez interneta)", "Off (no internet)")};
        final String[] keys = {Prefs.TILES_DAY, Prefs.TILES_DARK, Prefs.TILES_OSM, Prefs.TILES_OFF};
        action(s("Izgled mape", "Map style"), names[indexOf(keys, Prefs.str(act, Prefs.MAP_TILES, Prefs.TILES_DAY))], new Runnable() {
            public void run() {
                push(s("Izgled mape", "Map style"), new Page() {
                    public void build() {
                        for (int i = 0; i < keys.length; i++) {
                            final int idx = i;
                            choice(names[i], keys[i].equals(Prefs.str(act, Prefs.MAP_TILES, Prefs.TILES_DAY)), new Runnable() {
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

    // ---- Offline maps ------------------------------------------------------------------------

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable offlineTick = new Runnable() {
        public void run() {
            // Refresh download progress while the offline-maps page is open.
            if (stack.isEmpty() || !s("Offline mape", "Offline maps").equals(stack.get(stack.size() - 1).title)) return;
            refreshKeep();
        }
    };

    private void buildOfflineMaps() {
        final com.thecarlauncher.nav.OfflineMaps om = com.thecarlauncher.nav.OfflineMaps.get(act);
        info(s("Mape rade bez interneta i crtaju se na uređaju.", "Maps work without internet and are drawn on the unit."), null);
        toggle(s("Koristi offline mapu", "Use offline map"), com.thecarlauncher.nav.OfflineMaps.P_ON, true, new Runnable() {
            public void run() { com.thecarlauncher.views.TileCache.get(act).clearOffline(); }
        });
        for (String p : om.progress()) info(s("Preuzimanje: ", "Downloading: ") + p, null);
        if (om.downloading()) {
            ui.removeCallbacks(offlineTick);
            ui.postDelayed(offlineTick, 2000);
        }
        for (final java.io.File f : om.files()) {
            String name = f.getName().replace(".map", "").replace('-', ' ');
            action(name.substring(0, 1).toUpperCase() + name.substring(1), com.thecarlauncher.nav.OfflineMaps.size(f)
                    + s(" · dodirni za brisanje", " · tap to delete"), new Runnable() {
                public void run() {
                    Dialogs.choose(act, s("Obriši mapu?", "Delete map?"),
                            new String[]{s("Obriši", "Delete"), s("Otkaži", "Cancel")}, -1, new Dialogs.Chosen() {
                                public void onChosen(int i) {
                                    if (i != 0) return;
                                    om.delete(f);
                                    com.thecarlauncher.views.TileCache.get(act).clearOffline();
                                    refreshKeep();
                                }
                            });
                }
            });
        }
        action(s("Preuzmi mapu", "Download a map"), s("Države i regioni", "Countries and regions"), new Runnable() {
            public void run() { pushRegions(""); }
        });
        info(s("Podaci: © OpenStreetMap saradnici (ODbL), datoteke: mapsforge.org", "Data: © OpenStreetMap contributors (ODbL), files: mapsforge.org"), null);
    }

    private boolean quietRender;

    private void refreshKeep() {
        int y = scroll.getScrollY();
        quietRender = true;
        render();
        scroll.scrollTo(0, y);
    }

    /** Browses the map server: continents, then countries (and regions of big countries). */
    private void pushRegions(final String path) {
        String t = path.isEmpty() ? s("Preuzmi mapu", "Download a map")
                : path.substring(0, path.length() - 1).replace('/', ' ').replace('-', ' ');
        final java.util.List<com.thecarlauncher.nav.OfflineMaps.Region>[] got = new java.util.List[1];
        final String[] err = new String[1];
        push(t, new Page() {
            public void build() {
                if (got[0] == null && err[0] == null) {
                    info(s("Učitavanje liste…", "Loading the list…"), null);
                    return;
                }
                if (err[0] != null) {
                    info(s("Lista nije dostupna (potreban je internet): ", "List not available (needs internet): ") + err[0], null);
                    return;
                }
                for (final com.thecarlauncher.nav.OfflineMaps.Region r : got[0]) {
                    action(r.name, r.folder ? "›" : r.size, new Runnable() {
                        public void run() {
                            if (r.folder) {
                                pushRegions(r.path);
                            } else {
                                com.thecarlauncher.nav.OfflineMaps.get(act).download(r);
                                Ui.toast(act, s("Preuzimanje počelo: ", "Download started: ") + r.name);
                                // Back to the offline-maps page to watch the progress.
                                while (stack.size() > 1 && !s("Offline mape", "Offline maps").equals(stack.get(stack.size() - 1).title)) {
                                    stack.remove(stack.size() - 1);
                                }
                                render();
                            }
                        }
                    });
                }
            }
        });
        com.thecarlauncher.nav.OfflineMaps.get(act).list(path, new com.thecarlauncher.nav.OfflineMaps.Listed() {
            public void onListed(java.util.List<com.thecarlauncher.nav.OfflineMaps.Region> regions, String error) {
                got[0] = regions;
                err[0] = error;
                if (regions != null && regions.isEmpty() && error == null) err[0] = s("prazno", "empty");
                refreshKeep();
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
        if (com.thecarlauncher.util.Storage.needsAllFiles()) {
            boolean files = com.thecarlauncher.util.Storage.hasAllFiles();
            action(s("Pristup svim fajlovima (USB)", "All files access (USB)"),
                    files ? s("Dozvoljeno", "Allowed") : s("Nije dozvoljeno – dodirni", "Not allowed – tap"), new Runnable() {
                        public void run() { com.thecarlauncher.util.Storage.requestAllFiles(act); }
                    });
        }
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
        AppRepository.App screen = MediaHub.get(act).bluetooth().appScreen();
        String pkg = MediaHub.get(act).bluetooth().appPackage();
        action(s("Aplikacija za BT audio", "BT audio app"), screen != null ? screen.label
                        : pkg == null ? s("Nije pronađena", "Not found") : appLabel(pkg),
                new Runnable() {
                    public void run() {
                        push(s("Aplikacija za BT audio", "BT audio app"), new Page() {
                            public void build() { buildBtAppChoice(); }
                        });
                    }
                });
        action(s("Bluetooth uređaji", "Bluetooth devices"), s("poveži / novi uređaj", "connect / new device"), new Runnable() {
            public void run() { act.show(LauncherActivity.BTDEVICES, true); }
        });
        toggle(s("BT audio čim se telefon poveže", "BT audio as soon as the phone connects"),
                Prefs.BT_AUTO_SELECT, true, false);
        toggle(s("Otvori BT aplikaciju na trenutak pri izboru BT audio", "Open the BT app briefly when choosing BT audio"),
                Prefs.BT_OPEN_APP, false, false);
        if (!com.thecarlauncher.util.BringBack.canOverlay(act)) {
            action(s("Dozvoli povratak iz BT aplikacije", "Allow returning from the BT app"),
                    s("Prikaz preko drugih aplikacija: isključeno", "Display over other apps: off"), new Runnable() {
                        public void run() { com.thecarlauncher.util.BringBack.requestOverlay(act); }
                    });
        }
        toggle(s("Omot pesme sa interneta", "Cover art from the internet"), Prefs.ONLINE_COVER, true, new Runnable() {
            public void run() { MediaHub.get(act).radioChanged(); }
        });
        toggle(s("Vrati se u launcher posle pokretanja BT aplikacije", "Return to the launcher after starting the BT app"),
                Prefs.BT_WAKE, true, false);
        final int[] waits = {800, 1200, 2000, 3000};
        int w = Prefs.integer(act, Prefs.BT_WAKE_MS, 1200);
        action(s("Koliko dugo ostaje BT aplikacija", "How long the BT app stays open"), String.format(java.util.Locale.US, "%.1f s", w / 1000f),
                new Runnable() {
                    public void run() {
                        String[] o = {"0.8 s", "1.2 s", "2 s", "3 s"};
                        int c = 1;
                        for (int i = 0; i < waits.length; i++) if (waits[i] == Prefs.integer(act, Prefs.BT_WAKE_MS, 1200)) c = i;
                        Dialogs.choose(act, s("Koliko dugo ostaje BT aplikacija", "How long the BT app stays open"), o, c,
                                new Dialogs.Chosen() {
                                    public void onChosen(int i) {
                                        Prefs.put(act, Prefs.BT_WAKE_MS, waits[i]);
                                        refresh();
                                    }
                                });
                    }
                });
        int bc = Prefs.integer(act, Prefs.BT_CHANNEL, 0), learned = Prefs.integer(act, Prefs.BT_CHANNEL_LEARNED, 0);
        String bcText = bc == 0 ? s("Isključeno", "Off") : bc > 0 ? s("Kanal ", "Channel ") + bc
                : s("Automatski", "Automatic") + (learned > 0 ? " (" + learned + ")" : s(" (još nije naučen)", " (not learned yet)"));
        action(s("MCU kanal za BT zvuk", "MCU channel for BT audio"), bcText, new Runnable() {
            public void run() {
                final String[] opts = {s("Automatski (uči iz fabričke BT aplikacije)", "Automatic (learn from the factory BT app)"),
                        s("Isključeno (ne menjaj kanal)", "Off (leave the channel alone)"),
                        "1", "3", "5", "6", "7", "8"};
                final int[] vals = {-1, 0, 1, 3, 5, 6, 7, 8};
                int cur = 0;
                for (int i = 0; i < vals.length; i++) if (vals[i] == Prefs.integer(act, Prefs.BT_CHANNEL, 0)) cur = i;
                Dialogs.choose(act, s("MCU kanal za BT zvuk", "MCU channel for BT audio"), opts, cur, new Dialogs.Chosen() {
                    public void onChosen(int i) {
                        Prefs.put(act, Prefs.BT_CHANNEL, vals[i]);
                        refresh();
                    }
                });
            }
        });
        final com.thecarlauncher.media.GocBluetooth gocBt = com.thecarlauncher.media.GocBluetooth.get(act);
        checkRowWithAction(s("Čitaj BT modul direktno (bez fabričke BT aplikacije)", "Read the BT module directly (no factory BT app)"),
                gocBt.enabled(), new Runnable() {
                    public void run() {
                        boolean on = !gocBt.enabled();
                        Prefs.put(act, com.thecarlauncher.media.GocBluetooth.PREF, on);
                        gocBt.start();
                        if (on) {
                            Ui.toast(act, s("Uključeno. Fabrička BT aplikacija tada gubi poruke modula (kontrole, pesma). Isključi ako ona postoji.",
                                    "On. The factory BT app then loses module messages (controls, song). Switch off if it is installed."));
                        }
                        refresh();
                    }
                });
        action(s("Dijagnostika (QF / Bluetooth)", "Diagnostics (QF / Bluetooth)"), null, new Runnable() {
            public void run() {
                push(s("Dijagnostika", "Diagnostics"), new Page() {
                    public void build() { buildBtDiagnostics(); }
                });
            }
        });
        toggle(s("Šalji komande sistemski", "Send controls system-wide"), Prefs.BT_SYSTEM_KEYS, false, false);
    }

    /**
     * BT audio app: exactly the entries shown in Aplikacije (same names and icons, hidden
     * factory screens included), Bluetooth-looking ones first. Stored as "package/class".
     */
    private void buildBtAppChoice() {
        final String cur = Prefs.str(act, Prefs.BT_PKG, null);
        choice(s("Automatski (prepoznaj iz media sesije)", "Automatic (detect from media session)"), cur == null,
                new Runnable() {
                    public void run() {
                        Prefs.put(act, Prefs.BT_PKG, (String) null);
                        onBack();
                    }
                });
        BluetoothSource bt = MediaHub.get(act).bluetooth();
        List<AppRepository.App> first = new ArrayList<>(), rest = new ArrayList<>();
        for (AppRepository.App a : AppRepository.get(act).all()) {
            if (a.pkg.startsWith("@")) continue;
            String hay = (a.pkg + " " + a.label + " " + a.cls).toLowerCase(java.util.Locale.ROOT);
            boolean btLike = a.hidden || bt.looksLikeBluetooth(a.pkg) || hay.contains("bluetooth") || hay.contains("btmusic")
                    || hay.matches(".*\\bbt\\b.*") || hay.contains("a2dp");
            (btLike ? first : rest).add(a);
        }
        first.addAll(rest);
        for (final AppRepository.App a : first) {
            boolean sel = cur != null && (cur.equals(a.key()) || (!cur.contains("/") && cur.equals(a.pkg) && !a.hidden));
            LinearLayout row = choice(a.label, sel, new Runnable() {
                public void run() {
                    Prefs.put(act, Prefs.BT_PKG, a.key());
                    onBack();
                }
            });
            if (a.icon != null) {
                ImageView iv = new ImageView(act);
                iv.setImageDrawable(a.icon);
                addLeading(row, iv);
            }
        }
    }

    /** What this unit exposes for Bluetooth music: QF packages, hidden screens, QF broadcasts. */
    private void buildBtDiagnostics() {
        com.thecarlauncher.media.QfMediaBridge qf = com.thecarlauncher.media.QfMediaBridge.get(act);
        AppRepository.App screen = MediaHub.get(act).bluetooth().appScreen();
        info(s("BT ekran", "BT screen"), screen == null ? "—" : screen.key());
        info(s("BT_STATE", "BT_STATE"), qf.btState() < 0 ? "—" : String.valueOf(qf.btState()));
        final RadioController rc = RadioController.get(act);
        int ch = rc.mcuChannel();
        info(s("MCU audio kanal sada", "MCU audio channel now"), ch < 0 ? s("nedostupan", "unavailable") + " (" + rc.error() + ")"
                : ch + (ch == 2 ? " (FM)" : ch == 4 ? " (Android)" : ""));
        info(s("Naučen BT kanal", "Learned BT channel"), String.valueOf(Prefs.integer(act, Prefs.BT_CHANNEL_LEARNED, 0)));
        // Try each input of the audio switch while the phone plays music; the one with sound is BT.
        for (final int n : new int[]{1, 3, 5, 6, 7, 8, 4}) {
            action(s("Testiraj kanal ", "Test channel ") + n + (n == 4 ? s(" (vrati Android)", " (back to Android)") : ""),
                    s("dodirni i slušaj", "tap and listen"), new Runnable() {
                        public void run() {
                            boolean ok = rc.routeChannel(n);
                            Ui.toast(act, ok ? s("MCU kanal → ", "MCU channel → ") + n
                                    : s("MCU nije dostupan", "MCU not available"));
                            refresh();
                        }
                    });
        }
        com.thecarlauncher.media.PhoneStatus ps = com.thecarlauncher.media.PhoneStatus.get(act);
        info(s("Telefon (baterija / signal)", "Phone (battery / signal)"), (ps.connected() ? "BT ✓  " : "BT ✗  ")
                + ps.battery() + " / " + ps.signal());
        String last = qf.lastBroadcast();
        if (last == null) {
            info(s("Poslednja QF poruka", "Last QF broadcast"), s("još nijedna — pusti BT muziku", "none yet — play BT music"));
        } else {
            for (String line : last.split("\n")) info(line.trim(), null);
        }
        MediaHub hub = MediaHub.get(act);
        if (hub.hasNotificationAccess()) {
            info(s("Pristup obaveštenjima", "Notification access"), "✓");
        } else {
            final MediaHub h = hub;
            action(s("Pristup obaveštenjima", "Notification access"), s("✗ — dodirni da uključiš", "✗ — tap to enable"),
                    new Runnable() {
                        public void run() { h.openNotificationAccess(); }
                    });
        }
        info(s("BT paket (iz media sesije)", "BT package (from media session)"), Prefs.str(act, Prefs.BT_SESSION_PKG, "—"));
        for (String p : hub.sessionPackages()) info(s("Media sesija", "Media session"), p);
        String ni = hub.notificationInfo();
        info(s("Media obaveštenje", "Media notification"), ni == null ? "—" : (ni.length() > 60 ? ni.substring(0, 60) + "…" : ni));
        info(s("Trenutni izvor / naslov", "Current source / title"), hub.packageName() + " / " + hub.title());
        for (AppRepository.App a : AppRepository.get(act).all()) {
            if (a.hidden) info((a.label.length() > 28 ? a.label.substring(0, 28) : a.label), a.key());
        }
        // ---- Direct sources: MCU packets, system services, BT app components ----
        final com.thecarlauncher.media.GocBluetooth goc = com.thecarlauncher.media.GocBluetooth.get(act);
        info(s("GOC Bluetooth servis", "GOC Bluetooth daemon"), goc.status());
        info("/dev/goc_serial", goc.serialInfo());
        String tr = goc.traffic();
        for (int i = 0; i < tr.length(); i += 56) info((i == 0 ? s("GOC primljeno: ", "GOC received: ") : "") + tr.substring(i, Math.min(tr.length(), i + 56)), null);
        info(s("GOC pesma", "GOC song"), goc.hasSong() ? goc.title() + " — " + goc.artist() : "—");
        action(s("GOC: zatraži podatke o pesmi", "GOC: ask for song info"), "AT#MK", new Runnable() {
            public void run() {
                goc.start();
                boolean ok = goc.send("MK") & goc.send("VI");
                Ui.toast(act, ok ? s("Poslato — osveži za 2 s", "Sent — refresh in 2 s")
                        : s("GOC nije povezan: ", "GOC not connected: ") + goc.status());
            }
        });
        String fed = goc.appSongInfo();
        info(s("Pesma od BT aplikacije (broadcast)", "Song from BT app (broadcast)"), fed != null ? fed : s("ništa nije stiglo", "nothing received"));
        String cp = goc.connectProp();
        info("sys.qf.bt.is.connect", cp == null ? s("nečitljivo", "unreadable") : cp.isEmpty() ? s("prazno", "empty") : cp);
        String pp = goc.progressProp();
        info("sys.qf.bt.music.progress", pp == null ? s("nečitljivo", "unreadable") : pp.isEmpty() ? s("prazno", "empty") : pp);
        info(s("GOC brojevi uz pesmu (MI)", "GOC song numbers (MI)"), goc.lastMi() != null ? goc.lastMi() : "—");
        info(s("GOC pozicija (MP)", "GOC position (MP)"), goc.lastMp() != null ? goc.lastMp() : "—");
        info(s("GOC trajanje / pozicija", "GOC length / position"), Ui.time(Math.max(0, goc.lengthMs())) + " / " + Ui.time(Math.max(0, goc.positionMs())));
        for (String l : goc.lastLines()) info("GOC ← " + l, null);
        action(s("Proba BT servisa (pusti muziku)", "Probe BT daemon (play music)"), s("traje ~10 s", "takes ~10 s"), new Runnable() {
            public void run() {
                Ui.toast(act, s("Proba traje desetak sekundi…", "Probing for about ten seconds…"));
                new Thread(new Runnable() {
                    public void run() {
                        final java.util.List<String> res = com.thecarlauncher.media.BtProbe.run();
                        act.runOnUiThread(new Runnable() {
                            public void run() {
                                push(s("Proba BT servisa", "BT daemon probe"), new Page() {
                                    public void build() {
                                        if (res.isEmpty()) info(s("Ništa nije pronađeno", "Nothing found"), null);
                                        for (String l : res) {
                                            // Long results are split over several rows.
                                            for (int i = 0; i < l.length(); i += 56) info(l.substring(i, Math.min(l.length(), i + 56)), null);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }, "bt-probe").start();
            }
        });
        info(s("— MCU poruke (ne-radio) —", "— MCU packets (non-radio) —"), null);
        java.util.List<String> sum = com.thecarlauncher.radio.McuSniffer.get().summary();
        if (sum.isEmpty()) info(s("nijedna još", "none yet"), null);
        for (String l : sum) info(l, null);
        for (String t : com.thecarlauncher.radio.McuSniffer.get().texts()) info(s("Tekst ", "Text ") + t, null);
        info(s("— Sistemski servisi (BT / QF) —", "— System services (BT / QF) —"), null);
        try {
            String[] services = (String[]) Class.forName("android.os.ServiceManager").getMethod("listServices").invoke(null);
            if (services != null) {
                for (String sv : services) {
                    String l = sv.toLowerCase(java.util.Locale.ROOT);
                    if (l.contains("bt") || l.contains("blue") || l.contains("qf") || l.contains("music") || l.contains("avrcp")
                            || l.contains("a2dp") || l.contains("mcu") || l.contains("car")) info(sv, null);
                }
            }
        } catch (Throwable t) {
            info(s("lista servisa nedostupna", "service list unavailable"), t.getClass().getSimpleName());
        }
        String btPkg = Prefs.str(act, Prefs.BT_PKG, null);
        if (btPkg != null && btPkg.contains("/")) btPkg = btPkg.substring(0, btPkg.indexOf('/'));
        if (btPkg != null) {
            info(s("— Delovi BT aplikacije —", "— BT app components —"), btPkg);
            try {
                android.content.pm.PackageInfo pi = act.getPackageManager().getPackageInfo(btPkg,
                        android.content.pm.PackageManager.GET_SERVICES | android.content.pm.PackageManager.GET_PROVIDERS
                                | android.content.pm.PackageManager.GET_RECEIVERS);
                if (pi.services != null) for (android.content.pm.ServiceInfo si : pi.services)
                    info("S " + si.name.replace(btPkg, ""), si.exported ? "exported" : "—");
                if (pi.providers != null) for (android.content.pm.ProviderInfo pr : pi.providers)
                    info("P " + pr.authority, pr.exported ? "exported" : "—");
                if (pi.receivers != null) for (android.content.pm.ActivityInfo ri : pi.receivers)
                    info("R " + ri.name.replace(btPkg, ""), ri.exported ? "exported" : "—");
            } catch (Exception e) {
                info(s("nedostupno", "unavailable"), e.getClass().getSimpleName());
            }
        }
        java.util.List<String> qfPkgs = new ArrayList<>();
        try {
            for (android.content.pm.ApplicationInfo ai : act.getPackageManager().getInstalledApplications(0)) {
                if (ai.packageName.startsWith("com.qf") || ai.packageName.contains("bluetooth")) qfPkgs.add(ai.packageName);
            }
        } catch (Exception ignored) {
        }
        java.util.Collections.sort(qfPkgs);
        for (String p : qfPkgs) info(p, appLabel(p));
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
        final com.thecarlauncher.radio.RadioLogos logos = com.thecarlauncher.radio.RadioLogos.get(act);
        toggle(s("Logotipi stanica sa interneta", "Station logos from the internet"), com.thecarlauncher.radio.RadioLogos.P_ON, true, null);
        action(s("Preuzmi logotipe pronađenih stanica", "Download logos for found stations"),
                logos.count() + s(" sačuvano", " saved"), new Runnable() {
                    public void run() {
                        logos.downloadAll(radio, new Runnable() {
                            public void run() { refreshKeep(); }
                        });
                    }
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
        textRow(s("Marka", "Brand"), Prefs.VEHICLE_BRAND, "Opel");
        textRow(s("Model", "Model"), Prefs.VEHICLE_MODEL, "Corsa C");
        pictureRow(s("Slika vozila (Status vozila)", "Car picture (Vehicle status)"), com.thecarlauncher.util.CarImages.EXTERIOR);
        pictureRow(s("Slika unutrašnjosti (Ambijentalno)", "Interior picture (Ambient lighting)"), com.thecarlauncher.util.CarImages.INTERIOR);
    }

    private void textRow(final String label, final String key, String hint) {
        String v = Prefs.str(act, key, "");
        final String h = hint;
        action(label, v.isEmpty() ? "—" : v, new Runnable() {
            public void run() {
                Dialogs.text(act, label, h, Prefs.str(act, key, ""), new Dialogs.TextEntered() {
                    public void onText(String text) {
                        Prefs.put(act, key, text == null ? "" : text.trim());
                        refresh();
                    }
                });
            }
        });
    }

    /** Choose a picture from storage, or go back to the built-in one. */
    private void pictureRow(String label, final int which) {
        final boolean custom = com.thecarlauncher.util.CarImages.isCustom(act, which);
        action(label, custom ? s("Sopstvena slika", "Own picture") : s("Ugrađena", "Built-in"), new Runnable() {
            public void run() {
                if (!custom) {
                    pick(which);
                    return;
                }
                Dialogs.choose(act, s("Slika", "Picture"), new String[]{s("Izaberi drugu sliku", "Choose another picture"),
                        s("Vrati ugrađenu", "Back to built-in")}, -1, new Dialogs.Chosen() {
                    public void onChosen(int i) {
                        if (i == 0) pick(which);
                        else {
                            com.thecarlauncher.util.CarImages.reset(act, which);
                            act.restartUi();
                        }
                    }
                });
            }
        });
    }

    private void pick(final int which) {
        act.pickImage(new LauncherActivity.ImagePicked() {
            public void onPicked(android.net.Uri uri) {
                if (com.thecarlauncher.util.CarImages.set(act, which, uri)) {
                    Ui.toast(act, s("Slika je sačuvana", "Picture saved"));
                    act.restartUi();
                } else {
                    Ui.toast(act, s("Slika ne može da se otvori", "The picture can't be opened"));
                }
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
                    for (AppRepository.App a : AppRepository.get(act).all()) {
                        if (!a.hidden && !pkgs.contains(a.pkg)) pkgs.add(a.pkg);
                    }
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
                    // Entries are packages, or "package/class" for hidden factory screens.
                    AppRepository.App screen = p.contains("/") ? AppRepository.get(act).find(p) : null;
                    if (p.contains("/") && screen == null) continue;
                    LinearLayout row = choice(screen != null ? screen.label + "  (" + appLabel(screen.pkg) + ")" : appLabel(p),
                            p.equals(cur), new Runnable() {
                        public void run() {
                            Prefs.put(act, prefKey, p);
                            onBack();
                        }
                    });
                    AppRepository.App a = screen != null ? screen : AppRepository.get(act).findPackage(p);
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
        // Stored as "name|lat|lon" once picked from a search; show just the name.
        final String shown = addr == null ? null : addr.split("\\|")[0];
        action(name, Ui.isEmpty(shown) ? s("Nije podešeno", "Not set") : shown, new Runnable() {
            public void run() {
                Dialogs.text(act, name, s("Ulica, grad", "Street, city"), shown == null ? "" : shown, new Dialogs.TextEntered() {
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

    private void checkRowWithAction(String text, boolean on, final Runnable r) {
        LinearLayout row = bar(text, null);
        checkbox(row, on);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
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
                if (after != null) after.run();
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
        ImageView box = Mib.icon(act, R.drawable.ui_ic_check_off, 0xFF1C1C22);
        row.addView(box, new LinearLayout.LayoutParams(Mib.px(act, 46), Mib.px(act, 46)));
        setChecked(box, on);
        return box;
    }

    private static void setChecked(View box, boolean on) {
        ((ImageView) box).setImageResource(on ? R.drawable.ui_ic_check_on : R.drawable.ui_ic_check_off);
    }

    private void addLeading(LinearLayout row, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 30));
        lp.rightMargin = Mib.px(act, 18);
        row.addView(v, 0, lp);
    }

    /** Serbian tricolour, as the factory language bar shows the country flag. */
    private View flag() {
        ImageView f = new ImageView(act);
        f.setImageResource(R.drawable.ui_ic_flag_rs);
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
