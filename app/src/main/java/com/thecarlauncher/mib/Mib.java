package com.thecarlauncher.mib;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

import com.thecarlauncher.util.Prefs;

/**
 * The MIB 2.5-style skin: palette, typefaces, proportional sizing and the stock drawables
 * (menu tiles, soft-key bar, bordered popups, light settings bars).
 *
 * Everything is sized against a 1024×600 reference screen and scaled to the real panel,
 * so 800×480, 1024×600 and 1280×720 units all keep the same proportions.
 */
public final class Mib {
    public static final int DEFAULT_AMBIENT = 0xFF2D8CEB;

    /**
     * The "ambient lighting" colour used wherever the factory UI is blue.
     * Loaded from settings when the activity is (re)created.
     */
    public static int BLUE = DEFAULT_AMBIENT;

    /** 30 ambient-lighting colours: {Serbian name, English name, ARGB}. */
    public static final Object[][] AMBIENT = {
            {"Plava (MIB)", "Blue (MIB)", 0xFF2D8CEB}, {"Ledeno plava", "Ice blue", 0xFF5EC8FF},
            {"Svetlo plava", "Light blue", 0xFF7FB2FF}, {"Kraljevsko plava", "Royal blue", 0xFF2F5BFF},
            {"Tamno plava", "Deep blue", 0xFF2442E0}, {"Indigo", "Indigo", 0xFF5B4BFF},
            {"Ljubičasta", "Violet", 0xFF8A5CFF}, {"Lavanda", "Lavender", 0xFFB39DFF},
            {"Purpurna", "Purple", 0xFFB33DFF}, {"Magenta", "Magenta", 0xFFE040FB},
            {"Ružičasta", "Pink", 0xFFFF4FA3}, {"Roze", "Rose", 0xFFFF86B6},
            {"Crvena", "Red", 0xFFE8202A}, {"Rubin", "Ruby", 0xFFD0105A},
            {"Koral", "Coral", 0xFFFF6F61}, {"Narandžasta", "Orange", 0xFFFF7A1A},
            {"Ćilibar", "Amber", 0xFFFFA000}, {"Zlatna", "Gold", 0xFFFFC72C},
            {"Žuta", "Yellow", 0xFFFFE600}, {"Limeta", "Lime", 0xFFB8F02A},
            {"Zelena", "Green", 0xFF2ECC40}, {"Smaragdna", "Emerald", 0xFF00C27A},
            {"Menta", "Mint", 0xFF3DFFC2}, {"Tirkizna", "Turquoise", 0xFF1DE9D0},
            {"Cijan", "Cyan", 0xFF00D8FF}, {"Petrolej", "Teal", 0xFF00A3AD},
            {"Bela", "White", 0xFFF0F0F0}, {"Srebrna", "Silver", 0xFFB8BEC8},
            {"Toplo bela", "Warm white", 0xFFFFE2B8}, {"Hladno bela", "Cool white", 0xFFD8ECFF},
    };

    public static void loadAmbient(Context c) {
        BLUE = shade(Prefs.integer(c, Prefs.AMBIENT, DEFAULT_AMBIENT),
                Prefs.integer(c, Prefs.AMBIENT_LEVEL, 100) / 100f, 0xFF);
    }

    /** The ambient colour darkened (f < 1) or lightened (f > 1), alpha optionally replaced. */
    public static int shade(int color, float f, int alpha) {
        int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((color & 0xFF) * f));
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
    public static final int WHITE = 0xFFF3F3F5;
    public static final int GREY = 0xFFB9BBC3;
    public static final int DIM = 0xFF7E808A;
    public static final int LINE = 0xFF8E909A;
    public static final int BG_TOP = 0xFF26262F;
    public static final int BG_BOTTOM = 0xFF131318;
    public static final int TILE_TOP = 0xFF4C4D59;
    public static final int TILE_BOTTOM = 0xFF2A2B34;
    public static final int BAR_TEXT = 0xFF1C1C22;
    public static final int BAR_VALUE = 0xFF4A4B55;

    private static Typeface sRegular, sSemibold, sBold;
    private static float sScale = -1;

    private Mib() {}

    // ---- Language ---------------------------------------------------------------------------

    /** Serbian (Latin) by default, like the factory unit; English as an option. */
    public static String t(Context c, String sr, String en) {
        return "en".equals(Prefs.str(c, Prefs.LANGUAGE, "sr")) ? en : sr;
    }

    // ---- Sizing -----------------------------------------------------------------------------

    /** Scale relative to the 600 px-tall reference layout. */
    /** Re-measure on the next layout (full screen toggled). */
    public static void resetScale() { sScale = -1; }

