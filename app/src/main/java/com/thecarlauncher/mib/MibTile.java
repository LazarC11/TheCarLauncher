package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** MENU tile: gradient square with a line icon, blue underline and a bold caption beneath. */
public class MibTile extends ViewGroup {
    final FrameLayout box;
    final ImageView icon;
    final View line;
    final TextView label;
    private final boolean appIcon;
    private boolean artwork;

    public MibTile(Context c, String text, int iconRes) {
        this(c, text, c.getDrawable(iconRes), false);
    }

    public MibTile(Context c, String text, Drawable d, boolean appIcon) {
        super(c);
        this.appIcon = appIcon;
        box = new FrameLayout(c);
        box.setBackground(Mib.tile(c));
        box.setDuplicateParentStateEnabled(true);
        icon = new ImageView(c);
        icon.setImageDrawable(d);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!appIcon) {
            icon.setImageTintList(Mib.pressTint(Mib.WHITE));
        } else {
            // Artwork and app icons keep their colours; pressed they take on the ambient colour.
            icon.setImageTintMode(android.graphics.PorterDuff.Mode.MULTIPLY);
            icon.setImageTintList(Mib.pressTint(Mib.WHITE));
        }
        box.addView(icon, new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
        addView(box);
        line = new View(c);
        line.setBackgroundColor(Mib.BLUE);
        addView(line);
        label = Mib.text(c, text, 27, Mib.bold(c), Mib.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(Mib.px(c, 2), 0, Mib.px(c, 1), 0xAA000000);
        addView(label);
        setClickable(true);
    }

    /** MENU tile using the factory 235×154 tile artwork (drawn edge to edge, untinted). */
    public static MibTile artwork(Context c, String text, int res) {
        MibTile t = new MibTile(c, text, c.getDrawable(res), true);
        t.artwork = true;
        t.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // The artwork carries generous padding; the factory tiles show the glyph a little larger.
        t.icon.setScaleX(1.15f);
        t.icon.setScaleY(1.15f);
        return t;
    }

    public void setText(String s) { label.setText(s); }

    public void setLabelSize(float refSize) { Mib.size(label, refSize); }

    /** Height of the caption area under the tile. */
    static int captionHeight(Context c) {
        return Mib.px(c, 50);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int W = MeasureSpec.getSize(w), H = MeasureSpec.getSize(h);
        int lineH = Math.max(2, Mib.px(getContext(), 3));
        int boxH = H - captionHeight(getContext()) - lineH;
        box.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(boxH, MeasureSpec.EXACTLY));
        int is = (int) (Math.min(W, boxH) * (appIcon ? 0.5f : 0.66f));
        int iw = artwork ? W : is, ih = artwork ? boxH : is;
        FrameLayout.LayoutParams ip = (FrameLayout.LayoutParams) icon.getLayoutParams();
        if (ip.width != iw || ip.height != ih) {
            ip.width = iw;
            ip.height = ih;
            box.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(boxH, MeasureSpec.EXACTLY));
        }
        line.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(lineH, MeasureSpec.EXACTLY));
        label.measure(MeasureSpec.makeMeasureSpec(W + Mib.px(getContext(), 40), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(captionHeight(getContext()), MeasureSpec.EXACTLY));
        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int W = r - l;
        int boxH = box.getMeasuredHeight();
        box.layout(0, 0, W, boxH);
        line.layout(0, boxH, W, boxH + line.getMeasuredHeight());
        int lw = label.getMeasuredWidth();
        int top = boxH + line.getMeasuredHeight();
        label.layout((W - lw) / 2, top, (W - lw) / 2 + lw, top + label.getMeasuredHeight());
    }
}
