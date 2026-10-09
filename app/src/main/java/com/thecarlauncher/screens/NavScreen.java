package com.thecarlauncher.screens;

import android.location.Location;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.NavHelper;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Theme;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.views.MapTileView;

import java.util.Locale;

/** Map view with live position, quick destinations and trip data. */
public class NavScreen extends Screen implements LocationTracker.Listener {
    private MapTileView map;
    private TextView speed, speedUnit, status, openApp;
    private ImageView recenter;
    private EditText query;
    private LinearLayout places;
    private TextView statHeading, statAlt, statSats, statPos;

    public NavScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return "Navigation"; }

    @Override
    protected View create(ViewGroup parent) {
        View v = LayoutInflater.from(act).inflate(R.layout.screen_nav, parent, false);
        map = v.findViewById(R.id.nav_map);
        map.setAccent(accent);
        map.setSource(Prefs.tiles(act));
        map.setInteractive(true);
        map.setZoom(Prefs.integer(act, Prefs.MAP_ZOOM, 16));
        speed = v.findViewById(R.id.nav_speed);
        speedUnit = v.findViewById(R.id.nav_speed_unit);
        status = v.findViewById(R.id.nav_status);
        recenter = v.findViewById(R.id.nav_recenter);
        query = v.findViewById(R.id.nav_query);
        places = v.findViewById(R.id.nav_places);
        openApp = v.findViewById(R.id.nav_open_app);

        TextView go = v.findViewById(R.id.nav_go);
        go.setBackground(Theme.accentButton(act, accent));
        go.setTextColor(Theme.onAccent(accent));
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                submit();
            }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView t, int actionId, KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                        || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    submit();
                    return true;
                }
                return false;
            }
        });

        v.findViewById(R.id.nav_zoom_in).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                map.setZoom(map.getZoom() + 1);
            }
        });
        v.findViewById(R.id.nav_zoom_out).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                map.setZoom(map.getZoom() - 1);
            }
        });
        recenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                map.setFollow(true);
            }
        });
        map.setListener(new MapTileView.Listener() {
            @Override
            public void onFollowChanged(boolean follow) {
                Theme.tint(recenter, follow ? accent : Theme.TEXT_PRIMARY);
            }
        });
        Theme.tint(recenter, accent);

        openApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                NavHelper.openApp(act, LocationTracker.get(act).location());
            }
        });

        GridLayout stats = v.findViewById(R.id.nav_stats);
        statHeading = stat(stats, "Heading");
        statAlt = stat(stats, "Altitude");
        statSats = stat(stats, "Satellites");
        statPos = stat(stats, "Accuracy");
        speedUnit.setText(LocationTracker.unit(act));
        return v;
    }

    private TextView stat(GridLayout grid, String label) {
        LinearLayout cell = new LinearLayout(act);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        TextView cap = new TextView(act);
        cap.setText(label);
        cap.setTextSize(11);
        cap.setLetterSpacing(0.14f);
        cap.setAllCaps(true);
        cap.setTextColor(Theme.TEXT_SECONDARY);
        TextView val = new TextView(act);
        val.setTextSize(20);
        val.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        val.setTextColor(Theme.TEXT_PRIMARY);
        val.setText("–");
        cell.addView(cap);
        cell.addView(val);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        grid.addView(cell, lp);
        return val;
    }

    private void submit() {
        String q = query.getText().toString();
        if (Ui.isEmpty(q)) {
            query.requestFocus();
            return;
        }
        NavHelper.navigateTo(act, q);
        query.setText("");
    }

    @Override
    public void onShow() {
        LocationTracker.get(act).addListener(this);
        buildPlaces();
        String pkg = NavHelper.preferred(act);
        openApp.setText("Open " + NavHelper.label(act, pkg));
    }

    @Override
    public void onHide() {
        LocationTracker.get(act).removeListener(this);
        Prefs.put(act, Prefs.MAP_ZOOM, map.getZoom());
    }

    private void buildPlaces() {
        places.removeAllViews();
        addPlace(R.drawable.ic_home, "Home", Prefs.HOME_ADDR);
        View div = new View(act);
        div.setBackgroundColor(0xFF18202A);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dl.leftMargin = Ui.dp(act, 56);
        places.addView(div, dl);
        addPlace(R.drawable.ic_work, "Work", Prefs.WORK_ADDR);
    }

    private void addPlace(int icon, final String name, final String key) {
        View row = LayoutInflater.from(act).inflate(R.layout.row_item, places, false);
        row.setBackgroundResource(R.drawable.ripple_rect);
        ImageView iv = row.findViewById(R.id.row_icon);
        iv.setImageResource(icon);
        Theme.tint(iv, accent);
        ((TextView) row.findViewById(R.id.row_title)).setText(name);
        final String addr = Prefs.str(act, key, null);
        TextView sub = row.findViewById(R.id.row_subtitle);
        sub.setText(Ui.isEmpty(addr) ? "Tap to set address" : addr);
        TextView meta = row.findViewById(R.id.row_meta);
        meta.setText(Ui.isEmpty(addr) ? "" : "Edit");
        meta.setTextColor(accent);
        meta.setPadding(Ui.dp(act, 8), Ui.dp(act, 8), 0, Ui.dp(act, 8));
        meta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(name, key);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Ui.isEmpty(Prefs.str(act, key, null))) edit(name, key);
                else NavHelper.navigateTo(act, Prefs.str(act, key, null));
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                edit(name, key);
                return true;
            }
        });
        places.addView(row);
    }

    private void edit(String name, final String key) {
        Dialogs.text(act, name + " address", "Street, city", Prefs.str(act, key, ""), new Dialogs.TextEntered() {
            @Override
            public void onText(String text) {
                Prefs.put(act, key, Ui.isEmpty(text) ? null : text);
                buildPlaces();
            }
        });
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        Location l = t.location();
        speed.setText(String.valueOf(Math.round(t.speed(act))));
        if (l != null) {
            map.setMyLocation(l.getLatitude(), l.getLongitude(), t.bearing(), l.getAccuracy(), t.hasFix());
        }
        if (!t.hasPermission()) {
            status.setVisibility(View.VISIBLE);
            status.setText("Location permission needed — tap to allow");
            status.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    act.requestMissingPermissions(true);
                }
            });
        } else if (l == null) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.waiting_gps);
        } else {
            status.setVisibility(View.GONE);
        }
        boolean fix = t.hasFix();
        statHeading.setText(fix ? String.format(Locale.US, "%s  %.0f°", LocationTracker.heading(t.bearing()), t.bearing()) : "–");
        if (l != null && l.hasAltitude() && fix) {
            double alt = l.getAltitude();
            statAlt.setText(Prefs.metric(act) ? String.format(Locale.US, "%.0f m", alt)
                    : String.format(Locale.US, "%.0f ft", alt * 3.28084));
        } else {
            statAlt.setText("–");
        }
        statSats.setText(t.satsVisible() > 0 ? t.satsUsed() + " / " + t.satsVisible() : "–");
        statPos.setText(l != null && l.hasAccuracy() && fix ? String.format(Locale.US, "± %.0f m", l.getAccuracy()) : "–");
    }
}
