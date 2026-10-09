package com.thecarlauncher.screens;

import android.view.View;
import android.view.ViewGroup;

import com.thecarlauncher.LauncherActivity;

/** One full page of the launcher (Home, Navigation, Media, Apps, Settings). */
public abstract class Screen {
    protected final LauncherActivity act;
    protected final int accent;
    private View view;

    protected Screen(LauncherActivity act) {
        this.act = act;
        this.accent = act.accent();
    }

    public final View view(ViewGroup parent) {
        if (view == null) view = create(parent);
        return view;
    }

    public final boolean isCreated() { return view != null; }

    protected abstract View create(ViewGroup parent);

    public abstract String title();

    public void onShow() {}

    public void onHide() {}

    public void onDestroy() {}

    /** Return true if the back press was handled inside the screen. */
    public boolean onBack() { return false; }

    /** Called when permissions change, so screens can reload data. */
    public void onPermissionsChanged() {}
}
