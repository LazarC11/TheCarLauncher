package com.thecarlauncher.screens;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.TripComputer;
import com.thecarlauncher.util.Prefs;

import java.util.Locale;

/** MIB "Vozilo" → driving data since start, computed from GPS. */
public class VehicleScreen extends Screen implements LocationTracker.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView time, dist, avg, speed, heading, alt;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            main.postDelayed(this, 1000);
        }
    };

    public VehicleScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return s("Vozilo", "Vehicle"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        TripComputer.get(act);
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);

        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Mib.px(act, 44), Mib.px(act, 18), Mib.px(act, 44), Mib.px(act, 10));
        TextView head = Mib.text(act, Prefs.vehicle(act) + "  ·  " + s("Podaci o vožnji od početka", "Driving data since start"),
                32, Mib.semibold(act), Mib.WHITE);
        body.addView(head);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(2, Mib.px(act, 2)));
        rl.topMargin = Mib.px(act, 10);
        body.addView(rule, rl);

        LinearLayout row1 = row(body);
        time = cell(row1, R.drawable.mib_trip, s("Vreme vožnje", "Driving time"));
        dist = cell(row1, R.drawable.mib_nav, s("Pređeni put", "Distance"));
        avg = cell(row1, R.drawable.mib_car, s("Prosečna brzina", "Average speed"));
        View mid = new View(act);
        mid.setBackgroundColor(0xFF4C4D57);
        body.addView(mid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1))));
        LinearLayout row2 = row(body);
        speed = cell(row2, R.drawable.mib_car, s("Trenutna brzina", "Current speed"));
        heading = cell(row2, R.drawable.mib_compass, s("Smer", "Heading"));
        alt = cell(row2, R.drawable.mib_globe, s("Nadmorska visina", "Altitude"));
        r.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MibBar bar = new MibBar(act);
        bar.add(s("Od početka", "Since start"), R.drawable.mib_trip, null).setActive(true);
        bar.add(s("Resetuj", "Reset"), R.drawable.mib_back, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TripComputer.get(act).reset();
                update();
            }
        });
        bar.add(s("Navigacija", "Navigation"), R.drawable.mib_nav, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.NAV, true);
            }
        });
        bar.gap(3);
        bar.add("Setup", R.drawable.mib_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.SETTINGS, true);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        return r;
    }

    private LinearLayout row(LinearLayout body) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return row;
    }

    private TextView cell(LinearLayout row, int icon, String label) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        LinearLayout cap = new LinearLayout(act);
        cap.setGravity(Gravity.CENTER);
        cap.addView(Mib.icon(act, icon, Mib.GREY), new LinearLayout.LayoutParams(Mib.px(act, 28), Mib.px(act, 28)));
        TextView l = Mib.text(act, label, 25, Mib.regular(act), Mib.GREY);
        l.setPadding(Mib.px(act, 8), 0, 0, 0);
        cap.addView(l);
        c.addView(cap);
        TextView v = Mib.text(act, "–", 56, Mib.semibold(act), Mib.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, Mib.px(act, 10), 0, 0);
        c.addView(v);
        row.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    @Override
    public void onShow() {
        LocationTracker.get(act).addListener(this);
        main.post(ticker);
    }

    @Override
    public void onHide() {
        LocationTracker.get(act).removeListener(this);
        main.removeCallbacks(ticker);
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        String unit = LocationTracker.unit(act);
        speed.setText(Math.round(t.speed(act)) + " " + unit);
        heading.setText(t.hasFix() ? LocationTracker.heading(t.bearing()) + String.format(Locale.US, "  %.0f°", t.bearing()) : "–");
        Location l = t.location();
        if (l != null && l.hasAltitude() && t.hasFix()) {
            alt.setText(Prefs.metric(act) ? String.format(Locale.US, "%.0f m", l.getAltitude())
                    : String.format(Locale.US, "%.0f ft", l.getAltitude() * 3.28084));
        } else {
            alt.setText("–");
        }
    }

    private void update() {
        TripComputer tc = TripComputer.get(act);
        long m = tc.elapsedMs() / 60000;
        time.setText(String.format(Locale.US, "%d:%02d h", m / 60, m % 60));
        boolean metric = Prefs.metric(act);
        dist.setText(String.format(Locale.US, "%.1f %s", tc.distance(), metric ? "km" : "mi"));
        avg.setText(String.format(Locale.US, "%.0f %s", tc.averageSpeed(), LocationTracker.unit(act)));
    }
}
