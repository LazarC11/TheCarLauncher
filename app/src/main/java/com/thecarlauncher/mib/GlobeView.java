package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * A wireframe globe in the MIB style (thin light lines over a soft glow in the ambient colour),
 * turning slowly: meridians drift around the axis, parallels stay. Drawn, not a bitmap, so it is
 * sharp on every screen.
 */
public class GlobeView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float phase;
    private boolean turning = true;

    public GlobeView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setTurning(boolean on) {
        turning = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, rad = Math.min(w, h) * 0.36f;
        if (rad <= 0) return;
        // Glow behind the globe, fading out before the view's edge (no visible square).
        float gr = Math.min(w, h) / 2f;
        glow.setShader(new RadialGradient(cx, cy, gr,
                new int[]{Mib.shade(Mib.BLUE, 1f, 0x66), Mib.shade(Mib.BLUE, 0.8f, 0x22), 0x00000000},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, gr, glow);

        float sw = Math.max(1f, Mib.px(getContext(), 1.4f));
        line.setStrokeWidth(sw);
        // Parallels: flat ellipses, fainter towards the poles.
        for (int i = -3; i <= 3; i++) {
            double lat = i * Math.PI / 8;
            float y = cy - (float) (Math.sin(lat) * rad);
            float rx = (float) (Math.cos(lat) * rad);
            float ry = rx * 0.16f;
            line.setColor(i == 0 ? 0xE6F3F3F5 : 0x99D6D8E0);
            r.set(cx - rx, y - ry, cx + rx, y + ry);
            c.drawOval(r, line);
        }
        // Meridians: ellipses of changing width, turning with the phase; the far half is dimmer.
        int n = 6;
        for (int i = 0; i < n; i++) {
            double lon = phase + i * Math.PI / n;
            float rx = (float) Math.abs(Math.cos(lon)) * rad;
            boolean front = Math.sin(lon) >= 0;
            line.setColor(front ? 0xCCE8E9EE : 0x55B9BBC3);
            r.set(cx - rx, cy - rad, cx + rx, cy + rad);
            c.drawOval(r, line);
        }
        // Outline.
        line.setColor(0xFFF3F3F5);
        line.setStrokeWidth(sw * 1.6f);
        c.drawCircle(cx, cy, rad, line);

        if (turning && isShown()) {
            phase += 0.006f;
            if (phase > Math.PI) phase -= (float) Math.PI;
            postInvalidateDelayed(33);
        }
    }
}
