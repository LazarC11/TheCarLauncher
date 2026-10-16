package com.thecarlauncher.mib;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Soft light pool under the cover art in the ambient colour; fades in while a song is playing. */
public class CoverGlow extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private RadialGradient shader;
    private boolean on;
    private ValueAnimator fade;

    public CoverGlow(Context c) {
        super(c);
        setAlpha(0f);
    }

    public void setOn(boolean v) {
        if (v == on) return;
        on = v;
        if (fade != null) fade.cancel();
        fade = ValueAnimator.ofFloat(getAlpha(), v ? 1f : 0f);
        fade.setDuration(600);
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) { setAlpha((Float) a.getAnimatedValue()); }
        });
        fade.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // A circle gradient stretched into a flat ellipse.
        int rgb = Mib.BLUE & 0x00FFFFFF;
        shader = new RadialGradient(0, 0, 1f, new int[]{0x99000000 | rgb, 0x40000000 | rgb, 0x12000000 | rgb, 0},
                new float[]{0f, 0.38f, 0.72f, 1f}, Shader.TileMode.CLAMP);
        m.reset();
        m.setScale(w / 2f, h / 2f);
        m.postTranslate(w / 2f, h / 2f);
        shader.setLocalMatrix(m);
        paint.setShader(shader);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (shader != null) c.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
