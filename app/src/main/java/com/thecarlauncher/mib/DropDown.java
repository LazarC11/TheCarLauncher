package com.thecarlauncher.mib;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A window that drops down from the top of the screen with a title, a line of text and two keys
 * (e.g. "Compatibility update available" — Update / Later). One at a time.
 */
public final class DropDown {
    private static View current;

    private DropDown() {}

    public static void show(final FrameLayout root, String title, String text, String okLabel, final Runnable ok,
                            String laterLabel) {
        if (root == null) return;
        dismiss(root);
        Context c = root.getContext();
        final LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Mib.panel(c));
        int pad = Mib.px(c, 24);
        panel.setPadding(pad, Mib.px(c, 18), pad, Mib.px(c, 18));
        panel.setClickable(true);
        TextView t = Mib.text(c, title, 32, Mib.semibold(c), Mib.WHITE);
        panel.addView(t);
        TextView b = Mib.text(c, text, 24, Mib.regular(c), Mib.GREY);
        b.setSingleLine(false);
        b.setMaxLines(4);
        b.setPadding(0, Mib.px(c, 8), 0, Mib.px(c, 14));
        panel.addView(b);
        LinearLayout keys = new LinearLayout(c);
        keys.setGravity(Gravity.END);
        keys.addView(key(c, laterLabel, new Runnable() {
            public void run() { dismiss(root); }
        }));
        keys.addView(key(c, okLabel, new Runnable() {
            public void run() {
                dismiss(root);
                if (ok != null) ok.run();
            }
        }));
        panel.addView(keys);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lp.leftMargin = lp.rightMargin = Mib.px(c, 60);
        lp.topMargin = Mib.px(c, 8);
        root.addView(panel, lp);
        current = panel;
        panel.setTranslationY(-Mib.px(c, 400));
        panel.animate().translationY(0).setDuration(380)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
    }

    private static TextView key(Context c, String label, final Runnable r) {
        TextView k = Mib.text(c, label, 26, Mib.regular(c), Mib.WHITE);
        k.setGravity(Gravity.CENTER);
        k.setPadding(Mib.px(c, 24), 0, Mib.px(c, 24), 0);
        k.setBackground(Mib.box(c));
        Mib.pressText(k);
        k.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Mib.px(c, 56));
        l.leftMargin = Mib.px(c, 14);
        k.setMinWidth(Mib.px(c, 180));
        k.setLayoutParams(l);
        return k;
    }

    public static void dismiss(final FrameLayout root) {
        final View v = current;
        current = null;
        if (v == null || v.getParent() == null) return;
        v.animate().translationY(-v.getHeight() - Mib.px(v.getContext(), 20)).setDuration(260).withEndAction(new Runnable() {
            public void run() { root.removeView(v); }
        }).start();
    }

    public static boolean showing() { return current != null; }
}