    public static float scale(Context c) {
        if (sScale < 0) {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            if (Prefs.bool(c, Prefs.IMMERSIVE, false)) {
                // Full screen: size the UI for the whole panel, not the area left by the bars.
                try {
                    DisplayMetrics real = new DisplayMetrics();
                    ((android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(real);
                    dm = real;
                } catch (Exception ignored) {
                }
            }
            float hDp = Math.min(dm.heightPixels, dm.widthPixels) / dm.density;
            sScale = Math.max(0.68f, Math.min(1.25f, hDp / 600f));
        }
        return sScale;
    }

    /** Reference-layout units (≈dp on a 1024×600 mdpi unit) to pixels. */
    public static int px(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density * scale(c));
    }

    public static void size(TextView t, float v) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, v * t.getResources().getDisplayMetrics().density * scale(t.getContext()));
    }

    // ---- Type -------------------------------------------------------------------------------

    public static Typeface regular(Context c) {
        if (sRegular == null) sRegular = load(c, "fonts/regular.ttf", Typeface.NORMAL);
        return sRegular;
    }

    public static Typeface semibold(Context c) {
        if (sSemibold == null) sSemibold = load(c, "fonts/semibold.ttf", Typeface.BOLD);
        return sSemibold;
    }

    public static Typeface bold(Context c) {
        if (sBold == null) sBold = load(c, "fonts/bold.ttf", Typeface.BOLD);
        return sBold;
    }

    private static Typeface load(Context c, String asset, int fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), asset);
        } catch (Exception e) {
            return Typeface.create("sans-serif", fallback);
        }
    }

    public static TextView text(Context c, String s, float size, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        size(t, size);
        return t;
    }

    public static ImageView icon(Context c, int res, int color) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageTintList(ColorStateList.valueOf(color));
        return v;
    }

    // ---- Drawables --------------------------------------------------------------------------

    private static GradientDrawable grad(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    /** Menu tile: black with a soft grey haze from the top so it lifts off the black screen. */
    public static Drawable tile(Context c) {
        GradientDrawable base = grad(0xFF1E1E22, 0xFF050506);
        base.setStroke(Math.max(1, px(c, 1)), 0xFF26262B);
        GradientDrawable haze = new GradientDrawable();
        haze.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        haze.setGradientCenter(0.35f, 0.0f);
        haze.setGradientRadius(px(c, 170));
        haze.setColors(new int[]{0x2EC8C8D2, 0x00000000});
        LayerDrawable normal = new LayerDrawable(new Drawable[]{base, haze});
        GradientDrawable pressed = grad(shade(BLUE, 1.0f, 0xFF), shade(BLUE, 0.55f, 0xFF));
        pressed.setStroke(Math.max(1, px(c, 1)), shade(BLUE, 1.25f, 0xFF));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** Soft-key bar: blue rule on top, dark body deepening to blue at the bottom edge. */
    public static Drawable bar(Context c) {
        GradientDrawable body = grad(0xFF0E0E10, 0xFF040405);
        GradientDrawable rule = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{shade(BLUE, 0.8f, 0xFF), BLUE, BLUE, shade(BLUE, 0.8f, 0xFF)});
        LayerDrawable l = new LayerDrawable(new Drawable[]{body, rule});
        l.setLayerInset(1, 0, 0, 0, 0);
        // The rule is drawn as a 2px band at the top.
        l.setLayerInset(0, 0, Math.max(2, px(c, 2)), 0, 0);
        return new TopBand(l, Math.max(2, px(c, 2)));
    }

    public static Drawable keyPressed() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, grad(shade(BLUE, 1f, 0xAA), shade(BLUE, 0.6f, 0x66)));
        s.addState(new int[]{}, new GradientDrawable());
        return s;
    }

    /** Popup panel: near-black with a crisp white frame. */
    public static GradientDrawable panel(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF000000);
        d.setStroke(Math.max(1, px(c, 1.6f)), 0xFFE8E8EC);
        return d;
    }

    /** Square map/utility button: black box, light frame. */
    public static Drawable box(Context c) {
        GradientDrawable n = new GradientDrawable();
        n.setColor(0xFF000000);
        n.setStroke(Math.max(1, px(c, 1.6f)), 0xFFD6D6DC);
        GradientDrawable p = new GradientDrawable();
        p.setColor(shade(BLUE, 0.7f, 0xFF));
        p.setStroke(Math.max(1, px(c, 1.6f)), 0xFFD6D6DC);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, p);
        s.addState(new int[]{}, n);
        return s;
    }

    /** Light settings bar (Podešavanje sistema list rows). */
    public static Drawable settingsBar(Context c) {
        float r = px(c, 3);
        GradientDrawable n = grad(0xFFE9E9EC, 0xFFC5C6CC);
        n.setCornerRadius(r);
        n.setStroke(Math.max(1, px(c, 1)), 0xFFF4F4F6);
        GradientDrawable p = grad(shade(BLUE, 1.1f, 0xFF), shade(BLUE, 0.7f, 0xFF));
        p.setCornerRadius(r);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, p);
        s.addState(new int[]{}, n);
        return s;
    }

    /** Square checkbox used on settings bars. */
    public static GradientDrawable checkbox(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFFFAFAFB);
        d.setStroke(Math.max(1, px(c, 2)), 0xFF3A3B44);
        return d;
    }

    public static void center(TextView t) {
        t.setGravity(Gravity.CENTER);
    }
}
