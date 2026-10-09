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

        // ---- Now playing (measured from the factory media screen) ----
        // Cover box: top-right, ~30% of the width; text block centred beside it;
        // progress line along the bottom of the left area.
        FrameLayout np = new FrameLayout(act);
        nowPlaying = np;
        content.addView(np, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        int box = Mib.px(act, 312), boxTop = Mib.px(act, 36), boxRight = Mib.px(act, 74);

        art = new MibArtBox(act);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(box, box, Gravity.TOP | Gravity.RIGHT);
        al.topMargin = boxTop;
        al.rightMargin = boxRight;
        np.addView(art, al);

        FrameLayout left = new FrameLayout(act);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ll.rightMargin = box + boxRight + Mib.px(act, 30);
        np.addView(left, ll);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Mib.px(act, 60), 0, 0, 0);
        info = col;
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, box);
        cl.topMargin = boxTop;
        left.addView(col, cl);
        View titleRow = metaRow(R.drawable.mib_note, 36, Mib.semibold(act));
        title = (TextView) ((ViewGroup) titleRow).getChildAt(1);
        titleRow.setPadding(0, 0, 0, 0);
        col.addView(titleRow);
        artistRow = metaRow(R.drawable.mib_person, 29, Mib.regular(act));
        artist = (TextView) ((ViewGroup) artistRow).getChildAt(1);
        col.addView(artistRow);
        albumRow = metaRow(R.drawable.mib_disc, 29, Mib.regular(act));
        album = (TextView) ((ViewGroup) albumRow).getChildAt(1);
        col.addView(albumRow);

        LinearLayout pr = new LinearLayout(act);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.setPadding(Mib.px(act, 60), 0, 0, 0);
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
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        pl.bottomMargin = Mib.px(act, 30);
        left.addView(pr, pl);

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
        sourceKey = bar.add(s("Izvor", "Source"), R.drawable.mib_usb, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSources(v);
            }
        });
        bar.add(s("Izbor", "Browse"), R.drawable.mib_list, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBrowser();
            }
        });
        bar.add(s("Prethodni", "Previous"), R.drawable.mib_prev, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaHub.get(act).previous();
            }
        });
        playKey = bar.add(s("Reproduk.", "Play"), R.drawable.mib_play, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaHub.get(act).playPause();
            }
        });
        bar.add(s("Naredni", "Next"), R.drawable.mib_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaHub.get(act).next();
            }
        });
        bar.gap(1);
        bar.add("Setup", R.drawable.mib_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.showSettings(SettingsScreen.PAGE_MEDIA);
            }
        });
        bars.addView(bar);

        browseBar = new MibBar(act);
        browseBar.add(s("Nazad", "Back"), R.drawable.mib_back, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack();
            }
        });
        String[][] tabs = {{"Pesme", "Songs"}, {"Albumi", "Albums"}, {"Izvođači", "Artists"}, {"Fascikle", "Folders"}};
        int[] icons = {R.drawable.mib_note, R.drawable.mib_disc, R.drawable.mib_person, R.drawable.mib_folder};
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
        browseBar.add("Setup", R.drawable.mib_setup, new View.OnClickListener() {
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
        row.addView(iv, new LinearLayout.LayoutParams(Mib.px(act, 32), Mib.px(act, 32)));
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
        main.post(ticker);
        if (MusicLibrary.hasPermission(act)) MusicService.start(act);
    }

    @Override
    public void onHide() {
        MediaHub.get(act).removeListener(this);
        main.removeCallbacks(ticker);
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
        boolean bt = hub.isBluetooth();
        String key = hub.packageName();
        if (bt) sourceKey.setIcon(R.drawable.mib_bt_badge);
        else if (hub.isOwn()) sourceKey.setIcon(R.drawable.mib_usb);
        else {
            Drawable d = hub.sourceIcon(key);
            if (d != null) sourceKey.setIcon(d);
            else sourceKey.setIcon(R.drawable.mib_media);
        }
        boolean playing = hub.isPlaying();
        playKey.setLabel(playing ? s("Pauza", "Pause") : s("Reproduk.", "Play"));
        playKey.setIcon(playing ? R.drawable.mib_pause : R.drawable.mib_play);

        art.setPlaceholder(bt ? R.drawable.mib_bt_logo : hub.isOwn() ? R.drawable.mib_usb : R.drawable.mib_media);
        art.setBitmap(hub.art());

        String t = hub.hasTrackInfo() ? hub.title() : null;
        if (t == null) {
            info.setVisibility(View.INVISIBLE);
            message.setVisibility(View.VISIBLE);
            message.setText(bt
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
        progressRow.setVisibility(d > 0 ? View.VISIBLE : View.INVISIBLE);
        pos.setText(Ui.time(p));
        remain.setText("-" + Ui.time(Math.max(0, d - p)));
        progress.setFraction(d > 0 ? p / (float) d : 0);
    }

    private void showSources(View key) {
        final MediaHub hub = MediaHub.get(act);
        String cur = hub.packageName();
        MibPopup p = new MibPopup(act);
        p.add("USB / SD", R.drawable.mib_usb, hub.isOwn(), new Runnable() {
            @Override
            public void run() {
                hub.select(act.getPackageName());
                MusicService.start(act);
            }
        });
        p.add("BT-audio", R.drawable.mib_bt, hub.isBluetooth(), new Runnable() {
            @Override
            public void run() {
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
            p.add("App-Connect", R.drawable.mib_appconnect, false, new Runnable() {
                @Override
                public void run() {
                    act.openProjection();
                }
            });
        }
        p.add("Radio", R.drawable.mib_radio, false, new Runnable() {
            @Override
            public void run() {
                act.openRadio();
            }
        });
        if (!hub.hasNotificationAccess()) {
            p.add(s("Poveži aplikacije", "Connect apps"), R.drawable.mib_plus, false, new Runnable() {
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
        bar.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);
        browseBar.setVisibility(View.VISIBLE);
        loadTab();
    }

    private void closeBrowser() {
        browser.setVisibility(View.GONE);
        browseBar.setVisibility(View.GONE);
        nowPlaying.setVisibility(View.VISIBLE);
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
                            empty.setText(MusicLibrary.hasPermission(act)
                                    ? s("Nema medija.\nUbacite USB ili SD karticu sa muzikom.",
                                    "No media.\nInsert a USB stick or SD card with music.")
                                    : s("Dozvolite pristup muzici.", "Allow access to music."));
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
        MusicService svc = MusicService.get();
        if (svc == null) {
            MusicService.start(act);
            return;
        }
        List<Track> queue = new ArrayList<>();
        for (Object r : rows) if (r instanceof Track) queue.add((Track) r);
        svc.playQueue(queue, queue.indexOf(o), openGroup != null ? openGroup.key : MusicLibrary.CTX_ALL);
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
                ArtLoader.into(act, h.icon, t.albumId, R.drawable.mib_note);
            } else {
                MusicLibrary.Group g = (MusicLibrary.Group) o;
                h.title.setText(g.title);
                h.title.setTextColor(Mib.WHITE);
                h.sub.setText(g.subtitle);
                h.meta.setText(String.valueOf(g.count));
                int fb = tab == 2 ? R.drawable.mib_person : tab == 3 ? R.drawable.mib_folder : R.drawable.mib_disc;
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
