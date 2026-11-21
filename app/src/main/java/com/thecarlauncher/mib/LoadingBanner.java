package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drop-down loading banner: full width, two status bars tall, slides down from the top with the
 * factory spinner (tinted with the ambient colour) and a line saying what is happening.
 * Any part of the launcher calls {@link #start}, {@link #update} and {@link #stop} with its own key.
 * {@link #notice} drops the same banner down with a factory icon instead of the spinner (vehicle
 * events, "update available"); tapping it opens what it is about.
 */
public class LoadingBanner extends FrameLayout {
    private static final int[] FRAMES = {R.drawable.ui_spin_00, R.drawable.ui_spin_01, R.drawable.ui_spin_02,
            R.drawable.ui_spin_03, R.drawable.ui_spin_04, R.drawable.ui_spin_05, R.drawable.ui_spin_06,
            R.drawable.ui_spin_07, R.drawable.ui_spin_08, R.drawable.ui_spin_09, R.drawable.ui_spin_10,
            R.drawable.ui_spin_11};

    private static LoadingBanner host;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** A running job or a notice: its line, and for notices a factory icon and a tap action. */
    private static final class Job {
        final String message;
        final int icon;
        final Runnable tap;

        Job(String message, int icon, Runnable tap) {
            this.message = message;
            this.icon = icon;
            this.tap = tap;
        }
    }

    /** Running jobs, oldest first; the newest one is shown. */
    private static final Map<String, Job> JOBS = new LinkedHashMap<>();
    private Job showing;

    private final ImageView spinner;
    private final TextView text;
    private final android.view.View infoKey, closeKey;
    private AnimationDrawable anim;
    private boolean shown;

    public LoadingBanner(Context c) {
        super(c);
        // Solid black with a very thin line along the bottom edge only (the stroke on the
        // other three sides is pushed outside the view).
        int sw = Math.max(1, Mib.px(c, 1));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setStroke(sw, 0xFF55565C);
        setBackground(new android.graphics.drawable.InsetDrawable(bg, -sw, -sw, -sw, 0));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(c, 30), 0, Mib.px(c, 10), 0);
        spinner = new ImageView(c);
        row.addView(spinner, new LinearLayout.LayoutParams(Mib.px(c, 52), Mib.px(c, 52)));
        text = Mib.text(c, "", 26, Mib.semibold(c), Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Mib.px(c, 24);
        row.addView(text, tl);
        // Notices: "i" opens what the notice is about, "x" dismisses it — the factory notice keys
        // (bare icons like the soft keys, thin grey separators, the key glow when pressed).
        infoKey = key(c, com.thecarlauncher.R.drawable.ui_key_info, new Runnable() {
            public void run() { closeShowing(true); }
        });
        closeKey = key(c, com.thecarlauncher.R.drawable.ui_key_close, new Runnable() {
            public void run() { closeShowing(false); }
        });
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(Mib.px(c, 104), LayoutParams.MATCH_PARENT);
        row.addView(separator(c), new LinearLayout.LayoutParams(Math.max(1, Mib.px(c, 1)), Mib.px(c, 60)));
        row.addView(infoKey, kl);
        row.addView(separator(c), new LinearLayout.LayoutParams(Math.max(1, Mib.px(c, 1)), Mib.px(c, 60)));
        row.addView(closeKey, new LinearLayout.LayoutParams(kl));
        infoKey.setVisibility(GONE);
        closeKey.setVisibility(GONE);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setVisibility(GONE);
        // Taps pass through (long jobs such as map downloads must not block the screen); on a
        // notice the banner takes taps and a tap anywhere does what "i" does.
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (showing != null && showing.tap != null) closeShowing(true);
            }
        });
        setClickable(false);
    }

    private static android.view.View separator(Context c) {
        android.view.View v = new android.view.View(c);
        v.setBackgroundColor(0xFF848484); // factory soft-key separator
        v.setTag("sep");
        return v;
    }

    private static android.view.View key(Context c, int icon, final Runnable r) {
        FrameLayout k = new FrameLayout(c);
        k.setBackground(Mib.keyPressed(c));
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        k.addView(iv, new FrameLayout.LayoutParams(Mib.px(c, 78), Mib.px(c, 52), Gravity.CENTER));
        k.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(android.view.View v) { r.run(); }
        });
        return k;
    }

    /** Closes the notice on show; with {@code open}, runs what it is about. */
    private void closeShowing(boolean open) {
        final Job j = showing;
        if (j == null) return;
        for (Map.Entry<String, Job> e : JOBS.entrySet()) {
            if (e.getValue() == j) {
                stop(e.getKey());
                break;
            }
        }
        if (open && j.tap != null) j.tap.run();
    }

    /** Serbian / English text for the current language (works without a Context at hand). */
    public static String t(String sr, String en) {
        return host == null ? sr : Mib.t(host.getContext(), sr, en);
    }

    /** Height of the banner: two status bars. */
    public static int height(Context c) { return Mib.px(c, 104); }

    public static void attach(LoadingBanner b) {
        host = b;
        b.refresh();
    }

    public static void start(final String key, final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                JOBS.remove(key);
                JOBS.put(key, new Job(message, 0, null));
                if (host != null) host.refresh();
            }
        });
    }

    public static void update(final String key, final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Job j = JOBS.get(key);
                if (j == null) return;
                JOBS.put(key, new Job(message, j.icon, j.tap));
                if (host != null) host.refresh();
            }
        });
    }

    /** Shows a message for a moment (e.g. "connected"), then removes it. */
    public static void flash(final String key, String message, long ms) {
        start(key, message);
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() { stop(key); }
        }, ms);
    }

    /**
     * Drops the banner down with a factory icon (shown in its own colours) and a line, for
     * {@code ms}; a tap runs {@code tap} (may be null) and closes it.
     */
    public static void notice(final String key, final int icon, final String message, long ms, final Runnable tap) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                JOBS.remove(key);
                JOBS.put(key, new Job(message, icon, tap));
                if (host != null) host.refresh();
            }
        });
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() { stop(key); }
        }, ms);
    }

    public static void stop(final String key) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (JOBS.remove(key) != null && host != null) host.refresh();
            }
        });
    }

    private void refresh() {
        Job job = null;
        for (Job j : JOBS.values()) job = j;
        if (job != null) {
            text.setText(job.message);
            boolean was = shown;
            Job before = showing;
            showing = job;
            boolean notice = job.icon != 0;
            setClickable(notice);
            infoKey.setVisibility(notice && job.tap != null ? VISIBLE : GONE);
            closeKey.setVisibility(notice ? VISIBLE : GONE);
            ViewGroup row = (ViewGroup) closeKey.getParent();
            for (int i = 0; i < row.getChildCount(); i++) {
                android.view.View s2 = row.getChildAt(i);
                if (!"sep".equals(s2.getTag())) continue;
                // The separator before a hidden key goes with it.
                android.view.View next = row.getChildAt(i + 1);
                s2.setVisibility(next != null && next.getVisibility() == VISIBLE ? VISIBLE : GONE);
            }
            if (!was) slideDown();
            else if (before == null || before.icon != job.icon) showIcon();
        } else if (shown) {
            showing = null;
            setClickable(false);
            slideUp();
        }
    }

    /** The factory spinner for jobs, the notice's own icon for notices. */
    private void showIcon() {
        if (anim != null) anim.stop();
        if (showing != null && showing.icon != 0) {
            spinner.setImageResource(showing.icon);
            return;
        }
        anim = new AnimationDrawable();
        // The frames run anti-clockwise; play them backwards so the spinner turns clockwise.
        for (int i = FRAMES.length - 1; i >= 0; i--) anim.addFrame(getContext().getDrawable(FRAMES[i]).mutate(), 60);
        anim.setOneShot(false);
        anim.setColorFilter(new PorterDuffColorFilter(Mib.BLUE, PorterDuff.Mode.SRC_IN));
        spinner.setImageDrawable(anim);
        anim.start();
    }

    private void slideDown() {
        shown = true;
        showIcon();
        setVisibility(VISIBLE);
        animate().cancel();
        setTranslationY(-height(getContext()));
        animate().translationY(0).setDuration(220).withEndAction(null).start();
    }

    private void slideUp() {
        shown = false;
        animate().cancel();
        animate().translationY(-height(getContext())).setDuration(220).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (shown) return;
                setVisibility(GONE);
                if (anim != null) anim.stop();
            }
        }).start();
    }
}
