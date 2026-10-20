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
import com.thecarlauncher.util.DisplayControl;
import com.thecarlauncher.util.HotspotControl;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.WifiControl;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.util.Updater;

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
        sub("Wi-Fi", new Page() { public void build() { buildWifi(); } });
        sub(s("Hotspot", "Hotspot"), new Page() { public void build() { buildHotspot(); } });
        sub(s("Android sistem", "Android system"), new Page() { public void build() { buildAndroid(); } });
        sub(s("Informacije o sistemu", "System information"), new Page() { public void build() { buildAbout(); } });
        action(s("Ažuriranje softvera", "Software update"), Updater.isNewer(act, Updater.lastFound())
                ? s("dostupna ", "available: ") + Updater.lastFound().versionName : null, new Runnable() {
            public void run() { act.show(LauncherActivity.SOFTWARE, true); }
        });
    }

    private void buildDisplay() {
        final android.app.Activity a = act;
        stepper(s("Osvetljenost", "Brightness"), DisplayControl.brightness(act) + " %", new Runnable() {
            public void run() { DisplayControl.setBrightness(a, DisplayControl.brightness(a) - 10); refreshKeep(); }
        }, new Runnable() {
            public void run() { DisplayControl.setBrightness(a, DisplayControl.brightness(a) + 10); refreshKeep(); }
        });
        if (!DisplayControl.canWrite(act)) {
            action(s("Dozvoli menjanje osvetljenosti sistema", "Allow changing the system brightness"),
                    s("sada važi samo u launcheru", "now only inside the launcher"), new Runnable() {
                        public void run() { DisplayControl.requestWrite(act); }
                    });
        } else {
            checkRowWithAction(s("Automatska osvetljenost", "Automatic brightness"), DisplayControl.autoBrightness(act), new Runnable() {
                public void run() {
                    DisplayControl.setAuto(act, !DisplayControl.autoBrightness(act));
                    refreshKeep();
                }
            });
        }
        int pi = DisplayControl.profile(act);
        action(s("Profil boja", "Colour profile"), pi < 0 ? s("Sopstveni", "Custom")
                : s((String) DisplayControl.PROFILES[pi][0], (String) DisplayControl.PROFILES[pi][1]), new Runnable() {
            public void run() {
                String[] names = new String[DisplayControl.PROFILES.length];
                for (int i = 0; i < names.length; i++) names[i] = s((String) DisplayControl.PROFILES[i][0], (String) DisplayControl.PROFILES[i][1]);
                Dialogs.choose(act, s("Profil boja", "Colour profile"), names, DisplayControl.profile(act), new Dialogs.Chosen() {
                    public void onChosen(int i) {
                        DisplayControl.setProfile(act, i);
                        act.applyDisplay();
                        refreshKeep();
                    }
                });
            }
        });
        colorStepper(s("Zasićenost", "Saturation"), 0, 10);
        colorStepper(s("Kontrast", "Contrast"), 1, 5);
        colorStepper(s("Toplina boja", "Warmth"), 2, 1);
        toggle(s("Pun ekran (sakrij Android trake)", "Full screen (hide Android bars)"), Prefs.IMMERSIVE, false, true);
        note(s("Profil boja menja sliku launchera (sve njegove ekrane, i mapu). Boje drugih aplikacija mogu se menjati tek kada launcher postane sistemska aplikacija.",
                "The colour profile changes the launcher's picture (all its screens, the map too). Other apps' colours can only be changed once the launcher is a system app."));
    }

    /** Saturation (0), contrast (1) or warmth (2) with − / + keys. */
    private void colorStepper(String label, final int which, final int step) {
        int[] v = {DisplayControl.saturation(act), DisplayControl.contrast(act), DisplayControl.warmth(act)};
        String text = which == 2 ? (v[2] > 0 ? "+" + v[2] : String.valueOf(v[2])) : v[which] + " %";
        stepper(label, text, new Runnable() {
            public void run() { bumpColor(which, -step); }
        }, new Runnable() {
            public void run() { bumpColor(which, step); }
        });
    }

    private void bumpColor(int which, int d) {
        int sat = DisplayControl.saturation(act), con = DisplayControl.contrast(act), warm = DisplayControl.warmth(act);
        if (which == 0) sat += d;
        else if (which == 1) con += d;
        else warm += d;
        DisplayControl.set(act, sat, con, warm);
        act.applyDisplay();
        refreshKeep();
    }

    /** A settings bar with − and + keys on the right. */
    private void stepper(String text, String value, final Runnable minus, final Runnable plus) {
        LinearLayout row = bar(text, value);
        row.setClickable(false);
        for (int i = 0; i < 2; i++) {
            final Runnable r = i == 0 ? minus : plus;
            TextView k = Mib.text(act, i == 0 ? "−" : "+", 34, Mib.semibold(act), Mib.BAR_TEXT);
            k.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x00000000);
            g.setStroke(Math.max(1, Mib.px(act, 1.5f)), Mib.BAR_VALUE);
            g.setCornerRadius(Mib.px(act, 4));
            k.setBackground(g);
            Mib.pressText(k);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(Mib.px(act, 58), Mib.px(act, 54));
            kl.leftMargin = Mib.px(act, 12);
            row.addView(k, kl);
            k.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { r.run(); }
            });
        }
    }

    /** Grey explanatory text between bars. */
    private void note(String text) {
        TextView t = Mib.text(act, text, 22, Mib.regular(act), Mib.GREY);
        t.setSingleLine(false);
        t.setMaxLines(4);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = Mib.px(act, 30);
        l.rightMargin = Mib.px(act, 58);
        l.topMargin = Mib.px(act, 6);
        l.bottomMargin = Mib.px(act, 12);
        content.addView(t, l);
    }

    // ---- Wi-Fi --------------------------------------------------------------------------------

    private final WifiControl.Listener wifiListener = new WifiControl.Listener() {
        public void onWifiChanged() { if (onPage("Wi-Fi")) refreshKeep(); }
    };

    private boolean onPage(String title) {
        return !stack.isEmpty() && stack.get(stack.size() - 1).title.equals(title);
    }

    private void buildWifi() {
        final WifiControl w = WifiControl.get(act);
        w.addListener(wifiListener);
        if (!w.available()) {
            info(s("Wi-Fi nije dostupan", "Wi-Fi not available"), null);
            return;
        }
        final boolean on = w.isOn();
        checkRowWithAction("Wi-Fi", on, new Runnable() {
            public void run() {
                if (!w.setOn(!on)) Ui.start(act, w.systemPanel()); // Android 10+: only the system may switch it
                ui.postDelayed(new Runnable() { public void run() { if (onPage("Wi-Fi")) refreshKeep(); } }, 1500);
            }
        });
        if (!on) {
            note(s("Wi-Fi je isključen.", "Wi-Fi is off."));
            return;
        }
        String cur = w.connectedSsid();
        if (cur != null) {
            LinearLayout row = bar(cur, s("Povezano", "Connected") + (w.connectionDetails() != null ? " · " + w.connectionDetails() : ""));
            row.setClickable(false);
            addLeading(row, wifiIcon(w.connectedLevel(), false, true));
        }
        action(s("Traži mreže", "Search for networks"), null, new Runnable() {
            public void run() {
                w.scan();
                com.thecarlauncher.mib.LoadingBanner.flash("wifi-scan", s("Traženje Wi-Fi mreža…", "Searching for Wi-Fi networks…"), 3000);
            }
        });
        List<WifiControl.Network> nets = w.networks();
        if (nets.isEmpty()) {
            if (cur == null) w.scan();
            note(s("Nema pronađenih mreža (potrebna je dozvola za lokaciju).", "No networks found (location permission is needed)."));
        }
        for (final WifiControl.Network n : nets) {
            if (n.connected) continue;
            LinearLayout row = bar(n.ssid, n.secure ? s("zaštićena", "secured") : s("otvorena", "open"));
            addLeading(row, wifiIcon(n.level, n.secure, false));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!n.secure) joinWifi(n, null);
                    else Dialogs.text(act, n.ssid, s("Lozinka", "Password"), "", new Dialogs.TextEntered() {
                        public void onText(String text) { if (text != null && !text.isEmpty()) joinWifi(n, text); }
                    });
                }
            });
        }
        action(s("Sistemska Wi-Fi podešavanja", "System Wi-Fi settings"), s("zaboravi mrežu, napredno", "forget a network, advanced"),
                intent(Settings.ACTION_WIFI_SETTINGS));
    }

    private void joinWifi(WifiControl.Network n, String password) {
        WifiControl w = WifiControl.get(act);
        WifiControl.Join j = w.join(n, password);
        if (j == WifiControl.Join.ASKS_SYSTEM) {
            // Android 11+: the system saves the network after one confirmation.
            android.content.Intent i = w.addNetworksIntent(n, password);
            if (i == null || !Ui.start(act, i)) j = WifiControl.Join.FAILED;
            else {
                com.thecarlauncher.mib.LoadingBanner.flash("wifi-join", s("Potvrdi mrežu u sistemskom prozoru", "Confirm the network in the system window"), 3000);
                return;
            }
        }
        if (j == WifiControl.Join.CONNECTING) {
            com.thecarlauncher.mib.LoadingBanner.flash("wifi-join", s("Povezivanje sa ", "Connecting to ") + n.ssid + "…", 4000);
        } else {
            com.thecarlauncher.mib.LoadingBanner.flash("wifi-join", s("Povezivanje nije uspelo", "Could not connect"), 3000);
        }
    }

    /** Wi-Fi fan with level 0..4 (lock dot when secured), drawn so it needs no artwork. */
    private View wifiIcon(final int level, final boolean secure, final boolean connected) {
        return new View(act) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(android.graphics.Canvas c) {
                float w = getWidth(), h = getHeight(), cx = w / 2f, by = h * 0.92f;
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(h * 0.1f);
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                for (int i = 1; i <= 3; i++) {
                    p.setColor(level >= i + 1 ? Mib.BAR_TEXT : 0x55303038);
                    float r = h * 0.28f * i;
                    c.drawArc(new android.graphics.RectF(cx - r, by - r, cx + r, by + r), 225, 90, false, p);
                }
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(level >= 1 ? Mib.BAR_TEXT : 0x55303038);
                c.drawCircle(cx, by - h * 0.04f, h * 0.08f, p);
                if (secure) {
                    p.setColor(Mib.BAR_VALUE);
                    c.drawRect(w * 0.78f, h * 0.55f, w * 0.98f, h * 0.85f, p);
                }
            }
        };
    }

    // ---- Hotspot ------------------------------------------------------------------------------

    private void buildHotspot() {
        Boolean on = HotspotControl.isOn(act);
        info(s("Hotspot", "Hotspot"), on == null ? s("stanje nepoznato", "state unknown") : on ? s("Uključen", "On") : s("Isključen", "Off"));
        action(s("Uključi / isključi hotspot", "Turn the hotspot on / off"), null, new Runnable() {
            public void run() {
                if (!HotspotControl.openSystem(act)) Ui.toast(act, s("Podešavanje hotspota nije pronađeno", "Hotspot settings not found"));
            }
        });
        note(s("Android ne dozvoljava običnoj aplikaciji da sama pali hotspot: otvara se sistemski ekran za hotspot (naziv, lozinka, uključivanje). Kada launcher postane sistemska aplikacija, ovo ide direktno odavde.",
                "Android does not let an ordinary app switch the hotspot itself: the system hotspot page opens (name, password, on/off). Once the launcher is a system app this works right here."));
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
        toggle(s("Mapa okrenuta u smeru vožnje", "Map turns with the driving direction"), Prefs.NAV_HEADING_UP, false, false);
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

    /** Reads the library again now and says what it found. */
    private void rescanLibrary() {
        MusicLibrary.invalidate();
        new Thread(new Runnable() {
            public void run() {
                java.util.List<com.thecarlauncher.media.Track> all = MusicLibrary.all(act);
                int vids = 0;
                for (com.thecarlauncher.media.Track t : all) if (t.video) vids++;
                final int songs = all.size() - vids, v = vids;
                ui.post(new Runnable() {
                    public void run() {
                        com.thecarlauncher.mib.LoadingBanner.flash("library-done", s("Pronađeno: ", "Found: ") + songs
                                + s(" pesama", " songs") + (v > 0 ? ", " + v + s(" spotova", " videos") : ""), 3500);
                        act.onLibraryChanged();
                    }
                });
            }
        }, "library-rescan").start();
    }

    private void buildMusicFolders() {
        final java.util.List<String> mf = MusicLibrary.musicFolders(act);
        choice(s("Automatski (memorija i USB)", "Automatic (storage and USB)"), mf.isEmpty(), new Runnable() {
            public void run() {
                MusicLibrary.setFolders(act, new java.util.ArrayList<String>());
                refreshKeep();
                rescanLibrary();
            }
        });
        for (final String f : mf) {
            action(f, s("ukloni", "remove"), new Runnable() {
                public void run() {
                    java.util.List<String> l = new java.util.ArrayList<>(MusicLibrary.musicFolders(act));
                    l.remove(f);
                    MusicLibrary.setFolders(act, l);
                    refreshKeep();
                    rescanLibrary();
                }
            });
        }
        action("+ " + s("Dodaj folder", "Add a folder"), null, new Runnable() {
            public void run() {
                browseDir = null;
                push(s("Izaberi folder", "Choose a folder"), new Page() {
                    public void build() { buildFolderBrowser(); }
                });
            }
        });
        note(s("Sa izabranim folderima launcher čita samo njih (i podfoldere), kao obične fajlove. Bez njih sam traži muziku u memoriji i na USB-u.",
                "With folders chosen, the launcher reads only those (and their subfolders) as plain files. Without, it finds music in storage and on USB itself."));
    }

    private java.io.File browseDir;

    /** Folder browser: storage roots first, then into folders; "Use this folder" adds it. */
    private void buildFolderBrowser() {
        if (browseDir == null) {
            java.util.LinkedHashSet<String> roots = new java.util.LinkedHashSet<>();
            for (java.io.File r : com.thecarlauncher.util.Storage.removableRoots(act)) roots.add(r.getPath());
            roots.add(android.os.Environment.getExternalStorageDirectory().getPath());
            for (String base : new String[]{"/storage", "/mnt", "/mnt/media_rw", "/mnt/usbhost", "/udisk"}) {
                java.io.File[] fs = new java.io.File(base).listFiles();
                if (fs == null) continue;
                for (java.io.File f : fs) {
                    String n = f.getName();
                    if (!f.isDirectory() || !f.canRead() || n.equals("self") || n.equals("emulated") || n.equals("runtime")
                            || n.equals("secure") || n.equals("asec") || n.equals("obb") || n.equals("user") || n.equals("appfuse")) continue;
                    roots.add(f.getPath());
                }
            }
            for (final String r : roots) {
                action(r, s("otvori", "open"), new Runnable() {
                    public void run() {
                        browseDir = new java.io.File(r);
                        refreshKeep();
                    }
                });
            }
            note(s("USB je obično u /storage/… ili /mnt/… (npr. usbhost, udisk).", "USB is usually under /storage/… or /mnt/… (e.g. usbhost, udisk)."));
            return;
        }
        final java.io.File dir = browseDir;
        action(s("✓ Koristi ovaj folder", "✓ Use this folder"), dir.getPath(), new Runnable() {
            public void run() {
                java.util.List<String> l = new java.util.ArrayList<>(MusicLibrary.musicFolders(act));
                if (!l.contains(dir.getPath())) l.add(dir.getPath());
                MusicLibrary.setFolders(act, l);
                onBack();
                refreshKeep();
                rescanLibrary();
            }
        });
        action("..", s("nazad", "up"), new Runnable() {
            public void run() {
                java.io.File p = dir.getParentFile();
                browseDir = p == null || !p.canRead() ? null : p;
                refreshKeep();
            }
        });
        java.io.File[] fs = dir.listFiles();
        int files = 0;
        java.util.List<java.io.File> subs = new java.util.ArrayList<>();
        if (fs != null) {
            for (java.io.File f : fs) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) subs.add(f);
                else if (f.getName().matches("(?i).+\\.(mp3|m4a|aac|flac|ogg|opus|wav|wma|mp4|m4v|mkv|avi|mov|webm)$")) files++;
            }
        }
        java.util.Collections.sort(subs);
        if (files > 0) note(files + s(" muzičkih / video fajlova u ovom folderu", " music / video files in this folder"));
        for (final java.io.File f : subs) {
            action(f.getName(), null, new Runnable() {
                public void run() {
                    browseDir = f;
                    refreshKeep();
                }
            });
        }
        if (fs == null) note(s("Folder nije čitljiv (dozvola za pristup fajlovima?)", "Folder not readable (file access permission?)"));
    }

    private void buildMedia() {
        toggle(s("Nastavi reprodukciju pri pokretanju", "Resume playback on start"), Prefs.RESUME_MEDIA, true, false);
        action(s("Duboki - Srednji - Visoki", "Bass - Mid - Treble"), toneSummary(), new Runnable() {
            public void run() { act.show(LauncherActivity.SOUND, true); }
        });
        java.util.List<String> mf = MusicLibrary.musicFolders(act);
        action(s("Folderi za muziku", "Music folders"), mf.isEmpty() ? s("Automatski (memorija i USB)", "Automatic (storage and USB)")
                : mf.size() == 1 ? mf.get(0) : mf.size() + s(" foldera", " folders"), new Runnable() {
            public void run() {
                push(s("Folderi za muziku", "Music folders"), new Page() {
                    public void build() { buildMusicFolders(); }
                });
            }
        });
        toggle(s("Spotovi (MP4 i drugi video fajlovi)", "Music videos (MP4 and other video files)"), MusicLibrary.P_VIDEOS, true,
                new Runnable() { public void run() { rescanLibrary(); } });
        action(s("Muzička biblioteka", "Music library"), MusicLibrary.hasPermission(act)
                ? s("Ponovo skeniraj", "Rescan") : s("Dozvoli pristup", "Allow access"), new Runnable() {
            public void run() {
                if (MusicLibrary.hasPermission(act)) rescanLibrary();
                else act.requestMissingPermissions(true);
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
        boolean qf = com.thecarlauncher.util.Platform.qf(act);
        if (qf) {
            action(s("Bluetooth uređaji", "Bluetooth devices"), s("poveži / novi uređaj", "connect / new device"), new Runnable() {
                public void run() { act.show(LauncherActivity.BTDEVICES, true); }
            });
            toggle(s("BT audio čim se telefon poveže", "BT audio as soon as the phone connects"),
                    Prefs.BT_AUTO_SELECT, true, false);
        } else {
            // Other units: the factory BT music app plays the sound; the launcher opens it briefly.
            AppRepository.App screen = MediaHub.get(act).bluetooth().appScreen();
            String pkg = MediaHub.get(act).bluetooth().appPackage();
            action(s("Aplikacija za BT muziku", "BT music app"), screen != null ? screen.label
                    : pkg == null ? s("Nije pronađena", "Not found") : appLabel(pkg), new Runnable() {
                public void run() {
                    push(s("Aplikacija za BT muziku", "BT music app"), new Page() {
                        public void build() { buildBtAppChoice(); }
                    });
                }
            });
            toggle(s("Otvori BT aplikaciju na trenutak (pušta zvuk)", "Open the BT app briefly (plays the sound)"),
                    Prefs.BT_OPEN_APP, true, false);
            if (!com.thecarlauncher.util.BringBack.canOverlay(act)) {
                action(s("Dozvoli povratak iz BT aplikacije", "Allow returning from the BT app"),
                        s("Prikaz preko drugih aplikacija: isključeno", "Display over other apps: off"), new Runnable() {
                            public void run() { com.thecarlauncher.util.BringBack.requestOverlay(act); }
                        });
            }
        }
        toggle(s("Omot pesme sa interneta", "Cover art from the internet"), Prefs.ONLINE_COVER, true, new Runnable() {
            public void run() { MediaHub.get(act).radioChanged(); }
        });
        action(s("Dijagnostika (QF / Bluetooth)", "Diagnostics (QF / Bluetooth)"), null, new Runnable() {
            public void run() {
                push(s("Dijagnostika", "Diagnostics"), new Page() {
                    public void build() { buildBtDiagnostics(); }
                });
            }
        });
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
    private String reportText;

    /** Builds the device report, saves it and shows it. */
    private void deviceReport() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT <= 28
                && act.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 43);
        }
        com.thecarlauncher.mib.LoadingBanner.start("report", s("Pravljenje izveštaja o uređaju…", "Building the device report…"));
        new Thread(new Runnable() {
            public void run() {
                final String text = com.thecarlauncher.util.DeviceReport.build(act);
                final String path = com.thecarlauncher.util.DeviceReport.save(act, text);
                ui.post(new Runnable() {
                    public void run() {
                        com.thecarlauncher.mib.LoadingBanner.stop("report");
                        com.thecarlauncher.mib.LoadingBanner.flash("report-ok", path != null ? s("Sačuvano: ", "Saved: ") + path
                                : s("Izveštaj nije sačuvan", "Report not saved"), 5000);
                        reportText = (path != null ? path + "\n\n" : "") + text;
                        push(s("Izveštaj o uređaju", "Device report"), new Page() {
                            public void build() {
                                TextView t = Mib.text(act, reportText, 18, Mib.regular(act), Mib.WHITE);
                                t.setSingleLine(false);
                                t.setMaxLines(Integer.MAX_VALUE);
                                t.setEllipsize(null);
                                t.setTypeface(android.graphics.Typeface.MONOSPACE);
                                t.setTextIsSelectable(true);
                                t.setPadding(Mib.px(act, 30), Mib.px(act, 6), Mib.px(act, 40), Mib.px(act, 20));
                                content.addView(t);
                            }
                        });
                    }
                });
            }
        }, "device-report").start();
    }

    private void buildBtDiagnostics() {
        action(s("Izveštaj o uređaju", "Device report"), s("sačuvaj i prikaži", "save and show"), new Runnable() {
            public void run() { deviceReport(); }
        });
        final com.thecarlauncher.media.GocBluetooth gocBt = com.thecarlauncher.media.GocBluetooth.get(act);
        checkRowWithAction(s("Čitaj BT modul direktno (samo bez fabričke BT aplikacije)", "Read the BT module directly (only without the factory BT app)"),
                gocBt.enabled(), new Runnable() {
                    public void run() {
                        Prefs.put(act, com.thecarlauncher.media.GocBluetooth.PREF, !gocBt.enabled());
                        gocBt.start();
                        refreshKeep();
                    }
                });
        MediaHub hub0 = MediaHub.get(act);
        info(s("Pristup medijima", "Media access"), hub0.hasNotificationAccess() ? s("Da", "Yes") : s("Ne", "No"));
        StringBuilder srcs = new StringBuilder();
        for (String k : hub0.sources()) {
            if (srcs.length() > 0) srcs.append(", ");
            srcs.append(k.equals(MediaHub.BLUETOOTH) ? "BT" : k);
        }
        info(s("Izvori", "Sources"), srcs.toString());
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

    /** Opens the picture page: built-in Corsa C, no picture, or one imported from storage. */
    private void pictureRow(String label, final int which) {
        action(label, com.thecarlauncher.util.CarImages.label(act, which), new Runnable() {
            public void run() { pick(which); }
        });
    }

    // ---- Picture chooser (inside the launcher: unit pickers often never return a result) ------

    private java.util.List<com.thecarlauncher.util.ImageFinder.Image> pictures;
    private boolean picturesLoading;
    private int pictureFor;
    private final android.util.LruCache<String, android.graphics.Bitmap> thumbs = new android.util.LruCache<>(60);
    private final java.util.concurrent.ExecutorService thumbPool = java.util.concurrent.Executors.newFixedThreadPool(2);

    private void pick(final int which) {
        pictureFor = which;
        pictures = null;
        push(s("Izaberi sliku", "Choose a picture"), new Page() {
            public void build() { buildPictures(); }
        });
        loadPictures();
    }

    private void loadPictures() {
        if (picturesLoading) return;
        picturesLoading = true;
        com.thecarlauncher.mib.LoadingBanner.start("pictures", s("Traženje slika…", "Looking for pictures…"));
        new Thread(new Runnable() {
            public void run() {
                final java.util.List<com.thecarlauncher.util.ImageFinder.Image> found = com.thecarlauncher.util.ImageFinder.find(act);
                ui.post(new Runnable() {
                    public void run() {
                        picturesLoading = false;
                        pictures = found;
                        com.thecarlauncher.mib.LoadingBanner.stop("pictures");
                        if (onPicturePage()) refreshKeep();
                    }
                });
            }
        }, "pictures").start();
    }

    private boolean onPicturePage() {
        return !stack.isEmpty() && stack.get(stack.size() - 1).title.equals(s("Izaberi sliku", "Choose a picture"));
    }

    @Override
    public void onPermissionsChanged() {
        if (onPicturePage()) loadPictures();
    }

    private void buildPictures() {
        final int which = pictureFor;
        if (!com.thecarlauncher.util.ImageFinder.hasPermission(act)) {
            action(s("Dozvoli pristup slikama", "Allow access to pictures"), s("potrebno", "needed"), new Runnable() {
                public void run() {
                    act.requestPermissions(new String[]{com.thecarlauncher.util.ImageFinder.permission()}, 42);
                    if (com.thecarlauncher.util.Storage.needsAllFiles()) com.thecarlauncher.util.Storage.requestAllFiles(act);
                }
            });
        }
        final String mode = com.thecarlauncher.util.CarImages.mode(act, which);
        // Built-in set, then "none", then importing.
        LinearLayout corsa = choice(s("Corsa C (ugrađena)", "Corsa C (built-in)"),
                com.thecarlauncher.util.CarImages.BUILTIN.equals(mode), new Runnable() {
                    public void run() {
                        com.thecarlauncher.util.CarImages.useBuiltin(act, which);
                        pictureApplied();
                    }
                });
        ImageView ct = new ImageView(act);
        ct.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ct.setBackgroundColor(0xFF2A2A30);
        ct.setImageBitmap(builtinThumb(which));
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(Mib.px(act, 88), Mib.px(act, 62));
        ctl.rightMargin = Mib.px(act, 18);
        corsa.addView(ct, 0, ctl);
        choice(s("Bez slike", "No picture"), com.thecarlauncher.util.CarImages.NONE.equals(mode), new Runnable() {
            public void run() {
                com.thecarlauncher.util.CarImages.reset(act, which);
                pictureApplied();
            }
        });
        TextView imp = Mib.text(act, s("Uvezi sliku sa uređaja ili USB-a:", "Import a picture from the unit or USB:"),
                24, Mib.regular(act), Mib.GREY);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        il.leftMargin = Mib.px(act, 30);
        il.topMargin = Mib.px(act, 10);
        il.bottomMargin = Mib.px(act, 10);
        content.addView(imp, il);
        action(s("Druga aplikacija (galerija, fajl menadžer)", "Another app (gallery, file manager)"), null, new Runnable() {
            public void run() {
                act.pickImage(which, new LauncherActivity.ImagePicked() {
                    public void onPicked(android.net.Uri uri) { usePicture(which, uri); }
                });
            }
        });
        if (pictures == null) {
            info(s("Učitavanje…", "Loading…"), null);
            return;
        }
        if (pictures.isEmpty()) {
            info(s("Nema slika na uređaju ni na USB-u", "No pictures on the unit or on USB"), null);
            return;
        }
        for (final com.thecarlauncher.util.ImageFinder.Image im : pictures) {
            LinearLayout row = bar(im.name, im.folder);
            final ImageView th = new ImageView(act);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.setBackgroundColor(0xFF2A2A30);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Mib.px(act, 88), Mib.px(act, 62));
            tl.rightMargin = Mib.px(act, 18);
            row.addView(th, 0, tl);
            final String key = im.uri.toString();
            android.graphics.Bitmap cached = thumbs.get(key);
            if (cached != null) th.setImageBitmap(cached);
            else {
                th.setTag(key);
                thumbPool.execute(new Runnable() {
                    public void run() {
                        final android.graphics.Bitmap b = com.thecarlauncher.util.ImageFinder.thumb(act, im.uri, 176);
                        if (b == null) return;
                        thumbs.put(key, b);
                        ui.post(new Runnable() {
                            public void run() { if (key.equals(th.getTag())) th.setImageBitmap(b); }
                        });
                    }
                });
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { usePicture(which, im.uri); }
            });
        }
    }

    private final android.util.SparseArray<android.graphics.Bitmap> builtinThumbs = new android.util.SparseArray<>();

    private android.graphics.Bitmap builtinThumb(int which) {
        android.graphics.Bitmap b = builtinThumbs.get(which);
        if (b == null) {
            android.graphics.Bitmap full = com.thecarlauncher.util.CarImages.builtin(act, which);
            b = android.graphics.Bitmap.createScaledBitmap(full, 176, Math.max(1, 176 * full.getHeight() / full.getWidth()), true);
            builtinThumbs.put(which, b);
        }
        return b;
    }

    /** A choice was made: say so, go back to the vehicle page, rebuild the screens showing it. */
    private void pictureApplied() {
        com.thecarlauncher.mib.LoadingBanner.flash("picture-ok", s("Slika je postavljena", "Picture set"), 2000);
        onBack();
        act.carPicturesChanged();
    }

    private void usePicture(final int which, final android.net.Uri uri) {
        com.thecarlauncher.mib.LoadingBanner.start("picture-save", s("Čuvanje slike…", "Saving the picture…"));
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = com.thecarlauncher.util.CarImages.set(act, which, uri);
                ui.post(new Runnable() {
                    public void run() {
                        com.thecarlauncher.mib.LoadingBanner.stop("picture-save");
                        if (!ok) {
                            com.thecarlauncher.mib.LoadingBanner.flash("picture-fail", s("Slika ne može da se otvori", "The picture can't be opened"), 2500);
                            return;
                        }
                        pictureApplied();
                    }
                });
            }
        }, "picture-save").start();
    }

    private void buildAndroid() {
        final int cur = "qf".equals(Prefs.str(act, com.thecarlauncher.util.Platform.P_PLATFORM, "auto")) ? 1
                : "other".equals(Prefs.str(act, com.thecarlauncher.util.Platform.P_PLATFORM, "auto")) ? 2 : 0;
        action(s("Platforma uređaja", "Head unit platform"),
                (cur == 0 ? s("Automatski: ", "Automatic: ") : "") + com.thecarlauncher.util.Platform.name(act,
                        com.thecarlauncher.util.Platform.current(act)), new Runnable() {
                    public void run() {
                        String[] o = {s("Automatski", "Automatic"), com.thecarlauncher.util.Platform.name(act, com.thecarlauncher.util.Platform.QF),
                                com.thecarlauncher.util.Platform.name(act, com.thecarlauncher.util.Platform.OTHER)};
                        Dialogs.choose(act, s("Platforma uređaja", "Head unit platform"), o, cur, new Dialogs.Chosen() {
                            public void onChosen(int i) {
                                Prefs.put(act, com.thecarlauncher.util.Platform.P_PLATFORM, i == 1 ? "qf" : i == 2 ? "other" : "auto");
                                refreshKeep();
                            }
                        });
                    }
                });
        action(s("Izveštaj o uređaju", "Device report"), s("za podršku novih uređaja", "to support new units"), new Runnable() {
            public void run() { deviceReport(); }
        });
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

    /** Like the factory "Versions" page: a label, its value right-aligned under it. */
    private void buildAbout() {
        String version = "", built = "";
        long code = 0;
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            version = pi.versionName;
            code = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            built = new java.text.SimpleDateFormat("yyyyMMddHHmm", java.util.Locale.US).format(new java.util.Date(pi.lastUpdateTime));
        } catch (Exception ignored) {
        }
        section(s("Softver", "Software"));
        versionRow(s("Verzija softvera", "Software version"), "TCL_" + version + "_" + (s("SR", "EN")));
        versionRow(s("Osnova softvera", "Software base"), "TCL" + version + "-" + built);
        versionRow(s("Broj izdanja", "Build number"), String.valueOf(code));
        section(s("Uređaj", "Head unit"));
        versionRow(s("Model", "Model"), Build.MANUFACTURER + " " + Build.MODEL);
        String platform = sysProp("ro.qf.platform");
        String board = sysProp("ro.board.platform");
        versionRow(s("Hardver", "Hardware"), Build.HARDWARE + (board != null ? " / " + board : "")
                + (platform != null ? " / " + platform : ""));
        versionRow("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        versionRow(s("Verzija sistema", "System version"), Build.DISPLAY);
        versionRow(s("Datum sistema", "System date"),
                new java.text.SimpleDateFormat("yyyyMMddHHmm", java.util.Locale.US).format(new java.util.Date(Build.TIME)));
        String mcu = firstProp("persist.sys.qf.mcu.version", "sys.qf.mcu.version", "ro.qf.mcu.version", "persist.sys.mcu.version",
                "sys.mcu.version", "ro.mcu.version", "persist.mcu.version");
        if (mcu != null) versionRow(s("MCU verzija", "MCU version"), mcu);
        String fw = firstProp("ro.qf.version", "ro.qf.sw.version", "persist.sys.qf.version", "ro.build.version.incremental");
        if (fw != null) versionRow(s("Verzija fabričkog softvera", "Factory software version"), fw);
        versionRow("Kernel", System.getProperty("os.version", "—"));
        String btName = sysProp("persist.sys.qf.bt.local.name"), btAddr = sysProp("persist.sys.qf.bt.local.addr");
        if (btName != null || btAddr != null) {
            versionRow("Bluetooth", (btName != null ? btName : "") + (btAddr != null ? (btName != null ? " · " : "") + btAddr : ""));
        }
        android.util.DisplayMetrics dm = act.getResources().getDisplayMetrics();
        versionRow(s("Ekran", "Screen"), dm.widthPixels + "×" + dm.heightPixels + " · " + dm.densityDpi + " dpi");
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) act.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            android.os.StatFs st = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
            versionRow(s("Memorija", "Memory"), String.format(java.util.Locale.US, "RAM %.1f GB · %s %.1f / %.1f GB",
                    mi.totalMem / 1e9, s("slobodno", "free"), st.getAvailableBytes() / 1e9, st.getTotalBytes() / 1e9));
        } catch (Exception ignored) {
        }
        String proj = Projection.find(act);
        if (proj != null) versionRow("App-Connect", proj);
        versionRow(s("Podaci mape", "Map data"), "© OpenStreetMap, © CARTO");
    }

    private void section(String text) {
        TextView t = Mib.text(act, text, 24, Mib.semibold(act), Mib.GREY);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = Mib.px(act, 30);
        l.topMargin = Mib.px(act, 10);
        l.bottomMargin = Mib.px(act, 4);
        content.addView(t, l);
    }

    /** Factory "Versions" row: white label, the value right-aligned below, a thin rule. */
    private void versionRow(String label, String value) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Mib.px(act, 30), Mib.px(act, 10), Mib.px(act, 60), Mib.px(act, 10));
        box.addView(Mib.text(act, label, 28, Mib.regular(act), Mib.WHITE));
        TextView v = Mib.text(act, value == null || value.isEmpty() ? "—" : value, 28, Mib.regular(act), Mib.WHITE);
        v.setSingleLine(false);
        v.setMaxLines(3);
        v.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.topMargin = Mib.px(act, 6);
        box.addView(v, vl);
        content.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View rule = new View(act);
        rule.setBackgroundColor(0xFF3A3B42);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1)));
        rl.leftMargin = Mib.px(act, 26);
        rl.rightMargin = Mib.px(act, 58);
        content.addView(rule, rl);
    }

    private static String sysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class).invoke(null, key);
            return v == null || v.trim().isEmpty() ? null : v.trim();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String firstProp(String... keys) {
        for (String k : keys) {
            String v = sysProp(k);
            if (v != null) return v;
        }
        return null;
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
