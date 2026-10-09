package com.thecarlauncher;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.screens.AppsScreen;
import com.thecarlauncher.screens.Dialogs;
import com.thecarlauncher.screens.HomeScreen;
import com.thecarlauncher.screens.MediaScreen;
import com.thecarlauncher.screens.NavScreen;
import com.thecarlauncher.screens.Screen;
import com.thecarlauncher.screens.SettingsScreen;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Theme;
import com.thecarlauncher.util.Ui;
import com.thecarlauncher.views.ScreenBackground;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LauncherActivity extends Activity implements LocationTracker.Listener {
    public static final String EXTRA_SCREEN = "screen";
    public static final int HOME = 0, NAV = 1, MEDIA = 2, PHONE = 3, APPS = 4, SETTINGS = 5;

    private static final int REQ_PERMS = 41;
    private static final int[] DOCK_ICONS = {
            R.drawable.ic_home, R.drawable.ic_navigation, R.drawable.ic_music,
            R.drawable.ic_phone, R.drawable.ic_apps, R.drawable.ic_settings
    };
    private static final String[] DOCK_LABELS = {"Home", "Navigation", "Media", "Phone", "Apps", "Settings"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Screen[] screens = new Screen[6];
    private final View[] dockItems = new View[6];
    private int current = -1;
    private int accent;

    private FrameLayout content;
    private TextView timeView, dateView, titleView, vehicleView;
    private ImageView gpsIcon, btIcon, wifiIcon;

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
        accent = Theme.accent(this);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        setContentView(R.layout.activity_launcher);

        View root = findViewById(R.id.root);
        root.setBackground(new ScreenBackground(Prefs.str(this, Prefs.WALLPAPER, Prefs.WALL_AURORA), accent));
        content = findViewById(R.id.content);
        timeView = findViewById(R.id.status_time);
        dateView = findViewById(R.id.status_date);
        titleView = findViewById(R.id.status_title);
        vehicleView = findViewById(R.id.status_vehicle);
        gpsIcon = findViewById(R.id.status_gps);
        btIcon = findViewById(R.id.status_bt);
        wifiIcon = findViewById(R.id.status_wifi);
        findViewById(R.id.status_glow).setBackground(Theme.glowLine(accent));
        vehicleView.setText(Prefs.vehicle(this));
        buildDock();

        screens[HOME] = new HomeScreen(this);
        screens[NAV] = new NavScreen(this);
        screens[MEDIA] = new MediaScreen(this);
        screens[APPS] = new AppsScreen(this);
        screens[SETTINGS] = new SettingsScreen(this);

        int start = HOME;
        if (savedInstanceState != null) start = savedInstanceState.getInt("screen", HOME);
        show(screenFromIntent(getIntent(), start), false);

        MediaHub.get(this).connectExternal();
        if (MusicLibrary.hasPermission(this)) MusicService.start(this);
        AppRepository.get(this);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);

        IntentFilter sf = new IntentFilter();
        sf.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        sf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(statusReceiver, sf);

        requestMissingPermissions(false);
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
            // Home key pressed while already home: reset to the dashboard.
            if (current >= 0 && screens[current] != null) screens[current].onBack();
            show(HOME, true);
        }
    }

    private int screenFromIntent(Intent i, int def) {
        String s = i == null ? null : i.getStringExtra(EXTRA_SCREEN);
        if ("media".equals(s)) return MEDIA;
        if ("nav".equals(s)) return NAV;
        return def;
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocationTracker t = LocationTracker.get(this);
        t.start();
        t.addListener(this);
        main.post(clock);
        updateIndicators();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
        MediaHub.get(this).connectExternal();
        if (current >= 0 && screens[current] != null) screens[current].onShow();
    }

    @Override
    protected void onPause() {
        if (current >= 0 && screens[current] != null) screens[current].onHide();
        super.onPause();
    }

    @Override
    protected void onStop() {
        main.removeCallbacks(clock);
        LocationTracker t = LocationTracker.get(this);
        t.removeListener(this);
        t.stop();
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
        if (current >= 0 && screens[current] != null && screens[current].onBack()) return;
        if (current != HOME) show(HOME, true);
        // A launcher never finishes on back.
    }

    public int accent() { return accent; }

    // ---- Screens & dock --------------------------------------------------------------------

    private void buildDock() {
        LinearLayout dock = findViewById(R.id.dock);
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < DOCK_ICONS.length; i++) {
            View item = inf.inflate(R.layout.dock_item, dock, false);
            ((ImageView) item.findViewById(R.id.dock_icon)).setImageResource(DOCK_ICONS[i]);
            ((TextView) item.findViewById(R.id.dock_label)).setText(DOCK_LABELS[i]);
            final int idx = i;
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == PHONE) openPhone();
                    else show(idx, true);
                }
            });
            dockItems[i] = item;
            dock.addView(item);
        }
    }

    public void show(int idx, boolean animate) {
        if (idx == PHONE || screens[idx] == null) return;
        hideKeyboard();
        if (idx == current) return;
        if (current >= 0) {
            Screen old = screens[current];
            old.onHide();
            old.view(content).setVisibility(View.GONE);
        }
        current = idx;
        Screen s = screens[idx];
        View v = s.view(content);
        if (v.getParent() == null) {
            Theme.apply(v, accent);
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        v.setVisibility(View.VISIBLE);
        if (animate) {
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(this, 14));
            v.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
        titleView.setText(s.title());
        s.onShow();
        for (int i = 0; i < dockItems.length; i++) styleDockItem(i, i == idx);
    }

    private void styleDockItem(int i, boolean selected) {
        View item = dockItems[i];
        ImageView icon = item.findViewById(R.id.dock_icon);
        TextView label = item.findViewById(R.id.dock_label);
        View ind = item.findViewById(R.id.dock_indicator);
        Theme.tint(icon, selected ? accent : Theme.TEXT_SECONDARY);
        label.setTextColor(selected ? Theme.TEXT_PRIMARY : Theme.TEXT_DIM);
        ind.setBackground(selected ? Theme.indicator(this, accent) : null);
    }

    /** Opens the chosen phone app, asking once which one to use. */
    public void openPhone() {
        String pkg = Prefs.str(this, Prefs.PHONE_PKG, null);
        if (pkg != null && AppRepository.launchPackage(this, pkg)) return;
        Dialogs.pickApp(this, "Choose your phone app", "System dialer", new Dialogs.AppPicked() {
            @Override
            public void onPicked(String p) {
                if (p == null) {
                    Prefs.put(LauncherActivity.this, Prefs.PHONE_PKG, (String) null);
                    if (!Ui.start(LauncherActivity.this, new Intent(Intent.ACTION_DIAL))) {
                        Ui.toast(LauncherActivity.this, "No dialer available");
                    }
                } else {
                    Prefs.put(LauncherActivity.this, Prefs.PHONE_PKG, p);
                    AppRepository.launchPackage(LauncherActivity.this, p);
                }
            }
        });
    }

    /** Rebuilds the whole UI after theme-level settings change. */
    public void restartUi() {
        recreate();
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        View decor = getWindow().getDecorView();
        if (Prefs.bool(this, Prefs.IMMERSIVE, false)) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ---- Status bar -------------------------------------------------------------------------

    private void updateClock() {
        Date now = new Date();
        String pattern = Prefs.use24h(this) ? "HH:mm" : "h:mm";
        timeView.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(now));
        dateView.setText(new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now));
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private void updateIndicators() {
        boolean bt = false;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            bt = a != null && a.isEnabled();
        } catch (Exception ignored) {
        }
        Theme.tint(btIcon, bt ? Theme.TEXT_PRIMARY : Theme.TEXT_DIM);
        btIcon.setAlpha(bt ? 1f : 0.5f);

        boolean net = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            net = ni != null && ni.isConnected();
        } catch (Exception ignored) {
        }
        Theme.tint(wifiIcon, net ? Theme.TEXT_PRIMARY : Theme.TEXT_DIM);
        wifiIcon.setAlpha(net ? 1f : 0.5f);
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        boolean fix = t.hasFix();
        Theme.tint(gpsIcon, fix ? accent : Theme.TEXT_DIM);
        gpsIcon.setAlpha(fix ? 1f : 0.5f);
    }

    // ---- Permissions ------------------------------------------------------------------------

    /** Asks for location, music and notification permissions that are still missing. */
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
