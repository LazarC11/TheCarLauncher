package com.thecarlauncher.mib;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/** Draws layer 0 as the body and layer 1 squeezed into a thin band along the top edge. */
final class TopBand extends Drawable {
    private final LayerDrawable layers;
    private final int band;

    TopBand(LayerDrawable layers, int band) {
        this.layers = layers;
        this.band = band;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        layers.getDrawable(0).setBounds(b.left, b.top + band, b.right, b.bottom);
        layers.getDrawable(1).setBounds(b.left, b.top, b.right, b.top + band);
    }

    @Override
    public void draw(Canvas c) {
        layers.getDrawable(0).draw(c);
        layers.getDrawable(1).draw(c);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
