package com.thecarlauncher.screens;

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
import com.thecarlauncher.util.Prefs;

/**
 * The first page after installing, on its own like the factory Media Control start page: the
 * greeting on the left with Continue below it, a large turning wireframe globe with light streaks
 * in the bottom-right corner. Continue opens the setup assistant (WelcomeScreen), Skip goes to the home screen.
 */
public class IntroScreen extends Screen {
    public IntroScreen(LauncherActivity act) { super(act); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    public String title() { return s("Dobro došli", "Welcome"); }

    @Override
    public boolean hasOwnBack() { return true; }

    /** Nothing behind the first page. */
    @Override
    public boolean onBack() { return true; }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);

        FrameLayout body = new FrameLayout(act);
        ImageView bg = new ImageView(act);
        bg.setImageResource(R.drawable.ui_bg_appconnect);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        body.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // A large wireframe globe with light streaks, in the bottom-right corner behind everything.
        body.addView(new com.thecarlauncher.mib.GlobeView(act), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Like the factory Media Control start page: welcome text on the left with the key below
        // it, the globe on the right.
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(act, 60), 0, Mib.px(act, 40), 0);

        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView hello = Mib.text(act, s("Dobro došli", "Welcome"), 48, Mib.semibold(act), Mib.WHITE);
        left.addView(hello);
        TextView text = Mib.text(act, s("Za nekoliko minuta podesićemo uređaj: internet, vozilo, telefon, i proverićemo da radio i Bluetooth zvuk rade.",
                "In a few minutes we set up the unit: internet, vehicle, phone, and check that the radio and Bluetooth sound work."),
                26, Mib.regular(act), Mib.GREY);
        text.setSingleLine(false);
        text.setLineSpacing(Mib.px(act, 4), 1f);
        text.setPadding(0, Mib.px(act, 12), 0, Mib.px(act, 30));
        left.addView(text);
        LinearLayout keys = new LinearLayout(act);
        keys.addView(key(s("Nastavi", "Continue"), new Runnable() {
            public void run() { act.startAssistant(); }
        }), new LinearLayout.LayoutParams(Mib.px(act, 230), Mib.px(act, 60)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Mib.px(act, 230), Mib.px(act, 60));
        sl.leftMargin = Mib.px(act, 16);
        // The setup app (universal build) has nothing to skip to.
        if (!LauncherActivity.setupOnly(act)) keys.addView(key(s("Preskoči podešavanje", "Skip setup"), new Runnable() {
            public void run() {
                Prefs.put(act, WelcomeScreen.P_DONE, true);
                Prefs.put(act, WelcomeScreen.P_STEP, -1);
                act.show(LauncherActivity.MENU, true);
            }
        }), sl);
        left.addView(keys);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 0.75f)); // the globe's side
        body.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        r.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return r;
    }

    /** Framed key like the factory pages (pressed: the text turns the ambient colour). */
    private TextView key(String label, final Runnable r) {
        TextView k = Mib.text(act, label, 28, Mib.regular(act), Mib.WHITE);
        k.setGravity(Gravity.CENTER);
        k.setBackground(Mib.box(act));
        Mib.pressText(k);
        k.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        return k;
    }
}
