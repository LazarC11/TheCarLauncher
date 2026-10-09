package com.thecarlauncher.radio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import com.thecarlauncher.R;
import com.thecarlauncher.mib.Mib;

/** "Ručno" tuning: the factory FM dial scale with an accent needle; drag or tap to tune. */
public class FmScaleView extends View {
    public interface Listener {
        /** Called while dragging (final=false) and on release (final=true). */
        void onTune(int freq, boolean done);
    }

    // Positions measured on the factory scale image (958 px wide): 88 MHz at x=92, 108 MHz at x=885.
    private static final float IMG_W = 958f, IMG_H = 111f, X88 = 92f, X108 = 885f, DOT_Y = 70f;

    private final Drawable scale;
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int freq = RadioController.FM_MIN;
    private Listener listener;

    public FmScaleView(Context c) {
        super(c);
        scale = c.getResources().getDrawable(R.drawable.vw_radio_scale_fm);
    }

    public void setListener(Listener l) { listener = l; }

    public void setFreq(int f) {
        if (f == freq) return;
        freq = f;
        invalidate();
    }

    private float k() { return box.width() / IMG_W; }

    private float xOf(int f) {
        return box.left + (X88 + (f - 8800) / 2000f * (X108 - X88)) * k();
    }

    private int freqAt(float x) {
        float img = (x - box.left) / k();
        int f = Math.round(8800 + (img - X88) / (X108 - X88) * 2000f);
        f = Math.round(f / 10f) * 10;
        return Math.max(RadioController.FM_MIN, Math.min(RadioController.FM_MAX, f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float bw = w - getPaddingLeft() - getPaddingRight();
        float bh = bw * IMG_H / IMG_W;
        float top = (h - bh) / 2f;
        box.set(getPaddingLeft(), top, getPaddingLeft() + bw, top + bh);
        scale.setBounds(new Rect(Math.round(box.left), Math.round(box.top), Math.round(box.right), Math.round(box.bottom)));
    }

    @Override
    protected void onDraw(Canvas c) {
        scale.draw(c);
        float x = xOf(freq);
        float w = Math.max(3, Mib.px(getContext(), 4));
        needle.setColor(Mib.BLUE);
        c.drawRect(x - w / 2, box.top - Mib.px(getContext(), 14), x + w / 2, box.top + DOT_Y * k() + Mib.px(getContext(), 18), needle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int f = freqAt(e.getX());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
            case MotionEvent.ACTION_MOVE:
                setFreq(f);
                if (listener != null) listener.onTune(f, false);
                return true;
            case MotionEvent.ACTION_UP:
                setFreq(f);
                if (listener != null) listener.onTune(f, true);
                return true;
            default:
                return true;
        }
    }
}
