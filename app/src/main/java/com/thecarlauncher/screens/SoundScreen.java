package com.thecarlauncher.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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

        // Light panel with the three slider groups.
        LinearLayout panel = new LinearLayout(act);
        panel.setPadding(Mib.px(act, 16), Mib.px(act, 12), Mib.px(act, 16), Mib.px(act, 12));
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFE4E4E7, 0xFFCFCFD3}));
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

    /** Light framed + / − key as on the factory sound page. */
    private View step(String sign, final int idx, final int delta) {
        TextView t = Mib.text(act, sign, 40, Mib.regular(act), 0xFF1C1C22);
        t.setGravity(Gravity.CENTER);
        GradientDrawable n = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFF4F4F6, 0xFFDCDCE0});
        n.setCornerRadius(Mib.px(act, 4));
        n.setStroke(Math.max(1, Mib.px(act, 1.5f)), 0xFF8E8F96);
        GradientDrawable p = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Mib.shade(Mib.BLUE, 1.1f, 0xFF), Mib.shade(Mib.BLUE, 0.7f, 0xFF)});
        p.setCornerRadius(Mib.px(act, 4));
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
    public boolean onBack() {
        act.showSettings(SettingsScreen.PAGE_MEDIA);
        return true;
    }

    // ---- Slider ------------------------------------------------------------------------------

    /** Black slider frame: tick scales either side, a dark groove and the light MIB fader knob. */
    static final class ToneSlider extends View {
        interface Listener { void onValue(int v); }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int value;
        private Listener listener;

        ToneSlider(Context c) { super(c); }

        void setListener(Listener l) { listener = l; }

        int value() { return value; }

        void setValue(int v) {
            value = Math.max(Tone.MIN, Math.min(Tone.MAX, v));
            invalidate();
        }

        private float px(float v) { return Mib.px(getContext(), v); }

        private float top() { return px(34); }

        private float bottom() { return getHeight() - px(34); }

        private float yOf(int v) {
            return top() + (Tone.MAX - v) / (float) (Tone.MAX - Tone.MIN) * (bottom() - top());
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            // Frame
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF08080A);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, px(4), px(4), p);

            float trackL = w * 0.40f, trackR = w * 0.78f, cx = (trackL + trackR) / 2f;
            // Groove body
            p.setShader(new LinearGradient(trackL, 0, trackR, 0, new int[]{0xFF2A2B33, 0xFF3C3D46, 0xFF24252C}, null,
                    Shader.TileMode.CLAMP));
            r.set(trackL, px(10), trackR, h - px(10));
            c.drawRoundRect(r, px(3), px(3), p);
            p.setShader(null);
            p.setColor(0xFF0B0B0D);
            float slot = Math.max(3, w * 0.05f);
            r.set(cx - slot / 2, px(26), cx + slot / 2, h - px(26));
            c.drawRoundRect(r, slot / 2, slot / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, px(1.2f)));
            p.setColor(0xFF6A6B74);
            c.drawRoundRect(r, slot / 2, slot / 2, p);

            // Tick scales: long on the left, short on the right; ±9 white, 0 in the ambient colour.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            float lx0 = w * 0.10f, lx1 = w * 0.34f, rx0 = w * 0.84f, rx1 = w * 0.92f;
            for (int v = Tone.MAX; v >= Tone.MIN; v--) {
                float y = yOf(v);
                boolean end = v == Tone.MAX || v == Tone.MIN, zero = v == 0;
                p.setStrokeWidth(end || zero ? px(3) : px(1.6f));
                p.setColor(zero ? Mib.BLUE : end ? 0xFFF2F2F4 : 0xFFB4B5BC);
                c.drawLine(zero ? w * 0.18f : lx0, y, lx1, y, p);
                c.drawLine(rx0, y, rx1, y, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(px(24));
            p.setTypeface(Mib.regular(getContext()));
            p.setColor(0xFFF2F2F4);
            c.drawText("+9", w * 0.10f, yOf(Tone.MAX) - px(8), p);
            c.drawText("-9", w * 0.10f, yOf(Tone.MIN) + px(26), p);
            p.setColor(0xFFF2F2F4);
            c.drawText("0", w * 0.04f, yOf(0) + px(9), p);

            // Knob
            float ky = yOf(value), kw = w * 0.30f, kh = px(48);
            r.set(cx - kw, ky - kh, cx + kw, ky + kh);
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, new int[]{0xFFF7F7F8, 0xFFD9DADD, 0xFFF0F0F2, 0xFFCACBCF},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, px(4), px(4), p);
            p.setShader(null);
            p.setColor(0xFF9A9BA2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, px(1)));
            c.drawRoundRect(r, px(4), px(4), p);
            // Grip dots, upper and lower rows
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFA8A9B0);
            float dot = Math.max(2, px(3.4f)), gap = dot * 1.9f;
            int n = (int) ((kw * 2 - px(16)) / gap);
            float start = cx - (n - 1) * gap / 2f;
            for (int row = 0; row < 2; row++) {
                float yy = row == 0 ? ky - kh * 0.62f : ky + kh * 0.55f;
                for (int k = 0; k < n; k++) c.drawRect(start + k * gap - dot / 2, yy - dot / 2, start + k * gap + dot / 2, yy + dot / 2, p);
            }
            // Ambient line through the middle of the knob
            p.setColor(Mib.BLUE);
            c.drawRect(r.left + px(2), ky - px(2), r.right - px(2), ky + px(2), p);
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
