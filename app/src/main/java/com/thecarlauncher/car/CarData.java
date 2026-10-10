package com.thecarlauncher.car;

import android.content.Context;

import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.nav.TripComputer;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Everything the CAR menu knows without a diagnostic adapter: an odometer kept in step with
 * GPS distance, a fuel log typed in at each fill-up, and service / registration reminders.
 */
public final class CarData {
    /** One fill-up: when, odometer then, litres, price per litre, filled to the brim. */
    public static final class Refuel {
        public final long time;
        public final double odoKm, litres, price;
        public final boolean full;

        Refuel(long time, double odoKm, double litres, double price, boolean full) {
            this.time = time;
            this.odoKm = odoKm;
            this.litres = litres;
            this.price = price;
            this.full = full;
        }
    }

    /** A status line on the "Status vozila" page. */
    public static final class Notice {
        public final String text;
        public final boolean urgent;

        Notice(String text, boolean urgent) {
            this.text = text;
            this.urgent = urgent;
        }
    }

    public static final double DEFAULT_TANK = 44; // Corsa C fuel tank, litres
    private static final String P_ODO = "car_odo_km", P_ODO_LIFE = "car_odo_life_km", P_FUEL = "car_fuel_log",
            P_TANK = "car_tank", P_OIL_KM = "car_oil_km", P_OIL_DATE = "car_oil_date",
            P_REG_DATE = "car_reg_date", P_TYRES = "car_tyre_reminder", P_WINTER = "car_winter_tyres";

    private final Context c;

    private CarData(Context c) { this.c = c.getApplicationContext(); }

    public static CarData get(Context c) { return new CarData(c); }

    private String s(String sr, String en) { return Mib.t(c, sr, en); }

