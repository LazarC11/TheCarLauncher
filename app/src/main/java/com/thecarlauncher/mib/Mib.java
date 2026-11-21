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

    /** Text in the launcher's language (see {@link com.thecarlauncher.util.Lang}) from a Serbian / English pair. */
    public static String t(Context c, String sr, String en) {
        return com.thecarlauncher.util.Lang.text(c, sr, en);
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
        v.setImageTintList(pressTint(color));
        return v;
    }

    /**
     * Factory key feedback is a glow on the key's background (see the pressed states of the skin
     * pictures), not a colour change: icons and captions keep their colour while pressed.
     */
    public static ColorStateList pressTint(int color) {
        return ColorStateList.valueOf(color);
    }

    /** Text keys (framed boxes without an icon): the caption turns the ambient colour. */
    public static void pressText(TextView t) {
        t.setTextColor(pressTint(t.getCurrentTextColor()));
    }

    /** Framed key background (marker class so captions inside can get the press colour). */
    public static final class KeyBox extends StateListDrawable {}

    /** Gives every caption inside framed keys the press colour (call after building a page). */
    public static void keyFeedback(android.view.View v) {
        if (v.getBackground() instanceof KeyBox) applyText(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) keyFeedback(g.getChildAt(i));
        }
    }

    private static void applyText(android.view.View v) {
        // Re-applying is harmless: the colour shown when not pressed stays the same.
        if (v instanceof TextView && !v.isPressed()) pressText((TextView) v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyText(g.getChildAt(i));
        }
    }


    // ---- Drawables --------------------------------------------------------------------------

    private static GradientDrawable grad(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    // ---- Factory skin pictures --------------------------------------------------------------

    private static final java.util.Map<Integer, android.graphics.Bitmap> SKIN = new java.util.HashMap<>();

    /**
     * A picture from the factory MIB skin (ui_skin_*) stretched like a nine-patch: the outer
     * {@code capX} / {@code capY} skin pixels (corners, frame, shading) stay as drawn, only the
     * middle stretches. One skin pixel is one reference-layout unit, like {@link #px}.
     */
    public static Drawable skin(Context c, int res, int capX, int capY) {
        android.graphics.Bitmap b = skinBitmap(c, res);
        b.setDensity(Math.max(1, Math.round(DisplayMetrics.DENSITY_DEFAULT / scale(c))));
        return new android.graphics.drawable.NinePatchDrawable(c.getResources(), b,
                nineChunk(b.getWidth(), b.getHeight(), capX, capY), new android.graphics.Rect(), null);
    }

    /** A factory skin picture at its own pixel size (cached), for views that draw it themselves. */
    public static android.graphics.Bitmap skinBitmap(Context c, int res) {
        android.graphics.Bitmap b = SKIN.get(res);
        if (b == null) {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inScaled = false;
            b = android.graphics.BitmapFactory.decodeResource(c.getResources(), res, o);
            SKIN.put(res, b);
        }
        return b;
    }

    /** Page dot under pagers: the factory white dot for the current page, grey for the others. */
    public static void pageDot(android.view.View d, boolean current) {
        d.setBackgroundResource(current ? com.thecarlauncher.R.drawable.ui_skin_dot_on : com.thecarlauncher.R.drawable.ui_skin_dot_off);
        d.setScaleX(1f);
        d.setScaleY(1f);
    }

    /** Serialized nine-patch chunk: one stretchable column and row between the caps. */
    private static byte[] nineChunk(int w, int h, int cx, int cy) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(32 + 16 + 9 * 4).order(java.nio.ByteOrder.nativeOrder());
        buf.put((byte) 1).put((byte) 2).put((byte) 2).put((byte) 9);
        buf.putInt(0).putInt(0);                         // div offsets (filled in by the framework)
        buf.putInt(0).putInt(0).putInt(0).putInt(0);     // padding
        buf.putInt(0);                                   // colour offset
        buf.putInt(cx).putInt(w - cx).putInt(cy).putInt(h - cy);
        for (int i = 0; i < 9; i++) buf.putInt(1);       // Res_png_9patch::NO_COLOR
        return buf.array();
    }

    /** Menu tile: the factory tile (near black, a soft sheen from the top). Pressing colours the icon (MibTile). */
    public static Drawable tile(Context c) {
        StateListDrawable s = new StateListDrawable();
        // Pressed: the factory tile highlight laid over the tile at partial strength (a glow).
        Drawable lit = skin(c, com.thecarlauncher.R.drawable.ui_skin_tile_pressed, 12, 12);
        lit.setAlpha(0x48);
        s.addState(new int[]{android.R.attr.state_pressed}, new LayerDrawable(new Drawable[]{
                skin(c, com.thecarlauncher.R.drawable.ui_skin_tile, 12, 12), lit}));
        s.addState(new int[]{}, skin(c, com.thecarlauncher.R.drawable.ui_skin_tile, 12, 12));
        return s;
    }

    /** Soft-key bar: blue (ambient) rule on top, plain black body as in the factory skin. */
    public static Drawable bar(Context c) {
        GradientDrawable body = grad(0xFF000000, 0xFF000000); // factory bar body: plain black
        GradientDrawable rule = grad(BLUE, BLUE);
        LayerDrawable l = new LayerDrawable(new Drawable[]{body, rule});
        l.setLayerInset(1, 0, 0, 0, 0);
        // The rule is drawn as a 2px band at the top.
        l.setLayerInset(0, 0, Math.max(2, px(c, 2)), 0, 0);
        return new TopBand(l, Math.max(2, px(c, 2)));
    }

    /** List rows and popup cells: the factory pressed highlight (a light line on top fading down). */
    public static Drawable keyPressed(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, skin(c, com.thecarlauncher.R.drawable.ui_skin_key_pressed, 2, 1));
        s.addState(new int[]{}, new GradientDrawable());
        return s;
    }

    /** Keys (soft keys, icon buttons): no background change, the icon colours instead. */
    public static Drawable keyPlain() {
        return new GradientDrawable();
    }

    /** The factory white frame (rounded) over black, for popups and framed keys. */
    private static Drawable framed(Context c) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xFF000000);
        fill.setCornerRadius(px(c, 4));
        LayerDrawable l = new LayerDrawable(new Drawable[]{fill,
                skin(c, com.thecarlauncher.R.drawable.ui_skin_frame, 10, 10)});
        l.setLayerInset(0, px(c, 1), px(c, 1), px(c, 1), px(c, 1));
        return l;
    }

    /** Popup panel: black with the factory white frame. */
    public static Drawable panel(Context c) {
        return framed(c);
    }

    /** Framed key (map buttons, Reset, page arrows …): black with the factory white frame. */
    public static Drawable box(Context c) {
        KeyBox s = new KeyBox();
        // Pressed: the factory pressed key (grey inside the frame, soft shadow around it).
        s.addState(new int[]{android.R.attr.state_pressed},
                skin(c, com.thecarlauncher.R.drawable.ui_skin_frame_pressed, 16, 16));
        s.addState(new int[]{}, framed(c));
        return s;
    }

    /**
     * Light settings bar (system settings list rows): the factory row, shaded inward (shadow
     * along the top, lighter towards the bottom). Pressed, it lights up with the key glow.
     */
    public static Drawable settingsBar(Context c) {
        StateListDrawable s = new StateListDrawable();
        // Pressed: the row lit by the factory key glow.
        s.addState(new int[]{android.R.attr.state_pressed}, new LayerDrawable(new Drawable[]{
                skin(c, com.thecarlauncher.R.drawable.ui_skin_row, 10, 12),
                skin(c, com.thecarlauncher.R.drawable.ui_skin_key_pressed, 2, 1)}));
        s.addState(new int[]{}, skin(c, com.thecarlauncher.R.drawable.ui_skin_row, 10, 12));
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
