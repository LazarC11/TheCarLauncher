package com.thecarlauncher.screens;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibPager;
import com.thecarlauncher.mib.MibTile;
import com.thecarlauncher.util.Prefs;

import java.util.List;

/** The MENU page: 4×2 tiles, a second page of favourite apps, page dots at the bottom. */
public class MenuScreen extends Screen implements AppRepository.Listener {
    private MibPager pager;
    private LinearLayout dots;
    private Grid favPage;

    public MenuScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return "MENU"; }

    @Override
    protected View create(ViewGroup parent) {
        FrameLayout root = new FrameLayout(act);
        pager = new MibPager(act);
        Grid main = new Grid();
        main.add(tile(Mib.t(act, "Radio", "Radio"), R.drawable.vw_tile_radio, new Runnable() {
            public void run() { act.openRadio(); }
        }));
        main.add(tile(Mib.t(act, "Mediji", "Media"), R.drawable.vw_tile_media, new Runnable() {
            public void run() { act.show(LauncherActivity.MEDIA, true); }
        }));
        main.add(tile(Mib.t(act, "Aplikacije", "Apps"), R.drawable.vw_tile_apps, new Runnable() {
            public void run() { act.show(LauncherActivity.APPS, true); }
        }));
        main.add(tile("App-Connect", R.drawable.vw_tile_appconnect, new Runnable() {
            public void run() { act.openProjection(); }
        }));
        main.add(tile(Mib.t(act, "Telefon", "Phone"), R.drawable.vw_tile_phone, new Runnable() {
            public void run() { act.openPhone(); }
        }));
        main.add(tile(Mib.t(act, "Vozilo", "Vehicle"), R.drawable.vw_tile_car, new Runnable() {
            public void run() { act.show(LauncherActivity.VEHICLE, true); }
        }));
        main.add(tile(Mib.t(act, "Navigacija", "Navigation"), R.drawable.vw_tile_nav, new Runnable() {
            public void run() { act.show(LauncherActivity.NAV, true); }
        }));
        main.add(tile(Mib.t(act, "Podešavanja", "Setup"), R.drawable.vw_tile_settings, new Runnable() {
            public void run() { act.show(LauncherActivity.SETTINGS, true); }
        }));
        pager.addView(main);
        favPage = new Grid();
        pager.addView(favPage);
        root.addView(pager);

        dots = new LinearLayout(act);
        dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Mib.px(act, 20), Gravity.BOTTOM);
        dl.bottomMargin = Mib.px(act, 16);
        root.addView(dots, dl);
        pager.setListener(new MibPager.Listener() {
            @Override
            public void onPage(int page) {
                updateDots(page);
            }
        });
        buildDots();
        buildFavourites();
        return root;
    }

    private MibTile tile(String label, int icon, final Runnable r) {
        MibTile t = MibTile.artwork(act, label, icon);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        return t;
    }

    private void buildFavourites() {
        favPage.removeAllViews();
        List<AppRepository.App> favs = AppRepository.get(act).favourites();
        for (final AppRepository.App a : favs) {
            MibTile t = new MibTile(act, a.label, a.icon, true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppRepository.launch(act, a);
                }
            });
            favPage.add(t);
        }
        if (favs.size() < 8) {
            MibTile add = new MibTile(act, Mib.t(act, "Dodaj", "Add"), R.drawable.vw_ic_plus);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    act.show(LauncherActivity.APPS, true);
                }
            });
            favPage.add(add);
        }
    }

    private void buildDots() {
        dots.removeAllViews();
        for (int i = 0; i < pager.getChildCount(); i++) {
            View d = new View(act);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Mib.px(act, 13), Mib.px(act, 13));
            lp.leftMargin = lp.rightMargin = Mib.px(act, 5);
            dots.addView(d, lp);
        }
        updateDots(pager.page());
    }

    private void updateDots(int page) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(i == page ? Mib.BLUE : 0xFFE6E6EA);
            View d = dots.getChildAt(i);
            d.setBackground(g);
            d.setScaleX(i == page ? 1.2f : 0.85f);
            d.setScaleY(i == page ? 1.2f : 0.85f);
        }
    }

    @Override
    public void onShow() {
        AppRepository.get(act).addListener(this);
        buildFavourites();
    }

    @Override
    public void onHide() {
        AppRepository.get(act).removeListener(this);
    }

    @Override
    public boolean onBack() {
        if (pager.page() != 0) {
            pager.setPage(0);
            return true;
        }
        return false;
    }

    @Override
    public void onAppsChanged() {
        buildFavourites();
    }

    /** 4×2 tile grid laid out with MIB proportions. */
    private class Grid extends ViewGroup {
        Grid() {
            super(act);
        }

        void add(View v) {
            addView(v);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int W = MeasureSpec.getSize(w), H = MeasureSpec.getSize(h);
            int padX = (int) (W * 0.085f), gapX = (int) (W * 0.045f);
            int tileW = (W - 2 * padX - 3 * gapX) / 4;
            int cap = Mib.px(act, 50), lineH = Math.max(2, Mib.px(act, 3));
            int usable = H - Mib.px(act, 50) - Mib.px(act, 40); // top margin + dots
            int gapY = Mib.px(act, 18);
            int cellH = Math.min((int) (tileW * 0.73f) + lineH + cap, (usable - gapY) / 2);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(tileW, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(W, H);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int W = r - l, H = b - t;
            int padX = (int) (W * 0.085f), gapX = (int) (W * 0.045f);
            if (getChildCount() == 0) return;
            int tileW = getChildAt(0).getMeasuredWidth(), cellH = getChildAt(0).getMeasuredHeight();
            int gapY = Mib.px(act, 18);
            int total = 2 * cellH + gapY;
            int top = Math.max(Mib.px(act, 20), (H - Mib.px(act, 44) - total) / 2);
            for (int i = 0; i < getChildCount() && i < 8; i++) {
                int col = i % 4, row = i / 4;
                int x = padX + col * (tileW + gapX), y = top + row * (cellH + gapY);
                getChildAt(i).layout(x, y, x + tileW, y + cellH);
            }
        }
    }
}
