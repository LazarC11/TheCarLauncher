package com.thecarlauncher.screens;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.media.ArtLoader;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.media.MusicLibrary;
import com.thecarlauncher.media.MusicService;
import com.thecarlauncher.media.Track;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.CoverGlow;
import com.thecarlauncher.mib.MibArtBox;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibPopup;
import com.thecarlauncher.mib.MibProgress;
import com.thecarlauncher.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** MIB "Mediji": now playing with cover box, Izvor source popup, Izbor library browser. */
public class MediaScreen extends Screen implements MediaHub.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private View root, nowPlaying, info, browser;
    private TextView title, artist, album, message, pos, remain, path, empty;
    private View artistRow, albumRow, progressRow;
    private MibArtBox art;
    private CoverGlow glow;
    private MibProgress progress;
    private MibBar bar, browseBar;
    private MibBar.Key sourceKey, playKey;
    private final MibBar.Key[] tabKeys = new MibBar.Key[4];
    private ListView list;

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
    public String title() { return Mib.t(act, "Mediji", "Media"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        root = r;
        FrameLayout content = new FrameLayout(act);
        r.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- Now playing ----
        // Cover box on the right, vertically centred with the title / artist / album block;
        // progress line across the full width at the bottom.
        FrameLayout np = new FrameLayout(act);
        nowPlaying = np;
        content.addView(np, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        int box = Mib.px(act, 264), boxRight = Mib.px(act, 74), progH = Mib.px(act, 84);

        FrameLayout upper = new FrameLayout(act);
        upper.setClipChildren(false);
        np.setClipChildren(false);
        FrameLayout.LayoutParams ul = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ul.bottomMargin = progH;
        np.addView(upper, ul);

        // Soft glow under the cover while a song plays (sits behind the cover).
        glow = new CoverGlow(act);
        int glowW = (int) (box * 1.35f), glowH = (int) (box * 0.40f);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(glowW, glowH, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        gl.rightMargin = boxRight - (glowW - box) / 2;
        glow.setTranslationY(box / 2f - glowH * 0.12f);
        upper.addView(glow, gl);

        art = new MibArtBox(act);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(box, box, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        al.rightMargin = boxRight;
        upper.addView(art, al);

        FrameLayout left = new FrameLayout(act);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ll.rightMargin = box + boxRight + Mib.px(act, 30);
        upper.addView(left, ll);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Mib.px(act, 60), 0, 0, 0);
        info = col;
        left.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, box, Gravity.CENTER_VERTICAL));
        View titleRow = metaRow(R.drawable.ui_ic_note, 36, Mib.semibold(act));
        title = (TextView) ((ViewGroup) titleRow).getChildAt(1);
        titleRow.setPadding(0, 0, 0, 0);
        col.addView(titleRow);
        artistRow = metaRow(R.drawable.ui_ic_person, 29, Mib.regular(act));
        artist = (TextView) ((ViewGroup) artistRow).getChildAt(1);
        col.addView(artistRow);
        albumRow = metaRow(R.drawable.ui_ic_cd, 29, Mib.regular(act));
        album = (TextView) ((ViewGroup) albumRow).getChildAt(1);
        col.addView(albumRow);

        LinearLayout pr = new LinearLayout(act);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.setPadding(Mib.px(act, 60), 0, boxRight, 0);
        progressRow = pr;
        pos = Mib.text(act, "0:00", 24, Mib.regular(act), Mib.WHITE);
        remain = Mib.text(act, "0:00", 24, Mib.regular(act), Mib.WHITE);
        progress = new MibProgress(act);
        progress.setSeekListener(new MibProgress.SeekListener() {
            @Override
            public void onSeek(float f) {
                MediaHub hub = MediaHub.get(act);
                if (hub.canSeek()) hub.seekTo((long) (hub.duration() * f));
            }
        });
        pr.addView(pos, new LinearLayout.LayoutParams(Mib.px(act, 76), ViewGroup.LayoutParams.WRAP_CONTENT));
        pr.addView(progress, new LinearLayout.LayoutParams(0, Mib.px(act, 30), 1f));
        remain.setGravity(Gravity.RIGHT);
        pr.addView(remain, new LinearLayout.LayoutParams(Mib.px(act, 88), ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                progH, Gravity.BOTTOM);
        np.addView(pr, pl);

        // Music video: its picture where the cover is; tap for the whole screen.
        videoBox = box;
        videoRight = boxRight;
        videoBottom = progH;
        // TextureView cannot have a background: a black frame holds it (bars around the picture).
        videoFrame = new FrameLayout(act);
        videoFrame.setBackgroundColor(0xFF000000);
        videoFrame.setVisibility(View.GONE);
        video = new android.view.TextureView(act);
        videoFrame.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        video.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h) {
                videoSurface = new android.view.Surface(st);
                surfaceFor = null;
                attachVideo();
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h) { fitVideo(); }

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) {
                MusicService s = MusicService.get();
                if (s != null) s.setVideoSurface(null);
                if (videoSurface != null) videoSurface.release();
                videoSurface = null;
                surfaceFor = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {}
        });
        video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setVideoFull(!videoFull); }
        });
        content.addView(videoFrame, videoParams(false));

        // "Uređaj nije povezan."-style centred message when there is nothing to show.
        message = Mib.text(act, "", 38, Mib.regular(act), Mib.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setSingleLine(false);
        message.setLineSpacing(Mib.px(act, 6), 1f);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ml.leftMargin = Mib.px(act, 30);
        left.addView(message, ml);

        // ---- Browser ----
        LinearLayout br = new LinearLayout(act);
        br.setOrientation(LinearLayout.VERTICAL);
        br.setVisibility(View.GONE);
        br.setPadding(Mib.px(act, 40), Mib.px(act, 10), Mib.px(act, 40), 0);
        browser = br;
        path = Mib.text(act, "", 26, Mib.semibold(act), Mib.WHITE);
        path.setPadding(Mib.px(act, 6), 0, 0, Mib.px(act, 8));
        br.addView(path);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        br.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));
        FrameLayout lf = new FrameLayout(act);
        list = new ListView(act);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0xFF55565F));
        list.setDividerHeight(Math.max(1, Mib.px(act, 1)));
        list.setSelector(Mib.keyPressed());
        list.setFastScrollEnabled(true);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                onRowClicked(position);
            }
        });
        lf.addView(list);
        empty = Mib.text(act, "", 32, Mib.regular(act), Mib.WHITE);
        empty.setSingleLine(false);
        empty.setGravity(Gravity.CENTER);
        lf.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        br.addView(lf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(br, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Soft keys ----
        FrameLayout bars = new FrameLayout(act);
        r.addView(bars, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        bar = new MibBar(act);
        sourceKey = bar.add(s("Izvor", "Source"), R.drawable.ui_key_usb, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSources(v);
            }
        });
        bar.add(s("Izbor", "Browse"), R.drawable.ui_key_browse, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBrowser();
            }
        });
        bar.add(s("Prethodni", "Previous"), R.drawable.ui_key_prev, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaHub.get(act).previous();
            }
        });
        playKey = bar.add(s("Reproduk.", "Play"), R.drawable.ui_key_play, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaHub.get(act).playPause();
            }
        });
        bar.add(s("Naredni", "Next"), R.drawable.ui_key_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaHub.get(act).next();
            }
        });
        bar.gap(1);
        bar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.showSettings(SettingsScreen.PAGE_MEDIA);
            }
        });
        bars.addView(bar);

        browseBar = new MibBar(act);
        browseBar.add(s("Nazad", "Back"), R.drawable.ui_ic_back, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack();
            }
        });
        String[][] tabs = {{"Pesme", "Songs"}, {"Albumi", "Albums"}, {"Izvođači", "Artists"}, {"Fascikle", "Folders"}};
        int[] icons = {R.drawable.ui_key_music, R.drawable.ui_key_cd, R.drawable.ui_ic_person, R.drawable.ui_ic_folder};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tabKeys[i] = browseBar.add(s(tabs[i][0], tabs[i][1]), icons[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = idx;
                    openGroup = null;
                    loadTab();
                }
            });
        }
        browseBar.gap(1);
        browseBar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.showSettings(SettingsScreen.PAGE_MEDIA);
            }
        });
        browseBar.setVisibility(View.GONE);
        bars.addView(browseBar);
        return r;
    }

    private View metaRow(int icon, float size, android.graphics.Typeface tf) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = Mib.icon(act, icon, Mib.WHITE);
        row.addView(iv, new LinearLayout.LayoutParams(Mib.px(act, 36), Mib.px(act, 36)));
        TextView t = Mib.text(act, "", size, tf, Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Mib.px(act, 18);
        row.addView(t, tl);
        row.setPadding(0, Mib.px(act, 22), 0, 0);
        return row;
    }

    @Override
    public void onShow() {
        MediaHub.get(act).connectExternal();
        MediaHub.get(act).addListener(this);
        MediaHub.get(act).resumeBluetooth();
        main.post(ticker);
        if (MusicLibrary.hasPermission(act)) MusicService.start(act);
    }

    @Override
    public void onHide() {
        MediaHub.get(act).removeListener(this);
        main.removeCallbacks(ticker);
    }

    // ---- Music video ---------------------------------------------------------------------------

    private android.view.TextureView video;
    private FrameLayout videoFrame;
    private android.view.Surface videoSurface;
    private MusicService surfaceFor;
    private boolean videoFull;
    private int videoBox, videoRight, videoBottom;

    private FrameLayout.LayoutParams videoParams(boolean full) {
        if (full) return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(videoBox, videoBox, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        l.rightMargin = videoRight;
        l.bottomMargin = videoBottom;
        return l;
    }

    private void setVideoFull(boolean full) {
        videoFull = full;
        videoFrame.setLayoutParams(videoParams(full));
        video.post(new Runnable() {
            public void run() { fitVideo(); }
        });
    }

    /** Hands the picture surface to the player (once per player instance). */
    private void attachVideo() {
        MusicService s = MusicService.get();
        if (s == null || videoSurface == null || s == surfaceFor) return;
        s.setVideoSurface(videoSurface);
        surfaceFor = s;
    }

    /** Keeps the video's own shape inside the box (black bars), whatever the box is. */
    private void fitVideo() {
        MusicService s = MusicService.get();
        int vw = s == null ? 0 : s.videoWidth(), vh = s == null ? 0 : s.videoHeight();
        float w = video.getWidth(), h = video.getHeight();
        if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return;
        float k = Math.min(w / vw, h / vh);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(vw * k / w, vh * k / h, w / 2f, h / 2f);
        video.setTransform(m);
    }

    /** Shows the picture while the own player plays a music video. */
    private void updateVideo(MediaHub hub) {
        MusicService s = MusicService.get();
        Track t = s == null ? null : s.current();
        boolean on = hub.isOwn() && t != null && t.video && nowPlaying.getVisibility() == View.VISIBLE;
        videoFrame.setVisibility(on ? View.VISIBLE : View.GONE);
        art.setVisibility(on ? View.INVISIBLE : View.VISIBLE);
        if (!on && videoFull) setVideoFull(false);
        if (on) {
            attachVideo();
            fitVideo();
        }
    }

    @Override
    public boolean onBack() {
        if (videoFull) {
            setVideoFull(false);
            return true;
        }
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
        boolean bt = hub.isBluetooth();
        String key = hub.packageName();
        boolean fm = hub.isRadio();
        if (fm) sourceKey.setIcon(R.drawable.ui_key_radio);
        else if (bt) sourceKey.setIcon(R.drawable.ui_key_bt);
        else if (hub.isOwn()) sourceKey.setIcon(R.drawable.ui_key_usb);
        else {
            Drawable d = hub.sourceIcon(key);
            if (d != null) sourceKey.setIcon(d);
            else sourceKey.setIcon(R.drawable.ui_key_music);
        }
        boolean playing = hub.isPlaying();
        playKey.setLabel(playing ? s("Pauza", "Pause") : s("Reproduk.", "Play"));
        playKey.setIcon(playing ? R.drawable.ui_key_pause : R.drawable.ui_key_play);

        // Own player: the USB placeholder only while nothing is loaded; a playing song without
        // cover art gets the music-note placeholder.
        boolean usbIdle = hub.isOwn() && !hub.hasTrackInfo();
        // Bluetooth with no song (phone gone or not playing): the Bluetooth placeholder.
        boolean btIdle = bt && !hub.hasTrackInfo();
        glow.setOn(playing && hub.hasTrackInfo());
        art.setCover(fm ? R.drawable.ui_cover_radio : btIdle ? R.drawable.ui_cover_bt : usbIdle ? R.drawable.ui_cover_usb : R.drawable.ui_cover_music);
        art.setBitmap(hub.art());
        updateVideo(hub);

        String t = hub.hasTrackInfo() ? hub.title() : null;
        if (t == null) {
            info.setVisibility(View.INVISIBLE);
            message.setVisibility(View.VISIBLE);
            boolean noAccess = bt && !hub.hasNotificationAccess();
            message.setOnClickListener(noAccess ? new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MediaHub.get(act).openNotificationAccess();
                }
            } : null);
            message.setText(noAccess
                    ? s("Za naziv pesme i cover uključite\npristup obaveštenjima — dodirnite ovde.",
                        "To show the song and cover, enable\nnotification access — tap here.")
                    : bt
                    ? s("Pustite muziku na telefonu.", "Start music on your phone.")
                    : hub.isOwn()
                    ? s("Nema izabranog medija.\nDodirnite Izbor ili Reproduk.", "No media selected.\nTap Browse or Play.")
                    : s("Nema podataka o numeri.", "No track information."));
        } else {
            info.setVisibility(View.VISIBLE);
            message.setVisibility(View.GONE);
            title.setText(t);
            String a = hub.artist(), al = hub.album();
            artist.setText(a == null ? "" : a);
            artistRow.setVisibility(a == null || a.length() == 0 ? View.GONE : View.VISIBLE);
            album.setText(al == null ? "" : al);
            albumRow.setVisibility(al == null || al.length() == 0 ? View.GONE : View.VISIBLE);
        }
        progress.setEnabled(hub.canSeek());
        updateProgress();
    }

    private void updateProgress() {
        MediaHub hub = MediaHub.get(act);
        long d = hub.duration(), p = hub.position();
        // Bluetooth songs may come without a length: still show the elapsed time.
        boolean show = d > 0 || (hub.isBluetooth() && hub.hasTrackInfo() && p > 0);
        progressRow.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        pos.setText(Ui.time(p));
        remain.setText(d > 0 ? "-" + Ui.time(Math.max(0, d - p)) : "--:--");
        progress.setFraction(d > 0 ? p / (float) d : 0);
    }

    private void showSources(View key) {
        final MediaHub hub = MediaHub.get(act);
        String cur = hub.packageName();
        MibPopup p = new MibPopup(act);
        p.add("USB / SD", R.drawable.ui_ic_usb, hub.isOwn(), new Runnable() {
            @Override
            public void run() {
                hub.select(act.getPackageName());
                MusicService.start(act);
            }
        });
        p.add("BT-audio", R.drawable.ui_ic_bt, hub.isBluetooth(), new Runnable() {
            @Override
            public void run() {
                // No phone connected: pick one first (the list of remembered phones).
                if (com.thecarlauncher.media.BtDevices.get(act).knowsState()
                        && !com.thecarlauncher.media.BtDevices.get(act).isConnected()) {
                    act.show(LauncherActivity.BTDEVICES, true);
                    return;
                }
                hub.select(MediaHub.BLUETOOTH);
            }
        });
        for (final String k : hub.sources()) {
            if (k.equals(act.getPackageName()) || k.equals(MediaHub.BLUETOOTH)) continue;
            p.add(hub.sourceLabel(k), hub.sourceIcon(k), false, k.equals(cur), new Runnable() {
                @Override
                public void run() {
                    hub.select(k);
                }
            });
        }
        if (Projection.find(act) != null) {
            p.add("App-Connect", R.drawable.ui_ic_appconnect_s, false, new Runnable() {
                @Override
                public void run() {
                    act.openProjection();
                }
            });
        }
        p.add("Radio", R.drawable.ui_ic_radio, hub.isRadio(), new Runnable() {
            @Override
            public void run() {
                act.openRadio();
            }
        });
        if (!hub.hasNotificationAccess()) {
            p.add(s("Poveži aplikacije", "Connect apps"), R.drawable.ui_ic_plus, false, new Runnable() {
                @Override
                public void run() {
                    Ui.toast(act, s("Uključite \"Car Launcher\" da bi se Spotify i druge aplikacije pojavile ovde",
                            "Enable \"Car Launcher\" so Spotify & other players show up here"));
                    Ui.start(act, new android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                }
            });
        }
        p.show(root, key);
    }

    // ---- Browser -----------------------------------------------------------------------------

    private void openBrowser() {
        if (!MusicLibrary.hasPermission(act)) act.requestMissingPermissions(true);
        nowPlaying.setVisibility(View.GONE);
        updateVideo(MediaHub.get(act));
        bar.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);
        browseBar.setVisibility(View.VISIBLE);
        loadTab();
    }

    private void closeBrowser() {
        browser.setVisibility(View.GONE);
        browseBar.setVisibility(View.GONE);
        nowPlaying.setVisibility(View.VISIBLE);
        updateVideo(MediaHub.get(act));
        bar.setVisibility(View.VISIBLE);
    }

    private void loadTab() {
        for (int i = 0; i < 4; i++) tabKeys[i].setActive(i == tab);
        final int forTab = tab;
        final MusicLibrary.Group group = openGroup;
        String[] names = {s("Pesme", "Songs"), s("Albumi", "Albums"), s("Izvođači", "Artists"), s("Fascikle", "Folders")};
        path.setText("USB / SD  ›  " + names[tab] + (group != null ? "  ›  " + group.title : ""));
        empty.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Object> data = new ArrayList<>();
                if (group != null) data.addAll(MusicLibrary.resolve(act, group.key));
                else if (forTab == 0) data.addAll(MusicLibrary.all(act));
                else if (forTab == 1) data.addAll(MusicLibrary.albums(act));
                else if (forTab == 2) data.addAll(MusicLibrary.artists(act));
                else data.addAll(MusicLibrary.folders(act));
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (forTab != tab || group != openGroup) return;
                        rows.clear();
                        rows.addAll(data);
                        adapter.notifyDataSetChanged();
                        list.setSelection(0);
                        if (data.isEmpty()) {
                            empty.setVisibility(View.VISIBLE);
                            boolean files = com.thecarlauncher.util.Storage.hasAllFiles();
                            empty.setText(!MusicLibrary.hasPermission(act)
                                    ? s("Dozvolite pristup muzici.", "Allow access to music.")
                                    : !files ? s("Nema medija.\nDodirnite ovde i dozvolite \"Pristup svim fajlovima\" da bi se čitao USB.",
                                    "No media.\nTap here and allow \"All files access\" so USB sticks can be read.")
                                    : s("Nema medija.\nUbacite USB ili SD karticu sa muzikom.",
                                    "No media.\nInsert a USB stick or SD card with music."));
                            empty.setOnClickListener(files && MusicLibrary.hasPermission(act) ? null : new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (!MusicLibrary.hasPermission(act)) act.requestMissingPermissions(true);
                                    else com.thecarlauncher.util.Storage.requestAllFiles(act);
                                }
                            });
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
        final List<Track> queue = new ArrayList<>();
        for (Object r : rows) if (r instanceof Track) queue.add((Track) r);
        final int start = queue.indexOf(o);
        final String ctx = openGroup != null ? openGroup.key : MusicLibrary.CTX_ALL;
        openGroup = null;
        closeBrowser();
        // Switch the source first (pauses Bluetooth / radio), then start the song.
        MediaHub.get(act).selectOwn();
        playWhenReady(queue, start, ctx, 0);
    }

    /** Starts the queue as soon as the player service is up, and makes sure it really plays. */
    private void playWhenReady(final List<Track> queue, final int start, final String ctx, final int tries) {
        final MusicService svc = MusicService.get();
        if (svc == null) {
            if (tries == 0) MusicService.start(act);
            if (tries < 20) {
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() { playWhenReady(queue, start, ctx, tries + 1); }
                }, 150);
            }
            return;
        }
        svc.playQueue(queue, start, ctx);
        MediaHub.get(act).selectOwn();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                MusicService s = MusicService.get();
                if (s != null && s.current() != null && !s.isPlaying()) s.play();
            }
        }, 1500);
    }

    private class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            Holder h;
            if (v == null) {
                LinearLayout row = new LinearLayout(act);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Mib.px(act, 82));
                row.setPadding(Mib.px(act, 8), 0, Mib.px(act, 14), 0);
                h = new Holder();
                h.icon = new ImageView(act);
                h.icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(h.icon, new LinearLayout.LayoutParams(Mib.px(act, 58), Mib.px(act, 58)));
                LinearLayout texts = new LinearLayout(act);
                texts.setOrientation(LinearLayout.VERTICAL);
                h.title = Mib.text(act, "", 28, Mib.semibold(act), Mib.WHITE);
                h.sub = Mib.text(act, "", 21, Mib.regular(act), Mib.GREY);
                texts.addView(h.title);
                texts.addView(h.sub);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tl.leftMargin = Mib.px(act, 18);
                row.addView(texts, tl);
                h.meta = Mib.text(act, "", 23, Mib.regular(act), Mib.GREY);
                row.addView(h.meta);
                row.setTag(h);
                v = row;
            }
            h = (Holder) v.getTag();
            Object o = rows.get(p);
            MusicService svc = MusicService.get();
            Track cur = svc == null ? null : svc.current();
            if (o instanceof Track) {
                Track t = (Track) o;
                h.title.setText(t.title);
                h.title.setTextColor(cur != null && cur.id == t.id ? Mib.BLUE : Mib.WHITE);
                h.sub.setText(t.artist + "  ·  " + t.album);
                h.meta.setText(Ui.time(t.duration));
                ArtLoader.into(act, h.icon, t.albumId, R.drawable.ui_ic_note);
            } else {
                MusicLibrary.Group g = (MusicLibrary.Group) o;
                h.title.setText(g.title);
                h.title.setTextColor(Mib.WHITE);
                h.sub.setText(g.subtitle);
                h.meta.setText(String.valueOf(g.count));
                int fb = tab == 2 ? R.drawable.ui_ic_person : tab == 3 ? R.drawable.ui_ic_folder : R.drawable.ui_ic_cd;
                if (tab == 1) {
                    ArtLoader.into(act, h.icon, g.artAlbumId, fb);
                } else {
                    h.icon.setTag(null);
                    h.icon.setImageResource(fb);
                    h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int pad = Mib.px(act, 8);
                    h.icon.setPadding(pad, pad, pad, pad);
                    h.icon.setImageTintList(android.content.res.ColorStateList.valueOf(Mib.GREY));
                }
            }
            return v;
        }
    }

    private static class Holder {
        ImageView icon;
        TextView title, sub, meta;
    }
}
