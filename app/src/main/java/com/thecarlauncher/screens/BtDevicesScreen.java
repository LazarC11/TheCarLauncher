package com.thecarlauncher.screens;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
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
import com.thecarlauncher.media.BtDevices;
import com.thecarlauncher.mib.Mib;

import java.util.List;

/**
 * Bluetooth devices: the phones the unit remembers, a check mark on the connected one, tap to
 * connect. The corner key opens "New device": a search for nearby phones to pair with.
 * Opened from Media when BT audio is chosen with no phone connected.
 */
public class BtDevicesScreen extends Screen implements BtDevices.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView title, cornerText;
    private ImageView cornerIcon;
    private boolean newDevice;
    private String lastConnected;

    public BtDevicesScreen(LauncherActivity act) { super(act); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    private BtDevices bt() { return BtDevices.get(act); }

    @Override
    public String title() { return s("Bluetooth uređaji", "Bluetooth devices"); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Mib.px(act, 14), 0, 0);

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Mib.px(act, 30), 0, Mib.px(act, 30), Mib.px(act, 10));
        FrameLayout back = new FrameLayout(act);
        back.setBackground(Mib.box(act));
        back.addView(Mib.icon(act, R.drawable.ui_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 30), Mib.px(act, 30), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!onBack()) act.goBack();
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Mib.px(act, 62), Mib.px(act, 54));
        bl.rightMargin = Mib.px(act, 18);
        head.addView(back, bl);
        title = Mib.text(act, title(), 40, Mib.semibold(act), Mib.WHITE);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Corner key: "New device" on the list, "Search" on the new-device page.
        LinearLayout corner = new LinearLayout(act);
        corner.setGravity(Gravity.CENTER_VERTICAL);
        corner.setBackground(Mib.box(act));
        corner.setPadding(Mib.px(act, 16), 0, Mib.px(act, 20), 0);
        cornerIcon = Mib.icon(act, R.drawable.ui_ic_plus, Mib.WHITE);
        corner.addView(cornerIcon, new LinearLayout.LayoutParams(Mib.px(act, 30), Mib.px(act, 30)));
        cornerText = Mib.text(act, "", 24, Mib.semibold(act), Mib.WHITE);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ctl.leftMargin = Mib.px(act, 10);
        corner.addView(cornerText, ctl);
        corner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (newDevice) bt().search();
                else openNewDevice();
            }
        });
        head.addView(corner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Mib.px(act, 54)));
        r.addView(head);

        View rule = new View(act);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Mib.shade(Mib.BLUE, 0.8f, 0xFF), Mib.BLUE, Mib.BLUE, Mib.shade(Mib.BLUE, 0.8f, 0x80)}));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(2, Mib.px(act, 2.4f)));
        rl.leftMargin = Mib.px(act, 12);
        rl.rightMargin = Mib.px(act, 12);
        r.addView(rule, rl);

        ScrollView scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Mib.px(act, 12), 0, Mib.px(act, 12));
        scroll.addView(content);
        r.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        render(false);
        return r;
    }

    @Override
    public void onShow() {
        newDevice = false;
        bt().addListener(this);
        bt().refresh();
        lastConnected = bt().connectedAddress();
        main.post(poll);
        render(true);
    }

    @Override
    public void onHide() {
        bt().removeListener(this);
        main.removeCallbacks(poll);
    }

    @Override
    public boolean hasOwnBack() { return true; }

    @Override
    public boolean onBack() {
        if (newDevice) {
            newDevice = false;
            render(true);
            return true;
        }
        return false;
    }

    @Override
    public void onDevicesChanged() { render(false); }

    /** The connection itself is only seen in the BT app's properties: watch them while open. */
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            String c = bt().connectedAddress();
            if (c == null ? lastConnected != null : !c.equals(lastConnected)) {
                lastConnected = c;
                render(false);
            }
            main.postDelayed(this, 1000);
        }
    };

    private void openNewDevice() {
        newDevice = true;
        render(true);
        if (!bt().isConnected()) bt().search();
    }

    private void render(boolean fade) {
        if (content == null) return;
        title.setText(newDevice ? s("Novi uređaj", "New device") : title());
        cornerIcon.setImageResource(newDevice ? R.drawable.ui_ic_search : R.drawable.ui_ic_plus);
        cornerText.setText(newDevice ? s("Traži", "Search") : s("Novi uređaj", "New device"));
        content.removeAllViews();
        if (newDevice) buildNew();
        else buildList();
        if (fade) {
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void buildList() {
        List<BtDevices.Device> list = bt().known();
        String connected = bt().connectedAddress();
        String connecting = bt().connecting();
        if (list.isEmpty()) {
            note(s("Nema zapamćenih uređaja. Poveži novi telefon:",
                    "No remembered devices. Connect a new phone:"));
            LinearLayout add = row(R.drawable.ui_ic_plus, s("Poveži novi uređaj", "Connect a new device"), null, false);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { openNewDevice(); }
            });
            return;
        }
        for (final BtDevices.Device d : list) {
            boolean on = d.address.equals(connected);
            boolean busy = d.address.equals(connecting);
            String value = on ? s("Povezano", "Connected") : busy ? s("Povezivanje…", "Connecting…") : null;
            LinearLayout row = row(0, d.label(), value, on);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (d.address.equals(bt().connectedAddress())) askDisconnect(d);
                    else bt().connect(d);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    askForget(d);
                    return true;
                }
            });
        }
        note(s("Dodirni uređaj za povezivanje. Dugi pritisak: zaboravi uređaj.",
                "Tap a device to connect. Long press: forget the device."));
    }

    private void buildNew() {
        String name = bt().localName();
        if (bt().isConnected()) {
            note(s("Telefon je povezan. Za novi uređaj prvo ga odspoji.",
                    "A phone is connected. Disconnect it first to add a new device."));
            LinearLayout dis = row(R.drawable.ui_ic_bt, s("Odspoji trenutni telefon", "Disconnect the current phone"), null, false);
            dis.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { bt().disconnect(); }
            });
            return;
        }
        note(name != null
                ? s("Na telefonu uključi Bluetooth i izaberi „" + name + "“, ili izaberi telefon sa liste:",
                        "On the phone, turn on Bluetooth and choose \"" + name + "\", or pick the phone from the list:")
                : s("Na telefonu uključi Bluetooth i uredi ga vidljivim, pa ga izaberi sa liste:",
                        "Turn on Bluetooth on the phone and make it visible, then pick it from the list:"));
        List<BtDevices.Device> found = bt().found();
        String connecting = bt().connecting();
        for (final BtDevices.Device d : found) {
            LinearLayout row = row(R.drawable.ui_ic_phone, d.label(),
                    d.address.equals(connecting) ? s("Uparivanje…", "Pairing…") : null, false);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { bt().pair(d); }
            });
        }
        if (found.isEmpty()) {
            note(bt().searching() ? s("Traženje…", "Searching…")
                    : s("Nije pronađen nijedan uređaj. Dodirni „Traži“ u uglu.", "No devices found. Tap \"Search\" in the corner."));
        }
    }

    private void note(String text) {
        TextView t = Mib.text(act, text, 24, Mib.regular(act), Mib.GREY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Mib.px(act, 30);
        lp.rightMargin = Mib.px(act, 58);
        lp.topMargin = Mib.px(act, 6);
        lp.bottomMargin = Mib.px(act, 14);
        content.addView(t, lp);
    }

    /** A settings bar with a check mark (connected) or an icon on the left. */
    private LinearLayout row(int icon, String text, String value, boolean checked) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Mib.settingsBar(act));
        row.setPadding(Mib.px(act, 18), 0, Mib.px(act, 22), 0);
        ImageView lead = new ImageView(act);
        lead.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (checked) lead.setImageDrawable(new Tick(Mib.BAR_TEXT));
        else if (icon != 0) lead.setImageDrawable(Mib.icon(act, icon, Mib.BAR_TEXT).getDrawable());
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40));
        il.rightMargin = Mib.px(act, 16);
        row.addView(lead, il);
        TextView t = Mib.text(act, text, 30, checked ? Mib.semibold(act) : Mib.regular(act), Mib.BAR_TEXT);
        t.setSingleLine(true);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = Mib.text(act, value, 24, Mib.regular(act), Mib.BAR_VALUE);
            LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            vl.leftMargin = Mib.px(act, 16);
            row.addView(v, vl);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 78));
        lp.leftMargin = Mib.px(act, 26);
        lp.rightMargin = Mib.px(act, 58);
        lp.bottomMargin = Mib.px(act, 8);
        content.addView(row, lp);
        return row;
    }

    private void askDisconnect(final BtDevices.Device d) {
        Dialogs.choose(act, d.label(), new String[]{s("Odspoji", "Disconnect"), s("Otkaži", "Cancel")}, -1,
                new Dialogs.Chosen() {
                    @Override
                    public void onChosen(int i) { if (i == 0) bt().disconnect(); }
                });
    }

    private void askForget(final BtDevices.Device d) {
        Dialogs.choose(act, d.label(), new String[]{s("Zaboravi uređaj", "Forget device"), s("Otkaži", "Cancel")}, -1,
                new Dialogs.Chosen() {
                    @Override
                    public void onChosen(int i) { if (i == 0) bt().forget(d.address); }
                });
    }

    /** A plain check mark, drawn so it sits on the left of the connected phone's bar. */
    private static final class Tick extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Tick(int color) {
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            p.setStrokeWidth(Math.max(2f, w * 0.12f));
            Path path = new Path();
            path.moveTo(getBounds().left + w * 0.14f, getBounds().top + h * 0.52f);
            path.lineTo(getBounds().left + w * 0.40f, getBounds().top + h * 0.78f);
            path.lineTo(getBounds().left + w * 0.88f, getBounds().top + h * 0.24f);
            c.drawPath(path, p);
        }

        @Override
        public int getIntrinsicWidth() { return 48; }

        @Override
        public int getIntrinsicHeight() { return 48; }

        @Override
        public void setAlpha(int a) { p.setAlpha(a); }

        @Override
        public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }

        @Override
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
