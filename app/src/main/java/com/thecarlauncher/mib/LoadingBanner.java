package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
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
 */
public class LoadingBanner extends FrameLayout {
    private static final int[] FRAMES = {R.drawable.ui_spin_00, R.drawable.ui_spin_01, R.drawable.ui_spin_02,
            R.drawable.ui_spin_03, R.drawable.ui_spin_04, R.drawable.ui_spin_05, R.drawable.ui_spin_06,
            R.drawable.ui_spin_07, R.drawable.ui_spin_08, R.drawable.ui_spin_09, R.drawable.ui_spin_10,
            R.drawable.ui_spin_11};

    private static LoadingBanner host;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Running jobs, oldest first; the newest one is shown. */
    private static final Map<String, String> JOBS = new LinkedHashMap<>();

    private final ImageView spinner;
    private final TextView text;
    private final Paint rule = new Paint();
    private AnimationDrawable anim;
    private boolean shown;

    public LoadingBanner(Context c) {
        super(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF141417, 0xFF060607});
        setBackground(bg);
        setWillNotDraw(false);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(c, 30), 0, Mib.px(c, 30), 0);
        spinner = new ImageView(c);
        row.addView(spinner, new LinearLayout.LayoutParams(Mib.px(c, 52), Mib.px(c, 52)));
        text = Mib.text(c, "", 26, Mib.semibold(c), Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Mib.px(c, 24);
        row.addView(text, tl);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setVisibility(GONE);
        // Taps pass through: long jobs (map downloads) must not block the screen.
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
                JOBS.put(key, message);
                if (host != null) host.refresh();
            }
        });
    }

    public static void update(final String key, final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (!JOBS.containsKey(key)) return;
                JOBS.put(key, message);
                if (host != null) host.refresh();
            }
        });
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
        String msg = null;
        for (String m : JOBS.values()) msg = m;
        if (msg != null) {
            text.setText(msg);
            if (!shown) slideDown();
        } else if (shown) {
            slideUp();
        }
    }

    private void slideDown() {
        shown = true;
        anim = new AnimationDrawable();
        for (int f : FRAMES) anim.addFrame(getContext().getDrawable(f).mutate(), 60);
        anim.setOneShot(false);
        anim.setColorFilter(new PorterDuffColorFilter(Mib.BLUE, PorterDuff.Mode.SRC_IN));
        spinner.setImageDrawable(anim);
        anim.start();
        rule.setColor(Mib.BLUE);
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

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        // Thin ambient rule along the bottom edge, like the factory pop-downs.
        int h = Math.max(2, Mib.px(getContext(), 2));
        c.drawRect(0, getHeight() - h, getWidth(), getHeight(), rule);
    }
}
