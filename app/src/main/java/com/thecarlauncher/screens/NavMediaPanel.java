package com.thecarlauncher.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.R;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibArtBox;
import com.thecarlauncher.mib.MibProgress;
import com.thecarlauncher.util.Ui;

/**
 * Media controls shown over the navigation map: a small pop-up widget (cover, song, artist,
 * previous / play / next) or, in split screen, a full-height column beside the map.
 */
final class NavMediaPanel extends LinearLayout {
    interface Actions {
        void onSplit();
        void onClose();
        void onTouched();
    }

    private final boolean split;
    private final MibArtBox art;
    private final TextView title, artist, pos, remain;
    private final MibProgress progress;
    private final ImageButtonish play;

    NavMediaPanel(Context c, boolean split, final Actions actions) {
        super(c);
        this.split = split;
        setOrientation(split ? VERTICAL : HORIZONTAL);
        setGravity(split ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        setClickable(true);
        if (split) {
            setBackgroundColor(0xFF000000);
            setPadding(Mib.px(c, 24), Mib.px(c, 18), Mib.px(c, 24), Mib.px(c, 10));
        } else {
            setBackground(Mib.panel(c));
            setPadding(Mib.px(c, 12), Mib.px(c, 12), Mib.px(c, 12), Mib.px(c, 12));
        }

        art = new MibArtBox(c);
        int cover = Mib.px(c, split ? 200 : 116);
        LayoutParams cl = new LayoutParams(cover, cover);
        if (split) cl.bottomMargin = Mib.px(c, 14);
        addView(art, cl);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setGravity(split ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT);
        LayoutParams colLp = split ? new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                : new LayoutParams(Mib.px(c, 330), LayoutParams.WRAP_CONTENT);
        if (!split) colLp.leftMargin = Mib.px(c, 16);
        addView(col, colLp);

        title = Mib.text(c, "", split ? 28 : 25, Mib.semibold(c), Mib.WHITE);
        artist = Mib.text(c, "", split ? 23 : 20, Mib.regular(c), Mib.GREY);
        if (split) {
            title.setGravity(Gravity.CENTER);
            artist.setGravity(Gravity.CENTER);
        }
        col.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        col.addView(artist, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout prog = new LinearLayout(c);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        pos = Mib.text(c, "", 17, Mib.regular(c), Mib.GREY);
        remain = Mib.text(c, "", 17, Mib.regular(c), Mib.GREY);
        progress = new MibProgress(c);
        progress.setSeekListener(new MibProgress.SeekListener() {
            @Override
            public void onSeek(float f) {
                MediaHub hub = MediaHub.get(getContext());
                if (hub.canSeek()) hub.seekTo((long) (hub.duration() * f));
                actions.onTouched();
            }
        });
        if (split) prog.addView(pos, new LayoutParams(Mib.px(c, 56), LayoutParams.WRAP_CONTENT));
        prog.addView(progress, new LayoutParams(0, Mib.px(c, 26), 1f));
        if (split) {
            remain.setGravity(Gravity.RIGHT);
            prog.addView(remain, new LayoutParams(Mib.px(c, 64), LayoutParams.WRAP_CONTENT));
        }
        LayoutParams pl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        pl.topMargin = Mib.px(c, split ? 12 : 4);
        col.addView(prog, pl);
        if (split) {
            View spacer = new View(c);
            col.addView(spacer, new LayoutParams(1, 0, 1f));
        }

        LinearLayout keys = new LinearLayout(c);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        int k = Mib.px(c, split ? 70 : 60);
        keys.addView(key(c, R.drawable.ui_key_prev, k, new Runnable() {
            public void run() { MediaHub.get(getContext()).previous(); actions.onTouched(); }
        }));
        play = key(c, R.drawable.ui_key_play, k, new Runnable() {
            public void run() { MediaHub.get(getContext()).playPause(); actions.onTouched(); }
        });
        keys.addView(play);
        keys.addView(key(c, R.drawable.ui_key_next, k, new Runnable() {
            public void run() { MediaHub.get(getContext()).next(); actions.onTouched(); }
        }));
        View gap = new View(c);
        keys.addView(gap, new LayoutParams(0, 1, 1f));
        if (!split) {
            keys.addView(key(c, R.drawable.ui_ic_split, k, new Runnable() {
                public void run() { actions.onSplit(); }
            }));
        }
        keys.addView(key(c, R.drawable.ui_ic_close, k, new Runnable() {
            public void run() { actions.onClose(); }
        }));
        LayoutParams kl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        kl.topMargin = Mib.px(c, split ? 6 : 2);
        col.addView(keys, kl);
        update();
    }

    /** A soft-key-like square button with a white icon. */
    private static final class ImageButtonish extends FrameLayout {
        final android.widget.ImageView icon;

        ImageButtonish(Context c, int res, int size) {
            super(c);
            setBackground(Mib.keyPressed());
            icon = Mib.icon(c, res, Mib.WHITE);
            addView(icon, new LayoutParams(size * 2 / 3, size * 2 / 3, Gravity.CENTER));
        }
    }

    private ImageButtonish key(Context c, int res, int size, final Runnable r) {
        ImageButtonish b = new ImageButtonish(c, res, size);
        b.setLayoutParams(new LayoutParams(size, size));
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        return b;
    }

    /** Refreshes song, cover, play state and progress. */
    void update() {
        Context c = getContext();
        MediaHub hub = MediaHub.get(c);
        boolean info = hub.hasTrackInfo();
        String t = info ? hub.title() : null;
        title.setText(t != null ? t : Mib.t(c, "Nema muzike", "No music"));
        String a = info ? hub.artist() : null;
        artist.setText(a != null ? a : hub.isRadio() ? "FM" : "");
        art.setCover(hub.isRadio() ? R.drawable.ui_cover_radio
                : hub.isBluetooth() && !info ? R.drawable.ui_cover_bt : R.drawable.ui_cover_music);
        art.setBitmap(hub.art());
        boolean playing = hub.isPlaying();
        play.icon.setImageResource(playing ? R.drawable.ui_key_pause : R.drawable.ui_key_play);
        long d = hub.duration(), p = hub.position();
        progress.setVisibility(d > 0 ? VISIBLE : INVISIBLE);
        progress.setFraction(d > 0 ? (float) p / d : 0f);
        if (split) {
            pos.setText(d > 0 ? Ui.time(p) : "");
            remain.setText(d > 0 ? "-" + Ui.time(Math.max(0, d - p)) : "");
        }
    }
}
