package com.thecarlauncher.mib;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The flag of a launcher language for languages the factory MIB skin has no flag for (its
 * flags are the ui_flag_* pictures), drawn as simple shapes in a 3:2 box with the same dark
 * frame. Coats of arms are reduced to a plain shield so they still read at icon size.
 */
public final class FlagDrawable extends Drawable {
    private final String lang;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FlagDrawable(String lang) { this.lang = lang; }

    /** True when a flag is drawn for this language code. */
    public static boolean has(String lang) {
        return lang != null && lang.length() == 2
                && " sr en de fr it ga ro nl hu bg lt et lv es pt pl tr cs sk sl hr el da sv fi mt ".contains(" " + lang + " ");
    }

    /** An image of the language's flag (unknown codes show a generic flag). */
    public static android.widget.ImageView view(android.content.Context c, String lang) {
        android.widget.ImageView f = new android.widget.ImageView(c);
        // The factory MIB flag where the skin has one (ui_flag_<code>), else a drawn one.
        int id = lang == null ? 0 : c.getResources().getIdentifier("ui_flag_" + lang, "drawable", c.getPackageName());
        if (id != 0) f.setImageResource(id);
        else if (has(lang)) f.setImageDrawable(new FlagDrawable(lang));
        else f.setImageResource(com.thecarlauncher.R.drawable.ui_ic_flag);
        f.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        return f;
    }

    @Override public int getIntrinsicWidth() { return 40; }
    @Override public int getIntrinsicHeight() { return 27; }

    private float l, t, w, h;

    private void rect(int color, float x0, float y0, float x1, float y1) {
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        Canvas c = canvas;
        c.drawRect(l + w * x0, t + h * y0, l + w * x1, t + h * y1, p);
    }

    private void vertical(int a, int b, int c) {
        rect(a, 0, 0, 1 / 3f, 1);
        rect(b, 1 / 3f, 0, 2 / 3f, 1);
        rect(c, 2 / 3f, 0, 1, 1);
    }

    private void horizontal(int a, int b, int c) {
        rect(a, 0, 0, 1, 1 / 3f);
        rect(b, 0, 1 / 3f, 1, 2 / 3f);
        rect(c, 0, 2 / 3f, 1, 1);
    }

    /** Nordic cross: upright a little left of centre. */
    private void nordic(int bg, int cross, float thick) {
        rect(bg, 0, 0, 1, 1);
        float tv = thick * h / w;
        rect(cross, 0.36f - tv / 2, 0, 0.36f + tv / 2, 1);
        rect(cross, 0, 0.5f - thick / 2, 1, 0.5f + thick / 2);
    }

