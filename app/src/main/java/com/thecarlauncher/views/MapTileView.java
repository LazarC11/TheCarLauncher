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
    private int accent = Theme.ACCENTS[0];
    private Listener listener;
    private GestureDetector gestures;
    private ScaleGestureDetector scaler;
    private float scaleAccum = 1f;

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
        } else {
            tilePaint.setColorFilter(null);
        }
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
        myLat = lat;
        myLon = lon;
        this.bearing = bearing;
        this.accuracyM = accuracy;
        hasFix = fix;
        hasPosition = true;
        if (follow) {
            centerLat = lat;
            centerLon = lon;
        }
        invalidate();
    }

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
        c.drawColor(0xFF0A0E13);
        double world = tileSize * Math.pow(2, zoom);
        double cx = lonToX(centerLon) * world;
        double cy = latToY(centerLat) * world;
        double left = cx - w / 2.0, top = cy - h / 2.0;

        drawGrid(c, left, top, w, h);

        if (cache != null && !Prefs.TILES_OFF.equals(source)) {
            int n = 1 << zoom;
            int x0 = (int) Math.floor(left / tileSize), x1 = (int) Math.floor((left + w) / tileSize);
            int y0 = (int) Math.floor(top / tileSize), y1 = (int) Math.floor((top + h) / tileSize);
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

        if (hasPosition) drawMarker(c, left, top, world);
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

    private void drawGrid(Canvas c, double left, double top, int w, int h) {
        float step = 64f * density;
        float ox = (float) (-(left % step));
        float oy = (float) (-(top % step));
        for (float x = ox; x < w; x += step) c.drawLine(x, 0, x, h, gridPaint);
        for (float y = oy; y < h; y += step) c.drawLine(0, y, w, y, gridPaint);
    }

    private void drawMarker(Canvas c, double left, double top, double world) {
        float mx = (float) (lonToX(myLon) * world - left);
        float my = (float) (latToY(myLat) * world - top);
        float r = 26f * density;
        if (hasFix && accuracyM > 0) {
            double metersPerPx = 156543.03392 * Math.cos(Math.toRadians(myLat)) / Math.pow(2, zoom) * 256.0 / tileSize;
            float ar = (float) (accuracyM / metersPerPx);
            if (ar > r) {
                accuracyPaint.setColor(Theme.alpha(accent, 0x1E));
                c.drawCircle(mx, my, ar, accuracyPaint);
            }
        }
        glowPaint.setShader(new RadialGradient(mx, my, r * 1.6f,
                new int[]{Theme.alpha(accent, hasFix ? 0x88 : 0x33), Theme.alpha(accent, 0)}, null,
                Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, r * 1.6f, glowPaint);

        float s = 13f * density;
        arrow.reset();
        arrow.moveTo(0, -s);
        arrow.lineTo(s * 0.78f, s * 0.85f);
        arrow.lineTo(0, s * 0.4f);
        arrow.lineTo(-s * 0.78f, s * 0.85f);
        arrow.close();
        c.save();
        c.translate(mx, my);
        c.rotate(bearing);
        markerFill.setColor(hasFix ? accent : 0xFF6B7785);
        c.drawPath(arrow, markerFill);
        c.drawPath(arrow, markerStroke);
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
