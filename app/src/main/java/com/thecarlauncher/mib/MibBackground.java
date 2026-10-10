package com.thecarlauncher.mib;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Plain black backdrop, as on the factory unit. */
public class MibBackground extends Drawable {
    private final Paint base = new Paint();

    @Override
    protected void onBoundsChange(Rect r) {
        if (r.height() == 0) return;
        base.setShader(null);
        base.setColor(0xFF000000);
    }

    @Override
    public void draw(Canvas c) {
        c.drawRect(getBounds(), base);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
