package com.thecarlauncher.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.util.Compat;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.util.UnitKind;
import com.thecarlauncher.util.Updater;
import com.thecarlauncher.util.WifiControl;

/**
 * First start: welcome, Wi-Fi, compatibility check (with the update to the right build for this
 * unit), then the phone over Bluetooth. A unit the launcher does not know yet gets its device
 * report sent and may continue; it is told when a later release supports it (Compat.checkLater).
 * The step is remembered, so after an update installed from here the assistant goes on.
 */
public class WelcomeScreen extends Screen {
    public static final String P_DONE = "welcome_done", P_STEP = "welcome_step";
    static final int WELCOME = 0, WIFI = 1, COMPAT = 2, BLUETOOTH = 3, DONE = 4;

    private LinearLayout list;
    private TextView heading, stepText;
    private int step;
    // Compatibility check state
    private boolean checking, checked, offline, supported, reportBusy;
    private Updater.Release release;
    private String reportResult;

    public WelcomeScreen(LauncherActivity act) { super(act); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    /**
     * True when the assistant should open: never finished, and either a fresh install or an
     * assistant left half way (e.g. by its own update). Installs that existed before it are done.
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

    @Override
    public String title() { return s("Dobro došli", "Welcome"); }

    @Override
    public boolean hasOwnBack() { return true; }

    /** Back goes to the previous step, never out of the assistant. */
    @Override
    public boolean onBack() {
        if (step > WELCOME && step != DONE) go(step == BLUETOOTH && !supported ? COMPAT : step - 1);
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
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Mib.shade(Mib.BLUE, 0.8f, 0xFF), Mib.BLUE, Mib.BLUE, Mib.shade(Mib.BLUE, 0.8f, 0x80)}));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2.4f)));
        rl.leftMargin = Mib.px(act, 12);
        rl.rightMargin = Mib.px(act, 12);
        r.addView(rule, rl);

        ScrollView scroll = new ScrollView(act);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Mib.px(act, 8), Mib.px(act, 6), Mib.px(act, 40), Mib.px(act, 16));
        scroll.addView(list);
        r.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        step = Math.max(WELCOME, Math.min(DONE, Prefs.integer(act, P_STEP, WELCOME)));
        return r;
    }

    @Override
    public void onShow() {
        if (step == WIFI) WifiControl.get(act).addListener(wifiListener);
        render();
    }

    @Override
    public void onHide() {
        WifiControl.get(act).removeListener(wifiListener);
    }

    private final WifiControl.Listener wifiListener = new WifiControl.Listener() {
        public void onWifiChanged() { if (step == WIFI) render(); }
    };

    private void go(int to) {
        step = to;
        Prefs.put(act, P_STEP, to);
        if (to == WIFI) WifiControl.get(act).addListener(wifiListener);
        if (to == COMPAT && !checked) check();
        render();
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
        stepText.setText(step == DONE ? "" : (step + 1) + " / 4");
        switch (step) {
            case WELCOME:
                heading.setText(s("Dobro došli", "Welcome"));
                text(s("Car Launcher je spreman za ovaj uređaj. Za nekoliko koraka povezaćemo internet, proveriti da li je softver za ovaj uređaj odgovarajući i povezati telefon.",
                        "Car Launcher is ready to set up this unit. In a few steps we connect the internet, check that the software fits this unit and connect your phone."));
                button(s("Započni", "Start"), new Runnable() {
                    public void run() { go(WIFI); }
                });
                button(s("Preskoči podešavanje", "Skip setup"), new Runnable() {
                    public void run() { finish(); }
                });
                break;
            case WIFI:
                renderWifi();
                break;
            case COMPAT:
                renderCompat();
                break;
            case BLUETOOTH:
                renderBluetooth();
                break;
            default:
                heading.setText(s("Sve je spremno", "All set"));
                text(s("Uređaj je podešen. Sva podešavanja su i kasnije u meniju Podešavanje.",
                        "The unit is set up. Every setting can be changed later in Setup."));
                button(s("Završi", "Finish"), new Runnable() {
                    public void run() { finish(); }
                });
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
        checking = false;
        checked = true;
        release = r;
        supported = Compat.recognised(act);
        Prefs.put(act, Compat.P_UNSUPPORTED, !supported);
        if (!supported && !reportBusy && reportResult == null) {
            reportBusy = true;
            Compat.sendReport(act, new Compat.Sent() {
                @Override
                public void onSent(String url, String savedPath, String error) {
                    reportBusy = false;
                    if (url != null) reportResult = s("Izveštaj o uređaju je poslat programeru.", "The device report was sent to the developer.");
                    else if ("offline".equals(error)) reportResult = s("Izveštaj o uređaju biće poslat čim uređaj dobije internet. Sačuvan je i ovde: ", "The device report will be sent as soon as the unit is online. It is also saved here: ") + savedPath;
                    else reportResult = s("Izveštaj o uređaju je sačuvan; pošalji ga programeru: ", "The device report was saved; send it to the developer: ") + savedPath;
                    if (step == COMPAT) render();
                }
            });
        }
        if (step == COMPAT) render();
    }

    private void renderCompat() {
        heading.setText(s("Kompatibilnost", "Compatibility"));
        if (checking || !checked) {
            text(s("Provera da li softver odgovara ovom uređaju…", "Checking that the software fits this unit…"));
            if (!checking && !checked) check();
            return;
        }
        if (supported) {
            String name = UnitKind.name(act, UnitKind.detect(act));
            status(s("Uređaj je podržan: ", "This unit is supported: ") + name, true);
            if (release != null && Updater.isNewer(act, release)) {
                text(Updater.switching(act)
                        ? s("Za ovaj uređaj postoji posebna verzija launchera. Instaliraj je da bi radio i Bluetooth radili kako treba.",
                        "There is a launcher build made for this unit. Install it so the radio and Bluetooth work properly.")
                        + " (" + release.versionName + ")"
                        : s("Dostupna je nova verzija:", "A new version is available:") + " " + release.versionName);
                button(s("Instaliraj sada", "Install now"), new Runnable() {
                    public void run() {
                        Prefs.put(act, P_STEP, BLUETOOTH); // the assistant goes on after the update
                        act.show(LauncherActivity.SOFTWARE, true);
                    }
                });
                button(s("Kasnije", "Later"), new Runnable() {
                    public void run() { go(BLUETOOTH); }
                });
            } else {
                if (offline) note(s("Bez interneta: provereno prema pravilima ugrađenim u launcher.",
                        "No internet: checked against the rules built into the launcher."));
                button(s("Dalje", "Next"), new Runnable() {
                    public void run() { go(BLUETOOTH); }
                });
            }
            return;
        }
        status(s("Ovaj uređaj još nije podržan", "This unit is not supported yet"), false);
        text(s("Launcher radi, ali radio i Bluetooth muzika možda neće raditi preko fabričkih servisa ovog uređaja. Podrška može stići uskoro: kada uređaj bude na internetu i bude dostupno ažuriranje kompatibilnosti, launcher će to javiti.",
                "The launcher works, but the radio and Bluetooth music may not work through this unit's own services. Support may come soon: when the unit is online and a compatibility update is available, the launcher will tell you."));
        note(reportBusy || reportResult == null ? s("Slanje izveštaja o uređaju…", "Sending the device report…") : reportResult);
        button(s("Nastavi", "Continue"), new Runnable() {
            public void run() { go(DONE); }
        });
    }

    private void renderBluetooth() {
        heading.setText("Bluetooth");
        text(s("Poveži telefon za muziku i pozive.", "Connect your phone for music and calls."));
        button(s("Bluetooth uređaji", "Bluetooth devices"), new Runnable() {
            public void run() { openBluetooth(); }
        });
        button(s("Dalje", "Next"), new Runnable() {
            public void run() { go(DONE); }
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

    // ---- Views ---------------------------------------------------------------------------------

    private void text(String t) {
        TextView v = Mib.text(act, t, 28, Mib.regular(act), Mib.WHITE);
        v.setSingleLine(false);
        v.setLineSpacing(0, 1.15f);
        v.setPadding(Mib.px(act, 30), Mib.px(act, 16), Mib.px(act, 24), Mib.px(act, 8));
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
        v.setPadding(Mib.px(act, 30), Mib.px(act, 14), Mib.px(act, 24), Mib.px(act, 6));
        list.addView(v);
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
