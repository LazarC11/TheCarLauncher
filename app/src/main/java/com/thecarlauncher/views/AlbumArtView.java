package com.thecarlauncher.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.thecarlauncher.R;
import com.thecarlauncher.util.Theme;

/** Square, rounded album art with a cross-fade between covers and a styled placeholder. */
public class AlbumArtView extends View {
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint oldPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Drawable note;
    private Bitmap bitmap, oldBitmap;
    private float fade = 1f;
    private float radius;
    private int accent = Theme.ACCENTS[0];

    public AlbumArtView(Context c, AttributeSet a) {
        super(c, a);
        radius = 12 * getResources().getDisplayMetrics().density;
        note = c.getDrawable(R.drawable.ic_music).mutate();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(getResources().getDisplayMetrics().density);
        stroke.setColor(0x33FFFFFF);
    }

    public void setAccent(int accent) {
        this.accent = accent;
        updateShaders();
        invalidate();
    }

    public void setBitmap(Bitmap b) {
        if (b == bitmap) return;
        // Media sessions hand out a fresh copy on every update; only animate real changes.
        if (b != null && bitmap != null && !bitmap.isRecycled() && b.getWidth() == bitmap.getWidth()
                && b.getHeight() == bitmap.getHeight() && b.sameAs(bitmap)) {
            return;
        }
        oldBitmap = bitmap;
        bitmap = b;
        updateShaders();
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(350);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                fade = (Float) va.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        int wm = MeasureSpec.getMode(wSpec);
        int hm = MeasureSpec.getMode(hSpec);
        int size;
        if (wm == MeasureSpec.UNSPECIFIED) size = h;
        else if (hm == MeasureSpec.UNSPECIFIED) size = w;
        else size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rect.set(0, 0, w, h);
        radius = Math.max(8 * getResources().getDisplayMetrics().density, w * 0.05f);
        updateShaders();
    }

    private void updateShaders() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        placeholder.setShader(new LinearGradient(0, 0, w, h,
                new int[]{Theme.alpha(accent, 0x55), 0xFF121A24, 0xFF0A0F15}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, 0, 0, h, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        artPaint.setShader(shaderFor(bitmap, w, h));
        oldPaint.setShader(shaderFor(oldBitmap, w, h));
    }

    private static Shader shaderFor(Bitmap b, int w, int h) {
        if (b == null || b.isRecycled()) return null;
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        float scale = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        m.setScale(scale, scale);
        m.postTranslate((w - b.getWidth() * scale) / 2f, (h - b.getHeight() * scale) / 2f);
        s.setLocalMatrix(m);
        return s;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (getWidth() == 0) return;
        if (artPaint.getShader() == null) {
            drawPlaceholder(c, 255);
        } else {
            if (fade < 1f) {
                // Layer underneath the incoming cover: the previous cover or the placeholder.
                if (oldPaint.getShader() != null) {
                    oldPaint.setAlpha(255);
                    c.drawRoundRect(rect, radius, radius, oldPaint);
                } else {
                    drawPlaceholder(c, 255);
                }
            }
            artPaint.setAlpha((int) (255 * fade));
            c.drawRoundRect(rect, radius, radius, artPaint);
        }
        c.drawRoundRect(rect, radius, radius, sheen);
        float inset = stroke.getStrokeWidth() / 2f;
        RectF r = new RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset);
        c.drawRoundRect(r, radius, radius, stroke);
    }

    private void drawPlaceholder(Canvas c, int alpha) {
        placeholder.setAlpha(alpha);
        c.drawRoundRect(rect, radius, radius, placeholder);
        int s = (int) (getWidth() * 0.36f);
        int l = (getWidth() - s) / 2, t = (getHeight() - s) / 2;
        note.setBounds(l, t, l + s, t + s);
        note.setTint(0x66FFFFFF);
        note.setAlpha(alpha);
        note.draw(c);
    }
}
