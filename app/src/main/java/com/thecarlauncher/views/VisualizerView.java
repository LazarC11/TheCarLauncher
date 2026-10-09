package com.thecarlauncher.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.thecarlauncher.util.Theme;

import java.util.Random;

/** Decorative spectrum bars that dance while music is playing and settle when paused. */
public class VisualizerView extends View {
    private static final int FRAME_MS = 50;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final RectF bar = new RectF();
    private float[] level = new float[0];
    private float[] target = new float[0];
    private boolean playing;
    private long lastFrame;
    private int accent = Theme.ACCENTS[0];

    public VisualizerView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setAccent(int accent) {
        this.accent = accent;
        invalidate();
    }

    public void setPlaying(boolean p) {
        if (playing == p) return;
        playing = p;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float d = getResources().getDisplayMetrics().density;
        int n = Math.max(3, (int) (w / (6 * d)));
        level = new float[n];
        target = new float[n];
        for (int i = 0; i < n; i++) level[i] = target[i] = 0.12f;
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = level.length;
        if (n == 0) return;
        long now = System.currentTimeMillis();
        boolean step = now - lastFrame >= FRAME_MS;
        if (step) lastFrame = now;
        boolean moving = false;
        float slot = getWidth() / (float) n;
        float bw = slot * 0.55f;
        float h = getHeight();
        for (int i = 0; i < n; i++) {
            if (step) {
                if (playing && random.nextFloat() < 0.35f) {
                    // Lower bands (left) get more energy, like a real spectrum.
                    float bias = 1f - (i / (float) n) * 0.45f;
                    target[i] = 0.15f + random.nextFloat() * 0.85f * bias;
                } else if (!playing) {
                    target[i] = 0.12f;
                }
                level[i] += (target[i] - level[i]) * 0.35f;
            }
            if (Math.abs(target[i] - level[i]) > 0.01f) moving = true;
            float bh = Math.max(h * 0.12f, h * level[i]);
            float x = i * slot + (slot - bw) / 2f;
            bar.set(x, h - bh, x + bw, h);
            paint.setColor(Theme.alpha(accent, playing ? 0x50 + (int) (0xAF * level[i]) : 0x40));
            c.drawRoundRect(bar, bw / 2f, bw / 2f, paint);
        }
        if (playing || moving) postInvalidateOnAnimation();
    }
}
