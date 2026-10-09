package com.thecarlauncher.screens;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.media.ArtLoader;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.media.MusicLibrary;
import com.thecarlauncher.media.MusicService;
import com.thecarlauncher.media.Track;
import com.thecarlauncher.util.Theme;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.views.AlbumArtView;
import com.thecarlauncher.views.VisualizerView;

import java.util.ArrayList;
import java.util.List;

/** Full media player: now playing, source switcher and a local library browser. */
public class MediaScreen extends Screen implements MediaHub.Listener {
    private static final String[] TABS = {"Songs", "Albums", "Artists", "Folders"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private View nowPlaying, browser;
    private AlbumArtView art;
    private VisualizerView viz;
    private TextView title, artist, album, pos, dur, openApp, browserCount, browserPath, browserEmpty;
    private ImageView play, shuffle, repeat;
    private SeekBar seek;
    private LinearLayout sources, tabs;
    private ListView list;
    private boolean dragging, visible;

    private int tab;
    private MusicLibrary.Group openGroup;
    private final List<Object> rows = new ArrayList<>();
    private final RowAdapter adapter = new RowAdapter();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            main.postDelayed(this, 500);
        }
    };

    public MediaScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return "Media"; }

    @Override
    protected View create(ViewGroup parent) {
        View v = LayoutInflater.from(act).inflate(R.layout.screen_media, parent, false);
        nowPlaying = v.findViewById(R.id.now_playing);
        browser = v.findViewById(R.id.browser);
        art = v.findViewById(R.id.media_art);
        art.setAccent(accent);
        viz = v.findViewById(R.id.media_viz);
        viz.setAccent(accent);
        title = v.findViewById(R.id.media_title);
        artist = v.findViewById(R.id.media_artist);
        album = v.findViewById(R.id.media_album);
        pos = v.findViewById(R.id.media_pos);
        dur = v.findViewById(R.id.media_dur);
        play = v.findViewById(R.id.media_play);
        play.setBackground(Theme.playButton(act, accent));
        Theme.tint(play, Theme.TEXT_PRIMARY);
        shuffle = v.findViewById(R.id.media_shuffle);
        repeat = v.findViewById(R.id.media_repeat);
        seek = v.findViewById(R.id.media_seek);
        sources = v.findViewById(R.id.media_sources);
        openApp = v.findViewById(R.id.media_open_app);
        tabs = v.findViewById(R.id.browser_tabs);
        list = v.findViewById(R.id.browser_list);
        browserCount = v.findViewById(R.id.browser_count);
        browserPath = v.findViewById(R.id.browser_path);
        browserEmpty = v.findViewById(R.id.browser_empty);
        list.setAdapter(adapter);
        if (act.getResources().getBoolean(R.bool.compact)) {
            // Short screens: drop the decorative rows so the controls keep their size.
            album.setVisibility(View.GONE);
            viz.setVisibility(View.GONE);
        }

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MediaHub.get(act).playPause();
            }
        });
        v.findViewById(R.id.media_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MediaHub.get(act).previous();
            }
        });
        v.findViewById(R.id.media_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MediaHub.get(act).next();
            }
        });
        shuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MusicService s = MusicService.get();
                if (s != null) s.setShuffle(!s.isShuffle());
            }
        });
        repeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                MusicService s = MusicService.get();
                if (s != null) s.cycleRepeat();
            }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) pos.setText(Ui.time(MediaHub.get(act).duration() * p / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                MediaHub hub = MediaHub.get(act);
                if (hub.canSeek()) hub.seekTo(hub.duration() * s.getProgress() / 1000);
            }
        });
        v.findViewById(R.id.media_library).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                openBrowser();
            }
        });
        v.findViewById(R.id.media_sound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                if (MediaHub.get(act).isOwn()) Dialogs.equalizer(act, accent);
                else Ui.start(act, new Intent(Settings.ACTION_SOUND_SETTINGS));
            }
        });
        openApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                AppRepository.launchPackage(act, MediaHub.get(act).packageName());
            }
        });
        v.findViewById(R.id.browser_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                onBack();
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View view, int position, long id) {
                onRowClicked(position);
            }
        });
        buildTabs();
        return v;
    }

    @Override
    public void onShow() {
        visible = true;
        MediaHub.get(act).connectExternal();
        MediaHub.get(act).addListener(this);
        main.post(ticker);
        if (MusicLibrary.hasPermission(act)) MusicService.start(act);
    }

    @Override
    public void onHide() {
        visible = false;
        MediaHub.get(act).removeListener(this);
        main.removeCallbacks(ticker);
        viz.setPlaying(false);
    }

    @Override
    public boolean onBack() {
        if (browser.getVisibility() != View.VISIBLE) return false;
        if (openGroup != null) {
            openGroup = null;
            loadTab();
        } else {
            closeBrowser();
        }
        return true;
    }

    @Override
    public void onPermissionsChanged() {
        if (browser.getVisibility() == View.VISIBLE) loadTab();
    }

    // ---- Now playing -------------------------------------------------------------------------

    @Override
    public void onMediaChanged() {
        MediaHub hub = MediaHub.get(act);
        boolean own = hub.isOwn();
        if (hub.hasContent()) {
            title.setText(hub.title());
            artist.setText(nz(hub.artist()));
            album.setText(nz(hub.album()));
        } else {
            title.setText(R.string.nothing_playing);
            artist.setText(own ? "Open the library or press play to shuffle your music" : "");
            album.setText("");
        }
        art.setBitmap(hub.art());
        boolean playing = hub.isPlaying();
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        viz.setPlaying(playing && visible);

        shuffle.setVisibility(own ? View.VISIBLE : View.INVISIBLE);
        repeat.setVisibility(own ? View.VISIBLE : View.INVISIBLE);
        Theme.tint(shuffle, hub.shuffle() ? accent : Theme.TEXT_DIM);
        int r = hub.repeat();
        repeat.setImageResource(r == MusicService.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        Theme.tint(repeat, r == MusicService.REPEAT_OFF ? Theme.TEXT_DIM : accent);
        seek.setEnabled(hub.canSeek());
        openApp.setVisibility(own ? View.GONE : View.VISIBLE);
        if (!own) openApp.setText(hub.sourceLabel(hub.packageName()));
        buildSources();
        updateProgress();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void updateProgress() {
        MediaHub hub = MediaHub.get(act);
        long d = hub.duration(), p = hub.position();
        dur.setText(Ui.time(d));
        if (!dragging) {
            pos.setText(Ui.time(p));
            seek.setProgress(d > 0 ? (int) (p * 1000 / d) : 0);
        }
    }

    private void buildSources() {
        final MediaHub hub = MediaHub.get(act);
        sources.removeAllViews();
        List<String> pkgs = hub.sources();
        String current = hub.packageName();
        for (final String pkg : pkgs) {
            TextView chip = chip(hub.sourceLabel(pkg), pkg.equals(current));
            Drawable icon = pkg.equals(act.getPackageName()) ? act.getDrawable(R.drawable.ic_music) : hub.sourceIcon(pkg);
            if (icon != null) {
                icon = icon.mutate();
                int s = Ui.dp(act, 18);
                icon.setBounds(0, 0, s, s);
                if (pkg.equals(act.getPackageName())) icon.setTint(accent);
                chip.setCompoundDrawables(icon, null, null, null);
            }
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hub.select(pkg);
                }
            });
            sources.addView(chip);
        }
        if (!hub.hasNotificationAccess()) {
            TextView add = chip("+ Connect apps", false);
            add.setTextColor(Theme.TEXT_SECONDARY);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.toast(act, "Enable \"" + act.getString(R.string.app_name)
                            + "\" so Spotify, YouTube Music & Bluetooth audio show up here");
                    Ui.start(act, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                }
            });
            sources.addView(add);
        }
    }

    private TextView chip(String text, boolean selected) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Theme.TEXT_PRIMARY);
        t.setCompoundDrawablePadding(Ui.dp(act, 8));
        int p = Ui.dp(act, 14);
        t.setPadding(p, 0, p, 0);
        t.setBackground(selected ? Theme.chipSelected(act, accent) : act.getDrawable(R.drawable.chip_bg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(act, 38));
        lp.rightMargin = Ui.dp(act, 8);
        t.setLayoutParams(lp);
        return t;
    }

    // ---- Library browser ---------------------------------------------------------------------

    private void buildTabs() {
        tabs.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(act);
            t.setText(TABS[i].toUpperCase());
            t.setTextSize(13);
            t.setLetterSpacing(0.12f);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
            t.setGravity(Gravity.CENTER);
            int p = Ui.dp(act, 16);
            t.setPadding(p, 0, p, 0);
            t.setBackgroundResource(R.drawable.ripple_rect);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = idx;
                    openGroup = null;
                    styleTabs();
                    loadTab();
                }
            });
            tabs.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        styleTabs();
    }

    private void styleTabs() {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            t.setTextColor(i == tab ? accent : Theme.TEXT_SECONDARY);
        }
    }

    private void openBrowser() {
        if (!MusicLibrary.hasPermission(act)) {
            act.requestMissingPermissions(true);
        }
        nowPlaying.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);
        browser.setAlpha(0f);
        browser.animate().alpha(1f).setDuration(180).start();
        loadTab();
    }

    private void closeBrowser() {
        browser.setVisibility(View.GONE);
        nowPlaying.setVisibility(View.VISIBLE);
        nowPlaying.setAlpha(0f);
        nowPlaying.animate().alpha(1f).setDuration(180).start();
    }

    private void loadTab() {
        final int forTab = tab;
        final MusicLibrary.Group group = openGroup;
        browserEmpty.setVisibility(View.GONE);
        browserPath.setVisibility(group == null ? View.GONE : View.VISIBLE);
        if (group != null) browserPath.setText(TABS[tab] + "  ›  " + group.title);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Object> data = new ArrayList<>();
                if (group != null) {
                    data.addAll(MusicLibrary.resolve(act, group.key));
                } else if (forTab == 0) {
                    data.addAll(MusicLibrary.all(act));
                } else if (forTab == 1) {
                    data.addAll(MusicLibrary.albums(act));
                } else if (forTab == 2) {
                    data.addAll(MusicLibrary.artists(act));
                } else {
                    data.addAll(MusicLibrary.folders(act));
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (forTab != tab || group != openGroup) return;
                        rows.clear();
                        rows.addAll(data);
                        adapter.notifyDataSetChanged();
                        list.setSelection(0);
                        browserCount.setText(data.size() + (group != null || forTab == 0 ? " songs" : ""));
                        if (data.isEmpty()) {
                            browserEmpty.setVisibility(View.VISIBLE);
                            browserEmpty.setText(MusicLibrary.hasPermission(act)
                                    ? "No music found.\nCopy songs to the unit, an SD card or a USB stick."
                                    : "Allow access to music to browse your library.");
                        }
                    }
                });
            }
        }).start();
    }

    private void onRowClicked(int position) {
        Object o = rows.get(position);
        if (o instanceof MusicLibrary.Group) {
            openGroup = (MusicLibrary.Group) o;
            loadTab();
            return;
        }
        MusicService s = MusicService.get();
        if (s == null) {
            MusicService.start(act);
            Ui.toast(act, "Starting player…");
            return;
        }
        List<Track> queue = new ArrayList<>();
        for (Object r : rows) if (r instanceof Track) queue.add((Track) r);
        String ctx = openGroup != null ? openGroup.key : MusicLibrary.CTX_ALL;
        s.playQueue(queue, queue.indexOf(o), ctx);
        MediaHub.get(act).select(act.getPackageName());
        openGroup = null;
        closeBrowser();
    }

    private class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(act).inflate(R.layout.row_item, parent, false);
            ImageView icon = v.findViewById(R.id.row_icon);
            TextView t = v.findViewById(R.id.row_title);
            TextView sub = v.findViewById(R.id.row_subtitle);
            TextView meta = v.findViewById(R.id.row_meta);
            icon.setClipToOutline(true);
            icon.setBackground(Theme.round(0xFF18212B, Ui.dp(act, 8)));
            Object o = rows.get(p);
            MusicService svc = MusicService.get();
            Track cur = svc == null ? null : svc.current();
            if (o instanceof Track) {
                Track tr = (Track) o;
                boolean isCurrent = cur != null && cur.id == tr.id;
                t.setText(tr.title);
                t.setTextColor(isCurrent ? accent : Theme.TEXT_PRIMARY);
                sub.setText(tr.artist + "  ·  " + tr.album);
                meta.setText(Ui.time(tr.duration));
                ArtLoader.into(act, icon, tr.albumId, R.drawable.ic_music);
            } else {
                MusicLibrary.Group g = (MusicLibrary.Group) o;
                t.setText(g.title);
                t.setTextColor(Theme.TEXT_PRIMARY);
                sub.setText(g.subtitle);
                meta.setText(g.count + "");
                int fallback = tab == 2 ? R.drawable.ic_person : tab == 3 ? R.drawable.ic_folder : R.drawable.ic_album;
                if (tab == 1) {
                    ArtLoader.into(act, icon, g.artAlbumId, fallback);
                } else {
                    icon.setTag(null);
                    icon.setImageResource(fallback);
                    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int pad = Ui.dp(act, 8);
                    icon.setPadding(pad, pad, pad, pad);
                    icon.setImageTintList(ColorStateList.valueOf(Theme.TEXT_SECONDARY));
                }
            }
            return v;
        }
    }
}
