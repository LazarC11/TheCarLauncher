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
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.util.Prefs;

/**
 * The first page after installing, on its own like the factory App-Connect welcome: the dark
 * backdrop with the soft light band, a greeting in the middle and soft keys along the bottom.
 * "Start" opens the setup assistant (WelcomeScreen), "Skip" goes straight to the home screen.
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

        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView hello = Mib.text(act, s("Dobro došli", "Welcome"), 54, Mib.semibold(act), Mib.WHITE);
        hello.setGravity(Gravity.CENTER);
        mid.addView(hello);
        TextView name = Mib.text(act, "Car Launcher", 30, Mib.regular(act), Mib.GREY);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Mib.px(act, 4), 0, Mib.px(act, 26));
        mid.addView(name);
        TextView text = Mib.text(act, s("Za nekoliko minuta podesićemo uređaj: internet, vozilo, telefon, i proverićemo da radio i Bluetooth zvuk rade.",
                "In a few minutes we set up the unit: internet, vehicle, phone, and check that the radio and Bluetooth sound work."),
                28, Mib.regular(act), Mib.WHITE);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(false);
        text.setLineSpacing(Mib.px(act, 6), 1f);
        text.setPadding(Mib.px(act, 90), 0, Mib.px(act, 90), 0);
        mid.addView(text);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        ml.bottomMargin = Mib.px(act, 20);
        body.addView(mid, ml);
        r.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MibBar bar = new MibBar(act);
        bar.add(s("Preskoči", "Skip"), R.drawable.ui_ic_close, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.put(act, WelcomeScreen.P_DONE, true);
                Prefs.put(act, WelcomeScreen.P_STEP, -1);
                act.show(LauncherActivity.MENU, true);
            }
        });
        bar.gap(5);
        bar.add(s("Započni", "Start"), R.drawable.ui_key_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) { act.startAssistant(); }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        return r;
    }
}
