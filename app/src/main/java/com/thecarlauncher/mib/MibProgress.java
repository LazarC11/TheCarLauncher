package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * MIB progress line: the played part a solid ambient-coloured bar, the rest a row of white
 * dots, like the factory media page; optionally draggable for seeking.
 */
public class MibProgress extends View {
    public interface SeekListener { void onSeek(float fraction); }

    private final Paint track = new Paint();
    private final Paint fill = new Paint();
    private float fraction;
    private SeekListener seek;
    private boolean dragging;

    public MibProgress(Context c) {
        super(c);
        track.setColor(0xFFE6E7EB);
        track.setAntiAlias(true);
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
        float h = Math.max(3, Mib.px(getContext(), 6));
        float top = (getHeight() - h) / 2f, mid = top + h / 2f;
        float end = getWidth() * fraction;
        fill.setColor(Mib.BLUE);
        c.drawRect(0, top, end, top + h, fill);
        // Unplayed part: dots of the line's thickness, one dot-width apart.
        float r = h * 0.42f, step = h * 2f;
        for (float x = end + step / 2f; x <= getWidth() - r; x += step) c.drawCircle(x, mid, r, track);
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
