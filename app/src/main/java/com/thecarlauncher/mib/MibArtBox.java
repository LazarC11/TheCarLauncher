package com.thecarlauncher.mib;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Square cover-art frame with a thin grey border; shows a large line icon when there is no art. */
public class MibArtBox extends View {
    private final Paint border = new Paint();
    private final Paint fill = new Paint();
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private Drawable placeholder;
    private float alpha = 1f;

    public MibArtBox(Context c) {
        super(c);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1, Mib.px(c, 1.4f)));
        border.setColor(0xFF9C9EA8);
        fill.setColor(0x33000000);
    }

    public void setPlaceholder(int res) {
        placeholder = getContext().getDrawable(res).mutate();
        placeholder.setTint(0xFF9496A0);
        invalidate();
    }

    public void setBitmap(Bitmap b) {
        if (b == bitmap) return;
        if (b != null && bitmap != null && !bitmap.isRecycled() && b.getWidth() == bitmap.getWidth()
                && b.getHeight() == bitmap.getHeight() && b.sameAs(bitmap)) {
            return;
        }
        bitmap = b;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(300);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                alpha = (Float) va.getAnimatedValue();
                invalidate();
            }
        });
        a.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int W = MeasureSpec.getSize(w), H = MeasureSpec.getSize(h);
        int s = Math.min(W, H);
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float sw = border.getStrokeWidth();
        c.drawRect(0, 0, w, h, fill);
        if (bitmap != null && !bitmap.isRecycled()) {
            float scale = Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
            int sw2 = Math.round(w / scale), sh2 = Math.round(h / scale);
            int sx = (bitmap.getWidth() - sw2) / 2, sy = (bitmap.getHeight() - sh2) / 2;
            art.setAlpha((int) (255 * alpha));
            c.drawBitmap(bitmap, new Rect(sx, sy, sx + sw2, sy + sh2), new RectF(sw, sw, w - sw, h - sw), art);
        } else if (placeholder != null) {
            int s = (int) (w * 0.62f);
            int l = (w - s) / 2, t = (h - s) / 2;
            placeholder.setBounds(l, t, l + s, t + s);
            placeholder.draw(c);
        }
        c.drawRect(sw / 2, sw / 2, w - sw / 2, h - sw / 2, border);
    }
}
