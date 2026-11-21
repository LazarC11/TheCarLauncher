package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Bordered two-column picker that rises out of a soft key, with the little pointer
 * notch — the MIB "Izvor" (source) popup.
 */
public class MibPopup {
    public static class Item {
        final String label;
        final Drawable icon;
        final boolean tint, selected;
        final Runnable action;

        public Item(String label, Drawable icon, boolean tint, boolean selected, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.tint = tint;
            this.selected = selected;
            this.action = action;
        }
    }

    /** Separator lines: the grey of the soft-key separators. */
    private static final int SEP = 0xFF848484;

    private final Context c;
    private final List<Item> items = new ArrayList<>();
    private int columns = 2;

    public MibPopup(Context c) {
        this.c = c;
    }

    public MibPopup add(String label, int iconRes, boolean selected, Runnable action) {
        items.add(new Item(label, c.getDrawable(iconRes), true, selected, action));
        return this;
    }

    public MibPopup add(String label, Drawable icon, boolean tint, boolean selected, Runnable action) {
        items.add(new Item(label, icon, tint, selected, action));
        return this;
    }

    public MibPopup columns(int n) {
        columns = n;
        return this;
    }

    /** Shows the popup above {@code key}, inside {@code root}, with its pointer on the key. */
    public void show(View root, View key) {
        int rowH = Mib.px(c, 78);
        // The factory pop-up body: black, thin grey frame, soft shadow 8 px wide around it.
        int shadow = Mib.px(c, 8);
        int rows = (items.size() + columns - 1) / columns;
        int notch = Mib.px(c, 22);
        int width = Math.min(root.getWidth() - Mib.px(c, 24), Mib.px(c, columns == 1 ? 480 : 700)) + 2 * shadow;
        int maxRows = Math.max(1, (root.getHeight() - MibBar.height(c) - notch - Mib.px(c, 60)) / rowH);

        final PopupWindow pw = new PopupWindow(c);
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Mib.skin(c, com.thecarlauncher.R.drawable.ui_skin_popup, 12, 12));
        int pad = shadow + Math.max(1, Mib.px(c, 1));
        panel.setPadding(pad, pad, pad, pad);

        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                View sep = new View(c);
                sep.setBackgroundColor(SEP);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Mib.px(c, 1.5f)));
                sl.leftMargin = Mib.px(c, 6);
                sl.rightMargin = Mib.px(c, 6);
                list.addView(sep, sl);
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < columns; col++) {
                int i = r * columns + col;
                if (col > 0) {
                    FrameLayout dw = new FrameLayout(c);
                    View d = new View(c);
                    d.setBackgroundColor(SEP);
                    FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Math.max(1, Mib.px(c, 1.5f)),
                            Mib.px(c, 38), Gravity.CENTER);
                    dw.addView(d, dl);
                    row.addView(dw, new LinearLayout.LayoutParams(Mib.px(c, 6), ViewGroup.LayoutParams.MATCH_PARENT));
                }
                View cell = i < items.size() ? cell(items.get(i), pw) : new View(c);
                row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));
        }
        if (rows > maxRows) {
            android.widget.ScrollView sv = new android.widget.ScrollView(c);
            sv.addView(list);
            panel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxRows * rowH));
        } else {
            panel.addView(list);
        }

        int[] rootLoc = new int[2], keyLoc = new int[2];
        root.getLocationInWindow(rootLoc);
        key.getLocationInWindow(keyLoc);
        int keyCenter = keyLoc[0] - rootLoc[0] + key.getWidth() / 2;
        int left = Math.max(Mib.px(c, 12) - shadow,
                Math.min(keyCenter - Mib.px(c, 60) - shadow, root.getWidth() - width - Mib.px(c, 12) + shadow));

        FrameLayout wrap = new FrameLayout(c);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.bottomMargin = notch;
        wrap.addView(panel, pl);
        Notch n = new Notch(c, keyCenter - left);
        // The pointer starts on the panel's frame (above its shadow) so its black top hides that
        // piece of the frame.
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(width, notch + shadow + Mib.px(c, 3), Gravity.BOTTOM);
        wrap.addView(n, nl);
        wrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pw.dismiss();
            }
        });

        pw.setContentView(wrap);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setWidth(width);
        pw.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        pw.setFocusable(true);
        pw.setOutsideTouchable(true);
        pw.setAnimationStyle(android.R.style.Animation_Dialog);
        wrap.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
        // Factory: the panel's frame sits on the soft-key bar's line, the pointer reaches into the key.
        int y = keyLoc[1] - wrap.getMeasuredHeight() + notch + shadow;
        pw.showAtLocation(root, Gravity.TOP | Gravity.LEFT, rootLoc[0] + left, y);
    }

    private View cell(final Item it, final PopupWindow pw) {
        LinearLayout cell = new LinearLayout(c);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(Mib.px(c, 18), 0, Mib.px(c, 10), 0);
        cell.setBackground(Mib.keyPressed(c));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(it.icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Factory: everything white; the source playing now in bold, the others regular.
        if (it.tint) iv.setImageTintList(android.content.res.ColorStateList.valueOf(Mib.WHITE));
        cell.addView(iv, new LinearLayout.LayoutParams(Mib.px(c, 42), Mib.px(c, 42)));
        TextView t = Mib.text(c, it.label, 35, it.selected ? Mib.semibold(c) : Mib.regular(c), Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Mib.px(c, 20);
        cell.addView(t, tl);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pw.dismiss();
                if (it.action != null) it.action.run();
            }
        });
        return cell;
    }

    /**
     * The small downward pointer joining the panel to its soft key: the factory pointer (black,
     * white edges, soft shadow), whose black top covers the panel's frame where they meet.
     */
    private static class Notch extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Bitmap bmp;
        private final android.graphics.RectF dst = new android.graphics.RectF();
        private final int x;

        Notch(Context c, int x) {
            super(c);
            this.x = x;
            bmp = Mib.skinBitmap(c, com.thecarlauncher.R.drawable.ui_skin_notch);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float h = getHeight(), w = h * bmp.getWidth() / (float) bmp.getHeight();
            dst.set(x - w / 2f, 0, x + w / 2f, h);
            cv.drawBitmap(bmp, null, dst, paint);
        }
    }
}
