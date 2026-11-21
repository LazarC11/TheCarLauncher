package com.thecarlauncher.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.radio.RadioController;
import com.thecarlauncher.util.CarImages;
import com.thecarlauncher.util.Compat;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.StartupConnect;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.util.UnitKind;
import com.thecarlauncher.util.Updater;
import com.thecarlauncher.util.WifiControl;

import java.util.Locale;

/**
 * The setup assistant after the welcome page (IntroScreen): Wi-Fi, compatibility check (with the
 * update to the right build for this unit), the vehicle (brand, car and cabin pictures), settings
 * for this unit, the phone, then two short tests (radio station search, Bluetooth audio) so the
 * owner knows everything works. Results go into the device report. The step is remembered, so
 * after an update installed from here the assistant goes on.
 */
public class WelcomeScreen extends Screen implements RadioController.Listener, MediaHub.Listener {
    public static final String P_DONE = "welcome_done", P_STEP = "welcome_step";
    public static final String P_BRAND = "vehicle_brand";
    /** Test results, one line each ("Radio: …"), shown in the device report. */
    public static final String P_RESULTS = "welcome_results";
    static final int LANGUAGE = 0, WIFI = 1, COMPAT = 2, SCREEN = 3, VEHICLE = 4, AMBIENT = 5, UNIT = 6, PHONE = 7,
            RADIO = 8, BT_AUDIO = 9, DONE = 10;
    private static final int STEPS = 10;

    static final String[] BRANDS = {"Opel", "Volkswagen", "Škoda", "SEAT", "Audi", "Ford", "Renault", "Peugeot"};

    private LinearLayout list;
    private ScrollView scroll;
    private TextView heading, stepText;
    private int step;
    // Compatibility check
    private boolean checking, checked, offline, supported, reportBusy;
    private Updater.Release release;
    private String reportResult;
    // Tests
    private boolean radioSearching;
    private String radioResult, btResult;

