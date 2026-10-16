package com.thecarlauncher.screens;

import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.mib.LoadingBanner;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Updater;

import java.util.Locale;

/**
 * Software update in the style of the factory "SW download" screen: a title with two framed
 * keys, Versions and Update. Versions lists the installed software like the factory list;
 * Update offers the newest GitHub release and, while it installs, shows the factory-style
 * "Software update in progress" page with OK / N/A / NOK / Remaining and a bar per step.
 */
public class UpdateScreen extends Screen implements Updater.Progress {
    private LinearLayout list;
    private ScrollView scroll;
    private TextView versionsKey, updateKey;
    private boolean updateTab;
    private boolean checking;
    private String error;

    public UpdateScreen(LauncherActivity act) { super(act); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    public String title() { return s("SW-ažuriranje", "SW download"); }

    @Override
    public boolean hasOwnBack() { return true; }

    @Override
    public boolean onBack() { return false; }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Mib.px(act, 14), 0, 0);

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Mib.px(act, 30), 0, Mib.px(act, 26), Mib.px(act, 10));
        head.addView(Mib.text(act, title(), 40, Mib.semibold(act), Mib.WHITE),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        versionsKey = headKey(s("Verzije", "Versions"));
        updateKey = headKey(s("Ažuriranje", "Update"));
        versionsKey.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setTab(false); }
        });
        updateKey.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setTab(true); }
        });
        head.addView(versionsKey, keyLp(220));
        head.addView(updateKey, keyLp(220));
        FrameLayout back = new FrameLayout(act);
        back.setBackground(Mib.box(act));
        back.addView(Mib.icon(act, R.drawable.ui_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 30), Mib.px(act, 30), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { act.goBack(); }
        });
        head.addView(back, keyLp(70));
        r.addView(head);

        View rule = new View(act);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Mib.shade(Mib.BLUE, 0.8f, 0xFF), Mib.BLUE, Mib.BLUE, Mib.shade(Mib.BLUE, 0.8f, 0x80)}));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2.4f)));
        rl.leftMargin = Mib.px(act, 12);
        rl.rightMargin = Mib.px(act, 12);
        r.addView(rule, rl);

        scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Mib.px(act, 8), Mib.px(act, 6), Mib.px(act, 40), Mib.px(act, 16));
        scroll.addView(list);
        r.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        render();
        return r;
    }

    private TextView headKey(String text) {
        TextView k = Mib.text(act, text, 28, Mib.regular(act), Mib.WHITE);
        k.setGravity(Gravity.CENTER_VERTICAL);
        k.setPadding(Mib.px(act, 14), 0, Mib.px(act, 10), 0);
        k.setBackground(Mib.box(act));
        return k;
    }

    private LinearLayout.LayoutParams keyLp(int w) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Mib.px(act, w), Mib.px(act, 54));
        l.leftMargin = Mib.px(act, 10);
        return l;
    }

    @Override
    public void onShow() {
        Updater.addWatcher(this);
        // While an update runs, the progress page is what matters.
        updateTab = Updater.started();
        if (Updater.lastFound() == null && !checking) check();
        render();
    }

    @Override
    public void onHide() { Updater.removeWatcher(this); }

    @Override
    public void onUpdateProgress() { if (updateTab) render(); }

    private void setTab(boolean update) {
        updateTab = update;
        render();
        list.setAlpha(0f);
        list.animate().alpha(1f).setDuration(160).start();
    }

    private void check() {
        checking = true;
        error = null;
        LoadingBanner.start("update-check", s("Provera ažuriranja…", "Checking for updates…"));
        Updater.check(act, new Updater.Checked() {
            public void onChecked(Updater.Release r, String err) {
                checking = false;
                error = err;
                LoadingBanner.stop("update-check");
                render();
            }
        });
        render();
    }

    private void render() {
        if (list == null) return;
        versionsKey.setTextColor(updateTab ? Mib.GREY : Mib.WHITE);
        updateKey.setTextColor(updateTab ? Mib.WHITE : Mib.GREY);
        list.removeAllViews();
        if (!updateTab) buildVersions();
        else if (Updater.started()) buildProgress();
        else buildUpdate();
    }

    // ---- Versions -----------------------------------------------------------------------------

    private void buildVersions() {
        long code = Updater.installedCode(act);
        String name = Updater.installedName(act);
        String lang = "en".equals(Prefs.str(act, Prefs.LANGUAGE, "sr")) ? "EN" : "SR";
        row(s("Instalirana SW Train verzija:", "Installed SW Train version:"),
                String.format(Locale.US, "TCL_EU_%s_P%04d", lang, code));
        row(s("Instalirana SW MU verzija:", "Installed SW MU version:"), String.format(Locale.US, "%04d", code));
        String model = Build.MODEL == null ? "" : Build.MODEL.replaceAll("\\s+", "");
        row(s("Broj dela:", "Part number:"), model.isEmpty() ? "—" : model.toUpperCase(Locale.ROOT));
        row(s("Verzija softvera:", "Software version:"), "H" + name + "_TCLNav_EU");
        Updater.Release r = Updater.lastFound();
        row(s("Dostupna verzija:", "Available version:"), checking ? s("Provera…", "Checking…")
                : r != null ? r.versionName + (r.date.isEmpty() ? "" : "  ·  " + r.date)
                : error != null ? s("Greška: ", "Error: ") + error : "—");
        long last = Prefs.longVal(act, Updater.P_LAST, 0);
        row(s("Poslednja provera:", "Last check:"), last == 0 ? "—"
                : new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new java.util.Date(last)));
        row("Android:", Build.VERSION.RELEASE + "  ·  " + Build.DISPLAY);
    }

    /** Factory list entry: label on top, value right-aligned below it, a thin rule. */
    private void row(String label, String value) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Mib.px(act, 30), Mib.px(act, 12), Mib.px(act, 24), Mib.px(act, 12));
        box.addView(Mib.text(act, label, 30, Mib.regular(act), Mib.WHITE));
        TextView v = Mib.text(act, value == null ? "—" : value, 30, Mib.regular(act), Mib.WHITE);
        v.setSingleLine(false);
        v.setMaxLines(3);
        v.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.topMargin = Mib.px(act, 14);
        box.addView(v, vl);
        list.addView(box);
        rule();
    }

    private void rule() {
        View rule = new View(act);
        rule.setBackgroundColor(0xFF6A6B72);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1.5f)));
        rl.leftMargin = Mib.px(act, 24);
        list.addView(rule, rl);
    }

    // ---- Update -------------------------------------------------------------------------------

    private void buildUpdate() {
        final Updater.Release r = Updater.lastFound();
        if (checking) {
            row(s("Provera ažuriranja…", "Checking for updates…"), "");
            return;
        }
        if (r == null) {
            row(s("Nema informacija o ažuriranju", "No update information"), error != null ? error : "—");
        } else if (!Updater.isNewer(act, r)) {
            row(s("Softver je ažuran", "The software is up to date"), Updater.installedName(act));
        } else {
            row(s("Dostupno ažuriranje:", "Update available:"), r.versionName + (r.test ? s("  (test)", "  (test)") : "")
                    + (r.date.isEmpty() ? "" : "  ·  " + r.date));
            if (r.notes != null && !r.notes.isEmpty()) {
                TextView n = Mib.text(act, r.notes.length() > 700 ? r.notes.substring(0, 700) + "…" : r.notes, 24, Mib.regular(act), Mib.GREY);
                n.setSingleLine(false);
                n.setMaxLines(8);
                n.setPadding(Mib.px(act, 30), Mib.px(act, 10), Mib.px(act, 24), Mib.px(act, 10));
                list.addView(n);
            }
            button(s("Pokreni ažuriranje", "Start update"), new Runnable() {
                public void run() { Updater.install(act, r); }
            });
            if (!Updater.silent(act)) {
                note(s("Android će tražiti potvrdu instalacije. Kao sistemska aplikacija (Magisk modul) launcher se ažurira sam.",
                        "Android will ask to confirm the install. As a system app (Magisk module) the launcher updates on its own."));
            }
        }
        button(s("Proveri ponovo", "Check again"), new Runnable() {
            public void run() { check(); }
        });
        checkRow(s("Automatska provera (jednom dnevno)", "Automatic check (once a day)"), Updater.P_AUTO, true);
        checkRow(s("Test verzije (svaki build)", "Test builds (every build)"), Updater.P_BETA, false);
        if (!Updater.mayInstall(act)) {
            button(s("Dozvoli instaliranje ažuriranja", "Allow installing updates"), new Runnable() {
                public void run() { Updater.askInstallPermission(act); }
            });
        }
    }

    /** Factory "Software update in progress…" page. */
    private void buildProgress() {
        int ok = 0, nok = 0, remaining = 0;
        for (int i = 0; i < Updater.STEPS.length; i++) {
            int p = Updater.progress(i);
            if (Updater.failedStep() == i) nok++;
            else if (p >= 100) ok++;
            else remaining++;
        }
        boolean failed = Updater.failedStep() >= 0;
        boolean done = ok == Updater.STEPS.length;

        LinearLayout top = new LinearLayout(act);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Mib.px(act, 26), Mib.px(act, 12), 0, Mib.px(act, 6));
        if (!failed && !done) {
            ImageView spin = new ImageView(act);
            android.graphics.drawable.AnimationDrawable a = new android.graphics.drawable.AnimationDrawable();
            int[] f = {R.drawable.ui_spin_11, R.drawable.ui_spin_10, R.drawable.ui_spin_09, R.drawable.ui_spin_08,
                    R.drawable.ui_spin_07, R.drawable.ui_spin_06, R.drawable.ui_spin_05, R.drawable.ui_spin_04,
                    R.drawable.ui_spin_03, R.drawable.ui_spin_02, R.drawable.ui_spin_01, R.drawable.ui_spin_00};
            for (int id : f) a.addFrame(act.getDrawable(id).mutate(), 60);
            a.setOneShot(false);
            a.setColorFilter(new android.graphics.PorterDuffColorFilter(Mib.BLUE, android.graphics.PorterDuff.Mode.SRC_IN));
            spin.setImageDrawable(a);
            a.start();
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40));
            sl.rightMargin = Mib.px(act, 16);
            top.addView(spin, sl);
        }
        top.addView(Mib.text(act, failed ? s("Ažuriranje softvera nije uspelo", "Software update failed")
                : done ? s("Ažuriranje softvera završeno", "Software update finished")
                : s("Ažuriranje softvera u toku…", "Software update in progress…"), 32, Mib.semibold(act), Mib.WHITE));
        list.addView(top);

        LinearLayout counts = new LinearLayout(act);
        counts.setPadding(Mib.px(act, 26), Mib.px(act, 6), Mib.px(act, 10), Mib.px(act, 10));
        String[] c = {"OK: " + ok, "N/A: 0", "NOK: " + nok, s("Preostalo: ", "Remaining: ") + remaining};
        for (String t : c) {
            TextView v = Mib.text(act, t, 28, Mib.semibold(act), Mib.WHITE);
            counts.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, t.startsWith("R") || t.startsWith("P") ? 1.6f : 1f));
        }
        list.addView(counts);

        for (int i = 0; i < Updater.STEPS.length; i++) {
            int p = Math.max(0, Updater.progress(i));
            boolean bad = Updater.failedStep() == i;
            LinearLayout line = new LinearLayout(act);
            line.setOrientation(LinearLayout.VERTICAL);
            line.setPadding(Mib.px(act, 26), Mib.px(act, 8), Mib.px(act, 10), Mib.px(act, 4));
            LinearLayout labelRow = new LinearLayout(act);
            labelRow.addView(Mib.text(act, Updater.STEPS[i], 22, Mib.semibold(act), Mib.WHITE),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView pct = Mib.text(act, bad ? "NOK" : Updater.progress(i) < 0 ? "" : p + " %", 28, Mib.semibold(act), bad ? 0xFFE04545 : Mib.WHITE);
            labelRow.addView(pct);
            line.addView(labelRow);
            // Thin track with a filled part, like the factory bars.
            FrameLayout track = new FrameLayout(act);
            track.setBackgroundColor(0xFF6A6B72);
            View fill = new View(act);
            fill.setBackgroundColor(bad ? 0xFFE04545 : Mib.BLUE);
            track.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            final View ff = fill;
            final FrameLayout tt = track;
            final int pp = bad ? 100 : p;
            track.post(new Runnable() {
                public void run() {
                    FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) ff.getLayoutParams();
                    l.width = tt.getWidth() * pp / 100;
                    ff.setLayoutParams(l);
                }
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 3)));
            tl.topMargin = Mib.px(act, 6);
            line.addView(track, tl);
            list.addView(line);
        }
        if (failed && Updater.failMessage() != null) note(Updater.failMessage());
        if (done) note(s("Launcher se pokreće u novoj verziji.", "The launcher restarts in the new version."));

        LinearLayout bottom = new LinearLayout(act);
        bottom.setGravity(Gravity.RIGHT);
        bottom.setPadding(0, Mib.px(act, 18), Mib.px(act, 10), 0);
        TextView key = Mib.text(act, failed || done ? s("Nazad", "Back") : s("Otkaži", "Cancel"), 28, Mib.regular(act), Mib.WHITE);
        key.setGravity(Gravity.CENTER);
        key.setBackground(Mib.box(act));
        key.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Updater.busy()) Updater.cancel();
                else {
                    Updater.reset();
                    render();
                }
            }
        });
        bottom.addView(key, new LinearLayout.LayoutParams(Mib.px(act, 260), Mib.px(act, 54)));
        list.addView(bottom);
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

    private void checkRow(String text, final String key, final boolean def) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Mib.settingsBar(act));
        row.setPadding(Mib.px(act, 26), 0, Mib.px(act, 22), 0);
        row.addView(Mib.text(act, text, 30, Mib.regular(act), Mib.BAR_TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final ImageView box = Mib.icon(act, Prefs.bool(act, key, def) ? R.drawable.ui_ic_check_on : R.drawable.ui_ic_check_off, 0xFF1C1C22);
        row.addView(box, new LinearLayout.LayoutParams(Mib.px(act, 46), Mib.px(act, 46)));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean on = !Prefs.bool(act, key, def);
                Prefs.put(act, key, on);
                box.setImageResource(on ? R.drawable.ui_ic_check_on : R.drawable.ui_ic_check_off);
                if (Updater.P_BETA.equals(key)) check();
            }
        });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 78));
        l.leftMargin = Mib.px(act, 18);
        l.topMargin = Mib.px(act, 10);
        list.addView(row, l);
    }

    private void note(String text) {
        TextView n = Mib.text(act, text, 22, Mib.regular(act), Mib.GREY);
        n.setSingleLine(false);
        n.setMaxLines(4);
        n.setPadding(Mib.px(act, 30), Mib.px(act, 10), Mib.px(act, 24), Mib.px(act, 6));
        list.addView(n);
    }
}
