package com.thecarlauncher.mib;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Minimal horizontal pager for the MENU pages (swipe left/right, snaps per page). */
public class MibPager extends ViewGroup {
    public interface Listener { void onPage(int page); }

    private int page;
    private float offset; // in pixels, while dragging/animating
    private float downX, downY, lastX;
    private boolean dragging;
    private final int slop;
    private VelocityTracker vt;
    private Listener listener;

    public MibPager(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public void setListener(Listener l) { listener = l; }
    public int page() { return page; }

    @Override
    protected void onMeasure(int w, int h) {
        int W = MeasureSpec.getSize(w), H = MeasureSpec.getSize(h);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int W = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            int x = Math.round((i - page) * W + offset);
            getChildAt(i).layout(x, 0, x + W, b - t);
        }
    }

    private void position() {
        int W = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.setTranslationX(0);
            int x = Math.round((i - page) * W + offset);
            v.offsetLeftAndRight(x - v.getLeft());
        }
        invalidate();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    lastX = e.getX();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                offset += e.getX() - lastX;
                lastX = e.getX();
                if ((page == 0 && offset > 0) || (page == getChildCount() - 1 && offset < 0)) offset *= 0.5f;
                position();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                float v = vt.getXVelocity();
                vt.recycle();
                vt = null;
                int target = page;
                if ((offset < -getWidth() / 5f || v < -1200) && page < getChildCount() - 1) target++;
                else if ((offset > getWidth() / 5f || v > 1200) && page > 0) target--;
                settle(target);
                dragging = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    public void setPage(int target) {
        settle(Math.max(0, Math.min(getChildCount() - 1, target)));
    }

    private void settle(final int target) {
        final float from = offset + (page - target) * getWidth();
        page = target;
        offset = from;
        ValueAnimator a = ValueAnimator.ofFloat(from, 0f);
        a.setDuration(260);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                offset = (Float) va.getAnimatedValue();
                position();
            }
        });
        a.start();
        if (listener != null) listener.onPage(page);
    }
}
