package com.thecarlauncher.screens;

import android.graphics.Bitmap;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.NavHelper;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Theme;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.views.AlbumArtView;
import com.thecarlauncher.views.MapTileView;
import com.thecarlauncher.views.SpeedGaugeView;
import com.thecarlauncher.views.VisualizerView;

import java.util.List;

/** Dashboard: map, now playing, speed and favourite apps at a glance. */
public class HomeScreen extends Screen implements MediaHub.Listener, LocationTracker.Listener,
        AppRepository.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());
    private MapTileView map;
    private TextView navStatus, navHeading, title, artist, source;
    private ImageView sourceIcon, playButton;
    private AlbumArtView art;
    private VisualizerView viz;
    private ProgressBar progress;
    private SpeedGaugeView gauge;
    private GridLayout favGrid;
    private boolean visible;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            main.postDelayed(this, 1000);
        }
    };

    public HomeScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return "Home"; }

    @Override
    protected View create(ViewGroup parent) {
        View v = LayoutInflater.from(act).inflate(R.layout.screen_home, parent, false);
        map = v.findViewById(R.id.home_map);
        map.setAccent(accent);
        map.setSource(Prefs.tiles(act));
        map.setZoom(Prefs.integer(act, Prefs.MAP_ZOOM, 16));
        navStatus = v.findViewById(R.id.home_nav_status);
        navHeading = v.findViewById(R.id.home_nav_heading);
        title = v.findViewById(R.id.home_media_title);
        artist = v.findViewById(R.id.home_media_artist);
        source = v.findViewById(R.id.home_media_src);
        sourceIcon = v.findViewById(R.id.home_media_src_icon);
        art = v.findViewById(R.id.home_art);
        art.setAccent(accent);
        viz = v.findViewById(R.id.home_viz);
        viz.setAccent(accent);
        progress = v.findViewById(R.id.home_media_progress);
        gauge = v.findViewById(R.id.home_gauge);
        gauge.setAccent(accent);
        favGrid = v.findViewById(R.id.fav_grid);
        playButton = v.findViewById(R.id.home_play);
        playButton.setBackground(Theme.playButton(act, accent));
        if (Prefs.TILES_OFF.equals(Prefs.tiles(act))) v.findViewById(R.id.home_map_attrib).setVisibility(View.GONE);

        v.findViewById(R.id.nav_tile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                act.show(LauncherActivity.NAV, true);
            }
        });
        // The map swallows touches only when interactive; on the dashboard it is a preview.
        map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                act.show(LauncherActivity.NAV, true);
            }
        });
        v.findViewById(R.id.home_go_home).setOnClickListener(placeClick(Prefs.HOME_ADDR, "Home"));
        v.findViewById(R.id.home_go_work).setOnClickListener(placeClick(Prefs.WORK_ADDR, "Work"));

        v.findViewById(R.id.media_tile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                act.show(LauncherActivity.MEDIA, true);
            }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MediaHub.get(act).playPause();
            }
        });
        v.findViewById(R.id.home_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MediaHub.get(act).previous();
            }
        });
        v.findViewById(R.id.home_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MediaHub.get(act).next();
            }
        });
        v.findViewById(R.id.drive_tile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                act.show(LauncherActivity.NAV, true);
            }
        });
        gauge.setUnit(LocationTracker.unit(act), Prefs.metric(act) ? 200 : 120);
        return v;
    }

    private View.OnClickListener placeClick(final String key, final String label) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                String addr = Prefs.str(act, key, null);
                if (Ui.isEmpty(addr)) {
                    Dialogs.text(act, "Set " + label + " address", "Street, city", "", new Dialogs.TextEntered() {
                        @Override
                        public void onText(String text) {
                            if (!Ui.isEmpty(text)) {
                                Prefs.put(act, key, text);
                                NavHelper.navigateTo(act, text);
                            }
                        }
                    });
                } else {
                    NavHelper.navigateTo(act, addr);
                }
            }
        };
    }

    @Override
    public void onShow() {
        visible = true;
        MediaHub.get(act).addListener(this);
        LocationTracker.get(act).addListener(this);
        AppRepository.get(act).addListener(this);
        buildFavourites();
        main.post(ticker);
    }

    @Override
    public void onHide() {
        visible = false;
        MediaHub.get(act).removeListener(this);
        LocationTracker.get(act).removeListener(this);
        AppRepository.get(act).removeListener(this);
        main.removeCallbacks(ticker);
        viz.setPlaying(false);
    }

    // ---- Media -------------------------------------------------------------------------------

    @Override
    public void onMediaChanged() {
        MediaHub hub = MediaHub.get(act);
        String pkg = hub.packageName();
        source.setText(hub.sourceLabel(pkg));
        if (hub.hasContent()) {
            title.setText(hub.title());
            String a = hub.artist();
            artist.setText(a == null ? "" : a);
        } else {
            title.setText(R.string.nothing_playing);
            artist.setText(R.string.tap_to_play);
        }
        Bitmap b = hub.art();
        art.setBitmap(b);
        boolean playing = hub.isPlaying();
        playButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        viz.setPlaying(playing && visible);
        updateProgress();
    }

    private void updateProgress() {
        MediaHub hub = MediaHub.get(act);
        long d = hub.duration();
        progress.setProgress(d > 0 ? (int) (hub.position() * 1000 / d) : 0);
    }

    // ---- Location ----------------------------------------------------------------------------

    @Override
    public void onLocationUpdate(LocationTracker t) {
        Location l = t.location();
        float speed = t.speed(act);
        gauge.setSpeed(speed);
        String head = t.hasFix() && speed > 3 ? LocationTracker.heading(t.bearing()) : "";
        gauge.setSubLabel(head);
        navHeading.setText(t.hasFix() ? (head.length() > 0 ? "Heading " + head : "GPS fix") : "");
        if (l != null) {
            map.setMyLocation(l.getLatitude(), l.getLongitude(), t.bearing(), l.getAccuracy(), t.hasFix());
        }
        if (!t.hasPermission()) {
            navStatus.setVisibility(View.VISIBLE);
            navStatus.setText("Location permission needed");
        } else if (l == null) {
            navStatus.setVisibility(View.VISIBLE);
            navStatus.setText(R.string.waiting_gps);
        } else {
            navStatus.setVisibility(View.GONE);
        }
    }

    // ---- Favourites --------------------------------------------------------------------------

    @Override
    public void onAppsChanged() {
        buildFavourites();
    }

    private void buildFavourites() {
        favGrid.removeAllViews();
        List<AppRepository.App> favs = AppRepository.get(act).favourites();
        int size = Ui.dp(act, 40);
        for (int i = 0; i < 4; i++) {
            final AppRepository.App a = i < favs.size() ? favs.get(i) : null;
            LinearLayout cell = new LinearLayout(act);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundResource(R.drawable.ripple_rect);
            ImageView icon = new ImageView(act);
            TextView label = new TextView(act);
            label.setTextSize(11);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(Theme.TEXT_SECONDARY);
            if (a != null) {
                icon.setImageDrawable(a.icon);
                label.setText(a.label);
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppRepository.launch(act, a);
                    }
                });
            } else {
                icon.setImageResource(R.drawable.ic_add);
                Theme.tint(icon, Theme.TEXT_DIM);
                int pad = Ui.dp(act, 9);
                icon.setPadding(pad, pad, pad, pad);
                icon.setBackground(dashedCircle());
                label.setText("Add");
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.toast(act, "Long-press any app in Apps to pin it here");
                        act.show(LauncherActivity.APPS, true);
                    }
                });
            }
            cell.addView(icon, new LinearLayout.LayoutParams(size, size));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(act, 3);
            cell.addView(label, lp);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 2, 1f), GridLayout.spec(i % 2, 1f));
            glp.width = 0;
            glp.height = 0;
            favGrid.addView(cell, glp);
        }
    }

    private android.graphics.drawable.Drawable dashedCircle() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setStroke(Ui.dp(act, 1), Theme.STROKE, Ui.dp(act, 4), Ui.dp(act, 3));
        return d;
    }
}
