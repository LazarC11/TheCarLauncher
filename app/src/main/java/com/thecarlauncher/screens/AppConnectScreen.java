package com.thecarlauncher.screens;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.util.Ui;

/**
 * App-Connect in the factory style: a welcome line in the middle, the three phone modes along the
 * bottom (Android Auto, CarPlay, screen mirroring) and Info / Setup soft keys. The projection
 * itself is done by the unit's ZLink app, started in the chosen mode through its public
 * (exported) screens; ZLink's status broadcast tells which phone is connected.
 */
public class AppConnectScreen extends Screen {
    static final String ZLINK = "com.zjinnova.zlink";
    private static final String BASE = "com.zjinnova.android.zlink.features.";
    private static final String AUTO = BASE + "launcher.AutoActivity", CARPLAY = BASE + "launcher.CarPlayActivity",
            MIRROR = BASE + "launcher.MirrorActivity", MAIN = BASE + "main.MainActivity";

    /** Last ZLink status ("CONNECTED", "DISCONNECT", …) and mode ("carplay_wireless", …). */
    private static String status, mode;

    private TextView message;
    private android.widget.ImageView[] modes = new android.widget.ImageView[3];

    private final BroadcastReceiver zlinkStatus = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String st = i.getStringExtra("status"), md = i.getStringExtra("phoneMode");
            if ("CONNECTED".equals(st) || "DISCONNECT".equals(st)) status = st;
            if (md != null) mode = md;
            refresh();
        }
    };

    public AppConnectScreen(LauncherActivity act) {
        super(act);
        // Listen for the whole session, so the status is known before the page is opened.
        IntentFilter f = new IntentFilter(ZLINK);
        Context app = act.getApplicationContext();
        if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(zlinkStatus, f, Context.RECEIVER_EXPORTED);
        else app.registerReceiver(zlinkStatus, f);
    }

    @Override
    public String title() { return "App-Connect"; }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);

        FrameLayout body = new FrameLayout(act);
        // Dark backdrop with the soft light band, like the factory App-Connect page.
        android.widget.ImageView bg = new android.widget.ImageView(act);
        bg.setImageResource(R.drawable.ui_bg_appconnect);
        bg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        body.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        r.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        message = Mib.text(act, "", 34, Mib.regular(act), Mib.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setSingleLine(false);
        message.setLineSpacing(Mib.px(act, 6), 1f);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        ml.bottomMargin = Mib.px(act, 70);
        body.addView(message, ml);
        message.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Phone already connected: show it.
                if ("CONNECTED".equals(status)) open(activeMode());
            }
        });

        // The three modes, written like the factory logos, along the bottom of the page.
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(act, 60), 0, Mib.px(act, 60), 0);
        modes[0] = logo(R.drawable.ui_logo_aauto, 0);
        modes[1] = logo(R.drawable.ui_logo_carplay, 1);
        modes[2] = logo(R.drawable.ui_logo_mirrorlink, 2);
        for (View t : modes) row.addView(t, new LinearLayout.LayoutParams(0, Mib.px(act, 90), 1f));
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        rl.bottomMargin = Mib.px(act, 8);
        body.addView(row, rl);

        MibBar bar = new MibBar(act);
        bar.add("Info", R.drawable.ui_ic_info, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInfo();
            }
        });
        bar.gap(5);
        bar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ZLink's own settings live in its main screen.
                if (!start(MAIN)) act.showSettings(SettingsScreen.PAGE_ROOT);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        refresh();
        return r;
    }

    /** The factory logo artwork (white, half transparent): shown at full white like the photo. */
    private android.widget.ImageView logo(int res, final int idx) {
        android.widget.ImageView v = new android.widget.ImageView(act);
        v.setImageResource(res);
        v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int pad = Mib.px(act, 12);
        v.setPadding(pad, pad, pad, pad);
        v.setBackground(Mib.keyPressed());
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(idx);
            }
        });
        return v;
    }

    /** Colour filter: the logo's alpha boosted to near-opaque, drawn in the given colour. */
    private static android.graphics.ColorMatrixColorFilter tint(int color, float alpha) {
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        return new android.graphics.ColorMatrixColorFilter(new float[]{
                0, 0, 0, 0, r * 255,
                0, 0, 0, 0, g * 255,
                0, 0, 0, 0, b * 255,
                0, 0, 0, 1.5f * alpha, 0});
    }

    /** 0 Android Auto, 1 CarPlay, 2 mirroring. */
    private void open(int idx) {
        if (!installed()) {
            String other = Projection.find(act);
            if (other == null || !AppRepository.launchPackage(act, other)) {
                Ui.toast(act, s("ZLink nije instaliran", "ZLink is not installed"));
            }
            return;
        }
        if (!start(idx == 0 ? AUTO : idx == 1 ? CARPLAY : MIRROR)) start(MAIN);
    }

    private int activeMode() {
        if (mode == null) return 0;
        if (mode.startsWith("carplay") || mode.startsWith("airplay")) return mode.startsWith("airplay") ? 2 : 1;
        if (mode.startsWith("android_mirror")) return 2;
        return 0;
    }

    private boolean start(String cls) {
        return Ui.start(act, new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(ZLINK, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean installed() {
        return AppRepository.get(act).findPackage(ZLINK) != null || packageExists();
    }

    private boolean packageExists() {
        try {
            act.getPackageManager().getPackageInfo(ZLINK, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Keeps ZLink listening for phones in the background while App-Connect is open. */
    private void wakeZlink() {
        if (!packageExists()) return;
        try {
            act.sendBroadcast(new Intent("zjinnova.android.intent.action.START_DAEMON_SERVICE").setPackage(ZLINK));
        } catch (Exception ignored) {
        }
        try {
            act.startService(new Intent("zjinnova.android.intent.action.ZLINK_SERVICE").setPackage(ZLINK));
        } catch (Exception ignored) {
        }
    }

    private void refresh() {
        if (message == null) return;
        boolean connected = "CONNECTED".equals(status);
        int active = activeMode();
        if (!packageExists() && Projection.find(act) == null) {
            message.setText(s("App-Connect nije dostupan.\nNa uređaju nije pronađena ZLink aplikacija.",
                    "App-Connect is not available.\nNo ZLink app was found on this unit."));
        } else if (connected) {
            String name = active == 1 ? "Apple CarPlay" : active == 2 ? s("Preslikavanje ekrana", "Screen mirroring") : "Android Auto";
            message.setText(name + s(" je povezan.\nDodirnite za prikaz telefona.", " is connected.\nTap to show the phone."));
        } else {
            message.setText(s("Dobrodošli u App-Connect.\nPovežite uređaj preko USB-a.",
                    "Welcome to App-Connect.\nPlease connect a device using the USB."));
        }
        for (int i = 0; i < modes.length; i++) {
            boolean on = connected && i == active;
            modes[i].setColorFilter(tint(on ? Mib.BLUE : 0xFFF2F2F4, connected && !on ? 0.4f : 1f));
        }
    }

    private void showInfo() {
        String st = status == null ? s("nema podataka", "no data") : status;
        String md = mode == null || "none".equals(mode) ? "—" : mode.replace('_', ' ');
        String text = s("Telefon se povezuje preko USB kabla ili bežično (Android Auto, Apple CarPlay, preslikavanje ekrana). "
                        + "Prikaz telefona radi aplikacija ZLink na uređaju.\n\nStatus: ",
                "Phones connect by USB cable or wirelessly (Android Auto, Apple CarPlay, screen mirroring). "
                        + "The phone's screen is shown by the unit's ZLink app.\n\nStatus: ") + st + "\n"
                + s("Način: ", "Mode: ") + md;
        new android.app.AlertDialog.Builder(act).setTitle("App-Connect").setMessage(text)
                .setPositiveButton("OK", null).show();
    }

    @Override
    public void onShow() {
        wakeZlink();
        refresh();
    }
}
