package com.thecarlauncher.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
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

    public interface NumberEntered { void onNumber(double value); }
    public interface DatePicked { void onDate(long millis); }
    public interface RefuelEntered { void onRefuel(double litres, double price, boolean full); }

    private static EditText numberField(Context c, String hint, String value) {
        EditText f = new EditText(c);
        f.setSingleLine(true);
        f.setHint(hint);
        if (value != null) f.setText(value);
        f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        f.setTextColor(Theme.TEXT_PRIMARY);
        f.setHintTextColor(Theme.TEXT_DIM);
        if (value != null) f.setSelection(value.length());
        return f;
    }

    private static double parse(EditText f) {
        try {
            return Double.parseDouble(f.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void number(Context c, String title, String hint, String value, final NumberEntered cb) {
        final EditText field = numberField(c, hint, value);
        LinearLayout wrap = new LinearLayout(c);
        int p = Ui.dp(c, 20);
        wrap.setPadding(p, Ui.dp(c, 8), p, 0);
        wrap.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double v = parse(field);
                        if (v >= 0) cb.onNumber(v);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void date(Context c, long initial, final DatePicked cb) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (initial > 0) cal.setTimeInMillis(initial);
        new android.app.DatePickerDialog(c, new android.app.DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(android.widget.DatePicker v, int y, int m, int d) {
                java.util.Calendar out = java.util.Calendar.getInstance();
                out.set(y, m, d, 12, 0, 0);
                cb.onDate(out.getTimeInMillis());
            }
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    /** Fill-up entry: litres, price per litre, and whether the tank was filled to the brim. */
    public static void refuel(Context c, String title, String litresHint, String priceHint, String fullLabel,
                              String lastPrice, final RefuelEntered cb) {
        final EditText litres = numberField(c, litresHint, null);
        final EditText price = numberField(c, priceHint, lastPrice);
        final android.widget.CheckBox full = new android.widget.CheckBox(c);
        full.setText(fullLabel);
        full.setChecked(true);
        full.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(c, 20);
        wrap.setPadding(p, Ui.dp(c, 8), p, 0);
        wrap.addView(litres);
        wrap.addView(price);
        wrap.addView(full);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double l = parse(litres);
                        if (l > 0) cb.onRefuel(l, Math.max(0, parse(price)), full.isChecked());
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

}
