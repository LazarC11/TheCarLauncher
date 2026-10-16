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

    // The glide runs on the display's frame clock itself, not through Android's animators:
    // many head units ship with "Animator duration scale" off, which makes animators jump to
    // their end, and the page would just snap over without a transition.
    private boolean gliding;
    private float glideFrom;
    private long glideStart, glideDur;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!gliding) return;
            float t = Math.min(1f, (android.os.SystemClock.uptimeMillis() - glideStart) / (float) glideDur);
            // Decelerate (same curve as DecelerateInterpolator(1.4)).
            float e = (float) (1 - Math.pow(1 - t, 2 * 1.4));
            offset = glideFrom * (1 - e);
            position();
            if (t < 1f) {
                postOnAnimation(this);
            } else {
                gliding = false;
                offset = 0;
                position();
                setLayers(false);
            }
        }
    };

    private void stopAnimation() {
        if (anim != null) {
            anim.removeAllListeners();
            anim.cancel();
            anim = null;
        }
        if (gliding) {
            gliding = false;
            removeCallbacks(frame);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                // Catch a page that is still gliding: grab it where it is.
                boolean moving = anim != null || gliding;
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
                // Follow the finger 1:1; no stretching past the first / last page.
                float max = page > 0 ? getWidth() : 0, min = page < getChildCount() - 1 ? -getWidth() : 0;
                offset = Math.max(min, Math.min(max, offset + delta));
                position();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                vt.computeCurrentVelocity(1000);
                float v = vt.getXVelocity();
                vt.recycle();
                vt = null;
                // Only ever move the way the page was dragged: a flick in that direction or
                // a quarter of the screen turns the page, anything else settles back.
                int target = page;
                float w = getWidth();
                if (offset < 0 && page < getChildCount() - 1 && (offset < -w / 4f || v < -minFling)) target++;
                else if (offset > 0 && page > 0 && (offset > w / 4f || v > minFling)) target--;
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
        // Keep the pages where they are on screen: relative to the new page the offset is the
        // distance still to travel (x = -page * width + offset stays the same).
        final float from = offset + (target - page) * getWidth();
        boolean changed = target != page;
        page = target;
        offset = from;
        float dist = Math.abs(from);
        if (dist < 1) {
            offset = 0;
            position();
            setLayers(false);
        } else {
            // Continue at the finger's speed, easing out; steady timing for slow releases.
            long dur = (long) (220 + 120 * dist / Math.max(1, getWidth()));
            if (velocity > minFling) dur = Math.min(dur, (long) (dist / velocity * 1000 * 1.8f));
            dur = Math.max(180, Math.min(320, dur));
            setLayers(true);
            glideFrom = from;
            glideDur = dur;
            glideStart = android.os.SystemClock.uptimeMillis();
            gliding = true;
            removeCallbacks(frame);
            postOnAnimation(frame);
        }
        if (changed && listener != null) listener.onPage(page);
    }
}
