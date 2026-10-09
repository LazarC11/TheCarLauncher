package com.thecarlauncher.screens;

import android.location.Location;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibPopup;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.NavHelper;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.views.MapTileView;

/** MIB "Navigacija": full map, square map buttons on the right, soft keys below. */
public class NavScreen extends Screen implements LocationTracker.Listener {
    private static final int[] VIEW_ZOOMS = {17, 15, 13};
    private View root;
    private MapTileView map;
    private TextView status;
    private ImageView follow;

    public NavScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return Mib.t(act, "Navigacija", "Navigation"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        root = r;
        FrameLayout mapFrame = new FrameLayout(act);
        r.addView(mapFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        map = new MapTileView(act, null);
        map.setSource(Prefs.tiles(act));
        map.setInteractive(true);
        map.setZoom(Prefs.integer(act, Prefs.MAP_ZOOM, 16));
        mapFrame.addView(map);

        // Right-hand square buttons.
        LinearLayout side = new LinearLayout(act);
        side.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
        sl.rightMargin = Mib.px(act, 16);
        sl.topMargin = Mib.px(act, 16);
        sl.bottomMargin = Mib.px(act, 16);
        mapFrame.addView(side, sl);
        int box = Mib.px(act, 96);

        FrameLayout followBox = new FrameLayout(act);
        followBox.setBackground(Mib.box(act));
        follow = Mib.icon(act, R.drawable.mib_recenter, Mib.WHITE);
        followBox.addView(follow, new FrameLayout.LayoutParams(Mib.px(act, 46), Mib.px(act, 46), Gravity.CENTER));
        followBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                map.setFollow(true);
            }
        });
        side.addView(followBox, new LinearLayout.LayoutParams(box, box));

        View spacer = new View(act);
        side.addView(spacer, new LinearLayout.LayoutParams(box, 0, 1f));

        LinearLayout zoom = new LinearLayout(act);
        zoom.setOrientation(LinearLayout.VERTICAL);
        zoom.setBackground(Mib.box(act));
        zoom.addView(zoomCell(R.drawable.mib_zoom_in, 1), new LinearLayout.LayoutParams(box, box));
        View div = new View(act);
        div.setBackgroundColor(0xFFD6D6DC);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Mib.px(act, 1.4f)));
        dl.leftMargin = dl.rightMargin = Mib.px(act, 10);
        zoom.addView(div, dl);
        zoom.addView(zoomCell(R.drawable.mib_zoom_out, -1), new LinearLayout.LayoutParams(box, box));
        side.addView(zoom);

        map.setListener(new MapTileView.Listener() {
            @Override
            public void onFollowChanged(boolean f) {
                follow.setImageTintList(android.content.res.ColorStateList.valueOf(f ? Mib.WHITE : Mib.BLUE));
            }
        });

        status = Mib.text(act, "", 28, Mib.semibold(act), Mib.WHITE);
        status.setBackground(Mib.panel(act));
        status.setPadding(Mib.px(act, 18), Mib.px(act, 10), Mib.px(act, 18), Mib.px(act, 10));
        mapFrame.addView(status, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView attrib = Mib.text(act, "© OpenStreetMap  © CARTO", 11, Mib.regular(act), 0xAA3A3A3A);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        al.leftMargin = al.bottomMargin = Mib.px(act, 6);
        mapFrame.addView(attrib, al);

        // Soft keys.
        MibBar bar = new MibBar(act);
        bar.add(s("Novo odr.", "New dest."), R.drawable.mib_flag, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.text(act, s("Novo odredište", "New destination"), s("Ulica, grad", "Street, city"), "",
                        new Dialogs.TextEntered() {
                            @Override
                            public void onText(String text) {
                                NavHelper.navigateTo(act, text);
                            }
                        });
            }
        });
        bar.add(s("Moja odred.", "My dest."), R.drawable.mib_myplaces, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPlaces(v);
            }
        });
        bar.add(s("Pogled", "View"), R.drawable.mib_view, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int z = map.getZoom(), next = VIEW_ZOOMS[0];
                for (int i = 0; i < VIEW_ZOOMS.length; i++) {
                    if (z >= VIEW_ZOOMS[i]) {
                        next = VIEW_ZOOMS[(i + 1) % VIEW_ZOOMS.length];
                        break;
                    }
                }
                map.setZoom(next);
                map.setFollow(true);
            }
        });
        bar.add(s("Mediji", "Media"), R.drawable.mib_media, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.MEDIA, true);
            }
        });
        bar.add(s("Aplikacija", "Nav app"), R.drawable.mib_nav, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavHelper.openApp(act, LocationTracker.get(act).location());
            }
        });
        bar.gap(1);
        bar.add("Setup", R.drawable.mib_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.showSettings(SettingsScreen.PAGE_NAV);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        return r;
    }

    private View zoomCell(int icon, final int delta) {
        FrameLayout f = new FrameLayout(act);
        f.setBackground(Mib.keyPressed());
        f.addView(Mib.icon(act, icon, Mib.WHITE), new FrameLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40),
                Gravity.CENTER));
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                map.setZoom(map.getZoom() + delta);
            }
        });
        return f;
    }

    private void showPlaces(View key) {
        MibPopup p = new MibPopup(act).columns(1);
        place(p, R.drawable.mib_myplaces, s("Kuća", "Home"), Prefs.HOME_ADDR);
        place(p, R.drawable.mib_flag, s("Posao", "Work"), Prefs.WORK_ADDR);
        p.show(root, key);
    }

    private void place(MibPopup p, int icon, final String name, final String key) {
        final String addr = Prefs.str(act, key, null);
        p.add(Ui.isEmpty(addr) ? name + " — " + s("podesi", "set") : name, icon, false, new Runnable() {
            @Override
            public void run() {
                if (Ui.isEmpty(addr)) {
                    Dialogs.text(act, name, s("Ulica, grad", "Street, city"), "", new Dialogs.TextEntered() {
                        @Override
                        public void onText(String text) {
                            if (Ui.isEmpty(text)) return;
                            Prefs.put(act, key, text);
                            NavHelper.navigateTo(act, text);
                        }
                    });
                } else {
                    NavHelper.navigateTo(act, addr);
                }
            }
        });
    }

    @Override
    public void onShow() {
        LocationTracker.get(act).addListener(this);
    }

    @Override
    public void onHide() {
        LocationTracker.get(act).removeListener(this);
        Prefs.put(act, Prefs.MAP_ZOOM, map.getZoom());
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        Location l = t.location();
        if (l != null) map.setMyLocation(l.getLatitude(), l.getLongitude(), t.bearing(), l.getAccuracy(), t.hasFix());
        if (!t.hasPermission()) {
            status.setVisibility(View.VISIBLE);
            status.setText(s("Dozvolite pristup lokaciji", "Allow location access"));
            status.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    act.requestMissingPermissions(true);
                }
            });
        } else if (l == null) {
            status.setVisibility(View.VISIBLE);
            status.setText(s("Čeka se GPS signal…", "Waiting for GPS…"));
        } else {
            status.setVisibility(View.GONE);
        }
    }
}
