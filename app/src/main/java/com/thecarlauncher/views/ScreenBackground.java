package com.thecarlauncher.views;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Theme;

/** Full-screen backdrop: deep night gradient with a soft accent aurora. */
public class ScreenBackground extends Drawable {
    private final String style;
    private final int accent;
    private final Paint base = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pattern = new Paint();

    public ScreenBackground(String style, int accent) {
        this.style = style;
        this.accent = accent;
        if (Prefs.WALL_CARBON.equals(style)) {
            Bitmap b = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 6; x++) {
                    boolean on = ((x + y) % 6) < 3 ^ (y >= 3);
                    b.setPixel(x, y, on ? 0x0DFFFFFF : 0x00000000);
                }
            }
            pattern.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }
    }

    @Override
    protected void onBoundsChange(Rect r) {
        int w = r.width(), h = r.height();
        if (w == 0 || h == 0) return;
        if (Prefs.WALL_BLACK.equals(style)) {
            base.setShader(null);
            base.setColor(0xFF000000);
        } else {
            base.setShader(new LinearGradient(0, 0, 0, h, 0xFF0C131C, 0xFF020306, Shader.TileMode.CLAMP));
        }
        float rad = Math.max(w, h) * 0.65f;
        glow.setShader(new RadialGradient(w * 0.12f, -h * 0.05f, rad,
                new int[]{Theme.alpha(accent, 0x2A), Theme.alpha(accent, 0x0C), 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        glow2.setShader(new RadialGradient(w * 0.95f, h * 1.05f, rad * 0.7f,
                new int[]{0x1A4C6A8A, 0x00000000}, null, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect r = getBounds();
        c.drawRect(r, base);
        if (Prefs.WALL_BLACK.equals(style)) return;
        if (Prefs.WALL_CARBON.equals(style)) c.drawRect(r, pattern);
        c.drawRect(r, glow2);
        c.drawRect(r, glow);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
