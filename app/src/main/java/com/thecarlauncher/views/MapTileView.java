package com.thecarlauncher.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Theme;

/**
 * Lightweight slippy map (Web-Mercator tiles) with a vehicle marker.
 * Works offline as a styled grid, so the dashboard never shows a blank hole.
 */
public class MapTileView extends View implements TileCache.Listener {
    public interface Listener { void onFollowChanged(boolean follow); }
    public interface LongPressListener { void onLongPress(double lat, double lon); }

    private static final int MIN_ZOOM = 3, MAX_ZOOM = 19;

    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint markerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accuracyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Path arrow = new Path();
    private final float density;
    private final boolean hiDpi;
    private final float tileSize;

    private TileCache cache;
    private String source = Prefs.TILES_DARK;
    private double centerLat = 50.0, centerLon = 10.0;
    private int zoom = 16;
    private boolean follow = true, interactive, hasFix, hasPosition;
    private double myLat, myLon;
    private float bearing, accuracyM;
    // Smooth marker: glides from the shown position to each new fix over about one fix interval.
    private double fromLat, fromLon, toLat, toLon;
    private float fromBearing, toBearing;
    private long glideStart, glideMs = 1;
    private long lastFixAt;
    /** Map turns so the car points up (only while following). */
    private boolean headingUp;
    private int accent = Theme.ACCENTS[0];
    private Listener listener;
    private GestureDetector gestures;
    private ScaleGestureDetector scaler;
    private float scaleAccum = 1f;
    private int background = 0xFFD8CEBD;
    private final RectF ring = new RectF();
    // Route overlay
    private double[] routeLat, routeLon;
    private int routeFrom;
    private boolean hasDest;
    private double destLat, destLon;
    private final Paint routeCase = new Paint(Paint.ANTI_ALIAS_FLAG), routeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path routePath = new Path();
    private LongPressListener longPress;

