package com.thecarlauncher.screens;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
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
import com.thecarlauncher.car.CarData;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibPopup;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.TripComputer;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

import java.util.List;
import java.util.Locale;

/**
 * MIB "CAR": paged with the < > boxes like the factory menu —
 * Status vozila, Od starta, Dugoročno, Od dopune, Gorivo, Servis.
 * Everything comes from GPS and what the driver enters (no diagnostic adapter).
 */
public class VehicleScreen extends Screen implements LocationTracker.Listener {
    private static final int STATUS = 0, START = 1, LONG = 2, REFUEL = 3, FUEL = 4, SERVICE = 5, PAGES = 6;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView title;
    private FrameLayout body;
    private MibBar.Key izbor, actionKey;
    private int page;
    /** Value cells of the data pages, refreshed every second. */
    private TextView[] cells = new TextView[6];

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            main.postDelayed(this, 1000);
        }
    };

    public VehicleScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return s("Vozilo", "Vehicle"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    private String pageTitle(int p) {
        switch (p) {
            case STATUS: return s("Status vozila", "Vehicle status");
            case START: return s("Od starta", "Since start");
            case LONG: return s("Dugoročno", "Long-term");
            case REFUEL: return s("Od dopune", "Since refuelling");
            case FUEL: return s("Gorivo", "Fuel");
            default: return s("Servis", "Service");
        }
    }

    @Override
    protected View create(ViewGroup parent) {
        TripComputer.get(act);
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        FrameLayout content = new FrameLayout(act);
        r.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int arrow = Mib.px(act, 76), side = Mib.px(act, 18), top = Mib.px(act, 14);
        body = new FrameLayout(act);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        bl.topMargin = top + arrow + Mib.px(act, 6);
        content.addView(body, bl);

        content.addView(arrowBox(R.drawable.vw_key_prev, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage((page + PAGES - 1) % PAGES);
            }
        }), boxParams(arrow, side, top, Gravity.LEFT));
        content.addView(arrowBox(R.drawable.vw_key_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage((page + 1) % PAGES);
            }
        }), boxParams(arrow, side, top, Gravity.RIGHT));
        title = Mib.text(act, "", 40, Mib.regular(act), Mib.WHITE);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, arrow);
        tl.topMargin = top;
        tl.leftMargin = tl.rightMargin = arrow + side * 2;
        content.addView(title, tl);

        MibBar bar = new MibBar(act);
        izbor = bar.add(s("Izbor", "Select"), R.drawable.vw_ic_car_s, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPages(v);
            }
        });
        bar.add(s("Mediji", "Media"), R.drawable.vw_key_music, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.MEDIA, true);
            }
        });
        actionKey = bar.add("", R.drawable.vw_ic_refresh, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAction();
            }
        });
        bar.gap(3);
        bar.add("Setup", R.drawable.vw_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage(SERVICE);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        setPage(STATUS);
        return r;
    }

    private View arrowBox(int icon, View.OnClickListener l) {
        FrameLayout b = new FrameLayout(act);
        b.setBackground(Mib.box(act));
        b.addView(Mib.icon(act, icon, Mib.WHITE), new FrameLayout.LayoutParams(Mib.px(act, 54), Mib.px(act, 36), Gravity.CENTER));
        b.setOnClickListener(l);
        return b;
    }

    private FrameLayout.LayoutParams boxParams(int size, int side, int top, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(size, size, Gravity.TOP | gravity);
        p.topMargin = top;
        p.leftMargin = p.rightMargin = side;
        return p;
    }

    private void showPages(View key) {
        MibPopup p = new MibPopup(act).columns(1);
        int[] icons = {R.drawable.vw_ic_car_s, R.drawable.vw_ic_clock, R.drawable.vw_ic_route, R.drawable.vw_ic_speedo,
                R.drawable.vw_ic_speedo, R.drawable.vw_ic_eq};
        for (int i = 0; i < PAGES; i++) {
            final int idx = i;
            p.add(pageTitle(i), icons[i], i == page, new Runnable() {
                @Override
                public void run() {
                    setPage(idx);
                }
            });
        }
        p.show(act.getWindow().getDecorView(), key);
    }

    private void setPage(int p) {
        page = p;
        title.setText(pageTitle(p));
        body.removeAllViews();
        cells = new TextView[6];
        switch (p) {
            case STATUS: buildStatus(); break;
            case START:
                buildCells(new int[]{R.drawable.vw_ic_clock, R.drawable.vw_ic_route, R.drawable.vw_ic_speedo,
                                R.drawable.vw_ic_car_s, R.drawable.vw_ic_compass, R.drawable.vw_ic_mountain},
                        new String[]{s("Vreme vožnje", "Driving time"), s("Pređeni put", "Distance"),
                                s("Prosečna brzina", "Average speed"), s("Trenutna brzina", "Current speed"),
                                s("Smer", "Heading"), s("Nadmorska visina", "Altitude")});
                break;
            case LONG:
                buildCells(new int[]{R.drawable.vw_ic_clock, R.drawable.vw_ic_route, R.drawable.vw_ic_speedo,
                                R.drawable.vw_ic_car_s},
                        new String[]{s("Vreme vožnje", "Driving time"), s("Pređeni put", "Distance"),
                                s("Prosečna brzina", "Average speed"), s("Kilometraža", "Odometer")});
                break;
            case REFUEL:
                buildCells(new int[]{R.drawable.vw_ic_route, R.drawable.vw_ic_speedo, R.drawable.vw_ic_nav,
                                R.drawable.vw_ic_clock, R.drawable.vw_ic_eq, R.drawable.vw_ic_speedo},
                        new String[]{s("Pređeni put", "Distance"), s("Prosečna potrošnja", "Avg. consumption"),
                                s("Domet", "Range"), s("Poslednje točenje", "Last fill-up"),
                                s("Cena na 100 km", "Cost per 100 km"), s("Prosečna cena goriva", "Avg. fuel price")});
                break;
            case FUEL: buildFuel(); break;
            default: buildService(); break;
        }
        boolean action = p == START || p == LONG || p == REFUEL || p == FUEL;
        actionKey.setVisibility(action ? View.VISIBLE : View.INVISIBLE);
        if (p == START || p == LONG) actionKey.setLabel(s("Resetuj", "Reset")).setIcon(R.drawable.vw_ic_refresh);
        else actionKey.setLabel(s("Točenje", "Fill-up")).setIcon(R.drawable.vw_ic_plus);
        update();
    }

    private void onAction() {
        if (page == START) {
            TripComputer.get(act).reset();
        } else if (page == LONG) {
            TripComputer.get(act).resetLongTerm();
        } else {
            addRefuel();
            return;
        }
        update();
    }

    // ---- Status vozila -----------------------------------------------------------------------

    private void buildStatus() {
        final ImageView car = new ImageView(act);
        car.setImageResource(R.drawable.car_corsa);
        car.setScaleType(ImageView.ScaleType.FIT_END);
        car.setAdjustViewBounds(false);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT | Gravity.BOTTOM);
        body.addView(car, cl);
        // Size the picture after layout: right half of the page, bottom aligned like the factory one.
        body.post(new Runnable() {
            @Override
            public void run() {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) car.getLayoutParams();
                lp.width = (int) (body.getWidth() * 0.6f);
                lp.height = body.getHeight() + Mib.px(act, 40);
                lp.rightMargin = Mib.px(act, 10);
                lp.topMargin = -Mib.px(act, 40);
                car.setLayoutParams(lp);
            }
        });

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 44), Mib.px(act, 6), 0, 0);
        List<CarData.Notice> notices = CarData.get(act).notices();
        if (notices.isEmpty()) {
            col.addView(statusLine(new Mark(Mark.OK), s("Ne postoje unosi.", "No entries.")));
        } else {
            for (CarData.Notice n : notices) col.addView(statusLine(new Mark(n.urgent ? Mark.ALERT : Mark.WARN), n.text));
        }
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(Mib.px(act, 470),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        body.addView(col, ll);

        // Framed button like the factory "Start-stop" one.
        LinearLayout btn = new LinearLayout(act);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setBackground(Mib.box(act));
        btn.setPadding(Mib.px(act, 14), 0, Mib.px(act, 30), 0);
        btn.addView(Mib.icon(act, R.drawable.vw_ic_eq, Mib.WHITE), new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 44)));
        TextView bt = Mib.text(act, s("Servis", "Service"), 34, Mib.regular(act), Mib.WHITE);
        bt.setPadding(Mib.px(act, 14), 0, 0, 0);
        btn.addView(bt);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage(SERVICE);
            }
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Mib.px(act, 320), Mib.px(act, 60),
                Gravity.BOTTOM | Gravity.LEFT);
        bl.leftMargin = Mib.px(act, 30);
        bl.bottomMargin = Mib.px(act, 34);
        body.addView(btn, bl);
    }

    private View statusLine(Drawable mark, String text) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Mib.px(act, 6), 0, Mib.px(act, 6));
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(mark);
        row.addView(iv, new LinearLayout.LayoutParams(Mib.px(act, 34), Mib.px(act, 34)));
        TextView t = Mib.text(act, text, 31, Mib.regular(act), Mib.WHITE);
        t.setSingleLine(false);
        t.setPadding(Mib.px(act, 22), 0, 0, 0);
        row.addView(t);
        return row;
    }

    /** Green tick / amber or red warning triangle, drawn crisp at any size. */
    private static final class Mark extends Drawable {
        static final int OK = 0, WARN = 1, ALERT = 2;
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Mark(int type) { this.type = type; }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            c.save();
            c.translate(getBounds().left, getBounds().top);
            Path path = new Path();
            if (type == OK) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.14f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(0xFF3CC24A);
                path.moveTo(w * 0.12f, h * 0.55f);
                path.lineTo(w * 0.40f, h * 0.82f);
                path.lineTo(w * 0.88f, h * 0.18f);
                c.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(type == ALERT ? 0xFFE23B2E : 0xFFF2B01E);
                path.moveTo(w * 0.5f, h * 0.06f);
                path.lineTo(w * 0.96f, h * 0.92f);
                path.lineTo(w * 0.04f, h * 0.92f);
                path.close();
                c.drawPath(path, p);
                p.setColor(0xFF000000);
                c.drawRect(w * 0.45f, h * 0.34f, w * 0.55f, h * 0.66f, p);
                c.drawCircle(w * 0.5f, h * 0.78f, w * 0.06f, p);
            }
            c.restore();
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---- Data cells (Od starta / Dugoročno / Od dopune) --------------------------------------

    private void buildCells(int[] icons, String[] labels) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 44), 0, Mib.px(act, 44), Mib.px(act, 14));
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % 3 == 0) {
                if (row != null) {
                    View mid = new View(act);
                    mid.setBackgroundColor(0xFF4C4D57);
                    col.addView(mid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1))));
                }
                row = new LinearLayout(act);
                row.setGravity(Gravity.CENTER_VERTICAL);
                col.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            }
            cells[i] = cell(row, icons[i], labels[i]);
        }
        if (labels.length % 3 != 0) {
            for (int i = labels.length % 3; i < 3; i++) row.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        body.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView cell(LinearLayout row, int icon, String label) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        LinearLayout cap = new LinearLayout(act);
        cap.setGravity(Gravity.CENTER);
        cap.addView(Mib.icon(act, icon, Mib.GREY), new LinearLayout.LayoutParams(Mib.px(act, 32), Mib.px(act, 32)));
        TextView l = Mib.text(act, label, 24, Mib.regular(act), Mib.GREY);
        l.setPadding(Mib.px(act, 8), 0, 0, 0);
        cap.addView(l);
        c.addView(cap);
        TextView v = Mib.text(act, "–", 50, Mib.semibold(act), Mib.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, Mib.px(act, 8), 0, 0);
        c.addView(v);
        row.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    // ---- Gorivo (fuel log) / Servis lists ----------------------------------------------------

    private LinearLayout list() {
        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(true);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 40), 0, Mib.px(act, 40), Mib.px(act, 10));
        sv.addView(col);
        body.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        col.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));
        return col;
    }

    private TextView row(LinearLayout col, int icon, String label, String value, final Runnable click) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(act, 10), 0, Mib.px(act, 10), 0);
        row.setMinimumHeight(Mib.px(act, 70));
        if (click != null) {
            row.setBackground(Mib.keyPressed());
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    click.run();
                }
            });
        }
        if (icon != 0) row.addView(Mib.icon(act, icon, Mib.WHITE), new LinearLayout.LayoutParams(Mib.px(act, 34), Mib.px(act, 34)));
        TextView t = Mib.text(act, label, 28, Mib.regular(act), Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Mib.px(act, icon != 0 ? 18 : 0);
        row.addView(t, tl);
        TextView v = Mib.text(act, value == null ? "" : value, 26, Mib.regular(act), Mib.GREY);
        row.addView(v);
        col.addView(row);
        View div = new View(act);
        div.setBackgroundColor(0xFF55565F);
        col.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1))));
        return v;
    }

    private void checkRow(LinearLayout col, String label, boolean on, Runnable click) {
        TextView v = row(col, 0, label, null, click);
        LinearLayout row = (LinearLayout) v.getParent();
        ImageView box = Mib.icon(act, on ? R.drawable.vw_ic_check_on : R.drawable.vw_ic_check_off, Mib.WHITE);
        row.addView(box, new LinearLayout.LayoutParams(Mib.px(act, 42), Mib.px(act, 42)));
    }

    private String km(double v) {
        return v < 0 ? "–" : String.format(Locale.US, "%,.0f km", v).replace(',', '.');
    }

    private void buildFuel() {
        final CarData cd = CarData.get(act);
        LinearLayout col = list();
        row(col, R.drawable.vw_ic_plus, s("Dodaj točenje", "Add fill-up"), null, new Runnable() {
            public void run() { addRefuel(); }
        });
        double avg = cd.averageConsumption();
        row(col, R.drawable.vw_ic_speedo, s("Prosečna potrošnja", "Average consumption"),
                avg < 0 ? s("potrebna 2 puna rezervoara", "needs 2 full tanks") : String.format(Locale.US, "%.1f l/100 km", avg), null);
        List<CarData.Refuel> l = cd.refuels();
        for (int i = l.size() - 1; i >= 0; i--) {
            CarData.Refuel r = l.get(i);
            String v = String.format(Locale.US, "%.1f l", r.litres) + (r.price > 0 ? String.format(Locale.US, "  ·  %.2f din/l", r.price) : "")
                    + "  ·  " + km(r.odoKm);
            row(col, R.drawable.vw_ic_clock, CarData.date(r.time) + (r.full ? "" : s("  (delimično)", "  (partial)")), v, null);
        }
        if (!l.isEmpty()) {
            row(col, R.drawable.vw_ic_back, s("Obriši poslednje točenje", "Delete last fill-up"), null, new Runnable() {
                public void run() {
                    cd.removeLastRefuel();
                    setPage(FUEL);
                }
            });
        }
    }

    private void addRefuel() {
        final CarData cd = CarData.get(act);
        double last = cd.averagePrice();
        Dialogs.refuel(act, s("Točenje goriva", "Fill-up"), s("Litara", "Litres"), s("Cena po litru", "Price per litre"),
                s("Pun rezervoar", "Full tank"), last > 0 ? String.format(Locale.US, "%.2f", last) : null,
                new Dialogs.RefuelEntered() {
                    public void onRefuel(double litres, double price, boolean full) {
                        cd.addRefuel(litres, price, full);
                        Ui.toast(act, s("Točenje sačuvano", "Fill-up saved"));
                        setPage(page);
                    }
                });
    }

    private void buildService() {
        final CarData cd = CarData.get(act);
        LinearLayout col = list();
        row(col, R.drawable.vw_ic_car_s, s("Kilometraža", "Odometer"),
                cd.hasOdometer() ? km(cd.odometer()) : s("unesite", "enter"), new Runnable() {
                    public void run() {
                        Dialogs.number(act, s("Trenutna kilometraža (km)", "Current odometer (km)"), "150000",
                                cd.hasOdometer() ? String.valueOf(Math.round(cd.odometer())) : null, new Dialogs.NumberEntered() {
                                    public void onNumber(double v) {
                                        cd.setOdometer(v);
                                        setPage(SERVICE);
                                    }
                                });
                    }
                });
        row(col, R.drawable.vw_ic_eq, s("Sledeća zamena ulja", "Next oil change"),
                cd.oilDueKm() > 0 ? km(cd.oilDueKm()) : "–", new Runnable() {
                    public void run() {
                        Dialogs.number(act, s("Zamena ulja na (km)", "Oil change at (km)"), "165000",
                                cd.oilDueKm() > 0 ? String.valueOf(Math.round(cd.oilDueKm())) : null, new Dialogs.NumberEntered() {
                                    public void onNumber(double v) {
                                        cd.setOilDueKm(v);
                                        setPage(SERVICE);
                                    }
                                });
                    }
                });
        row(col, R.drawable.vw_ic_clock, s("Godišnji servis", "Annual service"),
                cd.oilDueDate() > 0 ? CarData.date(cd.oilDueDate()) : "–", new Runnable() {
                    public void run() {
                        Dialogs.date(act, cd.oilDueDate(), new Dialogs.DatePicked() {
                            public void onDate(long t) {
                                cd.setOilDueDate(t);
                                setPage(SERVICE);
                            }
                        });
                    }
                });
        row(col, R.drawable.vw_ic_refresh, s("Servis obavljen danas", "Service done today"),
                s("+15.000 km / +1 god.", "+15,000 km / +1 yr"), new Runnable() {
                    public void run() {
                        cd.oilServiceDone();
                        Ui.toast(act, s("Sledeći servis zakazan", "Next service scheduled"));
                        setPage(SERVICE);
                    }
                });
        row(col, R.drawable.vw_ic_flag, s("Registracija ističe", "Registration expires"),
                cd.registrationDate() > 0 ? CarData.date(cd.registrationDate()) : "–", new Runnable() {
                    public void run() {
                        Dialogs.date(act, cd.registrationDate(), new Dialogs.DatePicked() {
                            public void onDate(long t) {
                                cd.setRegistrationDate(t);
                                setPage(SERVICE);
                            }
                        });
                    }
                });
        checkRow(col, s("Zimske gume su montirane", "Winter tyres fitted"), cd.winterTyres(), new Runnable() {
            public void run() {
                cd.setWinterTyres(!cd.winterTyres());
                setPage(SERVICE);
            }
        });
        checkRow(col, s("Podsetnik za zimske gume (1.11. – 1.4.)", "Winter tyre reminder (1 Nov – 1 Apr)"), cd.tyreReminder(),
                new Runnable() {
                    public void run() {
                        cd.setTyreReminder(!cd.tyreReminder());
                        setPage(SERVICE);
                    }
                });
        row(col, R.drawable.vw_ic_speedo, s("Zapremina rezervoara", "Fuel tank"),
                String.format(Locale.US, "%.0f l", cd.tank()), new Runnable() {
                    public void run() {
                        Dialogs.number(act, s("Zapremina rezervoara (l)", "Fuel tank (l)"), "44",
                                String.format(Locale.US, "%.0f", cd.tank()), new Dialogs.NumberEntered() {
                                    public void onNumber(double v) {
                                        if (v > 5) cd.setTank(v);
                                        setPage(SERVICE);
                                    }
                                });
                    }
                });
        row(col, R.drawable.vw_ic_car_s, s("Naziv vozila", "Vehicle name"), Prefs.vehicle(act), new Runnable() {
            public void run() {
                Dialogs.text(act, s("Naziv vozila", "Vehicle name"), "Opel Corsa C", Prefs.vehicle(act), new Dialogs.TextEntered() {
                    public void onText(String text) {
                        Prefs.put(act, Prefs.VEHICLE, text);
                        setPage(SERVICE);
                    }
                });
            }
        });
    }

    // ---- Live values -------------------------------------------------------------------------

    @Override
    public void onShow() {
        LocationTracker.get(act).addListener(this);
        setPage(page);
        main.post(ticker);
    }

    @Override
    public void onHide() {
        LocationTracker.get(act).removeListener(this);
        main.removeCallbacks(ticker);
    }

    @Override
    public boolean onBack() {
        if (page == STATUS) return false;
        setPage(STATUS);
        return true;
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        if (page != START || cells[3] == null) return;
        String unit = LocationTracker.unit(act);
        cells[3].setText(Math.round(t.speed(act)) + " " + unit);
        cells[4].setText(t.hasFix() ? LocationTracker.heading(t.bearing()) + String.format(Locale.US, "  %.0f°", t.bearing()) : "–");
        Location l = t.location();
        if (l != null && l.hasAltitude() && t.hasFix()) {
            cells[5].setText(Prefs.metric(act) ? String.format(Locale.US, "%.0f m", l.getAltitude())
                    : String.format(Locale.US, "%.0f ft", l.getAltitude() * 3.28084));
        } else {
            cells[5].setText("–");
        }
    }

    private static String hm(long ms) {
        long m = ms / 60000;
        return String.format(Locale.US, "%d:%02d h", m / 60, m % 60);
    }

    private void update() {
        if (cells[0] == null) return;
        TripComputer tc = TripComputer.get(act);
        boolean metric = Prefs.metric(act);
        String du = metric ? "km" : "mi";
        double f = metric ? 1 : 0.621371;
        if (page == START) {
            cells[0].setText(hm(tc.elapsedMs()));
            cells[1].setText(String.format(Locale.US, "%.1f %s", tc.distance(), du));
            cells[2].setText(String.format(Locale.US, "%.0f %s", tc.averageSpeed(), LocationTracker.unit(act)));
        } else if (page == LONG) {
            cells[0].setText(hm(tc.longTermMs()));
            cells[1].setText(String.format(Locale.US, "%.0f %s", tc.longTermKm() * f, du));
            double h = tc.longTermMs() / 3600000.0;
            cells[2].setText(h < 0.01 ? "–" : String.format(Locale.US, "%.0f %s", tc.longTermKm() * f / h, LocationTracker.unit(act)));
            CarData cd = CarData.get(act);
            cells[3].setText(cd.hasOdometer() ? km(cd.odometer()) : "–");
        } else if (page == REFUEL) {
            CarData cd = CarData.get(act);
            double since = cd.kmSinceRefuel(), avg = cd.averageConsumption(), range = cd.range(), price = cd.averagePrice();
            cells[0].setText(since < 0 ? "–" : String.format(Locale.US, "%.0f km", since));
            cells[1].setText(avg < 0 ? "–" : String.format(Locale.US, "%.1f l/100", avg));
            cells[2].setText(range < 0 ? "–" : String.format(Locale.US, "%.0f km", range));
            List<CarData.Refuel> l = cd.refuels();
            cells[3].setText(l.isEmpty() ? "–" : CarData.date(l.get(l.size() - 1).time));
            cells[4].setText(avg < 0 || price < 0 ? "–" : String.format(Locale.US, "%.0f din", avg * price));
            cells[5].setText(price < 0 ? "–" : String.format(Locale.US, "%.1f din", price));
        }
    }
}