    public WelcomeScreen(LauncherActivity act) { super(act); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    /**
     * True when the welcome / assistant should open: never finished, and either a fresh install or
     * an assistant left half way (e.g. by its own update). Installs older than it count as done.
     */
    public static boolean needed(Context c) {
        if (Prefs.bool(c, P_DONE, false)) return false;
        if (Prefs.integer(c, P_STEP, -1) >= 0) return true;
        try {
            android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if (pi.firstInstallTime > 0 && pi.firstInstallTime == pi.lastUpdateTime) return true;
        } catch (Exception ignored) {
        }
        Prefs.put(c, P_DONE, true); // updated from a version without the assistant
        return false;
    }

    /** The assistant was left at a step (not the welcome page). */
    public static boolean inProgress(Context c) { return Prefs.integer(c, P_STEP, -1) >= 0; }

    @Override
    public String title() { return s("Asistent za podešavanje", "Setup assistant"); }

    @Override
    public boolean hasOwnBack() { return true; }

    /** Back goes to the previous step (the welcome page from the first one). */
    @Override
    public boolean onBack() {
        if (step == LANGUAGE) {
            Prefs.put(act, P_STEP, -1);
            act.show(LauncherActivity.INTRO, true);
        } else if (step != DONE) {
            go(step - 1);
        }
        return true;
    }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Mib.px(act, 14), 0, 0);

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Mib.px(act, 30), 0, Mib.px(act, 30), Mib.px(act, 10));
        heading = Mib.text(act, title(), 40, Mib.semibold(act), Mib.WHITE);
        head.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stepText = Mib.text(act, "", 26, Mib.regular(act), Mib.GREY);
        head.addView(stepText);
        r.addView(head);

        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE); // factory title rule: a plain line
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2.4f)));
        rl.leftMargin = Mib.px(act, 12);
        rl.rightMargin = Mib.px(act, 12);
        r.addView(rule, rl);

        scroll = new ScrollView(act);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Mib.px(act, 8), Mib.px(act, 6), Mib.px(act, 40), Mib.px(act, 16));
        scroll.addView(list);
        r.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        step = Math.max(LANGUAGE, Math.min(DONE, Prefs.integer(act, P_STEP, LANGUAGE)));
        supported = Compat.recognised(act);
        return r;
    }

    @Override
    public void onShow() {
        WifiControl.get(act).addListener(wifiListener);
        RadioController.get(act).addListener(this);
        MediaHub.get(act).addListener(this);
        if (step == COMPAT && !checked && !checking) check();
        render();
    }

    @Override
    public void onHide() {
        WifiControl.get(act).removeListener(wifiListener);
        RadioController.get(act).removeListener(this);
        MediaHub.get(act).removeListener(this);
    }

    private final WifiControl.Listener wifiListener = new WifiControl.Listener() {
        public void onWifiChanged() { if (step == WIFI) render(); }
    };

    @Override
    public void onRadioChanged() {
        if (step != RADIO) return;
        RadioController r = RadioController.get(act);
        if (radioSearching && !r.scanning()) {
            radioSearching = false;
            // Search done: play the first station found.
            if (!r.stations().isEmpty()) r.tune(r.stations().get(0).freq);
        }
        render();
    }

    @Override
    public void onMediaChanged() { if (step == BT_AUDIO) render(); }

    private void go(int to) {
        step = to;
        Prefs.put(act, P_STEP, to);
        if (to == COMPAT && !checked) check();
        if (to == BT_AUDIO) MediaHub.get(act).select(MediaHub.BLUETOOTH);
        render();
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    private void finish() {
        Prefs.put(act, P_DONE, true);
        Prefs.put(act, P_STEP, -1);
        act.show(LauncherActivity.MENU, true);
    }

    // ---- Steps ---------------------------------------------------------------------------------

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        // The setup app (universal build) shows only the first three steps.
        stepText.setText(step == DONE ? "" : (step + 1) + " / " + (LauncherActivity.setupOnly(act) ? COMPAT + 1 : STEPS));
        switch (step) {
            case LANGUAGE: renderLanguage(); break;
            case WIFI: renderWifi(); break;
            case SCREEN: renderScreen(); break;
            case AMBIENT: renderAmbient(); break;
            case COMPAT: renderCompat(); break;
            case VEHICLE: renderVehicle(); break;
            case UNIT: renderUnit(); break;
            case PHONE: renderPhone(); break;
            case RADIO: renderRadio(); break;
            case BT_AUDIO: renderBtAudio(); break;
            default: renderDone();
        }
    }

    private void renderWifi() {
        heading.setText("Wi-Fi");
        WifiControl w = WifiControl.get(act);
        String ssid = w.available() && w.isOn() ? w.connectedSsid() : null;
        boolean net = Compat.online(act);
        text(s("Poveži uređaj sa internetom (Wi-Fi mreža ili hotspot telefona) da bi se proverila kompatibilnost i preuzela ažuriranja.",
                "Connect the unit to the internet (a Wi-Fi network or your phone's hotspot) to check compatibility and get updates."));
        status(ssid != null ? s("Povezano: ", "Connected: ") + ssid
                : net ? s("Internet je povezan", "The internet is connected") : s("Nije povezano", "Not connected"), ssid != null || net);
        button(s("Izaberi Wi-Fi mrežu", "Choose a Wi-Fi network"), new Runnable() {
            public void run() { act.showSettings(SettingsScreen.PAGE_WIFI); }
        });
        button(net ? s("Dalje", "Next") : s("Dalje bez interneta", "Continue without internet"), new Runnable() {
            public void run() { go(COMPAT); }
        });
    }

    private void check() {
        checking = true;
        offline = !Compat.online(act);
        if (offline) {
            finishCheck(null);
            return;
        }
        Updater.check(act, new Updater.Checked() {
            @Override
            public void onChecked(Updater.Release r, String error) { finishCheck(r); }
        });
    }

    private void finishCheck(Updater.Release r) {
        release = r;
        supported = Compat.recognised(act);
        Prefs.put(act, Compat.P_UNSUPPORTED, !supported);
        if (!supported) sendReport();
        // No build for this unit yet: the default (QF) build is installed until one is released.
        if (!supported && !offline && !com.thecarlauncher.unit.Unit.QF) {
            Updater.check(act, UnitKind.assetName(act, "qf"), new Updater.Checked() {
                @Override
                public void onChecked(Updater.Release q, String error) {
                    release = q;
                    checking = false;
                    checked = true;
                    if (step == COMPAT) render();
                }
            });
            return;
        }
        checking = false;
        checked = true;
        if (step == COMPAT) render();
    }

    /** What Continue installs on the compatibility step, or null when this build is right. */
    private Updater.Release toInstall() {
        if (release == null) return null;
        if (supported) return Updater.isNewer(act, release) ? release : null;
        // Unsupported: the default build unless it is already installed (and current).
        if (com.thecarlauncher.unit.Unit.QF) return Updater.isNewer(act, release) ? release : null;
        return release;
    }

    /** Continue on the compatibility step: install what is due, else the next step. */
    private void compatContinue() {
        final Updater.Release r = toInstall();
        if (r == null) {
            if (LauncherActivity.setupOnly(act)) {
                // The setup app cannot go on without a build: check again (after Wi-Fi).
                checked = false;
                render();
                return;
            }
            go(SCREEN);
            return;
        }
        Prefs.put(act, P_STEP, SCREEN); // the assistant goes on after the update
        if (!Updater.mayInstall(act)) {
            Updater.askInstallPermission(act);
            return;
        }
        // Straight to the progress page: download, check, install over this one; the new build
        // then opens by itself into the next step.
        Prefs.put(act, com.thecarlauncher.util.ReplacedReceiver.P_OPEN_AFTER_UPDATE, true);
        act.show(LauncherActivity.SOFTWARE, true);
        if (!Updater.busy()) Updater.install(act, r);
    }

    private void sendReport() {
        if (reportBusy) return;
        reportBusy = true;
        reportResult = null;
        Compat.sendReport(act, new Compat.Sent() {
            @Override
            public void onSent(String url, String savedPath, String error) {
                reportBusy = false;
                if (url != null) reportResult = s("Izveštaj o uređaju je poslat programeru.", "The device report was sent to the developer.");
                else if ("daily limit".equals(error)) reportResult = s("Danas je već poslato dovoljno izveštaja; ovaj je sačuvan ovde: ", "Enough reports were sent today; this one is saved here: ") + savedPath;
                else if ("offline".equals(error)) reportResult = s("Izveštaj o uređaju biće poslat čim uređaj dobije internet. Sačuvan je i ovde: ", "The device report will be sent as soon as the unit is online. It is also saved here: ") + savedPath;
                else reportResult = s("Izveštaj o uređaju je sačuvan; pošalji ga programeru: ", "The device report was saved; send it to the developer: ") + savedPath;
                render();
            }
        });
    }

    private void renderCompat() {
        heading.setText(s("Kompatibilnost", "Compatibility"));
        if (checking || !checked) {
            text(s("Provera da li softver odgovara ovom uređaju…", "Checking that the software fits this unit…"));
            if (!checking && !checked) check();
            return;
        }
        Updater.Release r = toInstall();
        if (supported) {
            status(s("Uređaj je podržan: ", "This unit is supported: ") + UnitKind.name(act, UnitKind.detect(act)), true);
            if (r != null) {
                text((Updater.switching(act)
                        ? s("Nastavi instalira verziju napravljenu za ovaj uređaj.", "Continue installs the version made for this unit.")
                        : s("Nastavi instalira novu verziju.", "Continue installs the new version.")) + " (" + r.versionName + ")");
            }
        } else {
            status(s("Ovaj uređaj još nije podržan", "This unit is not supported yet"), false);
            text(s("Radio i Bluetooth muzika možda neće raditi preko fabričkih servisa ovog uređaja. Izveštaj o uređaju je poslat, a kada podrška stigne, launcher će ponuditi verziju za ovaj uređaj.",
                    "The radio and Bluetooth music may not work through this unit's own services. The device report is sent, and when support arrives the launcher will offer the version for this unit."));
            if (r != null) {
                text(s("Do tada, Nastavi instalira osnovnu verziju (QF).", "Until then, Continue installs the default version (QF).")
                        + " (" + r.versionName + ")");
            }
            note(reportBusy || reportResult == null ? s("Slanje izveštaja o uređaju…", "Sending the device report…") : reportResult);
        }
        if (r == null && (offline || LauncherActivity.setupOnly(act))) {
            note(offline ? s("Poveži uređaj sa internetom da bi se instalirala verzija za ovaj uređaj.",
                    "Connect the unit to the internet to install the version for this unit.")
                    : s("Verzija za ovaj uređaj trenutno ne može da se preuzme. Pokušaj ponovo za koji trenutak.",
                    "The version for this unit cannot be downloaded right now. Try again in a moment."));
            button(s("Izaberi Wi-Fi mrežu", "Choose a Wi-Fi network"), new Runnable() {
                public void run() { act.showSettings(SettingsScreen.PAGE_WIFI); }
            });
        }
        button(s("Nastavi", "Continue"), new Runnable() {
            public void run() { compatContinue(); }
        });
    }

    private void renderVehicle() {
        heading.setText(s("Vozilo", "Vehicle"));
        text(s("Izaberi marku vozila i slike koje launcher prikazuje: automobil (Vozilo) i unutrašnjost (ambijentalno osvetljenje).",
                "Choose the vehicle's brand and the pictures the launcher shows: the car (Vehicle) and the cabin (ambient lighting)."));
        String brand = Prefs.str(act, P_BRAND, null);
        LinearLayout grid = null;
        for (int i = 0; i < BRANDS.length + 1; i++) {
            if (i % 3 == 0) {
                grid = new LinearLayout(act);
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 70));
                gl.leftMargin = Mib.px(act, 18);
                gl.topMargin = Mib.px(act, 10);
                list.addView(grid, gl);
            }
            final String b = i < BRANDS.length ? BRANDS[i] : "other";
            String label = i < BRANDS.length ? b : s("Drugo", "Other");
            TextView k = Mib.text(act, label, 28, Mib.regular(act), b.equals(brand) ? 0xFFFFFFFF : Mib.BAR_TEXT);
            k.setGravity(Gravity.CENTER);
            k.setBackground(b.equals(brand) ? blueBar() : Mib.settingsBar(act));
            k.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.put(act, P_BRAND, b);
                    render();
                }
            });
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i % 3 != 0) kl.leftMargin = Mib.px(act, 10);
            grid.addView(k, kl);
        }
        if ("Opel".equals(brand)) {
            button(s("Ugrađene slike Corsa C (automobil i unutrašnjost)", "Built-in Corsa C pictures (car and cabin)"), new Runnable() {
                public void run() {
                    CarImages.useBuiltin(act, CarImages.EXTERIOR);
                    CarImages.useBuiltin(act, CarImages.INTERIOR);
                    act.carPicturesChanged();
                    render();
                }
            });
        }
        pictureRow(s("Slika automobila", "Car picture"), CarImages.EXTERIOR, SettingsScreen.PAGE_PICTURE_CAR);
        pictureRow(s("Unutrašnjost (ambijentalno osvetljenje)", "Cabin (ambient lighting)"), CarImages.INTERIOR, SettingsScreen.PAGE_PICTURE_CABIN);
        if (brand != null && !"Opel".equals(brand)) {
            note(s("Ugrađene slike postoje za Opel Corsa C; za druga vozila uvezi sopstvenu fotografiju sa uređaja ili USB-a.",
                    "Built-in pictures exist for the Opel Corsa C; for other vehicles import your own photo from the unit or USB."));
        }
        button(s("Dalje", "Next"), new Runnable() {
            public void run() { go(AMBIENT); }
        });
    }

    private void pictureRow(String title, int which, final int page) {
        LinearLayout row = bar(title, CarImages.label(act, which));
        ImageView th = new ImageView(act);
        th.setScaleType(ImageView.ScaleType.CENTER_CROP);
        th.setBackgroundColor(0xFF2A2A30);
        th.setImageBitmap(CarImages.get(act, which));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Mib.px(act, 88), Mib.px(act, 58));
        tl.rightMargin = Mib.px(act, 18);
        row.addView(th, 0, tl);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { act.showSettings(page); }
        });
    }

    /** Main settings, plus the ones that belong to this unit when it is supported. */
    private void renderUnit() {
        heading.setText(s("Podešavanja", "Settings"));
        String unit = UnitKind.name(act, UnitKind.detect(act));
        text(supported ? s("Preporučena podešavanja za ovaj uređaj:", "Recommended settings for this unit:") + " " + unit
                : s("Osnovna podešavanja (ovaj uređaj još nije podržan, pa su posebna podešavanja uređaja skrivena).",
                "Main settings (this unit is not supported yet, so its own settings are hidden)."));

        caption(s("Pri pokretanju uređaja", "When the unit starts"));
        int cur = StartupConnect.preset(act);
        for (int i = 0; i < StartupConnect.PRESETS.length; i++) {
            final int idx = i;
            Object[] p = StartupConnect.PRESETS[i];
            checkRow(s((String) p[0], (String) p[1]), cur == i, new Runnable() {
                public void run() {
                    StartupConnect.applyPreset(act, idx);
                    render();
                }
            });
        }

        final RadioController radio = RadioController.get(act);
        caption("Radio");
        final boolean fm = supported && radio.fmAvailable();
        if (fm) {
            checkRow(s("FM radio", "FM radio"), !radio.online(), new Runnable() {
                public void run() {
                    radio.setOnline(false);
                    render();
                }
            });
        }
        checkRow(s("Internet radio", "Online radio"), radio.online() || !fm, new Runnable() {
            public void run() {
                radio.setOnline(true);
                render();
            }
        });
        if (fm) {
            toggleRow(s("Samo jake stanice (LOC)", "Strong stations only (LOC)"), Prefs.RADIO_LOC, false, new Runnable() {
                public void run() { radio.setLocal(Prefs.bool(act, Prefs.RADIO_LOC, false)); }
            });
            toggleRow("RDS", Prefs.RADIO_RDS, true, new Runnable() {
                public void run() { radio.setRds(Prefs.bool(act, Prefs.RADIO_RDS, true)); }
            });
        } else {
            note(s("FM tjuner ovog uređaja ne može da se koristi iz launchera; internet radio radi preko interneta.",
                    "This unit's FM tuner cannot be used from the launcher; online radio plays over the internet."));
        }

        caption(s("Mediji i Bluetooth", "Media and Bluetooth"));
        toggleRow(s("Nastavi reprodukciju pri pokretanju", "Resume playback on start"), Prefs.RESUME_MEDIA, true, null);
        if (supported && com.thecarlauncher.unit.Unit.QF) {
            toggleRow(s("BT audio čim se telefon poveže", "BT audio as soon as the phone connects"), Prefs.BT_AUTO_SELECT, true, null);
        }
        if (supported && com.thecarlauncher.unit.Unit.CAR) {
            toggleRow(s("Sakrij fabrički plejer", "Hide the factory player"), MediaHub.P_HIDE_FACTORY_PLAYER, true, null);
        }
        if (!com.thecarlauncher.unit.Unit.QF && com.thecarlauncher.media.UnitBackend.Get.of(act) == null) {
            // Units whose factory BT app makes the sound.
            toggleRow(s("Otvori BT aplikaciju na trenutak (pušta zvuk)", "Open the BT app briefly (plays the sound)"),
                    Prefs.BT_OPEN_APP, MediaHub.get(act).btOpenAppDefault(), null);
        }
        button(s("Muzičke fascikle", "Music folders"), new Runnable() {
            public void run() { act.showSettings(SettingsScreen.PAGE_MEDIA); }
        });

        caption(s("Navigacija", "Navigation"));
        button(s("Navigacija i offline mape", "Navigation and offline maps"), new Runnable() {
            public void run() { act.showSettings(SettingsScreen.PAGE_NAV); }
        });

        caption(s("Ažuriranje softvera", "Software update"));
        toggleRow(s("Automatski proveri ažuriranja", "Check for updates automatically"), Updater.P_AUTO, true, null);
        toggleRow(s("Probne verzije", "Test builds"), Updater.P_BETA, false, null);

        button(s("Dalje", "Next"), new Runnable() {
            public void run() { go(PHONE); }
        });
    }

    private void renderLanguage() {
        heading.setText(s("Jezik i region", "Language and region"));
        caption("Jezik / Language");
        boolean auto = com.thecarlauncher.util.Lang.automatic(act);
        String cur = com.thecarlauncher.util.Lang.current(act);
        checkRow(s("Automatski (po regionu)", "Automatic (by region)"), auto, new Runnable() {
            public void run() { setLanguage(com.thecarlauncher.util.Lang.AUTO); }
        });
        for (final String[] l : com.thecarlauncher.util.Lang.CHOICES) {
            LinearLayout row = checkRow(l[1], !auto && l[0].equals(cur), new Runnable() {
                public void run() { setLanguage(l[0]); }
            });
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 30));
            fl.rightMargin = Mib.px(act, 18);
            row.addView(com.thecarlauncher.mib.FlagDrawable.view(act, l[0]), 0, fl);
        }
        caption(s("Jedinice", "Units"));
        final boolean metric = Prefs.metric(act);
        checkRow(s("Kilometri (km/h)", "Kilometres (km/h)"), metric, new Runnable() {
            public void run() {
                Prefs.put(act, Prefs.UNITS, "kmh");
                render();
            }
        });
        checkRow(s("Milje (mph)", "Miles (mph)"), !metric, new Runnable() {
            public void run() {
                Prefs.put(act, Prefs.UNITS, "mph");
                render();
            }
        });
        caption(s("Vreme i datum", "Time and date"));
        checkRow(s("Format 24 časa", "24-hour format"), Prefs.use24h(act), new Runnable() {
            public void run() {
                Prefs.put(act, Prefs.CLOCK_24, !Prefs.use24h(act));
                render();
            }
        });
        button(s("Datum i vreme (Android)", "Date and time (Android)"), new Runnable() {
            public void run() {
                Ui.start(act, new Intent(android.provider.Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        button(s("Dalje", "Next"), new Runnable() {
            public void run() { go(WIFI); }
        });
    }

    private void setLanguage(String l) {
        Prefs.put(act, Prefs.LANGUAGE, l);
        act.restartUi(); // the assistant comes back at this step in the new language
    }

    private void renderScreen() {
        heading.setText(s("Ekran", "Screen"));
        final android.app.Activity a = act;
        stepper(s("Osvetljenost", "Brightness"), com.thecarlauncher.util.DisplayControl.brightness(act) + " %", new Runnable() {
            public void run() {
                com.thecarlauncher.util.DisplayControl.setBrightness(a, com.thecarlauncher.util.DisplayControl.brightness(a) - 10);
                render();
            }
        }, new Runnable() {
            public void run() {
                com.thecarlauncher.util.DisplayControl.setBrightness(a, com.thecarlauncher.util.DisplayControl.brightness(a) + 10);
                render();
            }
        });
        if (!com.thecarlauncher.util.DisplayControl.canWrite(act)) {
            button(s("Dozvoli menjanje osvetljenosti sistema", "Allow changing the system brightness"), new Runnable() {
                public void run() { com.thecarlauncher.util.DisplayControl.requestWrite(act); }
            });
        } else {
            checkRow(s("Automatska osvetljenost", "Automatic brightness"), com.thecarlauncher.util.DisplayControl.autoBrightness(act), new Runnable() {
                public void run() {
                    com.thecarlauncher.util.DisplayControl.setAuto(act, !com.thecarlauncher.util.DisplayControl.autoBrightness(act));
                    render();
                }
            });
        }
        checkRow(s("Pun ekran (sakrij Android trake)", "Full screen (hide Android bars)"), Prefs.bool(act, Prefs.IMMERSIVE, false), new Runnable() {
            public void run() {
                Prefs.put(act, Prefs.IMMERSIVE, !Prefs.bool(act, Prefs.IMMERSIVE, false));
                act.restartUi();
            }
        });
        bar(s("Početni ekran pri pokretanju", "Boot screen"), com.thecarlauncher.util.BootScreen.on(act)
                ? s("uključen", "on") : s("isključen", "off")).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { act.showSettings(SettingsScreen.PAGE_BOOT); }
        });
        button(s("Profil boja i ostalo", "Colour profile and more"), new Runnable() {
            public void run() { act.showSettings(SettingsScreen.PAGE_SCREEN); }
        });
        button(s("Dalje", "Next"), new Runnable() {
            public void run() { go(VEHICLE); }
        });
    }

    /** Ambient lighting: the cabin picture with the colour slider and the brightness, like Setup. */
    private void renderAmbient() {
        heading.setText(s("Ambijentalno osvetljenje", "Ambient lighting"));
        text(s("Boja ambijentalnog osvetljenja je i boja celog launchera.", "The ambient lighting colour is also the colour of the whole launcher."));
        AmbientScreen.InteriorView cabin = new AmbientScreen.InteriorView(act);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 210));
        cl.leftMargin = Mib.px(act, 18);
        cl.topMargin = Mib.px(act, 6);
        list.addView(cabin, cl);
        final int cur = Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT);
        int sel = 0;
        for (int i = 0; i < Mib.AMBIENT.length; i++) if ((Integer) Mib.AMBIENT[i][2] == cur) sel = i;
        AmbientScreen.ColorSlider slider = new AmbientScreen.ColorSlider(act);
        slider.setSelected(sel);
        slider.setListener(new AmbientScreen.ColorSlider.Listener() {
            public void onPick(int index, boolean done) {
                int color = (Integer) Mib.AMBIENT[index][2];
                if (done && color != Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT)) {
                    Prefs.put(act, Prefs.AMBIENT, color);
                    act.restartUi();
                }
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 64));
        sl.leftMargin = Mib.px(act, 18);
        sl.topMargin = Mib.px(act, 10);
        list.addView(slider, sl);
        String name = s((String) Mib.AMBIENT[sel][0], (String) Mib.AMBIENT[sel][1]);
        note(s("Boja: ", "Colour: ") + name);
        caption(s("Jačina", "Brightness"));
        final int level = Prefs.integer(act, Prefs.AMBIENT_LEVEL, 100);
        for (final int l : new int[]{100, 80, 60}) {
            checkRow(l + " %", level == l, new Runnable() {
                public void run() {
                    if (level == l) return;
                    Prefs.put(act, Prefs.AMBIENT_LEVEL, l);
                    act.restartUi();
                }
            });
        }
        button(s("Dalje", "Next"), new Runnable() {
            public void run() { go(UNIT); }
        });
    }

    private void renderPhone() {
        heading.setText("Bluetooth");
        text(s("Poveži telefon za muziku i pozive.", "Connect your phone for music and calls."));
        button(s("Bluetooth uređaji", "Bluetooth devices"), new Runnable() {
            public void run() { openBluetooth(); }
        });
        button(s("Dalje", "Next"), new Runnable() {
            public void run() { go(RADIO); }
        });
    }

    /** The launcher's own device page where it drives the phone link, else Android's. */
    private void openBluetooth() {
        if (com.thecarlauncher.unit.Unit.QF || com.thecarlauncher.media.BtDevices.get(act).nforce()) {
            act.show(LauncherActivity.BTDEVICES, true);
            return;
        }
        if (!Ui.start(act, new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            act.showSettings(SettingsScreen.PAGE_BLUETOOTH);
        }
    }

    private void renderRadio() {
        heading.setText(s("Provera radija", "Radio check"));
        final RadioController r = RadioController.get(act);
        if (!r.fmAvailable()) {
            status(s("FM radio nije dostupan na ovom uređaju", "FM radio is not available on this unit"), false);
            String err = r.error();
            if (err != null) note(err);
            if (radioResult == null) result("Radio", "FM not available" + (err != null ? " (" + err + ")" : ""));
            button(s("Dalje", "Next"), new Runnable() {
                public void run() { go(BT_AUDIO); }
            });
            return;
        }
        if (radioSearching || r.scanning()) {
            text(s("Traženje radio stanica…", "Searching for radio stations…") + " " + r.stations().size());
            return;
        }
        if (r.stations().isEmpty()) {
            text(s("Launcher će potražiti stanice i pustiti prvu pronađenu.", "The launcher will search for stations and play the first one it finds."));
            button(s("Traži stanice", "Search for stations"), new Runnable() {
                public void run() {
                    radioSearching = true;
                    r.start();
                    r.startScan();
                    render();
                }
            });
        } else {
            String name = r.ps();
            status(s("Pronađeno stanica: ", "Stations found: ") + r.stations().size() + "  ·  "
                    + String.format(Locale.US, "%.2f MHz", r.freq() / 100f) + (name != null && !name.isEmpty() ? " " + name : ""), true);
            text(s("Da li čuješ radio?", "Do you hear the radio?"));
            button(s("Da, radio se čuje", "Yes, I hear the radio"), new Runnable() {
                public void run() {
                    radioResult = "ok";
                    result("Radio", "sound OK, " + r.stations().size() + " stations");
                    go(BT_AUDIO);
                }
            });
            button(s("Ne čujem ništa", "I hear nothing"), new Runnable() {
                public void run() {
                    radioResult = "no sound";
                    result("Radio", "NO SOUND, " + r.stations().size() + " stations, " + r.freq());
                    go(BT_AUDIO);
                }
            });
            button(s("Traži ponovo", "Search again"), new Runnable() {
                public void run() {
                    radioSearching = true;
                    r.startScan();
                    render();
                }
            });
            return;
        }
        button(s("Preskoči", "Skip"), new Runnable() {
            public void run() {
                result("Radio", "skipped");
                go(BT_AUDIO);
            }
        });
    }

    private void renderBtAudio() {
        heading.setText(s("Provera Bluetooth zvuka", "Bluetooth audio check"));
        MediaHub hub = MediaHub.get(act);
        text(s("Pusti muziku na telefonu. Launcher je prebačen na BT audio.", "Play music on your phone. The launcher has switched to BT audio."));
        String title = hub.hasTrackInfo() ? hub.title() : null;
        if (title != null) {
            String artist = hub.artist();
            status((hub.isPlaying() ? "▶  " : "❚❚  ") + title + (artist != null && !artist.isEmpty() ? " — " + artist : ""), true);
        } else {
            status(s("Još nema pesme sa telefona", "No song from the phone yet"), false);
        }
        button(s("Pusti / pauziraj", "Play / pause"), new Runnable() {
            public void run() { MediaHub.get(act).playPause(); }
        });
        button(s("Da, muzika se čuje", "Yes, I hear the music"), new Runnable() {
            public void run() {
                btResult = "ok";
                result("BT audio", "sound OK" + (MediaHub.get(act).hasTrackInfo() ? ", song shown" : ", no song info"));
                go(DONE);
            }
        });
        button(s("Ne čujem ništa", "I hear nothing"), new Runnable() {
            public void run() {
                btResult = "no sound";
                MediaHub h = MediaHub.get(act);
                result("BT audio", "NO SOUND, song=" + (h.hasTrackInfo() ? h.title() : "none") + " playing=" + h.isPlaying());
                go(DONE);
            }
        });
        button(s("Preskoči", "Skip"), new Runnable() {
            public void run() {
                result("BT audio", "skipped");
                go(DONE);
            }
        });
    }

    private void renderDone() {
        heading.setText(s("Sve je spremno", "All set"));
        boolean problem = !supported || "no sound".equals(radioResult) || "no sound".equals(btResult);
        String results = Prefs.str(act, P_RESULTS, "");
        for (String line : results.split("\n")) {
            if (line.isEmpty()) continue;
            boolean ok = !line.contains("NO SOUND") && !line.contains("not available");
            status(line, ok);
        }
        if (problem) {
            text(s("Nešto ne radi kako treba. Izveštaj o uređaju (sa rezultatima provere) pomaže da se to popravi u sledećem ažuriranju.",
                    "Something does not work as it should. The device report (with the check results) helps fix it in a coming update."));
            button(reportBusy ? s("Slanje izveštaja o uređaju…", "Sending the device report…") : s("Pošalji izveštaj o uređaju", "Send the device report"), new Runnable() {
                public void run() {
                    sendReport();
                    render();
                }
            });
            if (reportResult != null) note(reportResult);
        } else {
            text(s("Uređaj je podešen. Sva podešavanja su i kasnije u meniju Podešavanje.",
                    "The unit is set up. Every setting can be changed later in Setup."));
        }
        button(s("Završi", "Finish"), new Runnable() {
            public void run() { finish(); }
        });
    }

    /** Records a check result for the summary and the device report (one line per check). */
    private void result(String what, String value) {
        StringBuilder out = new StringBuilder();
        for (String line : Prefs.str(act, P_RESULTS, "").split("\n")) {
            if (!line.isEmpty() && !line.startsWith(what + ":")) out.append(line).append('\n');
        }
        out.append(what).append(": ").append(value);
        Prefs.put(act, P_RESULTS, out.toString().trim());
    }

    // ---- Views ---------------------------------------------------------------------------------

    private void text(String t) {
        TextView v = Mib.text(act, t, 28, Mib.regular(act), Mib.WHITE);
        v.setSingleLine(false);
        v.setLineSpacing(0, 1.15f);
        v.setPadding(Mib.px(act, 30), Mib.px(act, 16), Mib.px(act, 24), Mib.px(act, 8));
        list.addView(v);
    }

    private void caption(String t) {
        TextView v = Mib.text(act, t, 24, Mib.semibold(act), Mib.GREY);
        v.setPadding(Mib.px(act, 30), Mib.px(act, 18), Mib.px(act, 24), 0);
        list.addView(v);
    }

    private void note(String t) {
        TextView n = Mib.text(act, t, 22, Mib.regular(act), Mib.GREY);
        n.setSingleLine(false);
        n.setMaxLines(5);
        n.setPadding(Mib.px(act, 30), Mib.px(act, 10), Mib.px(act, 24), Mib.px(act, 6));
        list.addView(n);
    }

    private void status(String t, boolean ok) {
        TextView v = Mib.text(act, (ok ? "✓  " : "!  ") + t, 30, Mib.semibold(act), ok ? Mib.WHITE : 0xFFFFB347);
        v.setSingleLine(false);
        v.setPadding(Mib.px(act, 30), Mib.px(act, 14), Mib.px(act, 24), Mib.px(act, 6));
        list.addView(v);
    }

    private android.graphics.drawable.Drawable blueBar() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Mib.shade(Mib.BLUE, 0.6f, 0xFF), Mib.shade(Mib.BLUE, 1.05f, 0xFF)});
        d.setCornerRadius(Mib.px(act, 3));
        return d;
    }

    private LinearLayout bar(String title, String value) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Mib.settingsBar(act));
        row.setPadding(Mib.px(act, 26), 0, Mib.px(act, 22), 0);
        row.addView(Mib.text(act, title, 30, Mib.regular(act), Mib.BAR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) row.addView(Mib.text(act, value, 24, Mib.regular(act), Mib.BAR_VALUE));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 78));
        l.leftMargin = Mib.px(act, 18);
        l.topMargin = Mib.px(act, 10);
        list.addView(row, l);
        return row;
    }

    private LinearLayout checkRow(String title, boolean on, final Runnable r) {
        LinearLayout row = bar(title, null);
        row.addView(Mib.icon(act, on ? R.drawable.ui_ic_check_on : R.drawable.ui_ic_check_off, 0xFF1C1C22),
                new LinearLayout.LayoutParams(Mib.px(act, 46), Mib.px(act, 46)));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        return row;
    }

    private void toggleRow(String title, final String key, final boolean def, final Runnable then) {
        checkRow(title, Prefs.bool(act, key, def), new Runnable() {
            public void run() {
                Prefs.put(act, key, !Prefs.bool(act, key, def));
                if (then != null) then.run();
                render();
            }
        });
    }

    /** A bar with − / + keys at the end. */
    private void stepper(String title, String value, final Runnable minus, final Runnable plus) {
        LinearLayout row = bar(title, value);
        for (int i = 0; i < 2; i++) {
            final Runnable r = i == 0 ? minus : plus;
            TextView k = Mib.text(act, i == 0 ? "−" : "+", 34, Mib.semibold(act), Mib.BAR_TEXT);
            k.setGravity(Gravity.CENTER);
            k.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { r.run(); }
            });
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(Mib.px(act, 64), ViewGroup.LayoutParams.MATCH_PARENT);
            kl.leftMargin = Mib.px(act, 8);
            row.addView(k, kl);
        }
    }

    private void button(String text, final Runnable r) {
        TextView b = Mib.text(act, text, 30, Mib.regular(act), Mib.BAR_TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Mib.px(act, 26), 0, Mib.px(act, 22), 0);
        b.setBackground(Mib.settingsBar(act));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 78));
        l.leftMargin = Mib.px(act, 18);
        l.topMargin = Mib.px(act, 10);
        list.addView(b, l);
    }
}
