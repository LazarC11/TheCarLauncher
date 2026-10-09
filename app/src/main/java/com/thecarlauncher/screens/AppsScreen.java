package com.thecarlauncher.screens;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** All installed apps, searchable; long-press to pin, inspect or uninstall. */
public class AppsScreen extends Screen implements AppRepository.Listener {
    private final List<AppRepository.App> shown = new ArrayList<>();
    private final GridAdapter adapter = new GridAdapter();
    private EditText search;
    private TextView count;

    public AppsScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return "Apps"; }

    @Override
    protected View create(ViewGroup parent) {
        View v = LayoutInflater.from(act).inflate(R.layout.screen_apps, parent, false);
        search = v.findViewById(R.id.apps_search);
        count = v.findViewById(R.id.apps_count);
        GridView grid = v.findViewById(R.id.apps_grid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View view, int pos, long id) {
                AppRepository.launch(act, shown.get(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View view, int pos, long id) {
                showMenu(view, shown.get(pos));
                return true;
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                filter();
            }
        });
        return v;
    }

    @Override
    public void onShow() {
        AppRepository.get(act).addListener(this);
        filter();
    }

    @Override
    public void onHide() {
        AppRepository.get(act).removeListener(this);
        if (search != null && search.length() > 0) search.setText("");
    }

    @Override
    public void onAppsChanged() {
        filter();
    }

    private void filter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (AppRepository.App a : AppRepository.get(act).all()) {
            if (q.length() == 0 || a.label.toLowerCase(Locale.getDefault()).contains(q)) shown.add(a);
        }
        adapter.notifyDataSetChanged();
        count.setText(shown.size() + " apps");
    }

    private void showMenu(View anchor, final AppRepository.App a) {
        final AppRepository repo = AppRepository.get(act);
        PopupMenu m = new PopupMenu(act, anchor);
        m.getMenu().add(0, 1, 0, repo.isFavourite(a) ? "Remove from Home" : "Pin to Home");
        m.getMenu().add(0, 2, 1, "Use as phone app");
        m.getMenu().add(0, 3, 2, "App info");
        m.getMenu().add(0, 4, 3, "Uninstall");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        repo.toggleFavourite(a);
                        break;
                    case 2:
                        com.thecarlauncher.util.Prefs.put(act, com.thecarlauncher.util.Prefs.PHONE_PKG, a.pkg);
                        break;
                    case 3:
                        AppRepository.showInfo(act, a.pkg);
                        break;
                    case 4:
                        AppRepository.uninstall(act, a.pkg);
                        break;
                }
                return true;
            }
        });
        m.show();
    }

    private class GridAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(act).inflate(R.layout.app_cell, parent, false);
            AppRepository.App a = shown.get(p);
            ((ImageView) v.findViewById(R.id.cell_icon)).setImageDrawable(a.icon);
            ((TextView) v.findViewById(R.id.cell_label)).setText(a.label);
            return v;
        }
    }
}
