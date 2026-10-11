package com.thecarlauncher.screens;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibPopup;
import com.thecarlauncher.nav.Guidance;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.NavHelper;
import com.thecarlauncher.nav.OsmApi;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.views.MapTileView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * MIB "Navigacija": map with the factory buttons, plus built-in routing on OpenStreetMap —
 * address search (Nominatim), routes with turn-by-turn guidance (OSRM) and nearby places
 * (Overpass). Google Maps / Waze stay one key away.
 */
public class NavScreen extends Screen implements LocationTracker.Listener, Guidance.Listener {
    private static final int[] VIEW_ZOOMS = {17, 15, 13};

    /** What a chosen search result is for. */
    private interface Pick { void picked(OsmApi.Place p); }

    private View root;
    private MapTileView map;
    private TextView status;
    private ImageView follow;
    // Guidance panels
    private LinearLayout turnBox, infoBox;
    private ImageView turnIcon;
    private TextView turnDist, turnText, turnStreet, infoText;
    private MibBar.Key appKey;
    // Results list
    private LinearLayout listArea, listRows;
    private TextView listTitle;
    private Guidance guide;

    public NavScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return Mib.t(act, "Navigacija", "Navigation"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    private String lang() {
        return "en".equals(Prefs.str(act, Prefs.LANGUAGE, "sr")) ? "en" : "sr-Latn,sr,hr,bs,en";
    }

    @Override
    protected View create(ViewGroup parent) {
        guide = Guidance.get(act);
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        root = r;
        FrameLayout mapFrame = new FrameLayout(act);
        // Map, and (in split screen) the media column beside it.
        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(mid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mid.addView(mapFrame, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        splitLine = new View(act);
        splitLine.setBackgroundColor(0xFF55565C);
        mid.addView(splitLine, new LinearLayout.LayoutParams(Math.max(1, Mib.px(act, 1)), ViewGroup.LayoutParams.MATCH_PARENT));
        splitPanel = new NavMediaPanel(act, true, mediaActions);
        mid.addView(splitPanel, new LinearLayout.LayoutParams(
                (int) (act.getResources().getDisplayMetrics().widthPixels * 0.36f), ViewGroup.LayoutParams.MATCH_PARENT));
        boolean split = Prefs.bool(act, P_SPLIT, false);
        splitPanel.setVisibility(split ? View.VISIBLE : View.GONE);
        splitLine.setVisibility(split ? View.VISIBLE : View.GONE);

        map = new MapTileView(act, null);
        map.setSource(Prefs.tiles(act));
        map.setInteractive(true);
        map.setZoom(Prefs.integer(act, Prefs.MAP_ZOOM, 16));
        map.setLongPressListener(new MapTileView.LongPressListener() {
            @Override
            public void onLongPress(final double lat, final double lon) {
                confirmPoint(lat, lon);
            }
        });
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
        follow = Mib.icon(act, R.drawable.ui_ic_compass, Mib.WHITE);
        followBox.addView(follow, new FrameLayout.LayoutParams(Mib.px(act, 60), Mib.px(act, 60), Gravity.CENTER));
        followBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                map.setFollow(true);
            }
        });
        side.addView(followBox, new LinearLayout.LayoutParams(box, box));
        side.addView(new View(act), new LinearLayout.LayoutParams(box, 0, 1f));

        LinearLayout zoom = new LinearLayout(act);
        zoom.setOrientation(LinearLayout.VERTICAL);
        zoom.setBackground(Mib.box(act));
        zoom.addView(zoomCell(R.drawable.ui_map_zoom_in, 1), new LinearLayout.LayoutParams(box, box));
        View div = new View(act);
        div.setBackgroundColor(0xFFD6D6DC);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Mib.px(act, 1.4f)));
        dl.leftMargin = dl.rightMargin = Mib.px(act, 10);
        zoom.addView(div, dl);
        zoom.addView(zoomCell(R.drawable.ui_map_zoom_out, -1), new LinearLayout.LayoutParams(box, box));
        side.addView(zoom);

        map.setListener(new MapTileView.Listener() {
            @Override
            public void onFollowChanged(boolean f) {
                follow.setImageTintList(android.content.res.ColorStateList.valueOf(f ? Mib.WHITE : Mib.BLUE));
            }
        });

        // Manoeuvre box (top-left) and route info (bottom-left), black with a white frame.
        turnBox = new LinearLayout(act);
        turnBox.setOrientation(LinearLayout.VERTICAL);
        turnBox.setBackground(Mib.panel(act));
        turnBox.setPadding(Mib.px(act, 16), Mib.px(act, 10), Mib.px(act, 16), Mib.px(act, 12));
        LinearLayout tr = new LinearLayout(act);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        turnIcon = new ImageView(act);
        tr.addView(turnIcon, new LinearLayout.LayoutParams(Mib.px(act, 84), Mib.px(act, 84)));
        turnDist = Mib.text(act, "", 46, Mib.semibold(act), Mib.WHITE);
        turnDist.setPadding(Mib.px(act, 14), 0, 0, 0);
        tr.addView(turnDist);
        turnBox.addView(tr);
        turnText = Mib.text(act, "", 25, Mib.regular(act), Mib.WHITE);
        turnBox.addView(turnText);
        turnStreet = Mib.text(act, "", 25, Mib.semibold(act), Mib.WHITE);
        turnBox.addView(turnStreet);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Mib.px(act, 330), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        tl.leftMargin = tl.topMargin = Mib.px(act, 16);
        mapFrame.addView(turnBox, tl);

        infoBox = new LinearLayout(act);
        infoBox.setGravity(Gravity.CENTER_VERTICAL);
        infoBox.setBackground(Mib.panel(act));
        infoBox.setPadding(Mib.px(act, 16), Mib.px(act, 8), Mib.px(act, 18), Mib.px(act, 8));
        ImageView flag = new ImageView(act);
        flag.setImageDrawable(new Flag());
        infoBox.addView(flag, new LinearLayout.LayoutParams(Mib.px(act, 30), Mib.px(act, 30)));
        infoText = Mib.text(act, "", 26, Mib.semibold(act), Mib.WHITE);
        infoText.setPadding(Mib.px(act, 12), 0, 0, 0);
        infoBox.addView(infoText);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        il.leftMargin = Mib.px(act, 16);
        il.bottomMargin = Mib.px(act, 30);
        mapFrame.addView(infoBox, il);

        status = Mib.text(act, "", 28, Mib.semibold(act), Mib.WHITE);
        status.setBackground(Mib.panel(act));
        status.setPadding(Mib.px(act, 18), Mib.px(act, 10), Mib.px(act, 18), Mib.px(act, 10));
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(false);
        mapFrame.addView(status, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        String ck = Prefs.str(act, Prefs.CARTO_KEY, null);
        boolean carto = ck != null && !ck.trim().isEmpty() && !Prefs.TILES_OSM.equals(Prefs.tiles(act));
        TextView attrib = Mib.text(act, carto ? "© OpenStreetMap  © CARTO  · OSRM" : "© OpenStreetMap  · OSRM",
                11, Mib.regular(act), 0xAA3A3A3A);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        al.leftMargin = al.bottomMargin = Mib.px(act, 6);
        mapFrame.addView(attrib, al);

        buildList(mapFrame);

        // Small media pop-up over the map (the Media key opens this instead of leaving the map).
        miniPanel = new NavMediaPanel(act, false, mediaActions);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ml.bottomMargin = Mib.px(act, 16);
        miniPanel.setVisibility(View.GONE);
        mapFrame.addView(miniPanel, ml);

        // Soft keys.
        MibBar bar = new MibBar(act);
        bar.add(s("Novo odr.", "New dest."), R.drawable.ui_key_newdest, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askSearch(s("Novo odredište", "New destination"), new Pick() {
                    public void picked(OsmApi.Place p) { go(p); }
                });
            }
        });
        bar.add(s("Moja odred.", "My dest."), R.drawable.ui_key_mydest, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPlaces(v);
            }
        });
        bar.add(s("Spec. odred.", "Nearby"), R.drawable.ui_ic_search, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNearby(v);
            }
        });
        bar.add(s("Pogled", "View"), R.drawable.ui_key_view, new View.OnClickListener() {
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
        View mediaKey = bar.add(s("Mediji", "Media"), R.drawable.ui_key_music, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (splitPanel.getVisibility() == View.VISIBLE) setSplit(false);
                else if (miniPanel.getVisibility() == View.VISIBLE) hideMini();
                else showMini();
            }
        });
        // Long press: the full media screen.
        mediaKey.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                act.show(LauncherActivity.MEDIA, true);
                return true;
            }
        });
        appKey = bar.add(s("Aplikacija", "Nav app"), R.drawable.ui_ic_nav, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (guide.active() || guide.arrived()) {
                    guide.stop();
                } else {
                    NavHelper.openApp(act, LocationTracker.get(act).location());
                }
            }
        });
        bar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.showSettings(SettingsScreen.PAGE_NAV);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        onGuidanceChanged();
        return r;
    }

    private View zoomCell(int icon, final int delta) {
        FrameLayout f = new FrameLayout(act);
        f.setBackground(Mib.keyPressed());
        f.addView(Mib.icon(act, icon, Mib.WHITE), new FrameLayout.LayoutParams(Mib.px(act, 80), Mib.px(act, 60),
                Gravity.CENTER));
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                map.setZoom(map.getZoom() + delta);
            }
        });
        return f;
    }

    // ---- Destination search -------------------------------------------------------------------

    private void askSearch(String title, final Pick pick) {
        Dialogs.text(act, title, s("Ulica i broj, grad ili mesto", "Street and number, city or place"), "",
                new Dialogs.TextEntered() {
                    @Override
                    public void onText(String text) {
                        if (!Ui.isEmpty(text)) search(text, pick);
                    }
                });
    }

    private void search(final String query, final Pick pick) {
        Location l = LocationTracker.get(act).location();
        showList(s("Pretraga: ", "Search: ") + query);
        listRows.removeAllViews();
        row(R.drawable.ui_ic_search, s("Traži se…", "Searching…"), null, null);
        OsmApi.search(query, l == null ? Double.NaN : l.getLatitude(), l == null ? Double.NaN : l.getLongitude(), lang(),
                new OsmApi.Callback<List<OsmApi.Place>>() {
                    @Override
                    public void done(List<OsmApi.Place> res, String err) {
                        listRows.removeAllViews();
                        if (res == null) {
                            row(R.drawable.ui_ic_refresh, s("Nema veze sa serverom — pokušaj ponovo", "No connection — try again"),
                                    err, new Runnable() {
                                        public void run() { search(query, pick); }
                                    });
                            return;
                        }
                        if (res.isEmpty()) row(R.drawable.ui_ic_search, s("Nema rezultata", "No results"), null, null);
                        for (final OsmApi.Place p : res) placeRow(p, pick);
                    }
                });
    }

    private void showNearby(View key) {
        final String[][] kinds = {
                {"amenity=fuel", "Benzinska pumpa", "Fuel station"},
                {"amenity=parking", "Parking", "Parking"},
                {"amenity=charging_station", "Punjač za auto", "EV charger"},
                {"shop=car_repair", "Auto servis", "Car repair"},
                {"amenity=cafe", "Kafić", "Café"},
                {"amenity=restaurant", "Restoran", "Restaurant"},
                {"amenity=pharmacy", "Apoteka", "Pharmacy"},
                {"amenity=atm", "Bankomat", "ATM"},
        };
        MibPopup p = new MibPopup(act).columns(2);
        for (final String[] k : kinds) {
            p.add(s(k[1], k[2]), R.drawable.ui_ic_search, false, new Runnable() {
                @Override
                public void run() {
                    nearby(k[0], s(k[1], k[2]));
                }
            });
        }
        p.show(root, key);
    }

    private void nearby(final String tag, final String title) {
        final Location l = LocationTracker.get(act).location();
        if (l == null) {
            Ui.toast(act, s("Čeka se GPS pozicija", "Waiting for GPS position"));
            return;
        }
        showList(title + s(" u blizini", " nearby"));
        listRows.removeAllViews();
        row(R.drawable.ui_ic_search, s("Traži se…", "Searching…"), null, null);
        OsmApi.nearby(tag, l.getLatitude(), l.getLongitude(), 5000, new OsmApi.Callback<List<OsmApi.Place>>() {
            @Override
            public void done(List<OsmApi.Place> res, String err) {
                listRows.removeAllViews();
                if (res == null) {
                    row(R.drawable.ui_ic_refresh, s("Nema veze sa serverom — pokušaj ponovo", "No connection — try again"), err,
                            new Runnable() {
                                public void run() { nearby(tag, title); }
                            });
                    return;
                }
                if (res.isEmpty()) row(R.drawable.ui_ic_search, s("Ništa u krugu od 5 km", "Nothing within 5 km"), null, null);
                for (OsmApi.Place p : res) {
                    if (p.name.length() == 0) p = new OsmApi.Place(title, p.detail, p.lat, p.lon);
                    placeRow(withDistance(p, l), new Pick() {
                        public void picked(OsmApi.Place x) { go(x); }
                    });
                }
            }
        });
    }

    private static OsmApi.Place withDistance(OsmApi.Place p, Location l) {
        p.distanceM = (float) OsmApi.meters(l.getLatitude(), l.getLongitude(), p.lat, p.lon);
        return p;
    }

    private void confirmPoint(final double lat, final double lon) {
        new AlertDialog.Builder(act)
                .setTitle(s("Navigacija do ove tačke?", "Navigate to this point?"))
                .setMessage(String.format(Locale.US, "%.5f, %.5f", lat, lon))
                .setPositiveButton(s("Start", "Start"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        go(new OsmApi.Place(s("Tačka na mapi", "Point on map"), "", lat, lon));
                    }
                })
                .setNegativeButton(s("Otkaži", "Cancel"), null)
                .show();
    }

    // ---- My destinations ----------------------------------------------------------------------

    private void showPlaces(View key) {
        MibPopup p = new MibPopup(act).columns(1);
        place(p, R.drawable.ui_ic_home, s("Kuća", "Home"), Prefs.HOME_ADDR);
        place(p, R.drawable.ui_ic_flag, s("Posao", "Work"), Prefs.WORK_ADDR);
        p.add(s("Poslednja odredišta", "Recent destinations"), R.drawable.ui_ic_clock, false, new Runnable() {
            @Override
            public void run() {
                showRecent();
            }
        });
        p.show(root, key);
    }

    /** Home / Work: stored as "name|lat|lon"; older plain-text addresses are searched first. */
    private void place(MibPopup p, int icon, final String name, final String key) {
        final String saved = Prefs.str(act, key, null);
        p.add(Ui.isEmpty(saved) ? name + " — " + s("podesi", "set") : name, icon, false, new Runnable() {
            @Override
            public void run() {
                OsmApi.Place stored = parse(saved);
                Pick store = new Pick() {
                    public void picked(OsmApi.Place x) {
                        Prefs.put(act, key, x.name + "|" + x.lat + "|" + x.lon);
                        go(new OsmApi.Place(name, x.name, x.lat, x.lon));
                    }
                };
                if (stored != null) go(new OsmApi.Place(name, stored.name, stored.lat, stored.lon));
                else if (!Ui.isEmpty(saved)) search(saved, store);
                else askSearch(name, store);
            }
        });
    }

    private static OsmApi.Place parse(String v) {
        if (v == null) return null;
        String[] f = v.split("\\|");
        if (f.length < 3) return null;
        try {
            return new OsmApi.Place(f[0], "", Double.parseDouble(f[1]), Double.parseDouble(f[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showRecent() {
        showList(s("Poslednja odredišta", "Recent destinations"));
        listRows.removeAllViews();
        List<OsmApi.Place> rec = guide.recent();
        if (rec.isEmpty()) row(R.drawable.ui_ic_clock, s("Još nema odredišta", "No destinations yet"), null, null);
        Location l = LocationTracker.get(act).location();
        for (OsmApi.Place p : rec) {
            placeRow(l == null ? p : withDistance(p, l), new Pick() {
                public void picked(OsmApi.Place x) { go(x); }
            });
        }
        row(R.drawable.ui_ic_home, s("Promeni adresu kuće", "Change home address"), null, new Runnable() {
            public void run() { resetPlace(Prefs.HOME_ADDR, s("Kuća", "Home")); }
        });
        row(R.drawable.ui_ic_flag, s("Promeni adresu posla", "Change work address"), null, new Runnable() {
            public void run() { resetPlace(Prefs.WORK_ADDR, s("Posao", "Work")); }
        });
    }

    private void resetPlace(final String key, final String name) {
        askSearch(name, new Pick() {
            public void picked(OsmApi.Place x) {
                Prefs.put(act, key, x.name + "|" + x.lat + "|" + x.lon);
                Ui.toast(act, name + ": " + x.name);
                hideList();
            }
        });
    }

    // ---- Results list overlay -----------------------------------------------------------------

    private void buildList(FrameLayout frame) {
        listArea = new LinearLayout(act);
        listArea.setOrientation(LinearLayout.VERTICAL);
        listArea.setBackgroundColor(0xFF000000);
        listArea.setPadding(Mib.px(act, 40), Mib.px(act, 10), Mib.px(act, 40), 0);
        listArea.setVisibility(View.GONE);
        listArea.setClickable(true);
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        listTitle = Mib.text(act, "", 28, Mib.semibold(act), Mib.WHITE);
        head.addView(listTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout back = new FrameLayout(act);
        back.setBackground(Mib.box(act));
        back.addView(Mib.icon(act, R.drawable.ui_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 36), Mib.px(act, 36), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideList();
            }
        });
        head.addView(back, new LinearLayout.LayoutParams(Mib.px(act, 62), Mib.px(act, 52)));
        listArea.addView(head);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2)));
        rl.topMargin = Mib.px(act, 8);
        listArea.addView(rule, rl);
        ScrollView sv = new ScrollView(act);
        listRows = new LinearLayout(act);
        listRows.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listRows);
        listArea.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        frame.addView(listArea, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showList(String title) {
        listTitle.setText(title);
        listArea.setVisibility(View.VISIBLE);
    }

    private void hideList() {
        listArea.setVisibility(View.GONE);
    }

    private void placeRow(final OsmApi.Place p, final Pick pick) {
        String dist = p.distanceM >= 0 ? Guidance.distance(p.distanceM) : null;
        View v = row(R.drawable.ui_ic_flag, p.name, (p.detail.length() > 0 ? p.detail : "") , new Runnable() {
            public void run() {
                hideList();
                pick.picked(p);
            }
        });
        if (dist != null) {
            TextView d = Mib.text(act, dist, 25, Mib.regular(act), Mib.GREY);
            ((LinearLayout) v).addView(d);
        }
    }

    private View row(int icon, String title, String sub, final Runnable click) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(act, 10), Mib.px(act, 8), Mib.px(act, 10), Mib.px(act, 8));
        row.setMinimumHeight(Mib.px(act, 76));
        if (click != null) {
            row.setBackground(Mib.keyPressed());
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    click.run();
                }
            });
        }
        row.addView(Mib.icon(act, icon, Mib.WHITE), new LinearLayout.LayoutParams(Mib.px(act, 34), Mib.px(act, 34)));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 18), 0, Mib.px(act, 12), 0);
        col.addView(Mib.text(act, title, 28, Mib.regular(act), Mib.WHITE));
        if (sub != null && sub.length() > 0) col.addView(Mib.text(act, sub, 22, Mib.regular(act), Mib.GREY));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        listRows.addView(row);
        View div = new View(act);
        div.setBackgroundColor(0xFF55565F);
        listRows.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1))));
        return row;
    }

    // ---- Guidance -----------------------------------------------------------------------------

    private void go(OsmApi.Place p) {
        guide.start(p.name, p.lat, p.lon);
        map.setDestination(true, p.lat, p.lon);
        map.setFollow(true);
    }

    @Override
    public void onGuidanceChanged() {
        if (map == null) return;
        boolean on = guide.active() || guide.arrived();
        appKey.setLabel(on ? s("Zaustavi", "Stop") : s("Aplikacija", "Nav app"));
        appKey.setIcon(on ? R.drawable.ui_ic_back : R.drawable.ui_ic_nav);
        OsmApi.Route r = guide.route();
        map.setRoute(r == null ? null : r.lat, r == null ? null : r.lon);
        map.setRouteProgress(guide.progressIndex());
        map.setDestination(on, guide.destLat(), guide.destLon());

        if (r != null) {
            OsmApi.Step st = guide.nextStep();
            turnBox.setVisibility(View.VISIBLE);
            turnIcon.setImageDrawable(new TurnArrow(st == null ? "arrive" : st.type, st == null ? "" : st.modifier,
                    st == null ? 0 : st.exit));
            turnDist.setText(Guidance.distance(st == null ? guide.remaining() : guide.toNext()));
            turnText.setText(st == null ? s("Odredište", "Destination") : guide.instruction(st));
            String street = st == null ? guide.destination() : st.name;
            turnStreet.setText(street == null ? "" : street);
            turnStreet.setVisibility(street == null || street.length() == 0 ? View.GONE : View.VISIBLE);
            Calendar eta = Calendar.getInstance();
            eta.add(Calendar.SECOND, (int) guide.remainingSeconds());
            infoBox.setVisibility(View.VISIBLE);
            infoText.setText(Guidance.distance(guide.remaining()) + "   " + String.format(Locale.US, "%d:%02d",
                    eta.get(Calendar.HOUR_OF_DAY), eta.get(Calendar.MINUTE)));
        } else {
            turnBox.setVisibility(View.GONE);
            infoBox.setVisibility(View.GONE);
        }
        updateStatus();
    }

    private void updateStatus() {
        LocationTracker t = LocationTracker.get(act);
        String msg = null;
        status.setOnClickListener(null);
        if (guide.routing()) msg = s("Izračunavanje rute…", "Calculating route…");
        else if (guide.error() != null) msg = guide.error();
        else if (guide.arrived()) msg = s("Stigli ste na odredište", "You have arrived");
        else if (!t.hasPermission()) {
            msg = s("Dozvolite pristup lokaciji", "Allow location access");
            status.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    act.requestMissingPermissions(true);
                }
            });
        } else if (t.location() == null) msg = s("Čeka se GPS signal…", "Waiting for GPS…");
        status.setVisibility(msg == null ? View.GONE : View.VISIBLE);
        if (msg != null) status.setText(msg);
    }

    // ---- Media over the map -------------------------------------------------------------------

    private static final String P_SPLIT = "nav_media_split";
    private static final long MINI_TIMEOUT = 10_000L;
    private NavMediaPanel miniPanel, splitPanel;
    private View splitLine;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    private final NavMediaPanel.Actions mediaActions = new NavMediaPanel.Actions() {
        public void onSplit() {
            hideMini();
            setSplit(true);
        }

        public void onClose() {
            if (splitPanel.getVisibility() == View.VISIBLE) setSplit(false);
            hideMini();
        }

        public void onTouched() {
            if (miniPanel.getVisibility() == View.VISIBLE) {
                ui.removeCallbacks(autoHide);
                ui.postDelayed(autoHide, MINI_TIMEOUT);
            }
        }
    };

    private final Runnable autoHide = new Runnable() {
        public void run() { hideMini(); }
    };

    private final MediaHub.Listener mediaListener = new MediaHub.Listener() {
        public void onMediaChanged() { refreshMedia(); }
    };

    /** Keeps the progress line moving while a media panel is showing. */
    private final Runnable mediaTick = new Runnable() {
        public void run() {
            refreshMedia();
            if (miniPanel.getVisibility() == View.VISIBLE || splitPanel.getVisibility() == View.VISIBLE) {
                ui.postDelayed(this, 1000);
            }
        }
    };

    private void refreshMedia() {
        if (miniPanel == null) return;
        if (miniPanel.getVisibility() == View.VISIBLE) miniPanel.update();
        if (splitPanel.getVisibility() == View.VISIBLE) splitPanel.update();
    }

    private void showMini() {
        miniPanel.update();
        miniPanel.setVisibility(View.VISIBLE);
        miniPanel.setAlpha(0f);
        miniPanel.setTranslationY(Mib.px(act, 30));
        miniPanel.animate().alpha(1f).translationY(0).setDuration(180).start();
        ui.removeCallbacks(autoHide);
        ui.postDelayed(autoHide, MINI_TIMEOUT);
        ui.removeCallbacks(mediaTick);
        ui.post(mediaTick);
    }

    private void hideMini() {
        ui.removeCallbacks(autoHide);
        if (miniPanel.getVisibility() != View.VISIBLE) return;
        miniPanel.animate().alpha(0f).translationY(Mib.px(act, 30)).setDuration(160).withEndAction(new Runnable() {
            public void run() { miniPanel.setVisibility(View.GONE); }
        }).start();
    }

    private void setSplit(boolean on) {
        Prefs.put(act, P_SPLIT, on);
        splitPanel.setVisibility(on ? View.VISIBLE : View.GONE);
        splitLine.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            splitPanel.update();
            ui.removeCallbacks(mediaTick);
            ui.post(mediaTick);
        }
    }

    @Override
    public void onShow() {
        MediaHub.get(act).addListener(mediaListener);
        if (splitPanel != null && splitPanel.getVisibility() == View.VISIBLE) {
            ui.removeCallbacks(mediaTick);
            ui.post(mediaTick);
        }
        LocationTracker.get(act).addListener(this);
        guide.addListener(this);
        onGuidanceChanged();
    }

    @Override
    public void onHide() {
        MediaHub.get(act).removeListener(mediaListener);
        ui.removeCallbacks(mediaTick);
        if (miniPanel != null) {
            ui.removeCallbacks(autoHide);
            miniPanel.setVisibility(View.GONE);
        }
        LocationTracker.get(act).removeListener(this);
        guide.removeListener(this);
        Prefs.put(act, Prefs.MAP_ZOOM, map.getZoom());
    }

    @Override
    public boolean onBack() {
        if (listArea.getVisibility() == View.VISIBLE) {
            hideList();
            return true;
        }
        return false;
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        Location l = t.location();
        if (l != null) map.setMyLocation(l.getLatitude(), l.getLongitude(), t.bearing(), l.getAccuracy(), t.hasFix());
        updateStatus();
    }

    // ---- Glyphs -------------------------------------------------------------------------------

    /** Manoeuvre arrow in the factory style: thick white stem bending into the turn direction. */
    static final class TurnArrow extends Drawable {
        private final String type, modifier;
        private final int exit;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        TurnArrow(String type, String modifier, int exit) {
            this.type = type;
            this.modifier = modifier == null ? "" : modifier;
            this.exit = exit;
        }

        private float angle() {
            switch (modifier) {
                case "sharp left": return -135;
                case "left": return -90;
                case "slight left": return -45;
                case "slight right": return 45;
                case "right": return 90;
                case "sharp right": return 135;
                case "uturn": return 180;
                default: return 0;
            }
        }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            c.save();
            c.translate(getBounds().left, getBounds().top);
            p.setColor(Mib.WHITE);
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setStrokeJoin(Paint.Join.ROUND);
            float sw = w * 0.13f;
            if ("arrive".equals(type)) {
                new Flag().drawIn(c, w, h);
                c.restore();
                return;
            }
            boolean round = type.startsWith("roundabout") || "rotary".equals(type);
            float cx = w / 2f, base = h * 0.92f, mid = h * 0.5f;
            Path path = new Path();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            if (round) {
                float r = w * 0.17f;
                p.setStrokeWidth(sw * 0.7f);
                c.drawCircle(cx, mid, r, p);
                p.setStrokeWidth(sw);
                c.drawLine(cx, base, cx, mid + r, p);
                double a = Math.toRadians(-90 + Math.max(-150, Math.min(150, (exit <= 0 ? 2 : exit) * 70 - 140)));
                float ex = cx + (float) Math.cos(a) * r, ey = mid + (float) Math.sin(a) * r;
                float tx = cx + (float) Math.cos(a) * w * 0.42f, ty = mid + (float) Math.sin(a) * h * 0.42f;
                c.drawLine(ex, ey, tx, ty, p);
                head(c, ex, ey, tx, ty, sw);
            } else if ("uturn".equals(modifier)) {
                float r = w * 0.18f;
                path.moveTo(cx + r, base);
                path.lineTo(cx + r, mid);
                path.arcTo(new RectF(cx - r, mid - r, cx + r, mid + r), 0, -180);
                path.lineTo(cx - r, mid + h * 0.12f);
                c.drawPath(path, p);
                head(c, cx - r, mid, cx - r, mid + h * 0.2f, sw);
            } else {
                double a = Math.toRadians(angle() - 90);
                float len = h * 0.36f;
                float tx = cx + (float) Math.cos(a) * len, ty = mid + (float) Math.sin(a) * len;
                path.moveTo(cx, base);
                path.lineTo(cx, mid);
                path.lineTo(tx, ty);
                c.drawPath(path, p);
                head(c, cx + (tx - cx) * 0.4f, mid + (ty - mid) * 0.4f, tx, ty, sw);
            }
            c.restore();
        }

        /** Filled arrowhead at (tx, ty) pointing away from (fx, fy). */
        private void head(Canvas c, float fx, float fy, float tx, float ty, float sw) {
            double a = Math.atan2(ty - fy, tx - fx);
            float l = sw * 1.9f, wd = sw * 1.5f;
            float bx = tx - (float) Math.cos(a) * l * 0.2f, by = ty - (float) Math.sin(a) * l * 0.2f;
            Path h = new Path();
            h.moveTo(bx + (float) Math.cos(a) * l, by + (float) Math.sin(a) * l);
            h.lineTo(bx + (float) Math.cos(a + Math.PI / 2) * wd, by + (float) Math.sin(a + Math.PI / 2) * wd);
            h.lineTo(bx + (float) Math.cos(a - Math.PI / 2) * wd, by + (float) Math.sin(a - Math.PI / 2) * wd);
            h.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(h, p);
            p.setStyle(Paint.Style.STROKE);
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Chequered destination flag. */
    static final class Flag extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        void drawIn(Canvas c, float w, float h) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Mib.WHITE);
            c.drawRect(w * 0.18f, h * 0.08f, w * 0.25f, h * 0.95f, p);
            float fx = w * 0.25f, fy = h * 0.1f, fw = w * 0.6f, fh = h * 0.42f;
            int cols = 4, rows = 3;
            for (int r = 0; r < rows; r++) {
                for (int k = 0; k < cols; k++) {
                    p.setColor((r + k) % 2 == 0 ? Mib.WHITE : 0xFF000000);
                    c.drawRect(fx + k * fw / cols, fy + r * fh / rows, fx + (k + 1) * fw / cols, fy + (r + 1) * fh / rows, p);
                }
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, w * 0.03f));
            p.setColor(Mib.WHITE);
            c.drawRect(fx, fy, fx + fw, fy + fh, p);
        }

        @Override
        public void draw(Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            drawIn(c, getBounds().width(), getBounds().height());
            c.restore();
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
