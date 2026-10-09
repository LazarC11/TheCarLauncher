package com.thecarlauncher.mib;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * Horizontal pager for the MENU pages: follows the finger 1:1, then glides to the page
 * with a speed matched to the fling. Pages move by GPU translation and are cached as
 * hardware layers while sliding, so the tiles are not redrawn every frame.
 */
public class MibPager extends ViewGroup {
    public interface Listener { void onPage(int page); }

    private int page;
    private float offset; // finger / animation offset from the current page, pixels
    private float downX, downY, lastX;
    private boolean dragging, layered;
    private final int slop, minFling;
    private VelocityTracker vt;
    private ValueAnimator anim;
    private Listener listener;

    public MibPager(Context c) {
        super(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = Math.max(vc.getScaledMinimumFlingVelocity() * 4, Mib.px(c, 300));
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

    /** Pages sit side by side at fixed positions; scrolling only changes their translation. */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int W = r - l;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * W, 0, (i + 1) * W, b - t);
        position();
    }

    private void position() {
        float x = -page * getWidth() + offset;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setTranslationX(x);
    }

    /** Hardware layers while moving: each page is drawn once and slid as a texture. */
    private void setLayers(boolean on) {
        if (layered == on) return;
        layered = on;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setLayerType(on ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, null);
        }
    }

    private void stopAnimation() {
        if (anim != null) {
            anim.removeAllListeners();
            anim.cancel();
            anim = null;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                // Catch a page that is still gliding: grab it where it is.
                boolean moving = anim != null;
                stopAnimation();
                dragging = moving;
                if (moving) {
                    startTracking(e);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    // Start from the slop point so the page does not jump.
                    lastX = e.getX();
                    startTracking(e);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
        }
        return false;
    }

    private void startTracking(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        setLayers(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopAnimation();
                lastX = e.getX();
                setLayers(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float delta = e.getX() - lastX;
                lastX = e.getX();
                boolean edge = (page == 0 && offset + delta > 0) || (page == getChildCount() - 1 && offset + delta < 0);
                // Rubber band at the first / last page.
                offset += edge ? delta * 0.35f : delta;
                position();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                vt.computeCurrentVelocity(1000);
                float v = vt.getXVelocity();
                vt.recycle();
                vt = null;
                int target = page;
                boolean fling = Math.abs(v) > minFling;
                if (((fling && v < 0) || (!fling && offset < -getWidth() / 3f)) && page < getChildCount() - 1) target++;
                else if (((fling && v > 0) || (!fling && offset > getWidth() / 3f)) && page > 0) target--;
                glide(target, Math.abs(v));
                dragging = false;
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    public void setPage(int target) {
        glide(Math.max(0, Math.min(getChildCount() - 1, target)), 0);
    }

    /** Animates to the target page; a faster fling gives a shorter, snappier glide. */
    private void glide(int target, float velocity) {
        stopAnimation();
        final float from = offset + (page - target) * getWidth();
        boolean changed = target != page;
        page = target;
        offset = from;
        float dist = Math.abs(from);
        if (dist < 1) {
            offset = 0;
            position();
            setLayers(false);
        } else {
            long dur = velocity > 0 ? (long) (dist / velocity * 1000 * 2.2f) : (long) (180 + 160 * dist / Math.max(1, getWidth()));
            dur = Math.max(160, Math.min(380, dur));
            setLayers(true);
            anim = ValueAnimator.ofFloat(from, 0f);
            anim.setDuration(dur);
            anim.setInterpolator(new DecelerateInterpolator(1.6f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    offset = (Float) va.getAnimatedValue();
                    position();
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    anim = null;
                    offset = 0;
                    position();
                    setLayers(false);
                }
            });
            anim.start();
        }
        if (changed && listener != null) listener.onPage(page);
    }
}
