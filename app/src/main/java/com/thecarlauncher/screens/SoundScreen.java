package com.thecarlauncher.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.media.Tone;
import com.thecarlauncher.mib.Mib;

/** MIB "Duboki - Srednji - Visoki": three vertical tone sliders, -9…+9, with + / − keys. */
public class SoundScreen extends Screen {
    private final ToneSlider[] sliders = new ToneSlider[3];

    public SoundScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public boolean hasOwnBack() { return true; }

    @Override
    public String title() { return s("Duboki - Srednji - Visoki", "Bass - Mid - Treble"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);

        // Header: title, framed "Resetovanje", back key.
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Mib.px(act, 22), Mib.px(act, 8), Mib.px(act, 16), Mib.px(act, 8));
        head.addView(Mib.text(act, title(), 34, Mib.regular(act), Mib.WHITE),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView reset = Mib.text(act, s("Resetovanje", "Reset"), 30, Mib.regular(act), Mib.WHITE);
        reset.setGravity(Gravity.CENTER_VERTICAL);
        reset.setPadding(Mib.px(act, 16), 0, Mib.px(act, 16), 0);
        reset.setBackground(Mib.box(act));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tone.get(act).reset();
                refresh();
            }
        });
        head.addView(reset, new LinearLayout.LayoutParams(Mib.px(act, 290), Mib.px(act, 58)));
        FrameLayout back = new FrameLayout(act);
        back.setBackground(Mib.box(act));
        back.addView(Mib.icon(act, R.drawable.ui_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!onBack()) act.goBack();
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Mib.px(act, 66), Mib.px(act, 58));
        bl.leftMargin = Mib.px(act, 10);
        head.addView(back, bl);
        r.addView(head);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        r.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));

        // Light panel with the three slider groups.
        LinearLayout panel = new LinearLayout(act);
        panel.setPadding(Mib.px(act, 16), Mib.px(act, 12), Mib.px(act, 16), Mib.px(act, 12));
        // The factory sound panel's light grey (skin 2437), a little lighter towards the bottom.
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFC0C0C0, 0xFFD2D2D2}));
        String[] names = {s("Dubok", "Bass"), s("Srednji", "Mid"), s("Visine", "Treble")};
        for (int i = 0; i < 3; i++) panel.addView(group(i, names[i]), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        pl.leftMargin = pl.rightMargin = Mib.px(act, 16);
        pl.topMargin = Mib.px(act, 10);
        pl.bottomMargin = Mib.px(act, 12);
        r.addView(panel, pl);
        refresh();
        return r;
    }

    private View group(final int idx, String name) {
        LinearLayout g = new LinearLayout(act);
        g.setPadding(Mib.px(act, 6), 0, Mib.px(act, 6), 0);
        ToneSlider sl = new ToneSlider(act);
        sliders[idx] = sl;
        sl.setListener(new ToneSlider.Listener() {
            @Override
            public void onValue(int v) {
                setValue(idx, v);
            }
        });
        g.addView(sl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));

        LinearLayout side = new LinearLayout(act);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        side.setPadding(Mib.px(act, 10), Mib.px(act, 14), 0, Mib.px(act, 14));
        side.addView(step("+", idx, 1), new LinearLayout.LayoutParams(Mib.px(act, 104), Mib.px(act, 74)));
        TextView label = Mib.text(act, name, 32, Mib.regular(act), 0xFF1C1C22);
        label.setGravity(Gravity.CENTER);
        side.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        side.addView(step("–", idx, -1), new LinearLayout.LayoutParams(Mib.px(act, 104), Mib.px(act, 74)));
        g.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return g;
    }

    /** Light + / − key: the factory light key, in the ambient colour while pressed. */
    private View step(String sign, final int idx, final int delta) {
        TextView t = Mib.text(act, sign, 40, Mib.regular(act), 0xFF1C1C22);
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable n = Mib.skin(act, com.thecarlauncher.R.drawable.ui_skin_key_light, 8, 8);
        android.graphics.drawable.Drawable p = Mib.skin(act, com.thecarlauncher.R.drawable.ui_skin_key_light, 8, 8);
        p.setColorFilter(new android.graphics.LightingColorFilter(Mib.shade(Mib.BLUE, 1.3f, 0xFF), 0));
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_pressed}, p);
        sd.addState(new int[]{}, n);
        t.setBackground(sd);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setValue(idx, sliders[idx].value() + delta);
            }
        });
        return t;
    }

    private void setValue(int idx, int v) {
        Tone t = Tone.get(act);
        int[] cur = {t.bass(), t.mid(), t.treble()};
        cur[idx] = v;
        t.set(cur[0], cur[1], cur[2]);
        refresh();
    }

    private void refresh() {
        Tone t = Tone.get(act);
        sliders[0].setValue(t.bass());
        sliders[1].setValue(t.mid());
        sliders[2].setValue(t.treble());
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public boolean onBack() { return false; }

    // ---- Slider ------------------------------------------------------------------------------

    /**
     * The factory tone slider: the skin's slider picture (frame, tick scale with +9 / 0 / -9 and
     * the slot) with the factory knob on it, its centre stripe in the ambient colour.
     */
    static final class ToneSlider extends View {
        interface Listener { void onValue(int v); }

        // Positions in the factory slider picture (158×349): +9 tick at y 47, then 14 px a step;
        // the slot's centre at x 98.
        private static final float PIC_TOP = 47, PIC_STEP = 14, PIC_SLOT_X = 98;

        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final android.graphics.Bitmap pic, knob, stripe;
        private int value;
        private Listener listener;

        ToneSlider(Context c) {
            super(c);
            pic = Mib.skinBitmap(c, com.thecarlauncher.R.drawable.ui_skin_eq);
            knob = Mib.skinBitmap(c, com.thecarlauncher.R.drawable.ui_skin_eq_knob);
            stripe = Mib.skinBitmap(c, com.thecarlauncher.R.drawable.ui_skin_eq_knob_line);
        }

        void setListener(Listener l) { listener = l; }

        int value() { return value; }

        void setValue(int v) {
            value = Math.max(Tone.MIN, Math.min(Tone.MAX, v));
            invalidate();
        }

        /** The picture's scale: as tall as the view, but never wider than it. */
        private float scale() {
            return Math.min(getHeight() / (float) pic.getHeight(), getWidth() / (float) pic.getWidth());
        }

        private float left() { return (getWidth() - pic.getWidth() * scale()) / 2f; }

        private float picTop() { return (getHeight() - pic.getHeight() * scale()) / 2f; }

        private float top() { return yOf(Tone.MAX); }

        private float bottom() { return yOf(Tone.MIN); }

        private float yOf(int v) {
            return picTop() + (PIC_TOP + (Tone.MAX - v) * PIC_STEP) * scale();
        }

        @Override
        protected void onDraw(Canvas c) {
            float sc = scale(), l = left();
            r.set(l, picTop(), l + pic.getWidth() * sc, picTop() + pic.getHeight() * sc);
            c.drawBitmap(pic, null, r, p);
            float cx = l + PIC_SLOT_X * sc, ky = yOf(value);
            float kw = knob.getWidth() * sc, kh = knob.getHeight() * sc;
            r.set(cx - kw / 2f, ky - kh / 2f, cx + kw / 2f, ky + kh / 2f);
            c.drawBitmap(knob, null, r, p);
            line.setColorFilter(new android.graphics.PorterDuffColorFilter(Mib.BLUE, android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(stripe, null, r, line);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                float f = (e.getY() - top()) / (bottom() - top());
                int v = Math.round(Tone.MAX - f * (Tone.MAX - Tone.MIN));
                v = Math.max(Tone.MIN, Math.min(Tone.MAX, v));
                if (v != value && listener != null) listener.onValue(v);
            }
            return true;
        }
    }
}
