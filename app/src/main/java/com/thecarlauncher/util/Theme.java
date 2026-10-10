package com.thecarlauncher.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Accent colour handling. Views opt in through their tag:
 * "accent" tints an ImageView, "accent_text" colours a TextView.
 */
public final class Theme {
    public static final int[] ACCENTS = {
            0xFF2FA4FF, 0xFFE4002B, 0xFFFF9F1C, 0xFFFFE11A, 0xFF19D3A2, 0xFF8C7CFF, 0xFFE8EDF2
    };
    public static final String[] ACCENT_NAMES = {
            "Ice Blue", "Racing Red", "Signal Amber", "Lightning Yellow", "Emerald", "Ultraviolet", "Platinum"
    };

    public static final int TEXT_PRIMARY = 0xFFF3F6F9;
    public static final int TEXT_SECONDARY = 0xFF9AA6B4;
    public static final int TEXT_DIM = 0xFF5C6876;
    public static final int STROKE = 0xFF2C3846;

    private Theme() {}

    public static int accent(Context c) {
        return Prefs.integer(c, Prefs.ACCENT, ACCENTS[0]);
    }

    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Dark text on bright accents, white text on dark ones. */
    public static int onAccent(int accent) {
        double lum = 0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent);
        return lum > 150 ? 0xFF061018 : 0xFFFFFFFF;
    }

    public static void apply(View v, int accent) {
        Object tag = v.getTag();
        if ("accent".equals(tag) && v instanceof ImageView) {
            ((ImageView) v).setImageTintList(ColorStateList.valueOf(accent));
        } else if ("accent_text".equals(tag) && v instanceof TextView) {
            ((TextView) v).setTextColor(accent);
        }
        if (v instanceof ProgressBar) {
            ((ProgressBar) v).setProgressTintList(ColorStateList.valueOf(accent));
            if (v instanceof SeekBar) {
                ((SeekBar) v).setThumbTintList(ColorStateList.valueOf(TEXT_PRIMARY));
            }
        }
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            Drawable[] ds = tv.getCompoundDrawablesRelative();
            Drawable[] ds2 = tv.getCompoundDrawables();
            if (ds2[0] != null || ds[0] != null) {
                tv.setCompoundDrawableTintList(ColorStateList.valueOf("accent_text".equals(tag) ? accent : TEXT_SECONDARY));
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), accent);
        }
    }

    public static void tint(ImageView v, int color) {
        v.setImageTintList(ColorStateList.valueOf(color));
    }

    /** Round play button: soft accent fill with a crisp accent ring. */
    public static Drawable playButton(Context c, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColors(new int[]{alpha(accent, 0x55), alpha(accent, 0x22)});
        d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        d.setGradientRadius(Ui.dp(c, 44));
        d.setStroke(Ui.dp(c, 2), accent);
        return new RippleDrawable(ColorStateList.valueOf(alpha(accent, 0x66)), d, null);
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    public static Drawable accentButton(Context c, int accent) {
        GradientDrawable d = round(accent, Ui.dp(c, 12));
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), d, null);
    }

    /** Thin horizontal light strip fading out at both ends. */
    public static Drawable glowLine(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{alpha(accent, 0), alpha(accent, 0xCC), alpha(accent, 0xCC), alpha(accent, 0)});
        return d;
    }

    /** Selected-state marker: a short glowing bar. */
    public static Drawable indicator(Context c, int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{alpha(accent, 0x40), accent, alpha(accent, 0x40)});
        d.setCornerRadius(Ui.dp(c, 2));
        return d;
    }

    public static Drawable chipSelected(Context c, int accent) {
        GradientDrawable d = round(alpha(accent, 0x2E), Ui.dp(c, 19));
        d.setStroke(Ui.dp(c, 1), accent);
        return d;
    }
}
