package com.thecarlauncher.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.media.audiofx.Equalizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.media.MusicService;
import com.thecarlauncher.util.Theme;
import com.thecarlauncher.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dark, touch-friendly dialogs used throughout the launcher. */
public final class Dialogs {
    public interface AppPicked { void onPicked(String pkg); }
    public interface TextEntered { void onText(String text); }
    public interface Chosen { void onChosen(int index); }

    private Dialogs() {}

    /** Lets the user pick an installed app. {@code noneLabel} adds a "no app" first row. */
    public static void pickApp(final LauncherActivity act, String title, final String noneLabel, final AppPicked cb) {
        List<AppRepository.App> apps = AppRepository.get(act).all();
        List<String> pkgs = new ArrayList<>();
        for (AppRepository.App a : apps) if (!pkgs.contains(a.pkg)) pkgs.add(a.pkg);
        pickPackage(act, title, noneLabel, pkgs, cb);
    }

    public static void pickPackage(final LauncherActivity act, String title, final String noneLabel,
                                   final List<String> pkgs, final AppPicked cb) {
        final List<String> rows = new ArrayList<>();
        if (noneLabel != null) rows.add(null);
        rows.addAll(pkgs);
        final AppRepository repo = AppRepository.get(act);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(act, R.layout.row_item, rows) {
            @Override
            public View getView(int pos, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(act).inflate(R.layout.row_item, parent, false);
                String pkg = rows.get(pos);
                ImageView icon = v.findViewById(R.id.row_icon);
                TextView t = v.findViewById(R.id.row_title);
                v.findViewById(R.id.row_subtitle).setVisibility(View.GONE);
                if (pkg == null) {
                    icon.setImageResource(R.drawable.ic_close);
                    icon.setImageTintList(android.content.res.ColorStateList.valueOf(Theme.TEXT_SECONDARY));
                    t.setText(noneLabel);
                } else {
                    AppRepository.App a = repo.findPackage(pkg);
                    Drawable d = null;
                    String label = pkg;
                    if (a != null) {
                        d = a.icon;
                        label = a.label;
                    } else {
                        try {
                            d = act.getPackageManager().getApplicationIcon(pkg);
                            label = act.getPackageManager().getApplicationLabel(
                                    act.getPackageManager().getApplicationInfo(pkg, 0)).toString();
                        } catch (Exception ignored) {
                        }
                    }
                    icon.setImageDrawable(d);
                    icon.setImageTintList(null);
                    t.setText(label);
                }
                icon.setPadding(0, 0, 0, 0);
                return v;
            }
        };
        new AlertDialog.Builder(act)
                .setTitle(title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        cb.onPicked(rows.get(which));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void text(Context c, String title, String hint, String value, final TextEntered cb) {
        final EditText field = new EditText(c);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        field.setTextColor(Theme.TEXT_PRIMARY);
        field.setHintTextColor(Theme.TEXT_DIM);
        if (value != null) field.setSelection(value.length());
        LinearLayout wrap = new LinearLayout(c);
        int p = Ui.dp(c, 20);
        wrap.setPadding(p, Ui.dp(c, 8), p, 0);
        wrap.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cb.onText(field.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void choose(Context c, String title, String[] options, int checked, final Chosen cb) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setSingleChoiceItems(options, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        cb.onChosen(which);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Graphic equalizer for the built-in player. */
    public static void equalizer(final Context c, int accent) {
        final MusicService svc = MusicService.get();
        final Equalizer eq = svc == null ? null : svc.equalizer();
        if (eq == null) {
            Ui.toast(c, "This head unit doesn't offer an equalizer for apps. Use the unit's own sound settings.");
            return;
        }
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(c, 20);
        box.setPadding(p, Ui.dp(c, 4), p, 0);

        Switch enable = new Switch(c);
        enable.setText("Equalizer");
        enable.setTextColor(Theme.TEXT_PRIMARY);
        enable.setChecked(eq.getEnabled());
        enable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                eq.setEnabled(on);
            }
        });
        box.addView(enable);

        final short[] range = eq.getBandLevelRange();
        final List<SeekBar> bars = new ArrayList<>();
        for (short i = 0; i < eq.getNumberOfBands(); i++) {
            final short band = i;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(c);
            int hz = eq.getCenterFreq(band) / 1000;
            label.setText(hz >= 1000 ? String.format(Locale.US, "%.1f kHz", hz / 1000f) : hz + " Hz");
            label.setTextColor(Theme.TEXT_SECONDARY);
            label.setWidth(Ui.dp(c, 72));
            SeekBar bar = new SeekBar(c);
            bar.setMax(range[1] - range[0]);
            bar.setProgress(eq.getBandLevel(band) - range[0]);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            bar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int v, boolean user) {
                    if (user) eq.setBandLevel(band, (short) (v + range[0]));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            bars.add(bar);
            row.addView(label);
            row.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f));
            box.addView(row);
        }

        final AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle("Sound")
                .setView(box)
                .setPositiveButton("Done", null)
                .setNeutralButton("Flat", null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        svc.saveEqualizer();
                    }
                })
                .create();
        dlg.show();
        // Override the neutral button so it doesn't close the dialog.
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                short mid = (short) ((range[0] + range[1]) / 2);
                for (short i = 0; i < bars.size(); i++) {
                    eq.setBandLevel(i, mid);
                    bars.get(i).setProgress(mid - range[0]);
                }
            }
        });
    }
}
