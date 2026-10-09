package com.thecarlauncher.mib;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The soft-key strip along the bottom of every MIB screen:
 * label on top, line icon underneath, thin grey separators, a blue rule above.
 */
public class MibBar extends LinearLayout {
    public static final int SLOTS = 7;

    /** One soft key. */
    public static class Key extends LinearLayout {
        final TextView label;
        final ImageView icon;

        Key(Context c, String text, int iconRes) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setBackground(Mib.keyPressed());
            label = Mib.text(c, text, 25, Mib.semibold(c), Mib.WHITE);
            label.setGravity(Gravity.CENTER);
            addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            icon = Mib.icon(c, iconRes, Mib.WHITE);
            LayoutParams ip = new LayoutParams(Mib.px(c, 46), Mib.px(c, 46));
            ip.topMargin = Mib.px(c, 4);
            addView(icon, ip);
        }

        public Key setLabel(String s) {
            label.setText(s);
            return this;
        }

        public Key setIcon(int res) {
            icon.setImageResource(res);
            icon.setImageTintList(ColorStateList.valueOf(Mib.WHITE));
            return this;
        }

        /** App icons keep their own colours. */
        public Key setIcon(android.graphics.drawable.Drawable d) {
            icon.setImageDrawable(d);
            icon.setImageTintList(null);
            return this;
        }

        public Key setActive(boolean on) {
            label.setTextColor(on ? Mib.BLUE : Mib.WHITE);
            icon.setImageTintList(icon.getImageTintList() == null ? null
                    : ColorStateList.valueOf(on ? Mib.BLUE : Mib.WHITE));
            return this;
        }

        @Override
        public void setEnabled(boolean e) {
            super.setEnabled(e);
            setAlpha(e ? 1f : 0.35f);
        }
    }

    private boolean lastWasKey;

    public MibBar(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setBackground(Mib.bar(c));
        int pad = Mib.px(c, 10);
        setPadding(pad, Mib.px(c, 4), pad, Mib.px(c, 2));
        setMotionEventSplittingEnabled(false);
    }

    public static int height(Context c) {
        return Mib.px(c, 104);
    }

    public Key add(String label, int icon, View.OnClickListener l) {
        if (lastWasKey) divider();
        Key k = new Key(getContext(), label, icon);
        k.setOnClickListener(l);
        addView(k, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        lastWasKey = true;
        return k;
    }

    /** Empty slot (keeps the MIB grid: Setup always sits in the right-most slot). */
    public void gap(int slots) {
        if (lastWasKey) divider();
        View v = new View(getContext());
        addView(v, new LayoutParams(0, 1, slots));
        lastWasKey = false;
    }

    private void divider() {
        FrameLayout wrap = new FrameLayout(getContext());
        View line = new View(getContext());
        line.setBackgroundColor(0xFF9A9CA6);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, Mib.px(getContext(), 1.4f)),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        lp.topMargin = Mib.px(getContext(), 12);
        lp.bottomMargin = Mib.px(getContext(), 10);
        wrap.addView(line, lp);
        addView(wrap, new LayoutParams(Math.max(1, Mib.px(getContext(), 2)), ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
