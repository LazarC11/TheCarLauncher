package com.thecarlauncher.screens;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibPopup;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * All apps as a list, laid out like the radio station list: a Search row first, then every app
 * with its icon and name (A–Z, fast scroll). Long-press: pin to MENU page 2, use as phone/radio
 * app, info, uninstall.
 */
public class AppsScreen extends Screen implements AppRepository.Listener {
    private final List<AppRepository.App> shown = new ArrayList<>();
    private final ListAdapter adapter = new ListAdapter();
    private ListView list;
    private TextView listTitle;
    private View root;
    private String query = "";
    private MibBar.Key searchKey;

    public AppsScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return s("Aplikacije", "Apps"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        root = r;
        LinearLayout la = new LinearLayout(act);
        la.setOrientation(LinearLayout.VERTICAL);
        la.setPadding(Mib.px(act, 40), Mib.px(act, 10), Mib.px(act, 40), 0);
        listTitle = Mib.text(act, "", 26, Mib.semibold(act), Mib.WHITE);
        listTitle.setPadding(Mib.px(act, 6), 0, 0, Mib.px(act, 8));
        la.addView(listTitle);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        la.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));
        list = new ListView(act);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0xFF55565F));
        list.setDividerHeight(Math.max(1, Mib.px(act, 1)));
        list.setSelector(Mib.keyPressed(act));
        list.setFastScrollEnabled(true);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) search();
                else AppRepository.launch(act, shown.get(pos - 1));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return false;
                menu(v, shown.get(pos - 1));
                return true;
            }
        });
        la.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        r.addView(la, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MibBar bar = new MibBar(act);
        searchKey = bar.add(s("Pretraga", "Search"), R.drawable.ui_ic_search, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });
        bar.add(s("Sve", "All"), R.drawable.ui_ic_apps, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                query = "";
                filter();
            }
        });
        bar.add("MENU", R.drawable.ui_ic_home, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.MENU, true);
            }
        });
        bar.gap(3);
        bar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.SETTINGS, true);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        return r;
    }

    @Override
    public void onShow() {
        AppRepository.get(act).addListener(this);
        filter();
    }

    @Override
    public void onHide() {
        AppRepository.get(act).removeListener(this);
    }

    @Override
    public void onAppsChanged() {
        filter();
    }

    private void search() {
        Dialogs.text(act, s("Pretraga aplikacija", "Search apps"), s("Naziv aplikacije", "App name"), query,
                new Dialogs.TextEntered() {
                    @Override
                    public void onText(String text) {
                        query = text;
                        filter();
                    }
                });
    }

    private void filter() {
        String q = query.trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (AppRepository.App a : AppRepository.get(act).all()) {
            if (q.length() == 0 || a.label.toLowerCase(Locale.getDefault()).contains(q)) shown.add(a);
        }
        searchKey.setActive(q.length() > 0);
        listTitle.setText(s("Aplikacije", "Apps") + "  —  " + shown.size());
        adapter.notifyDataSetChanged();
    }

    /** Long-press menu in the factory popup, rising from the pressed row. */
    private void menu(View anchor, final AppRepository.App a) {
        final AppRepository repo = AppRepository.get(act);
        MibPopup m = new MibPopup(act).columns(1);
        m.add(repo.isFavourite(a) ? s("Ukloni iz MENU", "Remove from MENU") : s("Dodaj u MENU", "Add to MENU"),
                R.drawable.ui_ic_home, false, new Runnable() { public void run() { repo.toggleFavourite(a); filter(); } });
        m.add(s("Koristi kao telefon", "Use as phone app"), R.drawable.ui_ic_phone, false,
                new Runnable() { public void run() { Prefs.put(act, Prefs.PHONE_PKG, a.pkg); } });
        m.add(s("Koristi kao radio", "Use as radio app"), R.drawable.ui_ic_radio, false,
                new Runnable() { public void run() { Prefs.put(act, Prefs.RADIO_PKG, a.pkg); } });
        m.add(s("Informacije o aplikaciji", "App info"), R.drawable.ui_ic_info, false,
                new Runnable() { public void run() { AppRepository.showInfo(act, a.pkg); } });
        m.add(s("Deinstaliraj", "Uninstall"), R.drawable.ui_ic_close, false,
                new Runnable() { public void run() { AppRepository.uninstall(act, a.pkg); } });
        m.show(root, anchor);
    }

    /** Row 0: Search; then the apps (icon, name, "MENU" when pinned). */
    private class ListAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size() + 1; }
        @Override public Object getItem(int p) { return p; }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Mib.px(act, 10), 0, Mib.px(act, 10), 0);
            row.setMinimumHeight(Mib.px(act, 72));
            ImageView iv;
            TextView t, f;
            if (p == 0) {
                iv = Mib.icon(act, R.drawable.ui_ic_search, Mib.WHITE);
                t = Mib.text(act, s("Pretraga", "Search"), 28, Mib.regular(act), Mib.WHITE);
                f = Mib.text(act, query.length() > 0 ? query : "", 26, Mib.regular(act), Mib.GREY);
            } else {
                AppRepository.App a = shown.get(p - 1);
                iv = new ImageView(act);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(a.icon);
                t = Mib.text(act, a.label, 28, Mib.regular(act), Mib.WHITE);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                f = Mib.text(act, AppRepository.get(act).isFavourite(a) ? "MENU" : "", 22, Mib.regular(act), Mib.GREY);
            }
            row.addView(iv, new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 44)));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.leftMargin = Mib.px(act, 18);
            row.addView(t, tl);
            row.addView(f);
            return row;
        }
    }
}
