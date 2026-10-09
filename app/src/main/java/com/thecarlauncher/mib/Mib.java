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
    public static final int BLUE = 0xFF2D8CEB;
    public static final int BLUE_DEEP = 0xFF1A5DAA;
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
    public static float scale(Context c) {
        if (sScale < 0) {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
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

    /** Menu tile: soft grey-violet gradient with a faint top highlight; turns blue when pressed. */
    public static Drawable tile(Context c) {
        GradientDrawable base = grad(TILE_TOP, TILE_BOTTOM);
        base.setStroke(Math.max(1, px(c, 1)), 0xFF3C3D48);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x22FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        LayerDrawable normal = new LayerDrawable(new Drawable[]{base, sheen});
        GradientDrawable pressed = grad(0xFF3D8FE0, 0xFF1D4E8C);
        pressed.setStroke(Math.max(1, px(c, 1)), 0xFF5AA6F0);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** Soft-key bar: blue rule on top, dark body deepening to blue at the bottom edge. */
    public static Drawable bar(Context c) {
        GradientDrawable body = grad(0xFF000000, 0xFF0C2140);
        GradientDrawable rule = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF1E6FC4, BLUE, BLUE, 0xFF1E6FC4});
        LayerDrawable l = new LayerDrawable(new Drawable[]{body, rule});
        l.setLayerInset(1, 0, 0, 0, 0);
        // The rule is drawn as a 2px band at the top.
        l.setLayerInset(0, 0, Math.max(2, px(c, 2)), 0, 0);
        return new TopBand(l, Math.max(2, px(c, 2)));
    }

    public static Drawable keyPressed() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, grad(0xAA2D7FD6, 0x661A4A86));
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
        p.setColor(0xFF1F5FA6);
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
        GradientDrawable p = grad(0xFF4A9DF0, 0xFF1F67B8);
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
