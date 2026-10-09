package com.thecarlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.media.MediaHub;
import com.thecarlauncher.media.MusicLibrary;
import com.thecarlauncher.media.MusicService;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBackground;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.StreetName;
import com.thecarlauncher.nav.TripComputer;
import com.thecarlauncher.radio.RadioController;
import com.thecarlauncher.screens.RadioScreen;
import com.thecarlauncher.screens.AmbientScreen;
import com.thecarlauncher.screens.SoundScreen;
import com.thecarlauncher.screens.AppsScreen;
import com.thecarlauncher.screens.Dialogs;
import com.thecarlauncher.screens.MediaScreen;
import com.thecarlauncher.screens.MenuScreen;
import com.thecarlauncher.screens.NavScreen;
import com.thecarlauncher.screens.Screen;
import com.thecarlauncher.screens.SettingsScreen;
import com.thecarlauncher.screens.VehicleScreen;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Home activity: MIB status bar on top, one screen at a time below, MENU as the root. */
public class LauncherActivity extends Activity implements LocationTracker.Listener, MediaHub.Listener,
        StreetName.Listener {
    public static final String EXTRA_SCREEN = "screen";
    public static final int MENU = 0, NAV = 1, MEDIA = 2, APPS = 3, SETTINGS = 4, VEHICLE = 5, RADIO = 6, AMBIENT = 7, SOUND = 8;

    private static final int REQ_PERMS = 41;
    private static final Pattern RADIO_APP = Pattern.compile("(?i).*(radio|\\bfm\\b|\\bdab\\b).*");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Screen[] screens = new Screen[9];
    private int current = -1;

    private FrameLayout content;
    private TextView timeView, tagView, rightView, streetView;
    private ImageView globe;

    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            updateClock();
            main.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateIndicators();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            AppRepository.get(c).reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mib.loadAmbient(this);
        com.thecarlauncher.media.Tone.get(this).attachGlobal();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new MibBackground());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col);
        col.addView(buildStatusBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(this, 52)));
        View rule = new View(this);
        rule.setBackgroundColor(0xFF7D7F89);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Mib.px(this, 1.3f)));
        rl.leftMargin = Mib.px(this, 14);
        rl.rightMargin = Mib.px(this, 14);
        col.addView(rule, rl);
        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        screens[MENU] = new MenuScreen(this);
        screens[NAV] = new NavScreen(this);
        screens[MEDIA] = new MediaScreen(this);
        screens[APPS] = new AppsScreen(this);
        screens[SETTINGS] = new SettingsScreen(this);
        screens[VEHICLE] = new VehicleScreen(this);
        screens[RADIO] = new RadioScreen(this);
        screens[AMBIENT] = new AmbientScreen(this);
        screens[SOUND] = new SoundScreen(this);

        int start = MENU;
        if (savedInstanceState != null) start = savedInstanceState.getInt("screen", MENU);
        show(screenFromIntent(getIntent(), start), false);

        MediaHub.get(this).connectExternal();
        if (MusicLibrary.hasPermission(this)) MusicService.start(this);
        AppRepository.get(this);
        TripComputer.get(this);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);
        IntentFilter sf = new IntentFilter();
        sf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(statusReceiver, sf);

        requestMissingPermissions(false);
    }

    /** Status bar: [compass street] ... [globe clock tag] ... [speed], all on one centre line. */
    private View buildStatusBar() {
        FrameLayout bar = new FrameLayout(this);
        int pad = Mib.px(this, 22);

        LinearLayout left = new LinearLayout(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setBackground(Mib.keyPressed());
        left.setPadding(pad, 0, Mib.px(this, 10), 0);
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(MENU, true);
            }
        });
        ImageView compass = Mib.icon(this, R.drawable.vw_sb_compass, Mib.WHITE);
        left.addView(compass, new LinearLayout.LayoutParams(Mib.px(this, 28), Mib.px(this, 28)));
        streetView = Mib.text(this, "", 22, Mib.semibold(this), Mib.WHITE);
        streetView.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.30f));
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stl.leftMargin = Mib.px(this, 14);
        left.addView(streetView, stl);
        bar.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));

        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER_VERTICAL);
        globe = Mib.icon(this, R.drawable.vw_ic_globe, Mib.WHITE);
        center.addView(globe, new LinearLayout.LayoutParams(Mib.px(this, 26), Mib.px(this, 26)));
        timeView = Mib.text(this, "", 32, Mib.semibold(this), Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = Mib.px(this, 30);
        tl.rightMargin = Mib.px(this, 24);
        center.addView(timeView, tl);
        tagView = Mib.text(this, "", 22, Mib.semibold(this), Mib.WHITE);
        tagView.setMinWidth(Mib.px(this, 34));
        center.addView(tagView);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        // Balance the globe on the left with the tag on the right so the clock sits at the centre.
        ml.leftMargin = Mib.px(this, 4);
        bar.addView(center, ml);

        rightView = Mib.text(this, "", 22, Mib.semibold(this), Mib.WHITE);
        rightView.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
        rl.rightMargin = pad;
        bar.addView(rightView, rl);
        return bar;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("screen", current);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int target = screenFromIntent(intent, -1);
        if (target >= 0) {
            show(target, true);
        } else if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Home key: back to MENU, like the factory MENU button.
            if (current >= 0) while (screens[current].onBack()) { /* unwind sub-pages */ }
            show(MENU, true);
        }
    }

    private int screenFromIntent(Intent i, int def) {
        String s = i == null ? null : i.getStringExtra(EXTRA_SCREEN);
        if ("media".equals(s)) return MEDIA;
        if ("nav".equals(s)) return NAV;
        if ("radio".equals(s)) return RADIO;
        return def;
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocationTracker t = LocationTracker.get(this);
        t.start();
        t.addListener(this);
        MediaHub.get(this).addListener(this);
        StreetName.get(this).addListener(this);
        main.post(clock);
        updateIndicators();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
        MediaHub.get(this).connectExternal();
        if (current >= 0) screens[current].onShow();
    }

    @Override
    protected void onPause() {
        if (current >= 0) screens[current].onHide();
        super.onPause();
    }

    @Override
    protected void onStop() {
        main.removeCallbacks(clock);
        LocationTracker t = LocationTracker.get(this);
        t.removeListener(this);
        t.stop();
        MediaHub.get(this).removeListener(this);
        StreetName.get(this).removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        unregisterReceiver(statusReceiver);
        for (Screen s : screens) if (s != null) s.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override
    public void onBackPressed() {
        if (current >= 0 && screens[current].onBack()) return;
        if (current != MENU) show(MENU, true);
    }

    /** Kept for screens that still ask for an accent colour. */
    public int accent() { return Mib.BLUE; }

    // ---- Screens ----------------------------------------------------------------------------

    public void show(int idx, boolean animate) {
        if (idx < 0 || idx >= screens.length || screens[idx] == null) return;
        hideKeyboard();
        if (idx == current) return;
        if (current >= 0) {
            screens[current].onHide();
            screens[current].view(content).setVisibility(View.GONE);
        }
        current = idx;
        Screen s = screens[idx];
        View v = s.view(content);
        if (v.getParent() == null) {
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        v.setVisibility(View.VISIBLE);
        if (animate) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(180).start();
        }
        s.onShow();
    }

    public void showSettings(int page) {
        show(SETTINGS, true);
        ((SettingsScreen) screens[SETTINGS]).open(page);
    }

    public void openPhone() {
        String pkg = Prefs.str(this, Prefs.PHONE_PKG, null);
        if (pkg != null && AppRepository.launchPackage(this, pkg)) return;
        if (!Ui.start(this, new Intent(Intent.ACTION_DIAL))) {
            Ui.toast(this, Mib.t(this, "Izaberite aplikaciju za telefon u Podešavanjima",
                    "Choose a phone app in Setup"));
            show(SETTINGS, true);
        }
    }

    /**
     * MENU → Radio: the built-in radio screen (it explains itself when this unit has no
     * reachable tuner). Only when the built-in radio is switched off in Setup does this go
     * straight to the factory radio app.
     */
    public void openRadio() {
        if (Prefs.bool(this, Prefs.RADIO_INTERNAL, true)) {
            show(RADIO, true);
            return;
        }
        openRadioApp();
    }

    public void openRadioApp() {
        String pkg = radioPackage(this);
        if (pkg != null && AppRepository.launchPackage(this, pkg)) return;
        Ui.toast(this, Mib.t(this, "Izaberite aplikaciju za radio (dugi pritisak u Aplikacijama)",
                "Choose a radio app (long-press it in Apps)"));
        show(APPS, true);
    }

    public void openProjection() {
        List<String> found = new ArrayList<>();
        String pkg = Prefs.str(this, Prefs.PROJECTION_PKG, null);
        if (pkg == null) {
            for (AppRepository.App a : AppRepository.get(this).all()) {
                String h = (a.pkg + " " + a.label).toLowerCase(Locale.ROOT);
                if (h.contains("zlink") || h.contains("carplay") || h.contains("carlink") || h.contains("android auto")
                        || h.contains("autokit") || h.contains("zjinnova")) found.add(a.pkg);
            }
            if (!found.isEmpty()) pkg = found.get(0);
        }
        if (pkg != null && AppRepository.launchPackage(this, pkg)) return;
        Ui.toast(this, Mib.t(this, "ZLink / CarPlay aplikacija nije pronađena", "No ZLink / CarPlay app found"));
    }

    public static String radioPackage(Context c) {
        String p = Prefs.str(c, Prefs.RADIO_PKG, null);
        if (p != null) return p;
        List<String> r = radioCandidates(c);
        return r.isEmpty() ? null : r.get(0);
    }

    public static List<String> radioCandidates(Context c) {
        List<String> out = new ArrayList<>();
        for (AppRepository.App a : AppRepository.get(c).all()) {
            if (a.pkg.equals(c.getPackageName()) || a.pkg.startsWith("com.android.systemui")
                    || a.pkg.startsWith("com.android.settings") || out.contains(a.pkg)) continue;
            if (RADIO_APP.matcher(a.pkg + " " + a.label).matches()) out.add(a.pkg);
        }
        return out;
    }

    /** Rebuilds the UI after language / map / full-screen changes. */
    public void restartUi() {
        recreate();
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        View decor = getWindow().getDecorView();
        if (Prefs.bool(this, Prefs.IMMERSIVE, false)) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ---- Status bar -------------------------------------------------------------------------

    private void updateClock() {
        String pattern = Prefs.use24h(this) ? "HH:mm" : "h:mm";
        timeView.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date()));
    }

    @SuppressWarnings("deprecation")
    private void updateIndicators() {
        boolean net = false;
        try {
            NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            net = ni != null && ni.isConnected();
        } catch (Exception ignored) {
        }
        globe.setAlpha(net ? 1f : 0.35f);
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        float sp = t.speed(this);
        rightView.setText(t.hasFix() && sp >= 3 ? Math.round(sp) + " " + LocationTracker.unit(this) : "");
    }

    @Override
    public void onStreet(String street) {
        streetView.setText(street);
    }

    @Override
    public void onMediaChanged() {
        MediaHub hub = MediaHub.get(this);
        tagView.setText(hub.isRadio() ? "FM" : hub.isBluetooth() ? "BT" : "");
    }

    // ---- Permissions ------------------------------------------------------------------------

    public void requestMissingPermissions(boolean force) {
        if (!force && Prefs.bool(this, Prefs.ASKED_PERMS, false)) return;
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!MusicLibrary.hasPermission(this)) need.add(MusicLibrary.permission());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        Prefs.put(this, Prefs.ASKED_PERMS, true);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        LocationTracker t = LocationTracker.get(this);
        t.stop();
        t.start();
        if (MusicLibrary.hasPermission(this)) {
            MusicLibrary.invalidate();
            MusicService.start(this);
        }
        for (Screen s : screens) if (s != null && s.isCreated()) s.onPermissionsChanged();
    }
}