    private double d(String key, double def) {
        try {
            return Double.parseDouble(Prefs.str(c, key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void put(String key, double v) { Prefs.put(c, key, String.valueOf(v)); }

    // ---- Odometer ----------------------------------------------------------------------------

    public boolean hasOdometer() { return d(P_ODO, -1) >= 0; }

    /** Odometer: the reading the driver typed in plus GPS distance driven since. */
    public double odometer() {
        double base = d(P_ODO, -1);
        if (base < 0) return -1;
        return base + Math.max(0, TripComputer.get(c).lifetimeKm() - d(P_ODO_LIFE, 0));
    }

    public void setOdometer(double km) {
        put(P_ODO, km);
        put(P_ODO_LIFE, TripComputer.get(c).lifetimeKm());
    }

    // ---- Fuel --------------------------------------------------------------------------------

    public double tank() { return d(P_TANK, DEFAULT_TANK); }

    public void setTank(double l) { put(P_TANK, l); }

    public List<Refuel> refuels() {
        List<Refuel> out = new ArrayList<>();
        String raw = Prefs.str(c, P_FUEL, "");
        if (raw.length() == 0) return out;
        for (String e : raw.split(";")) {
            String[] f = e.split(",");
            if (f.length < 5) continue;
            try {
                out.add(new Refuel(Long.parseLong(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2]),
                        Double.parseDouble(f[3]), "1".equals(f[4])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** Logs a fill-up at the current odometer (GPS distance if no odometer was entered). */
    public void addRefuel(double litres, double price, boolean full) {
        double odo = hasOdometer() ? odometer() : TripComputer.get(c).lifetimeKm();
        List<Refuel> list = refuels();
        list.add(new Refuel(System.currentTimeMillis(), odo, litres, price, full));
        while (list.size() > 30) list.remove(0);
        saveRefuels(list);
    }

    public void removeLastRefuel() {
        List<Refuel> list = refuels();
        if (!list.isEmpty()) list.remove(list.size() - 1);
        saveRefuels(list);
    }

    private void saveRefuels(List<Refuel> list) {
        StringBuilder sb = new StringBuilder();
        for (Refuel r : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(r.time).append(',').append(r.odoKm).append(',').append(r.litres).append(',')
                    .append(r.price).append(',').append(r.full ? 1 : 0);
        }
        Prefs.put(c, P_FUEL, sb.toString());
    }

    private double currentKm() {
        return hasOdometer() ? odometer() : TripComputer.get(c).lifetimeKm();
    }

    /** Distance since the last fill-up, or -1 without one. */
    public double kmSinceRefuel() {
        List<Refuel> l = refuels();
        if (l.isEmpty()) return -1;
        return Math.max(0, currentKm() - l.get(l.size() - 1).odoKm);
    }

    /**
     * Average consumption in L/100 km, measured fill-to-fill: litres put in at every fill after
     * the first full tank, over the distance between the first and the last full tank.
     */
    public double averageConsumption() {
        List<Refuel> l = refuels();
        int first = -1, last = -1;
        for (int i = 0; i < l.size(); i++) {
            if (!l.get(i).full) continue;
            if (first < 0) first = i;
            last = i;
        }
        if (first < 0 || last <= first) return -1;
        double litres = 0;
        for (int i = first + 1; i <= last; i++) litres += l.get(i).litres;
        double km = l.get(last).odoKm - l.get(first).odoKm;
        return km < 20 ? -1 : litres / km * 100.0;
    }

    /** Average fuel price paid, per litre. */
    public double averagePrice() {
        double litres = 0, money = 0;
        for (Refuel r : refuels()) {
            if (r.price <= 0) continue;
            litres += r.litres;
            money += r.litres * r.price;
        }
        return litres <= 0 ? -1 : money / litres;
    }

    /** Estimated range left, assuming the last fill-up was a full tank. */
    public double range() {
        double avg = averageConsumption();
        List<Refuel> l = refuels();
        if (avg <= 0 || l.isEmpty() || !l.get(l.size() - 1).full) return -1;
        double left = tank() - kmSinceRefuel() * avg / 100.0;
        return Math.max(0, left / avg * 100.0);
    }

    // ---- Service -----------------------------------------------------------------------------

    public double oilDueKm() { return d(P_OIL_KM, -1); }

    public void setOilDueKm(double km) { put(P_OIL_KM, km); }

    public long oilDueDate() { return Prefs.of(c).getLong(P_OIL_DATE, 0); }

    public void setOilDueDate(long t) { Prefs.of(c).edit().putLong(P_OIL_DATE, t).apply(); }

    public long registrationDate() { return Prefs.of(c).getLong(P_REG_DATE, 0); }

    public void setRegistrationDate(long t) { Prefs.of(c).edit().putLong(P_REG_DATE, t).apply(); }

    public boolean tyreReminder() { return Prefs.bool(c, P_TYRES, true); }

    public void setTyreReminder(boolean on) { Prefs.put(c, P_TYRES, on); }

    public boolean winterTyres() { return Prefs.bool(c, P_WINTER, false); }

    public void setWinterTyres(boolean on) { Prefs.put(c, P_WINTER, on); }

    /** Oil service: logs it as done now and sets the next one 15 000 km / one year away. */
    public void oilServiceDone() {
        double odo = currentKm();
        setOilDueKm(Math.round((odo + 15000) / 100.0) * 100.0);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 1);
        setOilDueDate(cal.getTimeInMillis());
    }

    public static int daysUntil(long t) {
        return (int) Math.floor((t - System.currentTimeMillis()) / 86400000.0) + 1;
    }

    public static String date(long t) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        return String.format(Locale.US, "%02d.%02d.%04d.", cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
    }

    /** Status entries, most urgent first; empty means "Ne postoje unosi." */
    public List<Notice> notices() {
        List<Notice> out = new ArrayList<>();
        if (hasOdometer() && oilDueKm() > 0) {
            double left = oilDueKm() - odometer();
            if (left <= 0) out.add(new Notice(s("Zamena ulja je dospela!", "Oil change due now!"), true));
            else if (left <= 1000) out.add(new Notice(s("Zamena ulja za ", "Oil change in ") + Math.round(left) + " km", false));
        }
        if (oilDueDate() > 0) {
            int days = daysUntil(oilDueDate());
            if (days <= 0) out.add(new Notice(s("Godišnji servis je dospeo!", "Annual service due now!"), true));
            else if (days <= 30) out.add(new Notice(s("Servis za ", "Service in ") + days + s(" dana", " days"), false));
        }
        if (registrationDate() > 0) {
            int days = daysUntil(registrationDate());
            if (days <= 0) out.add(new Notice(s("Registracija je istekla!", "Registration has expired!"), true));
            else if (days <= 30) out.add(new Notice(s("Registracija ističe za ", "Registration expires in ") + days
                    + s(" dana", " days"), days <= 7));
        }
        if (tyreReminder()) {
            // Serbia: winter tyres are compulsory in winter conditions from 1 November to 1 April.
            int m = Calendar.getInstance().get(Calendar.MONTH);
            boolean winter = m >= Calendar.NOVEMBER || m < Calendar.APRIL;
            boolean soon = m == Calendar.OCTOBER;
            if ((winter || soon) && !winterTyres()) {
                out.add(new Notice(s("Vreme je za zimske gume", "Time for winter tyres"), winter));
            } else if (!winter && !soon && winterTyres() && m >= Calendar.APRIL && m <= Calendar.MAY) {
                out.add(new Notice(s("Možete vratiti letnje gume", "You can switch back to summer tyres"), false));
            }
        }
        double range = range();
        if (range >= 0 && range < 60) {
            out.add(new Notice(s("Rezerva goriva: još oko ", "Low fuel: about ") + Math.round(range) + " km", range < 30));
        }
        List<Notice> sorted = new ArrayList<>();
        for (Notice n : out) if (n.urgent) sorted.add(n);
        for (Notice n : out) if (!n.urgent) sorted.add(n);
        return sorted;
    }
}
