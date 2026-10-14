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
import com.thecarlauncher.media.PhoneStatus;
import com.thecarlauncher.media.MediaListenerService;
import com.thecarlauncher.mib.MibPopup;
import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.graphics.drawable.Drawable;
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
import com.thecarlauncher.util.Storage;
import com.thecarlauncher.util.Weather;
import com.thecarlauncher.mib.LoadingBanner;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Home activity: MIB status bar on top, one screen at a time below, MENU as the root. */
public class LauncherActivity extends Activity implements LocationTracker.Listener, MediaHub.Listener,
        StreetName.Listener, PhoneStatus.Listener {
    public static final String EXTRA_SCREEN = "screen";
    public static final int MENU = 0, NAV = 1, MEDIA = 2, APPS = 3, SETTINGS = 4, VEHICLE = 5, RADIO = 6, AMBIENT = 7, SOUND = 8, APPCONNECT = 9, BTDEVICES = 10;

    private static final int REQ_PERMS = 41;
    private static final Pattern RADIO_APP = Pattern.compile("(?i).*(radio|\\bfm\\b|\\bdab\\b).*");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Screen[] screens = new Screen[11];
    private int current = -1;

    private FrameLayout content;
    private TextView timeView, tagView, rightView, streetView;
    private View statusBar, statusRule;
    private ImageView backArrow;
    private ImageView btIcon, battIcon, signalIcon, msgIcon;
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
        com.thecarlauncher.util.BringBack.launcherTask = getTaskId();
        hadAllFiles = Storage.hasAllFiles();
        IntentFilter mf = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_EJECT);
        mf.addAction(Intent.ACTION_MEDIA_REMOVED);
        mf.addDataScheme("file");
        registerReceiver(mediaMount, mf);
        Mib.loadAmbient(this);
        com.thecarlauncher.media.Tone.get(this).attachGlobal();
        com.thecarlauncher.media.QfMediaBridge.get(this);
        com.thecarlauncher.media.GocBluetooth.get(this).start();
        RadioController.get(this).mcuChannel(); // connects the MCU listener (also feeds the sniffer)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new MibBackground());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col);
        statusBar = buildStatusBar();
        col.addView(statusBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(this, 52)));
        View rule = new View(this);
        statusRule = rule;
        rule.setBackgroundColor(0xFF7D7F89);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Mib.px(this, 1.3f)));
        rl.leftMargin = Mib.px(this, 14);
        rl.rightMargin = Mib.px(this, 14);
        col.addView(rule, rl);
        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LoadingBanner banner = new LoadingBanner(this);
        root.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LoadingBanner.height(this),
                Gravity.TOP));
        LoadingBanner.attach(banner);
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
        screens[APPCONNECT] = new com.thecarlauncher.screens.AppConnectScreen(this);
        screens[BTDEVICES] = new com.thecarlauncher.screens.BtDevicesScreen(this);

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

    /** Status bar text / icons: a touch dimmer than the white screen content. */
    private static final int SB_GREY = 0xFFD2D3D8;

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
        // Back arrow for pages that have no back key of their own.
        backArrow = Mib.icon(this, R.drawable.ui_ic_back, SB_GREY);
        backArrow.setBackground(Mib.keyPressed());
        backArrow.setPadding(Mib.px(this, 4), Mib.px(this, 4), Mib.px(this, 4), Mib.px(this, 4));
        backArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        LinearLayout.LayoutParams bal = new LinearLayout.LayoutParams(Mib.px(this, 44), Mib.px(this, 40));
        bal.rightMargin = Mib.px(this, 14);
        left.addView(backArrow, bal);
        ImageView compass = Mib.icon(this, R.drawable.ui_sb_compass, SB_GREY);
        left.addView(compass, new LinearLayout.LayoutParams(Mib.px(this, 24), Mib.px(this, 24)));
        streetView = Mib.text(this, "", 20, Mib.regular(this), SB_GREY);
        streetView.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.30f));
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stl.leftMargin = Mib.px(this, 12);
        left.addView(streetView, stl);
        bar.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));

        // Centre: [globe] clock [FM · Bluetooth · battery · signal · envelope]. Equal-width side
        // slots keep the clock exactly centred however many phone icons are showing.
        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER_VERTICAL);
        int slot = Mib.px(this, 230);
        LinearLayout leftSlot = new LinearLayout(this);
        leftSlot.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        globe = Mib.icon(this, R.drawable.ui_sb_globe, SB_GREY);
        leftSlot.addView(globe, new LinearLayout.LayoutParams(Mib.px(this, 23), Mib.px(this, 23)));
        center.addView(leftSlot, new LinearLayout.LayoutParams(slot, ViewGroup.LayoutParams.MATCH_PARENT));
        timeView = Mib.text(this, "", 28, Mib.semibold(this), SB_GREY);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = Mib.px(this, 30);
        tl.rightMargin = Mib.px(this, 26);
        center.addView(timeView, tl);
        LinearLayout rightSlot = new LinearLayout(this);
        rightSlot.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        tagView = Mib.text(this, "", 22, Mib.semibold(this), Mib.WHITE);
        rightSlot.addView(tagView);
        btIcon = statusIcon(rightSlot, R.drawable.ui_sb_bt, 26, 26);
        battIcon = statusIcon(rightSlot, R.drawable.ui_sb_batt_4, 38, 26);
        signalIcon = statusIcon(rightSlot, R.drawable.ui_sb_sig_5, 54, 25);
        msgIcon = statusIcon(rightSlot, R.drawable.ui_sb_msg, 38, 26);
        msgIcon.setBackground(Mib.keyPressed());
        msgIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNotifications(v);
            }
        });
        center.addView(rightSlot, new LinearLayout.LayoutParams(slot, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
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
        PhoneStatus.get(this).addListener(this);
        StreetName.get(this).addListener(this);
        main.post(clock);
        updateIndicators();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
        // Came back from the "All files access" page with it switched on: rescan.
        boolean files = Storage.hasAllFiles();
        if (files && !hadAllFiles) {
            MusicLibrary.invalidate();
            for (Screen s : screens) if (s != null && s.isCreated()) s.onPermissionsChanged();
        }
        hadAllFiles = files;
        com.thecarlauncher.nav.OfflineMaps.get(this).finishDownloads();
        Weather.get(this).update(LocationTracker.get(this));
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
        PhoneStatus.get(this).removeListener(this);
        StreetName.get(this).removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(mediaMount);
        } catch (Exception ignored) {
        }
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
        goBack();
    }

    /** Screens visited before the current one (MENU clears it). */
    private final java.util.ArrayList<Integer> history = new java.util.ArrayList<>();
    private boolean goingBack;

    /** Back to the screen we came from (MENU when there is none). */
    public void goBack() {
        if (current == MENU) return;
        int prev = MENU;
        while (!history.isEmpty()) {
            int h = history.remove(history.size() - 1);
            if (h != current && h >= 0 && h < screens.length && screens[h] != null) {
                prev = h;
                break;
            }
        }
        goingBack = true;
        try {
            show(prev, true);
        } finally {
            goingBack = false;
        }
    }

    /** Shows the status-bar back arrow unless the page has its own back key. */
    public void updateBackArrow() {
        if (backArrow == null) return;
        boolean own = current >= 0 && screens[current] != null && screens[current].hasOwnBack();
        backArrow.setVisibility(current == MENU || own ? View.GONE : View.VISIBLE);
    }

    /** Kept for screens that still ask for an accent colour. */
    public int accent() { return Mib.BLUE; }

    // ---- Screens ----------------------------------------------------------------------------

    public void show(int idx, boolean animate) {
        if (idx < 0 || idx >= screens.length || screens[idx] == null) return;
        hideKeyboard();
        if (idx == current) return;
        if (!goingBack) {
            if (idx == MENU) {
                history.clear();
            } else {
                // Each page is in the trail once: back always goes to the page shown before.
                history.remove(Integer.valueOf(idx));
                if (current >= 0) {
                    history.remove(Integer.valueOf(current));
                    history.add(current);
                    if (history.size() > 20) history.remove(0);
                }
            }
        }
        // Every page fades in softly (not on the very first page after start).
        animate = current >= 0;
        if (current >= 0) {
            screens[current].onHide();
            screens[current].view(content).setVisibility(View.GONE);
        }
        current = idx;
        // The home screen is just the tile grid, like the factory MENU: no status bar.
        int bar = idx == MENU ? View.GONE : View.VISIBLE;
        statusBar.setVisibility(bar);
        statusRule.setVisibility(bar);
        updateBackArrow();
        Screen s = screens[idx];
        View v = s.view(content);
        if (v.getParent() == null) {
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        v.setVisibility(View.VISIBLE);
        if (animate) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        s.onShow();
    }

    public interface ImagePicked { void onPicked(android.net.Uri uri); }
    private static final int REQ_IMAGE = 4711;
    private ImagePicked imagePicked;

    /** Lets the owner choose a picture from the unit's storage. */
    public void pickImage(int which, ImagePicked cb) {
        imagePicked = cb;
        // Remembered too: if the launcher is rebuilt while the picker is open, the result still applies.
        Prefs.put(this, "pending_picture", which);
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try {
            startActivityForResult(i, REQ_IMAGE);
        } catch (Exception e) {
            try {
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_IMAGE);
            } catch (Exception e2) {
                Ui.toast(this, Mib.t(this, "Nema aplikacije za izbor slike", "No app to choose a picture"));
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMAGE && res == RESULT_OK && data != null && data.getData() != null) {
            if (imagePicked != null) {
                imagePicked.onPicked(data.getData());
            } else {
                int which = Prefs.integer(this, "pending_picture", -1);
                if (which >= 0 && com.thecarlauncher.util.CarImages.set(this, which, data.getData())) carPicturesChanged();
            }
        }
        imagePicked = null;
        Prefs.put(this, "pending_picture", -1);
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

    /** MENU → App-Connect: the launcher's own App-Connect page (ZLink does the projection). */
    public void openProjection() {
        show(APPCONNECT, true);
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
    /** A car picture changed: rebuild the screens that show one (Vehicle, Ambient). */
    public void carPicturesChanged() {
        for (int i : new int[]{VEHICLE, AMBIENT}) {
            Screen old = screens[i];
            if (old == null || !old.isCreated()) continue;
            View v = old.view(content);
            if (i == current) continue; // never the page on screen
            old.onDestroy();
            content.removeView(v);
            screens[i] = i == VEHICLE ? new VehicleScreen(this) : new AmbientScreen(this);
        }
    }

    public void restartUi() {
        Mib.resetScale();
        recreate();
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    /**
     * Full screen: hide the unit's status and navigation bars AND lay the launcher out over the
     * space they used, so the MIB UI really fills the whole display.
     */
    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        android.view.Window w = getWindow();
        View decor = w.getDecorView();
        boolean on = Prefs.bool(this, Prefs.IMMERSIVE, false);
        if (on) {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            if (Build.VERSION.SDK_INT >= 28) {
                android.view.WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            if (Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(true);
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) c.show(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
            }
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
        // Top-right corner: outside temperature (weather at the car's position).
        Weather.get(this).update(t);
    }

    /** Called by Weather when a new temperature is known. */
    public void onTemperature(String text) {
        rightView.setText(text == null ? "" : text);
    }

    @Override
    public void onStreet(String street) {
        streetView.setText(street);
    }

    @Override
    public void onMediaChanged() {
        MediaHub hub = MediaHub.get(this);
        tagView.setText(hub.isRadio() ? "FM" : "");
        tagView.setVisibility(hub.isRadio() ? View.VISIBLE : View.GONE);
        onPhoneStatusChanged();
    }

    private ImageView statusIcon(LinearLayout row, int res, int w, int h) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Mib.px(this, w), Mib.px(this, h));
        lp.rightMargin = Mib.px(this, 12);
        row.addView(iv, lp);
        return iv;
    }

    private static final int[] BATT = {R.drawable.ui_sb_batt_0, R.drawable.ui_sb_batt_1, R.drawable.ui_sb_batt_2,
            R.drawable.ui_sb_batt_3, R.drawable.ui_sb_batt_4};
    private static final int[] SIGNAL = {R.drawable.ui_sb_sig_0, R.drawable.ui_sb_sig_1, R.drawable.ui_sb_sig_2,
            R.drawable.ui_sb_sig_3, R.drawable.ui_sb_sig_4, R.drawable.ui_sb_sig_5};

    /** Bluetooth, phone battery and signal, and the envelope when notifications are waiting. */
    @Override
    public void onPhoneStatusChanged() {
        if (btIcon == null) return;
        PhoneStatus ps = PhoneStatus.get(this);
        boolean bt = ps.connected() || MediaHub.get(this).isBluetooth();
        btIcon.setVisibility(bt ? View.VISIBLE : View.GONE);
        int b = ps.battery();
        battIcon.setVisibility(b >= 0 ? View.VISIBLE : View.GONE);
        // Factory steps: empty (red) below 10 %, then quarters.
        if (b >= 0) battIcon.setImageResource(BATT[b < 10 ? 0 : b < 35 ? 1 : b < 60 ? 2 : b < 85 ? 3 : 4]);
        int sg = ps.signal();
        signalIcon.setVisibility(sg == -1 ? View.GONE : View.VISIBLE);
        if (sg != -1) signalIcon.setImageResource(sg == -2 ? R.drawable.ui_sb_sig_none : SIGNAL[sg]);
        msgIcon.setVisibility(ps.notifications().isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The waiting notifications in a MIB popup; tapping one opens it. */
    private void showNotifications(View key) {
        List<StatusBarNotification> list = PhoneStatus.get(this).notifications();
        if (list.isEmpty()) return;
        MibPopup p = new MibPopup(this).columns(1);
        PackageManager pm = getPackageManager();
        for (final StatusBarNotification n : list) {
            Notification no = n.getNotification();
            CharSequence title = no.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = no.extras.getCharSequence(Notification.EXTRA_TEXT);
            String label = (title == null ? "" : title.toString()) + (text == null ? "" : ": " + text);
            if (label.length() > 60) label = label.substring(0, 57) + "…";
            Drawable icon = null;
            try {
                icon = pm.getApplicationIcon(n.getPackageName());
            } catch (Exception ignored) {
            }
            p.add(label, icon, false, false, new Runnable() {
                @Override
                public void run() {
                    try {
                        if (n.getNotification().contentIntent != null) n.getNotification().contentIntent.send();
                    } catch (Exception ignored) {
                    }
                    MediaListenerService s = MediaListenerService.get();
                    if (s != null && (n.getNotification().flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                        s.cancelNotification(n.getKey());
                    }
                }
            });
        }
        p.show(getWindow().getDecorView(), key);
    }

    // ---- Permissions ------------------------------------------------------------------------

    public void requestMissingPermissions(boolean force) {
        if (!force && Prefs.bool(this, Prefs.ASKED_PERMS, false)) return;
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (checkSelfPermission(MusicLibrary.permission()) != PackageManager.PERMISSION_GRANTED) {
            need.add(MusicLibrary.permission());
        }
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        Prefs.put(this, Prefs.ASKED_PERMS, true);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        else askAllFiles(force);
    }

    /** USB sticks are read as folders, which needs "All files access" on Android 11+: ask once. */
    private void askAllFiles(boolean force) {
        if (Storage.hasAllFiles() || (!force && Prefs.bool(this, "asked_all_files", false))) return;
        Prefs.put(this, "asked_all_files", true);
        Ui.toast(this, Mib.t(this, "Dozvolite \"Pristup svim fajlovima\" za Car Launcher da bi se čitala muzika sa USB-a.",
                "Allow \"All files access\" for Car Launcher so music on USB sticks can be read."));
        Storage.requestAllFiles(this);
    }

    private boolean hadAllFiles;
    private final android.content.BroadcastReceiver mediaMount = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            // USB stick plugged in or removed: read the library again.
            MusicLibrary.invalidate();
            for (Screen s : screens) if (s != null && s.isCreated()) s.onPermissionsChanged();
        }
    };

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        LocationTracker t = LocationTracker.get(this);
        t.stop();
        t.start();
        PhoneStatus.get(this).refresh();
        if (MusicLibrary.hasPermission(this)) {
            MusicLibrary.invalidate();
            MusicService.start(this);
        }
        for (Screen s : screens) if (s != null && s.isCreated()) s.onPermissionsChanged();
        askAllFiles(false);
    }
}
