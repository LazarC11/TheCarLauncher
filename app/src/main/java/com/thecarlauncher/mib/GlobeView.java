package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Welcome-page backdrop: a large wireframe globe sitting in the bottom-right corner (partly off
 * screen), drawn as a real 3D sphere — a tilted axis, meridians turning slowly, lines bright in
 * front and faint behind, a glowing rim — with light streaks orbiting it on tilted rings, each a
 * bright head with a fading tail that passes behind the globe. Everything is in the ambient colour
 * and drawn per frame, so it stays sharp at any size.
 */
public class GlobeView extends View {
    private static final double TILT = Math.toRadians(23);
    private static final int MERIDIANS = 16, PARALLELS = 11, SEGMENTS = 64;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] p = new float[3];
    private float spin, orbit;
    private long last;

    /** Orbit rings: radius (× globe), tilt about x, tilt about z, speed, tail length (rad), phase. */
    private static final double[][] RINGS = {
            {1.12, Math.toRadians(70), Math.toRadians(-28), 0.32, 1.7, 1.2},
            {1.24, Math.toRadians(62), Math.toRadians(32), -0.24, 2.0, 2.6},
            {1.06, Math.toRadians(80), Math.toRadians(-8), 0.45, 1.3, 4.4},
    };

    /** Horizontal light streaks: height (× globe radius above the centre), length (× width), speed, phase. */
    private static final float[][] STREAKS = {
            {0.62f, 0.34f, 0.35f, 0f}, {0.38f, 0.44f, 0.22f, 1.7f}, {0.80f, 0.26f, 0.48f, 3.1f}, {0.18f, 0.40f, 0.28f, 4.6f},
    };

    public GlobeView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        blur.setStyle(Paint.Style.STROKE);
        blur.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // blur mask filters for the glows
    }

    /** Sphere point (lat, lon) after spin and tilt; p = {x, y (up), depth (toward the viewer)}. */
    private void sphere(double lat, double lon) {
        double x = Math.cos(lat) * Math.sin(lon + spin);
        double y = Math.sin(lat);
        double z = Math.cos(lat) * Math.cos(lon + spin);
        p[0] = (float) x;
        p[1] = (float) (y * Math.cos(TILT) - z * Math.sin(TILT));
        p[2] = (float) (y * Math.sin(TILT) + z * Math.cos(TILT));
    }

    /** Point on an orbit ring (circle of radius rr in a plane tilted by ax about x, then az about z). */
    private void ring(double rr, double ax, double az, double t) {
        double x = rr * Math.cos(t), y = 0, z = rr * Math.sin(t);
        double y1 = y * Math.cos(ax) - z * Math.sin(ax), z1 = y * Math.sin(ax) + z * Math.cos(ax);
        double x2 = x * Math.cos(az) - y1 * Math.sin(az), y2 = x * Math.sin(az) + y1 * Math.cos(az);
        p[0] = (float) x2;
        p[1] = (float) y2;
        p[2] = (float) z1;
    }

    private static int alpha(int color, float a) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        spin += dt * 0.12f;
        orbit += dt;

        float r = Math.min(h * 0.80f, w * 0.40f);
        float cx = w - r * 0.10f, cy = h + r * 0.02f;
        int blue = Mib.BLUE, light = 0xFFE9F1FF;
        float sw = Math.max(1f, Mib.px(getContext(), 1.3f));

        // Atmosphere: a wide soft glow, then the dark sphere with a lighter rim (fresnel).
        fill.setShader(new RadialGradient(cx, cy, r * 1.45f, new int[]{alpha(blue, 0.30f), alpha(blue, 0.12f), 0},
                new float[]{0.62f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.45f, fill);
        fill.setShader(new RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.05f,
                new int[]{0xF00A0F1C, 0xF0081020, alpha(Mib.shade(blue, 0.55f, 0xFF), 0.85f)},
                new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);

        // Orbit streaks behind the globe first, then the wireframe, then the ones in front.
        drawStreaks(c, cx, cy, r, sw, false);

        // Meridians and parallels as short segments, alpha by depth.
        line.setStrokeWidth(sw);
        for (int m = 0; m < MERIDIANS; m++) {
            double lon = m * Math.PI * 2 / MERIDIANS;
            float px = 0, py = 0, pz = 0;
            for (int s = 0; s <= SEGMENTS; s++) {
                double lat = -Math.PI / 2 + s * Math.PI / SEGMENTS;
                sphere(lat, lon);
                float x = cx + p[0] * r, y = cy - p[1] * r, z = p[2];
                if (s > 0) segment(c, px, py, x, y, (pz + z) / 2f, light);
                px = x; py = y; pz = z;
            }
        }
        for (int k = 1; k <= PARALLELS; k++) {
            double lat = -Math.PI / 2 + k * Math.PI / (PARALLELS + 1);
            float px = 0, py = 0, pz = 0;
            for (int s = 0; s <= SEGMENTS; s++) {
                double lon = s * Math.PI * 2 / SEGMENTS;
                sphere(lat, lon);
                float x = cx + p[0] * r, y = cy - p[1] * r, z = p[2];
                if (s > 0) segment(c, px, py, x, y, (pz + z) / 2f, light);
                px = x; py = y; pz = z;
            }
        }
        // Small lights where the lines cross on the front.
        for (int m = 0; m < MERIDIANS; m += 2) {
            for (int k = 2; k <= PARALLELS - 1; k += 3) {
                sphere(-Math.PI / 2 + k * Math.PI / (PARALLELS + 1), m * Math.PI * 2 / MERIDIANS);
                if (p[2] < 0.25f) continue;
                dot.setColor(alpha(light, 0.35f + 0.55f * p[2]));
                c.drawCircle(cx + p[0] * r, cy - p[1] * r, sw * 1.6f, dot);
            }
        }
        // Rim light.
        blur.setStrokeWidth(sw * 6f);
        blur.setColor(alpha(blue, 0.55f));
        blur.setMaskFilter(new BlurMaskFilter(sw * 6f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cy, r, blur);
        blur.setMaskFilter(null);
        line.setColor(alpha(light, 0.9f));
        line.setStrokeWidth(sw * 1.5f);
        c.drawCircle(cx, cy, r, line);

        drawStreaks(c, cx, cy, r, sw, true);
        drawLightStreaks(c, w, cx, cy, r, sw);

        if (isShown()) postInvalidateDelayed(50); // 20 fps: smooth enough, light on slow units
    }

    /**
     * Long thin horizontal streaks of light leaving the globe's edge to the left, each fading in
     * and out slowly (a lens-like shimmer), brightest where they touch the rim.
     */
    private void drawLightStreaks(Canvas c, float w, float cx, float cy, float r, float sw) {
        for (float[] st : STREAKS) {
            float y = cy - st[0] * r;
            float dy = y - cy;
            if (Math.abs(dy) >= r) continue;
            float edge = cx - (float) Math.sqrt(r * r - dy * dy); // where the streak meets the rim
            float len = st[1] * w;
            float pulse = 0.5f + 0.5f * (float) Math.sin(orbit * st[2] + st[3]);
            float a = 0.25f + 0.55f * pulse;
            fill.setShader(new android.graphics.LinearGradient(edge - len, y, edge + r * 0.25f, y,
                    new int[]{0, alpha(Mib.BLUE, a * 0.5f), alpha(0xFFFFFFFF, a), 0},
                    new float[]{0f, 0.65f, 0.86f, 1f}, Shader.TileMode.CLAMP));
            float th = sw * (0.8f + 0.9f * pulse);
            c.drawRect(edge - len, y - th, edge + r * 0.25f, y + th, fill);
            // soft halo around the bright part
            fill.setShader(new android.graphics.LinearGradient(edge - len * 0.35f, y, edge + r * 0.1f, y,
                    new int[]{0, alpha(Mib.BLUE, a * 0.35f), 0}, new float[]{0f, 0.8f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(edge - len * 0.35f, y - th * 5f, edge + r * 0.1f, y + th * 5f, fill);
        }
        fill.setShader(null);
    }

    /** One wireframe segment: bright in front, faint behind. */
    private void segment(Canvas c, float x0, float y0, float x1, float y1, float z, int light) {
        float a = z >= 0 ? 0.28f + 0.62f * z : 0.10f + 0.08f * (1 + z);
        line.setColor(alpha(light, a));
        c.drawLine(x0, y0, x1, y1, line);
    }

    /**
     * The light streaks: a faint full ring and a bright head with a fading tail. Parts behind the
     * globe (depth < 0 and inside its disc) are hidden; {@code front} draws only the near half.
     */
    private void drawStreaks(Canvas c, float cx, float cy, float r, float sw, boolean front) {
        for (double[] g : RINGS) {
            double rr = g[0], ax = g[1], az = g[2], speed = g[3], tail = g[4];
            double head = g[5] + orbit * speed;
            int steps = 120;
            // Faint ring
            float px = 0, py = 0;
            boolean pv = false;
            line.setStrokeWidth(sw * 0.8f);
            for (int s = 0; s <= steps; s++) {
                double t = s * Math.PI * 2 / steps;
                ring(rr, ax, az, t);
                float x = cx + p[0] * r, y = cy - p[1] * r;
                boolean vis = visible(x, y, cx, cy, r, front);
                if (s > 0 && vis && pv) {
                    line.setColor(alpha(Mib.BLUE, front ? 0.30f : 0.12f));
                    c.drawLine(px, py, x, y, line);
                }
                px = x; py = y; pv = vis;
            }
            // Tail: from the end towards the head, brighter and thicker as it nears the head.
            int ts = 48;
            pv = false;
            for (int s = 0; s <= ts; s++) {
                float f = s / (float) ts; // 0 tail end … 1 head
                double t = head - (speed >= 0 ? 1 : -1) * tail * (1 - f);
                ring(rr, ax, az, t);
                float x = cx + p[0] * r, y = cy - p[1] * r;
                boolean vis = visible(x, y, cx, cy, r, front);
                if (s > 0 && vis && pv) {
                    blur.setStrokeWidth(sw * (1f + 4f * f));
                    blur.setColor(alpha(Mib.BLUE, 0.55f * f * f));
                    blur.setMaskFilter(new BlurMaskFilter(sw * (1.5f + 3f * f), BlurMaskFilter.Blur.NORMAL));
                    c.drawLine(px, py, x, y, blur);
                    blur.setMaskFilter(null);
                    line.setStrokeWidth(sw * (0.6f + 1.6f * f));
                    line.setColor(alpha(0xFFFFFFFF, 0.85f * f * f));
                    c.drawLine(px, py, x, y, line);
                }
                px = x; py = y; pv = vis;
            }
            // Head
            ring(rr, ax, az, head);
            float hx = cx + p[0] * r, hy = cy - p[1] * r;
            if (visible(hx, hy, cx, cy, r, front)) {
                dot.setMaskFilter(new BlurMaskFilter(sw * 7f, BlurMaskFilter.Blur.NORMAL));
                dot.setColor(alpha(Mib.BLUE, 0.9f));
                c.drawCircle(hx, hy, sw * 6f, dot);
                dot.setMaskFilter(null);
                dot.setColor(0xFFFFFFFF);
                c.drawCircle(hx, hy, sw * 2.2f, dot);
            }
        }
    }

    /** In this pass: front pass = near side; back pass = far side not hidden by the globe. */
    private boolean visible(float x, float y, float cx, float cy, float r, boolean front) {
        boolean near = p[2] >= 0;
        if (front) return near;
        if (near) return false;
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy > r * r;
    }
}
