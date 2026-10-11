package com.thecarlauncher.screens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.util.Prefs;

/**
 * MIB "Ambijentalno osvetljenje": the Corsa C interior picture, a 30-colour
 * slider with < > steps, and a brightness key. The colour becomes the accent of the whole UI.
 */
public class AmbientScreen extends Screen {
    private static final int[] LEVELS = {100, 80, 60};

    private InteriorView interior;
    private ColorSlider slider;
    private TextView value;
    private int index;

    public AmbientScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return s("Ambijentalno osvetljenje", "Ambient lighting"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    private boolean en() { return "en".equals(Prefs.str(act, Prefs.LANGUAGE, "sr")); }

    private static int color(int i) { return (Integer) Mib.AMBIENT[i][2]; }

    private static int indexOf(int color) {
        for (int i = 0; i < Mib.AMBIENT.length; i++) if (color(i) == color) return i;
        return 0;
    }

    @Override
    protected View create(ViewGroup parent) {
        index = indexOf(Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT));
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);

        // Header: title, framed value box, back key.
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Mib.px(act, 22), Mib.px(act, 8), Mib.px(act, 16), Mib.px(act, 8));
        TextView t = Mib.text(act, title(), 34, Mib.regular(act), Mib.WHITE);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        value = Mib.text(act, "", 30, Mib.regular(act), Mib.WHITE);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(Mib.px(act, 16), 0, Mib.px(act, 16), 0);
        value.setBackground(Mib.box(act));
        head.addView(value, new LinearLayout.LayoutParams(Mib.px(act, 290), Mib.px(act, 58)));
        FrameLayout back = new FrameLayout(act);
        back.setBackground(Mib.box(act));
        back.addView(Mib.icon(act, R.drawable.ui_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack();
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Mib.px(act, 66), Mib.px(act, 58));
        bl.leftMargin = Mib.px(act, 10);
        head.addView(back, bl);
        r.addView(head);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        r.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));

        // Interior with the light strips, slider row overlaid along the bottom.
        FrameLayout stage = new FrameLayout(act);
        interior = new InteriorView(act);
        stage.addView(interior, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(act, 22), 0, Mib.px(act, 16), 0);
        int box = Mib.px(act, 62);
        row.addView(key(R.drawable.ui_key_prev, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick((index + Mib.AMBIENT.length - 1) % Mib.AMBIENT.length, true);
            }
        }), new LinearLayout.LayoutParams(box, box));
        slider = new ColorSlider(act);
        slider.setListener(new ColorSlider.Listener() {
            @Override
            public void onPick(int i, boolean done) {
                pick(i, done);
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Mib.px(act, 62), 1f);
        sl.leftMargin = sl.rightMargin = Mib.px(act, 10);
        row.addView(slider, sl);
        row.addView(key(R.drawable.ui_key_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick((index + 1) % Mib.AMBIENT.length, true);
            }
        }), new LinearLayout.LayoutParams(box, box));
        FrameLayout sun = new FrameLayout(act);
        sun.setBackground(Mib.box(act));
        ImageView sv = new ImageView(act);
        sv.setImageDrawable(new Sun());
        sun.addView(sv, new FrameLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40), Gravity.CENTER));
        sun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleLevel();
            }
        });
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(box, box);
        ul.leftMargin = Mib.px(act, 18);
        row.addView(sun, ul);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Mib.px(act, 90), Gravity.BOTTOM);
        rl.bottomMargin = Mib.px(act, 10);
        stage.addView(row, rl);
        r.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        show(index);
        return r;
    }

    private View key(int icon, View.OnClickListener l) {
        FrameLayout b = new FrameLayout(act);
        b.setBackground(Mib.box(act));
        b.addView(Mib.icon(act, icon, Mib.WHITE), new FrameLayout.LayoutParams(Mib.px(act, 46), Mib.px(act, 30), Gravity.CENTER));
        b.setOnClickListener(l);
        return b;
    }

    private int level() {
        return Prefs.integer(act, Prefs.AMBIENT_LEVEL, 100);
    }

    /** Preview while dragging; store and recolour the whole UI once the finger lifts. */
    private void pick(int i, boolean done) {
        show(i);
        if (!done) return;
        if (color(i) != Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT)) {
            Prefs.put(act, Prefs.AMBIENT, color(i));
            act.restartUi();
        }
    }

    private void cycleLevel() {
        int l = level(), next = LEVELS[0];
        for (int k = 0; k < LEVELS.length; k++) if (LEVELS[k] == l) next = LEVELS[(k + 1) % LEVELS.length];
        Prefs.put(act, Prefs.AMBIENT_LEVEL, next);
        act.restartUi();
    }

    private void show(int i) {
        index = i;
        slider.setSelected(i);
        int lvl = level();
        String name = (String) Mib.AMBIENT[i][en() ? 1 : 0];
        value.setText(lvl < 100 ? name + "  " + lvl + "%" : name);
    }

    @Override
    public void onShow() {
        show(indexOf(Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT)));
    }

    @Override
    public boolean onBack() {
        act.showSettings(SettingsScreen.PAGE_ROOT);
        return true;
    }

    // ---- Interior picture ------------------------------------------------------------------------

    /** The Corsa C cabin, centre-cropped to fill the page. */
    static final class InteriorView extends View {
        private final Bitmap photo;
        private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        InteriorView(Context c) {
            super(c);
            photo = BitmapFactory.decodeResource(c.getResources(), R.drawable.car_interior);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0 || photo == null) return;
            float scale = Math.max(w / (float) photo.getWidth(), h / (float) photo.getHeight());
            float pw = photo.getWidth() * scale, ph = photo.getHeight() * scale;
            float ox = (w - pw) / 2f, oy = (h - ph) / 2f;
            dst.set(ox, oy, ox + pw, oy + ph);
            src.set(0, 0, photo.getWidth(), photo.getHeight());
            c.drawBitmap(photo, src, dst, img);
        }
    }

    // ---- 30-colour slider ---------------------------------------------------------------------

    /** Row of colour cells in a dark frame; drag or tap to choose, the choice gets a white frame. */
    static final class ColorSlider extends View {
        interface Listener { void onPick(int index, boolean done); }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int selected;
        private Listener listener;

        ColorSlider(Context c) { super(c); }

        void setListener(Listener l) { listener = l; }

        void setSelected(int i) {
            selected = i;
            invalidate();
        }

        private float pad() { return Mib.px(getContext(), 8); }

        private int indexAt(float x) {
            int n = Mib.AMBIENT.length;
            float cw = (getWidth() - 2 * pad()) / n;
            return Math.max(0, Math.min(n - 1, (int) ((x - pad()) / cw)));
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = Mib.AMBIENT.length;
            float w = getWidth(), h = getHeight(), pad = pad();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xE6000000);
            r.set(0, 0, w, h);
            c.drawRect(r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, Mib.px(getContext(), 1.6f)));
            p.setColor(0xFFD6D6DC);
            c.drawRect(r, p);
            float cw = (w - 2 * pad) / n, top = pad * 1.4f, bottom = h - pad * 1.4f, gap = Math.max(1, cw * 0.12f);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                p.setColor((Integer) Mib.AMBIENT[i][2]);
                c.drawRect(pad + i * cw + gap / 2, top, pad + (i + 1) * cw - gap / 2, bottom, p);
            }
            // Selected cell: taller, framed in white.
            float x0 = pad + selected * cw - gap, x1 = pad + (selected + 1) * cw + gap;
            p.setColor((Integer) Mib.AMBIENT[selected][2]);
            c.drawRect(x0, pad * 0.5f, x1, h - pad * 0.5f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2, Mib.px(getContext(), 2.5f)));
            p.setColor(0xFFFFFFFF);
            c.drawRect(x0, pad * 0.5f, x1, h - pad * 0.5f, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int i = indexAt(e.getX());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                case MotionEvent.ACTION_MOVE:
                    if (i != selected && listener != null) listener.onPick(i, false);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (listener != null) listener.onPick(i, true);
                    return true;
                default:
                    return true;
            }
        }
    }

    /** Brightness glyph for the key next to the slider. */
    private static final class Sun extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY(), s = b.width();
            p.setColor(Mib.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.07f);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawCircle(cx, cy, s * 0.2f, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(cx + (float) Math.cos(a) * s * 0.32f, cy + (float) Math.sin(a) * s * 0.32f,
                        cx + (float) Math.cos(a) * s * 0.45f, cy + (float) Math.sin(a) * s * 0.45f, p);
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
