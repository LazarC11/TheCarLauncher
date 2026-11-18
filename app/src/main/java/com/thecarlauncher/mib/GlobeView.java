package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Welcome-page backdrop: a large grey wireframe globe in the bottom-right corner (partly off
 * screen), drawn as a lit 3D sphere — axis tilted like the Earth's, meridians turning slowly, lines
 * shaded by the light and by depth (bright in front, faint behind), a soft sheen and a glowing rim
 * — with light streaks orbiting it on tilted rings (bright head, tail fading through the ambient
 * colour into green) and thin horizontal streaks shimmering off its edge. Drawn per frame, so it
 * stays sharp at any size; lines are batched by brightness to keep it light on slow units.
 */
public class GlobeView extends View {
    /** The Earth's axial tilt, shown as a lean in the picture plane, plus a slight lean toward us. */
    private static final double ROLL = Math.toRadians(23.4), PITCH = Math.toRadians(16);
    private static final int MERIDIANS = 24, PARALLELS = 17, SEGMENTS = 72, BUCKETS = 10;
    /** Light from the upper left, a little in front. */
    private static final double LX = -0.55, LY = 0.62, LZ = 0.56;
    private static final int GREEN = 0xFF38E8A0;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] front = new Path[BUCKETS], back = new Path[BUCKETS];
    private final float[] p = new float[3];
    private float spin, time;
    private long last;

    /** Orbit rings: radius (× globe), tilt about x, tilt about z, speed (rad/s), tail length (rad), phase. */
    private static final double[][] RINGS = {
            {1.10, Math.toRadians(70), Math.toRadians(-30), 0.34, 1.8, 1.2},
            {1.22, Math.toRadians(62), Math.toRadians(30), -0.25, 2.1, 2.6},
            {1.05, Math.toRadians(80), Math.toRadians(-6), 0.46, 1.4, 4.4},
            {1.34, Math.toRadians(55), Math.toRadians(-48), 0.20, 2.3, 0.4},
            {1.16, Math.toRadians(76), Math.toRadians(52), -0.38, 1.6, 5.3},
            {1.28, Math.toRadians(68), Math.toRadians(10), 0.28, 1.9, 3.5},
    };

    /** Horizontal streaks: height (× globe radius above the centre), length (× width), speed, phase. */
    private static final float[][] STREAKS = {
            {0.86f, 0.22f, 0.50f, 0f}, {0.70f, 0.34f, 0.35f, 1.1f}, {0.55f, 0.28f, 0.42f, 2.3f},
            {0.40f, 0.44f, 0.22f, 1.7f}, {0.27f, 0.30f, 0.31f, 3.9f}, {0.14f, 0.40f, 0.28f, 4.6f},
            {0.04f, 0.25f, 0.45f, 5.4f},
    };

    public GlobeView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        blur.setStyle(Paint.Style.STROKE);
        blur.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < BUCKETS; i++) {
            front[i] = new Path();
            back[i] = new Path();
        }
        setLayerType(LAYER_TYPE_SOFTWARE, null); // blur mask filters for the glows
    }

    /** Sphere point (lat, lon) after spin, pitch and roll; p = {x, y (up), depth (toward us)}. */
    private void sphere(double lat, double lon) {
        double x = Math.cos(lat) * Math.sin(lon + spin);
        double y = Math.sin(lat);
        double z = Math.cos(lat) * Math.cos(lon + spin);
        double y1 = y * Math.cos(PITCH) - z * Math.sin(PITCH);
        double z1 = y * Math.sin(PITCH) + z * Math.cos(PITCH);
        p[0] = (float) (x * Math.cos(ROLL) - y1 * Math.sin(ROLL));
        p[1] = (float) (x * Math.sin(ROLL) + y1 * Math.cos(ROLL));
        p[2] = (float) z1;
    }

    /** Point on an orbit ring (circle of radius rr in a plane tilted by ax about x, then az about z). */
    private void ring(double rr, double ax, double az, double t) {
        double x = rr * Math.cos(t), z = rr * Math.sin(t);
        double y1 = -z * Math.sin(ax), z1 = z * Math.cos(ax);
        p[0] = (float) (x * Math.cos(az) - y1 * Math.sin(az));
        p[1] = (float) (x * Math.sin(az) + y1 * Math.cos(az));
        p[2] = (float) z1;
    }

    private static int alpha(int color, float a) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (color & 0x00FFFFFF);
    }

    /** Colour between a and b. */
    private static int mix(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int x = (a >>> sh) & 0xFF, y = (b >>> sh) & 0xFF;
            r |= (Math.round(x + (y - x) * f) & 0xFF) << sh;
        }
        return r;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        spin += dt * 0.10f;
        time += dt;

        float r = Math.min(h * 0.80f, w * 0.40f);
        float cx = w - r * 0.10f, cy = h + r * 0.02f;
        int blue = Mib.BLUE;
        float sw = Math.max(1.5f, Mib.px(getContext(), 2.1f));

        // Atmosphere around the globe.
        fill.setShader(new RadialGradient(cx, cy, r * 1.45f, new int[]{alpha(blue, 0.26f), alpha(blue, 0.10f), 0},
                new float[]{0.66f, 0.80f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.45f, fill);
        // The grey sphere, lit from the upper left: highlight, body, dark limb with a blue rim tint.
        float hx = cx - r * 0.42f, hy = cy - r * 0.46f;
        fill.setShader(new RadialGradient(hx, hy, r * 1.55f,
                new int[]{0xFF4E535C, 0xFF30343B, 0xFF16181D, mix(0xFF0C0D11, blue, 0.35f)},
                new float[]{0f, 0.38f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        // Soft sheen.
        fill.setShader(new RadialGradient(hx + r * 0.08f, hy + r * 0.08f, r * 0.55f,
                new int[]{0x2EFFFFFF, 0x0CFFFFFF, 0}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(hx + r * 0.08f, hy + r * 0.08f, r * 0.55f, fill);
        fill.setShader(null);

        // Streaks behind the globe, then the wireframe, then the ones in front.
        drawOrbits(c, cx, cy, r, sw, false);
        drawWireframe(c, cx, cy, r, sw);

        // Rim light.
        blur.setStrokeWidth(sw * 5f);
        blur.setColor(alpha(blue, 0.50f));
        blur.setMaskFilter(new BlurMaskFilter(sw * 5f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cy, r, blur);
        blur.setMaskFilter(null);
        line.setColor(alpha(mix(0xFFD8DCE4, blue, 0.25f), 0.95f));
        line.setStrokeWidth(sw * 1.3f);
        c.drawCircle(cx, cy, r, line);

        drawOrbits(c, cx, cy, r, sw, true);
        drawLightStreaks(c, w, cx, cy, r, sw);

        if (isShown()) postInvalidateDelayed(50); // 20 fps: smooth enough, light on slow units
    }

    /** Meridians and parallels, each short segment put in a brightness bucket (light × depth). */
    private void drawWireframe(Canvas c, float cx, float cy, float r, float sw) {
        for (int i = 0; i < BUCKETS; i++) {
            front[i].rewind();
            back[i].rewind();
        }
        for (int m = 0; m < MERIDIANS; m++) {
            double lon = m * Math.PI * 2 / MERIDIANS;
            float px = 0, py = 0, pl = 0, pz = 0;
            for (int s = 0; s <= SEGMENTS; s++) {
                double lat = -Math.PI / 2 + s * Math.PI / SEGMENTS;
                sphere(lat, lon);
                float x = cx + p[0] * r, y = cy - p[1] * r, z = p[2];
                float l = (float) (p[0] * LX + p[1] * LY + p[2] * LZ);
                if (s > 0) addSegment(px, py, x, y, (pz + z) / 2f, (pl + l) / 2f);
                px = x; py = y; pz = z; pl = l;
            }
        }
        for (int k = 1; k <= PARALLELS; k++) {
            double lat = -Math.PI / 2 + k * Math.PI / (PARALLELS + 1);
            float px = 0, py = 0, pl = 0, pz = 0;
            for (int s = 0; s <= SEGMENTS; s++) {
                double lon = s * Math.PI * 2 / SEGMENTS;
                sphere(lat, lon);
                float x = cx + p[0] * r, y = cy - p[1] * r, z = p[2];
                float l = (float) (p[0] * LX + p[1] * LY + p[2] * LZ);
                if (s > 0) addSegment(px, py, x, y, (pz + z) / 2f, (pl + l) / 2f);
                px = x; py = y; pz = z; pl = l;
            }
        }
        int grey = 0xFFDCDFE6;
        line.setStrokeWidth(sw * 0.75f);
        for (int i = 0; i < BUCKETS; i++) {
            line.setColor(alpha(grey, 0.05f + 0.10f * i / (BUCKETS - 1f)));
            c.drawPath(back[i], line);
        }
        line.setStrokeWidth(sw);
        for (int i = 0; i < BUCKETS; i++) {
            float f = i / (BUCKETS - 1f);
            line.setColor(alpha(mix(0xFF9CA3AE, grey, f), 0.22f + 0.70f * f));
            c.drawPath(front[i], line);
        }
        // Small lights where the lines cross on the lit front.
        for (int m = 0; m < MERIDIANS; m += 3) {
            for (int k = 2; k <= PARALLELS - 1; k += 3) {
                sphere(-Math.PI / 2 + k * Math.PI / (PARALLELS + 1), m * Math.PI * 2 / MERIDIANS);
                float l = (float) (p[0] * LX + p[1] * LY + p[2] * LZ);
                if (p[2] < 0.2f || l < 0.1f) continue;
                dot.setColor(alpha(0xFFFFFFFF, 0.30f + 0.6f * l));
                c.drawCircle(cx + p[0] * r, cy - p[1] * r, sw * 1.3f, dot);
            }
        }
    }

    private void addSegment(float x0, float y0, float x1, float y1, float z, float light) {
        if (z >= 0) {
            // Front: lit side bright, shadow side dimmer, edges (small z) dimmer too.
            float f = 0.15f + 0.55f * Math.max(0, light) + 0.30f * z;
            int b = Math.max(0, Math.min(BUCKETS - 1, (int) (f * BUCKETS)));
            front[b].moveTo(x0, y0);
            front[b].lineTo(x1, y1);
        } else {
            int b = Math.max(0, Math.min(BUCKETS - 1, (int) ((1 + z) * BUCKETS)));
            back[b].moveTo(x0, y0);
            back[b].lineTo(x1, y1);
        }
    }

    /**
     * The orbit streaks: a faint ring and a bright head with a tail that fades from white through
     * the ambient colour into green. Parts behind the globe are hidden; {@code near} draws only
     * the near half.
     */
    private void drawOrbits(Canvas c, float cx, float cy, float r, float sw, boolean near) {
        for (double[] g : RINGS) {
            double rr = g[0], ax = g[1], az = g[2], speed = g[3], tail = g[4];
            double head = g[5] + time * speed;
            int steps = 120;
            float px = 0, py = 0;
            boolean pv = false;
            line.setStrokeWidth(sw * 0.6f);
            line.setColor(alpha(Mib.BLUE, near ? 0.22f : 0.09f));
            for (int s = 0; s <= steps; s++) {
                ring(rr, ax, az, s * Math.PI * 2 / steps);
                float x = cx + p[0] * r, y = cy - p[1] * r;
                boolean vis = visible(x, y, cx, cy, r, near);
                if (s > 0 && vis && pv) c.drawLine(px, py, x, y, line);
                px = x; py = y; pv = vis;
            }
            int ts = 44;
            pv = false;
            for (int s = 0; s <= ts; s++) {
                float f = s / (float) ts; // 0 tail end … 1 head
                ring(rr, ax, az, head - Math.signum(speed) * tail * (1 - f));
                float x = cx + p[0] * r, y = cy - p[1] * r;
                boolean vis = visible(x, y, cx, cy, r, near);
                if (s > 0 && vis && pv) {
                    // green at the end of the tail, ambient colour in the middle, white at the head
                    int col = f < 0.5f ? mix(GREEN, Mib.BLUE, f * 2f) : mix(Mib.BLUE, 0xFFFFFFFF, (f - 0.5f) * 2f);
                    float a = 0.12f + 0.80f * f * f;
                    blur.setStrokeWidth(sw * (1f + 3.5f * f));
                    blur.setColor(alpha(f < 0.6f ? col : Mib.BLUE, a * 0.6f));
                    blur.setMaskFilter(new BlurMaskFilter(sw * (1.2f + 2.6f * f), BlurMaskFilter.Blur.NORMAL));
                    c.drawLine(px, py, x, y, blur);
                    blur.setMaskFilter(null);
                    line.setStrokeWidth(sw * (0.5f + 1.2f * f));
                    line.setColor(alpha(col, a));
                    c.drawLine(px, py, x, y, line);
                }
                px = x; py = y; pv = vis;
            }
            ring(rr, ax, az, head);
            float hx = cx + p[0] * r, hy = cy - p[1] * r;
            if (visible(hx, hy, cx, cy, r, near)) {
                dot.setMaskFilter(new BlurMaskFilter(sw * 6f, BlurMaskFilter.Blur.NORMAL));
                dot.setColor(alpha(Mib.BLUE, 0.9f));
                c.drawCircle(hx, hy, sw * 5f, dot);
                dot.setMaskFilter(null);
                dot.setColor(0xFFFFFFFF);
                c.drawCircle(hx, hy, sw * 1.8f, dot);
            }
        }
    }

    /** Near pass = near side; far pass = far side not hidden by the globe. */
    private boolean visible(float x, float y, float cx, float cy, float r, boolean near) {
        boolean isNear = p[2] >= 0;
        if (near) return isNear;
        if (isNear) return false;
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy > r * r;
    }

    /**
     * Thin horizontal streaks leaving the globe's edge to the left, each pulsing slowly: green at
     * the far end, the ambient colour, white where they touch the rim.
     */
    private void drawLightStreaks(Canvas c, float w, float cx, float cy, float r, float sw) {
        for (float[] st : STREAKS) {
            float y = cy - st[0] * r;
            float dy = y - cy;
            if (Math.abs(dy) >= r) continue;
            float edge = cx - (float) Math.sqrt(r * r - dy * dy);
            float len = st[1] * w;
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * st[2] + st[3]);
            float a = 0.22f + 0.58f * pulse;
            float th = sw * (0.45f + 0.55f * pulse);
            fill.setShader(new LinearGradient(edge - len, y, edge + r * 0.22f, y,
                    new int[]{0, alpha(GREEN, a * 0.55f), alpha(Mib.BLUE, a * 0.8f), alpha(0xFFFFFFFF, a), 0},
                    new float[]{0f, 0.25f, 0.62f, 0.86f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(edge - len, y - th, edge + r * 0.22f, y + th, fill);
            fill.setShader(new LinearGradient(edge - len * 0.4f, y, edge + r * 0.1f, y,
                    new int[]{0, alpha(Mib.BLUE, a * 0.30f), 0}, new float[]{0f, 0.8f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(edge - len * 0.4f, y - th * 5f, edge + r * 0.1f, y + th * 5f, fill);
        }
        fill.setShader(null);
    }
}
