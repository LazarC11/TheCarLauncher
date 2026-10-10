package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Thin MIB progress line; optionally draggable for seeking. */
public class MibProgress extends View {
    public interface SeekListener { void onSeek(float fraction); }

    private final Paint track = new Paint();
    private final Paint fill = new Paint();
    private float fraction;
    private SeekListener seek;
    private boolean dragging;

    public MibProgress(Context c) {
        super(c);
        track.setColor(0xFF4A4B56);
        fill.setColor(Mib.BLUE);
    }

    public void setSeekListener(SeekListener l) { seek = l; }

    public void setFraction(float f) {
        if (dragging) return;
        fraction = Math.max(0, Math.min(1, f));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = Math.max(2, Mib.px(getContext(), 4));
        float top = (getHeight() - h) / 2f;
        c.drawRect(0, top, getWidth(), top + h, track);
        c.drawRect(0, top, getWidth() * fraction, top + h, fill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (seek == null || !isEnabled()) return false;
        fraction = Math.max(0, Math.min(1, e.getX() / getWidth()));
        invalidate();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_UP:
                dragging = false;
                seek.onSeek(fraction);
                break;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
        }
        return true;
    }
}