    /** A small shield (rounded bottom) centred at (cx, cy) as fractions of the flag. */
    private void shield(float cx, float cy, float sw, int fill, int edge) {
        float x = l + w * cx, y = t + h * cy, half = w * sw / 2, sh = half * 2.4f;
        Path s = new Path();
        s.moveTo(x - half, y - sh / 2);
        s.lineTo(x + half, y - sh / 2);
        s.lineTo(x + half, y + sh * 0.1f);
        s.quadTo(x + half, y + sh / 2, x, y + sh / 2);
        s.quadTo(x - half, y + sh / 2, x - half, y + sh * 0.1f);
        s.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        canvas.drawPath(s, p);
        if (edge != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, w * 0.02f));
            p.setColor(edge);
            canvas.drawPath(s, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private Canvas canvas;

    @Override
    public void draw(Canvas c) {
        canvas = c;
        Rect b = getBounds();
        w = b.width();
        h = w * 2 / 3f;
        l = b.left;
        t = b.top + (b.height() - h) / 2f;
        c.save();
        c.clipRect(l, t, l + w, t + h);
        switch (lang) {
            case "sr":
                horizontal(0xFFC6363C, 0xFF0C4076, 0xFFFFFFFF);
                shield(0.34f, 0.5f, 0.2f, 0xFFC6363C, 0xFFEDB92E);
                rect(0xFFFFFFFF, 0.325f, 0.3f, 0.355f, 0.66f);
                rect(0xFFFFFFFF, 0.27f, 0.45f, 0.41f, 0.5f);
                break;
            case "de": horizontal(0xFF000000, 0xFFDD0000, 0xFFFFCE00); break;
            case "fr": vertical(0xFF0055A4, 0xFFFFFFFF, 0xFFEF4135); break;
            case "it": vertical(0xFF009246, 0xFFFFFFFF, 0xFFCE2B37); break;
            case "ga": vertical(0xFF169B62, 0xFFFFFFFF, 0xFFFF883E); break;
            case "ro": vertical(0xFF002B7F, 0xFFFCD116, 0xFFCE1126); break;
            case "nl": horizontal(0xFFAE1C28, 0xFFFFFFFF, 0xFF21468B); break;
            case "hu": horizontal(0xFFCE2939, 0xFFFFFFFF, 0xFF477050); break;
            case "bg": horizontal(0xFFFFFFFF, 0xFF00966E, 0xFFD62612); break;
            case "lt": horizontal(0xFFFDB913, 0xFF006A44, 0xFFC1272D); break;
            case "et": horizontal(0xFF0072CE, 0xFF000000, 0xFFFFFFFF); break;
            case "lv":
                rect(0xFF9E3039, 0, 0, 1, 1);
                rect(0xFFFFFFFF, 0, 0.4f, 1, 0.6f);
                break;
            case "es":
                rect(0xFFAA151B, 0, 0, 1, 1);
                rect(0xFFF1BF00, 0, 0.25f, 1, 0.75f);
                break;
            case "pt":
                rect(0xFF006600, 0, 0, 0.4f, 1);
                rect(0xFFFF0000, 0.4f, 0, 1, 1);
                p.setColor(0xFFFFE000);
                c.drawCircle(l + w * 0.4f, t + h * 0.5f, h * 0.2f, p);
                shield(0.4f, 0.5f, 0.11f, 0xFFFFFFFF, 0xFFFF0000);
                break;
            case "pl":
                rect(0xFFFFFFFF, 0, 0, 1, 0.5f);
                rect(0xFFDC143C, 0, 0.5f, 1, 1);
                break;
            case "tr": turkey(c); break;
            case "cs": {
                rect(0xFFFFFFFF, 0, 0, 1, 0.5f);
                rect(0xFFD7141A, 0, 0.5f, 1, 1);
                Path tri = new Path();
                tri.moveTo(l, t);
                tri.lineTo(l + w * 0.5f, t + h / 2);
                tri.lineTo(l, t + h);
                tri.close();
                p.setColor(0xFF11457E);
                c.drawPath(tri, p);
                break;
            }
            case "sk":
                horizontal(0xFFFFFFFF, 0xFF0B4EA2, 0xFFEE1C25);
                shield(0.34f, 0.5f, 0.22f, 0xFFEE1C25, 0xFFFFFFFF);
                rect(0xFFFFFFFF, 0.325f, 0.3f, 0.355f, 0.62f);
                rect(0xFFFFFFFF, 0.29f, 0.38f, 0.39f, 0.42f);
                rect(0xFFFFFFFF, 0.28f, 0.48f, 0.40f, 0.52f);
                break;
            case "sl":
                horizontal(0xFFFFFFFF, 0xFF005DA4, 0xFFED1C24);
                shield(0.3f, 0.36f, 0.16f, 0xFF005DA4, 0xFFED1C24);
                break;
            case "hr": {
                horizontal(0xFFFF0000, 0xFFFFFFFF, 0xFF171796);
                // Red-and-white chequered shield in the middle.
                float cw = 0.05f, ch = cw * w / h, sx = 0.5f - 2.5f * cw, sy = 0.5f - 2.5f * ch;
                for (int r = 0; r < 5; r++)
                    for (int k = 0; k < 5; k++)
                        rect((r + k) % 2 == 0 ? 0xFFFF0000 : 0xFFFFFFFF,
                                sx + k * cw, sy + r * ch, sx + (k + 1) * cw, sy + (r + 1) * ch);
                break;
            }
            case "el": {
                for (int i = 0; i < 9; i++) rect(i % 2 == 0 ? 0xFF0D5EAF : 0xFFFFFFFF, 0, i / 9f, 1, (i + 1) / 9f);
                float cw = 5 / 9f * h / w; // canton is 5 stripes square
                rect(0xFF0D5EAF, 0, 0, cw, 5 / 9f);
                rect(0xFFFFFFFF, cw * 0.4f, 0, cw * 0.6f, 5 / 9f);
                rect(0xFFFFFFFF, 0, 2 / 9f, cw, 3 / 9f);
                break;
            }
            case "da": nordic(0xFFC8102E, 0xFFFFFFFF, 0.14f); break;
            case "sv": nordic(0xFF006AA7, 0xFFFECC00, 0.2f); break;
            case "fi": nordic(0xFFFFFFFF, 0xFF002F6C, 0.27f); break;
            case "mt":
                rect(0xFFFFFFFF, 0, 0, 0.5f, 1);
                rect(0xFFCF142B, 0.5f, 0, 1, 1);
                rect(0xFF9A9A9A, 0.1f, 0.1f, 0.2f, 0.2f + 0.033f);
                rect(0xFF9A9A9A, 0.133f, 0.06f, 0.167f, 0.29f);
                rect(0xFF9A9A9A, 0.085f, 0.155f, 0.215f, 0.195f);
                break;
            default: unionJack(c); break;
        }
        c.restore();
        // Dark frame like the MIB flag pictures.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, w * 0.07f));
        p.setColor(0xFF161616);
        float hs = p.getStrokeWidth() / 2;
        c.drawRect(l + hs, t + hs, l + w - hs, t + h - hs, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void unionJack(Canvas c) {
        rect(0xFF012169, 0, 0, 1, 1);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setColor(0xFFFFFFFF);
        p.setStrokeWidth(h * 0.2f);
        c.drawLine(l, t, l + w, t + h, p);
        c.drawLine(l + w, t, l, t + h, p);
        p.setColor(0xFFC8102E);
        p.setStrokeWidth(h * 0.067f);
        c.drawLine(l, t, l + w, t + h, p);
        c.drawLine(l + w, t, l, t + h, p);
        p.setStyle(Paint.Style.FILL);
        float tv = 0.33f * h / w, tr = 0.2f * h / w;
        rect(0xFFFFFFFF, 0.5f - tv / 2, 0, 0.5f + tv / 2, 1);
        rect(0xFFFFFFFF, 0, 0.5f - 0.165f, 1, 0.5f + 0.165f);
        rect(0xFFC8102E, 0.5f - tr / 2, 0, 0.5f + tr / 2, 1);
        rect(0xFFC8102E, 0, 0.4f, 1, 0.6f);
    }

    private void turkey(Canvas c) {
        rect(0xFFE30A17, 0, 0, 1, 1);
        float cy = t + h / 2, r = h * 0.25f, cx = l + w * 0.375f;
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r, p);
        p.setColor(0xFFE30A17);
        c.drawCircle(cx + r * 0.25f, cy, r * 0.8f, p);
        p.setColor(0xFFFFFFFF);
        Path star = new Path();
        float sx = cx + r * 1.05f, sr = r * 0.5f;
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI * i / 5 - Math.PI;
            float rr = i % 2 == 0 ? sr : sr * 0.4f;
            float x = sx + (float) (Math.cos(ang) * rr), y = cy + (float) (Math.sin(ang) * rr);
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.close();
        c.drawPath(star, p);
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
