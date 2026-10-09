package com.thecarlauncher.mib;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Charcoal-violet screen backdrop with a soft lift towards the upper centre. */
public class MibBackground extends Drawable {
    private final Paint base = new Paint();
    private final Paint lift = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    protected void onBoundsChange(Rect r) {
        if (r.height() == 0) return;
        base.setShader(new LinearGradient(0, 0, 0, r.height(), Mib.BG_TOP, Mib.BG_BOTTOM, Shader.TileMode.CLAMP));
        lift.setShader(new RadialGradient(r.width() * 0.45f, r.height() * 0.25f, r.width() * 0.6f,
                new int[]{0x14B4B0D8, 0x00000000}, null, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        c.drawRect(getBounds(), base);
        c.drawRect(getBounds(), lift);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