    public MapTileView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        hiDpi = density >= 1.5f;
        tileSize = 256f * Math.max(1f, density);
        gridPaint.setColor(0xFF141B24);
        gridPaint.setStrokeWidth(Math.max(1f, density));
        markerStroke.setStyle(Paint.Style.STROKE);
        markerStroke.setStrokeWidth(2f * density);
        markerStroke.setColor(0xFFFFFFFF);
        markerStroke.setStrokeJoin(Paint.Join.ROUND);
        if (!isInEditMode()) cache = TileCache.get(c);
        routeCase.setStyle(Paint.Style.STROKE);
        routeCase.setStrokeJoin(Paint.Join.ROUND);
        routeCase.setStrokeCap(Paint.Cap.ROUND);
        routeCase.setStrokeWidth(10f * density);
        routeCase.setColor(0xFF0B3D7A);
        routeLine.setStyle(Paint.Style.STROKE);
        routeLine.setStrokeJoin(Paint.Join.ROUND);
        routeLine.setStrokeCap(Paint.Cap.ROUND);
        routeLine.setStrokeWidth(6.5f * density);
        routeLine.setColor(0xFF3C9BFF);
    }

    public void setSource(String s) {
        source = s;
        if (Prefs.TILES_OSM.equals(s)) {
            // Invert the light OSM style into a dimmed night map.
            ColorMatrix invert = new ColorMatrix(new float[]{
                    -1, 0, 0, 0, 255,
                    0, -1, 0, 0, 255,
                    0, 0, -1, 0, 255,
                    0, 0, 0, 1, 0});
            ColorMatrix dim = new ColorMatrix();
            dim.setScale(0.75f, 0.8f, 0.9f, 1f);
            invert.postConcat(dim);
            tilePaint.setColorFilter(new ColorMatrixColorFilter(invert));
        } else if (Prefs.TILES_DAY.equals(s)) {
            // Warm, slightly muted day palette: beige land, cream roads, like a factory map.
            ColorMatrix warm = new ColorMatrix();
            warm.setSaturation(0.7f);
            ColorMatrix tint = new ColorMatrix();
            tint.setScale(0.97f, 0.93f, 0.85f, 1f);
            warm.postConcat(tint);
            tilePaint.setColorFilter(new ColorMatrixColorFilter(warm));
        } else {
            tilePaint.setColorFilter(null);
        }
        boolean day = Prefs.TILES_DAY.equals(s) || Prefs.TILES_OFF.equals(s);
        background = day ? 0xFFD8CEBD : 0xFF0A0E13;
        gridPaint.setColor(day ? 0xFFCDC2AF : 0xFF141B24);
        invalidate();
    }

    public void setAccent(int accent) {
        this.accent = accent;
        markerFill.setColor(accent);
        invalidate();
    }

    public void setInteractive(boolean on) {
        interactive = on;
        if (on && gestures == null) {
            gestures = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    panBy(dx, dy);
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (longPress == null) return;
                    double[] p = screenToLatLon(e.getX(), e.getY());
                    longPress.onLongPress(p[0], p[1]);
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    setZoom(zoom + 1);
                    return true;
                }
            });
            scaler = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    scaleAccum *= d.getScaleFactor();
                    if (scaleAccum > 1.5f) {
                        setZoom(zoom + 1);
                        scaleAccum = 1f;
                    } else if (scaleAccum < 0.67f) {
                        setZoom(zoom - 1);
                        scaleAccum = 1f;
                    }
                    return true;
                }
            });
        }
    }

    public void setListener(Listener l) { listener = l; }

    public void setLongPressListener(LongPressListener l) { longPress = l; }

    /** Route line to draw (null clears), plus the destination flag. */
    public void setRoute(double[] lat, double[] lon) {
        routeLat = lat;
        routeLon = lon;
        routeFrom = 0;
        invalidate();
    }

    /** Hides the part of the route already driven. */
    public void setRouteProgress(int fromIndex) {
        routeFrom = Math.max(0, fromIndex);
        invalidate();
    }

    public void setDestination(boolean show, double lat, double lon) {
        hasDest = show;
        destLat = lat;
        destLon = lon;
        invalidate();
    }

    /** Centres and zooms so the box fits with a margin; stops following. */
    public void fit(double minLat, double minLon, double maxLat, double maxLon) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        int z = MAX_ZOOM - 2;
        for (; z > MIN_ZOOM; z--) {
            double world = tileSize * Math.pow(2, z);
            double dx = (lonToX(maxLon) - lonToX(minLon)) * world, dy = (latToY(minLat) - latToY(maxLat)) * world;
            if (dx < w * 0.75 && dy < h * 0.75) break;
        }
        centerLat = yToLat((latToY(minLat) + latToY(maxLat)) / 2);
        centerLon = (minLon + maxLon) / 2;
        zoom = z;
        setFollow(false);
        invalidate();
    }

    public int getZoom() { return zoom; }

    public void setZoom(int z) {
        z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        if (z == zoom) return;
        zoom = z;
        invalidate();
    }

    public boolean isFollowing() { return follow; }

    public void setFollow(boolean f) {
        if (follow == f) return;
        follow = f;
        if (f && hasPosition) {
            centerLat = myLat;
            centerLon = myLon;
        }
        if (listener != null) listener.onFollowChanged(f);
        invalidate();
    }

    public void setCenter(double lat, double lon) {
        centerLat = lat;
        centerLon = lon;
        invalidate();
    }

    /** Updates the vehicle marker; recentres when following. */
    public void setMyLocation(double lat, double lon, float bearing, float accuracy, boolean fix) {
        long now = android.os.SystemClock.uptimeMillis();
        // On the route: ride on the line instead of the raw (noisy) GPS point.
        double[] snapped = snapToRoute(lat, lon);
        if (snapped != null) {
            lat = snapped[0];
            lon = snapped[1];
            if (snapped[2] >= 0) bearing = (float) snapped[2];
        }
        this.accuracyM = accuracy;
        hasFix = fix;
        if (!hasPosition || distanceM(myLat, myLon, lat, lon) > 300) {
            // First fix or a jump (tunnel exit): no glide.
            myLat = fromLat = toLat = lat;
            myLon = fromLon = toLon = lon;
            this.bearing = fromBearing = toBearing = bearing;
            glideMs = 1;
        } else {
            fromLat = myLat;
            fromLon = myLon;
            fromBearing = this.bearing;
            toLat = lat;
            toLon = lon;
            toBearing = bearing;
            glideMs = Math.max(250, Math.min(1500, now - lastFixAt));
        }
        glideStart = now;
        lastFixAt = now;
        hasPosition = true;
        if (follow) {
            centerLat = myLat;
            centerLon = myLon;
        }
        postInvalidateOnAnimation();
    }

    /** Advances the glide; true while still moving. */
    private boolean stepGlide() {
        float t = Math.min(1f, (android.os.SystemClock.uptimeMillis() - glideStart) / (float) glideMs);
        myLat = fromLat + (toLat - fromLat) * t;
        myLon = fromLon + (toLon - fromLon) * t;
        float d = ((toBearing - fromBearing) % 360 + 540) % 360 - 180; // shortest way round
        bearing = (fromBearing + d * t + 360) % 360;
        return t < 1f;
    }

    public void setHeadingUp(boolean on) {
        headingUp = on;
        invalidate();
    }

    public boolean isHeadingUp() { return headingUp; }

    /** The angle the map is turned by (0 = north up). */
    public float mapRotation() { return headingUp && follow && hasPosition ? bearing : 0f; }

    /** Nearest point on the remaining route within 25 m, with that segment's direction. */
    private double[] snapToRoute(double lat, double lon) {
        if (routeLat == null || routeLat.length < 2) return null;
        double cos = Math.cos(Math.toRadians(lat));
        double best = 25, bl = 0, bo = 0, bb = -1;
        int from = Math.max(0, routeFrom - 2), to = Math.min(routeLat.length - 1, routeFrom + 400);
        for (int i = from; i < to; i++) {
            double ax = (routeLon[i] - lon) * 111320 * cos, ay = (routeLat[i] - lat) * 110540;
            double bx = (routeLon[i + 1] - lon) * 111320 * cos, by = (routeLat[i + 1] - lat) * 110540;
            double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            if (len2 < 0.01) continue;
            double u = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            double px = ax + u * dx, py = ay + u * dy;
            double d = Math.sqrt(px * px + py * py);
            if (d < best) {
                best = d;
                bl = lat + py / 110540;
                bo = lon + px / (111320 * cos);
                bb = (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360;
            }
        }
        return best < 25 ? new double[]{bl, bo, bb} : null;
    }

    private static double distanceM(double la1, double lo1, double la2, double lo2) {
        double x = (lo2 - lo1) * 111320 * Math.cos(Math.toRadians((la1 + la2) / 2)), y = (la2 - la1) * 110540;
        return Math.sqrt(x * x + y * y);
    }

    /** Screen point → map position, taking a turned map into account. */
    private double[] screenToLatLon(float sx, float sy) {
        int w = getWidth(), h = getHeight();
        float ax = w / 2f, ay = anchorY(h);
        float rot = mapRotation();
        double dx = sx - ax, dy = sy - ay;
        if (rot != 0) {
            double r = Math.toRadians(rot), cs = Math.cos(r), sn = Math.sin(r);
            double rx = dx * cs - dy * sn, ry = dx * sn + dy * cs;
            dx = rx;
            dy = ry;
        }
        double world = tileSize * Math.pow(2, zoom);
        double x = lonToX(centerLon) * world + dx, y = latToY(centerLat) * world + dy;
        return new double[]{yToLat(y / world), xToLon(x / world)};
    }

    /** Where the car sits on screen: lower down when the map turns, to see more road ahead. */
    private float anchorY(int h) { return mapRotation() != 0 ? h * 0.68f : h / 2f; }

    private void panBy(float dx, float dy) {
        double world = tileSize * Math.pow(2, zoom);
        double cx = lonToX(centerLon) * world + dx;
        double cy = latToY(centerLat) * world + dy;
        centerLon = xToLon(cx / world);
        centerLat = yToLat(Math.max(0, Math.min(world, cy)) / world);
        setFollow(false);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!interactive) return super.onTouchEvent(e);
        getParent().requestDisallowInterceptTouchEvent(true);
        scaler.onTouchEvent(e);
        if (!scaler.isInProgress()) gestures.onTouchEvent(e);
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (cache != null) cache.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (cache != null) cache.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onTileLoaded() {
        if (isShown()) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        glowPaint.setShader(null);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(background);
        boolean gliding = hasPosition && stepGlide();
        if (follow && hasPosition) {
            centerLat = myLat;
            centerLon = myLon;
        }
        float rot = mapRotation();
        float ax = w / 2f, ay = anchorY(h);
        double world = tileSize * Math.pow(2, zoom);
        double cx = lonToX(centerLon) * world;
        double cy = latToY(centerLat) * world;
        double left = cx - ax, top = cy - ay;
        // A turned map must cover the corners too: draw the circle around the anchor.
        double reach = rot != 0 ? Math.hypot(Math.max(ax, w - ax), Math.max(ay, h - ay)) : 0;

        c.save();
        if (rot != 0) c.rotate(-rot, ax, ay);
        drawGrid(c, left, top, (float) -reach, (float) -reach, (float) (w + reach), (float) (h + reach));

        if (cache != null && cache.draws(source)) {
            int n = 1 << zoom;
            int x0 = (int) Math.floor((left - reach) / tileSize), x1 = (int) Math.floor((left + w + reach) / tileSize);
            int y0 = (int) Math.floor((top - reach) / tileSize), y1 = (int) Math.floor((top + h + reach) / tileSize);
            for (int ty = y0; ty <= y1; ty++) {
                if (ty < 0 || ty >= n) continue;
                for (int tx = x0; tx <= x1; tx++) {
                    int wx = ((tx % n) + n) % n;
                    float px = (float) (tx * tileSize - left), py = (float) (ty * tileSize - top);
                    Bitmap b = cache.get(source, zoom, wx, ty, hiDpi);
                    if (b != null) {
                        dst.set(px, py, px + tileSize, py + tileSize);
                        c.drawBitmap(b, null, dst, tilePaint);
                    } else {
                        drawParent(c, wx, ty, px, py);
                    }
                }
            }
        }

        drawRoute(c, left, top, world);
        c.restore();
        if (hasPosition) drawMarker(c, left, top, world, rot, ax, ay);
        if (gliding) postInvalidateOnAnimation();
    }

    private void drawRoute(Canvas c, double left, double top, double world) {
        if (routeLat != null && routeLat.length > 1) {
            routePath.reset();
            boolean started = false;
            float lx = 0, ly = 0;
            for (int i = Math.min(routeFrom, routeLat.length - 2); i < routeLat.length; i++) {
                float x = (float) (lonToX(routeLon[i]) * world - left), y = (float) (latToY(routeLat[i]) * world - top);
                if (!started) {
                    routePath.moveTo(x, y);
                    started = true;
                } else if (Math.abs(x - lx) + Math.abs(y - ly) < 1.5f && i < routeLat.length - 1) {
                    continue; // skip sub-pixel points
                } else {
                    routePath.lineTo(x, y);
                }
                lx = x;
                ly = y;
            }
            c.drawPath(routePath, routeCase);
            c.drawPath(routePath, routeLine);
        }
        if (hasDest) {
            float x = (float) (lonToX(destLon) * world - left), y = (float) (latToY(destLat) * world - top);
            float s = 14f * density;
            flagPaint.setStyle(Paint.Style.FILL);
            flagPaint.setColor(0xFF1C1C22);
            c.drawRect(x - 1.2f * density, y - 2.6f * s, x + 1.2f * density, y, flagPaint);
            // Chequered finish flag
            float fw = s * 1.5f, fh = s, cell = fw / 4f;
            for (int r = 0; r < 3; r++) {
                for (int k = 0; k < 4; k++) {
                    flagPaint.setColor(((r + k) % 2 == 0) ? 0xFF1C1C22 : 0xFFFFFFFF);
                    float fx = x + k * cell, fy = y - 2.6f * s + r * fh / 3f;
                    c.drawRect(fx, fy, fx + cell, fy + fh / 3f, flagPaint);
                }
            }
            flagPaint.setStyle(Paint.Style.STROKE);
            flagPaint.setStrokeWidth(density);
            flagPaint.setColor(0xFF1C1C22);
            c.drawRect(x, y - 2.6f * s, x + fw, y - 2.6f * s + fh, flagPaint);
        }
    }

    /** While a tile loads, stretch the matching quarter of a cached lower-zoom tile. */
    private void drawParent(Canvas c, int x, int y, float px, float py) {
        for (int up = 1; up <= 3 && zoom - up >= 0; up++) {
            Bitmap p = cache.peek(source, zoom - up, x >> up, y >> up, hiDpi);
            if (p == null) continue;
            int parts = 1 << up;
            int sub = p.getWidth() / parts;
            int sx = (x % parts) * sub, sy = (y % parts) * sub;
            src.set(sx, sy, sx + sub, sy + sub);
            dst.set(px, py, px + tileSize, py + tileSize);
            c.drawBitmap(p, src, dst, tilePaint);
            return;
        }
    }

    /** Background grid; lines are drawn over the view range [min, max] (wider when the map turns). */
    private void drawGrid(Canvas c, double left, double top, float minX, float minY, float maxX, float maxY) {
        float step = 64f * density;
        for (double x = Math.ceil((left + minX) / step) * step - left; x < maxX; x += step) {
            c.drawLine((float) x, minY, (float) x, maxY, gridPaint);
        }
        for (double y = Math.ceil((top + minY) / step) * step - top; y < maxY; y += step) {
            c.drawLine(minX, (float) y, maxX, (float) y, gridPaint);
        }
    }

    /** Factory-style position marker: red arrowhead inside a red ellipse ring. */
    private void drawMarker(Canvas c, double left, double top, double world, float rot, float ax, float ay) {
        float mx = (float) (lonToX(myLon) * world - left);
        float my = (float) (latToY(myLat) * world - top);
        if (rot != 0) {
            // Map turned around the anchor: the car is the anchor (it is being followed).
            mx = ax;
            my = ay;
        }
        float rx = 30f * density, ry = 20f * density;
        int red = hasFix ? 0xFFD3202B : 0xFF8A8A8A;
        accuracyPaint.setStyle(Paint.Style.STROKE);
        accuracyPaint.setStrokeWidth(2.6f * density);
        accuracyPaint.setColor(red);
        ring.set(mx - rx, my - ry, mx + rx, my + ry);
        c.drawOval(ring, accuracyPaint);

        // The arrow is built around its own middle so it turns on the spot, centred in the ring.
        float s = 13f * density, k = 0.125f * s;
        arrow.reset();
        arrow.moveTo(0, -s + k);
        arrow.lineTo(s * 0.7f, s * 0.75f + k);
        arrow.lineTo(0, s * 0.42f + k);
        arrow.lineTo(-s * 0.7f, s * 0.75f + k);
        arrow.close();
        c.save();
        c.translate(mx, my);
        c.rotate(bearing - rot);
        markerFill.setColor(red);
        c.drawPath(arrow, markerFill);
        markerStroke.setColor(hasFix ? 0xFF7A0E14 : 0xFF555555);
        markerStroke.setStrokeWidth(1.2f * density);
        c.drawPath(arrow, markerStroke);
        // Lighter right half gives the arrow its 3-D facet.
        arrow.reset();
        arrow.moveTo(0, -s + k);
        arrow.lineTo(s * 0.7f, s * 0.75f + k);
        arrow.lineTo(0, s * 0.42f + k);
        arrow.close();
        glowPaint.setShader(null);
        glowPaint.setColor(0x40FFFFFF);
        c.drawPath(arrow, glowPaint);
        c.restore();
    }

    private static double lonToX(double lon) {
        return (lon + 180.0) / 360.0;
    }

    private static double latToY(double lat) {
        lat = Math.max(-85.0511, Math.min(85.0511, lat));
        double r = Math.toRadians(lat);
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2;
    }

    private static double xToLon(double x) {
        double lon = x * 360.0 - 180.0;
        while (lon < -180) lon += 360;
        while (lon > 180) lon -= 360;
        return lon;
    }

    private static double yToLat(double y) {
        double n = Math.PI - 2 * Math.PI * y;
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}
