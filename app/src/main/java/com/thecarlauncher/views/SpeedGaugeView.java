package com.thecarlauncher.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.thecarlauncher.util.Theme;

/** 270-degree speed arc with tick marks, in the spirit of a digital cockpit. */
public class SpeedGaugeView extends View {
    private static final float START = 135f, SWEEP = 270f;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float shown, max = 200f;
    private String unitLabel = "km/h", subLabel = "";
    private int accent = Theme.ACCENTS[0];
    private ValueAnimator anim;

    public SpeedGaugeView(Context c, AttributeSet a) {
        super(c, a);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0xFF1C2530);
        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(0xFF3A4654);
        value.setColor(Theme.TEXT_PRIMARY);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unit.setColor(Theme.TEXT_SECONDARY);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        unit.setLetterSpacing(0.12f);
    }

    public void setAccent(int accent) {
        this.accent = accent;
        updateShader();
        invalidate();
    }

    public void setUnit(String unit, float max) {
        this.unitLabel = unit;
        this.max = max;
        invalidate();
    }

    public void setSubLabel(String s) {
        if (s.equals(subLabel)) return;
        subLabel = s;
        invalidate();
    }

    public void setSpeed(float speed) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(shown, speed);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                shown = (Float) va.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float size = Math.min(w, h * 1.12f);
        float stroke = Math.max(4f, size * 0.055f);
        track.setStrokeWidth(stroke);
        progress.setStrokeWidth(stroke);
        tick.setStrokeWidth(Math.max(1f, stroke * 0.22f));
        float r = size / 2f - stroke;
        float cx = w / 2f, cy = r + stroke;
        arc.set(cx - r, cy - r, cx + r, cy + r);
        value.setTextSize(r * 0.62f);
        unit.setTextSize(Math.max(9f * getResources().getDisplayMetrics().density * 0.9f, r * 0.17f));
        updateShader();
    }

    private void updateShader() {
        if (arc.width() <= 0) return;
        SweepGradient g = new SweepGradient(arc.centerX(), arc.centerY(),
                new int[]{Theme.alpha(accent, 0x55), accent, accent},
                new float[]{0f, 0.6f, 1f});
        Matrix m = new Matrix();
        m.setRotate(START - 2f, arc.centerX(), arc.centerY());
        g.setLocalMatrix(m);
        progress.setShader(g);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (arc.width() <= 0) return;
        c.drawArc(arc, START, SWEEP, false, track);
        float frac = Math.max(0f, Math.min(1f, shown / max));
        if (frac > 0.002f) c.drawArc(arc, START, SWEEP * frac, false, progress);

        float cx = arc.centerX(), cy = arc.centerY();
        float r = arc.width() / 2f;
        float inner = r - track.getStrokeWidth() * 1.4f;
        float outer = inner - track.getStrokeWidth() * 0.7f;
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(START + SWEEP * i / steps);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            tick.setColor(i / (float) steps <= frac ? Theme.alpha(accent, 0xCC) : 0xFF3A4654);
            c.drawLine(cx + cos * inner, cy + sin * inner, cx + cos * outer, cy + sin * outer, tick);
        }

        String v = String.valueOf(Math.round(shown));
        c.drawText(v, cx, cy + value.getTextSize() * 0.32f, value);
        String u = unitLabel.toUpperCase();
        if (subLabel.length() > 0) u += "  ·  " + subLabel;
        c.drawText(u, cx, cy + value.getTextSize() * 0.32f + unit.getTextSize() * 1.6f, unit);
    }
}
