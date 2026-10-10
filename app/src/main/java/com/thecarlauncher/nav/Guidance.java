package com.thecarlauncher.nav;

import android.content.Context;
import android.location.Location;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;

import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.util.Prefs;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Turn-by-turn guidance on an {@link OsmApi.Route}: tracks progress along the route from GPS,
 * picks the next manoeuvre, re-routes when the car leaves the route, and speaks the prompts.
 */
public final class Guidance implements LocationTracker.Listener {
    public interface Listener { void onGuidanceChanged(); }

    public static final String P_VOICE = "nav_voice", P_RECENT = "nav_recent";
    private static final double OFF_ROUTE_M = 60, ARRIVED_M = 35;
    private static Guidance sInstance;

    private final Context app;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private OsmApi.Route route;
    private String destName;
    private double destLat, destLon;
    private int pos;            // index of the geometry point we are past
    private double done;        // metres driven along the route
    private int next = -1;      // index into route.steps of the next manoeuvre
    private int offCount;
    private long lastReroute;
    private boolean routing, arrived;
    private String error;
    // Voice
    private TextToSpeech tts;
    private boolean ttsReady;
    private int spokenStep = -1, spokenStage;

    public static synchronized Guidance get(Context c) {
        if (sInstance == null) sInstance = new Guidance(c.getApplicationContext());
        return sInstance;
    }

    private Guidance(Context c) { app = c; }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        for (Listener l : listeners) l.onGuidanceChanged();
    }

    private String s(String sr, String en) { return Mib.t(app, sr, en); }

    // ---- State -------------------------------------------------------------------------------

    public boolean active() { return route != null || routing; }
    public boolean routing() { return routing; }
    public boolean arrived() { return arrived; }
    public String error() { return error; }
    public OsmApi.Route route() { return route; }
    public String destination() { return destName; }
    public double destLat() { return destLat; }
    public double destLon() { return destLon; }

    /** Geometry index the car has passed (to fade out the driven part of the line). */
    public int progressIndex() { return pos; }

    public double remaining() { return route == null ? 0 : Math.max(0, route.distance - done); }

    /** Remaining time, scaled from the route's own estimate. */
    public double remainingSeconds() {
        if (route == null || route.distance <= 0) return 0;
        return route.duration * remaining() / route.distance;
    }

    public OsmApi.Step nextStep() {
        return route == null || next < 0 || next >= route.steps.size() ? null : route.steps.get(next);
    }

    /** Metres to the next manoeuvre. */
    public double toNext() {
        OsmApi.Step st = nextStep();
        if (st == null) return 0;
        return Math.max(0, route.along[st.index] - done);
    }

    // ---- Start / stop ------------------------------------------------------------------------

    public void start(String name, double lat, double lon) {
        destName = name;
        destLat = lat;
        destLon = lon;
        arrived = false;
        error = null;
        remember(name, lat, lon);
        LocationTracker.get(app).addListener(this);
        initTts();
        calculate();
    }

    public void stop() {
        route = null;
        routing = false;
        arrived = false;
        destName = null;
        LocationTracker.get(app).removeListener(this);
        changed();
    }

    private void calculate() {
        Location l = LocationTracker.get(app).location();
        if (l == null) {
            error = s("Nema GPS pozicije", "No GPS position");
            changed();
            return;
        }
        routing = true;
        lastReroute = SystemClock.elapsedRealtime();
        changed();
        float bearing = l.hasBearing() && l.getSpeed() > 3 ? l.getBearing() : -1;
        OsmApi.route(l.getLatitude(), l.getLongitude(), bearing, destLat, destLon, new OsmApi.Callback<OsmApi.Route>() {
            @Override
            public void done(OsmApi.Route r, String err) {
                routing = false;
                if (destName == null) return; // stopped meanwhile
                if (r == null) {
                    error = s("Ruta nije izračunata", "Could not calculate route") + (err != null ? " (" + err + ")" : "");
                } else {
                    error = null;
                    boolean first = route == null;
                    route = r;
                    pos = 0;
                    done = 0;
                    next = r.steps.size() > 1 ? 1 : -1;
                    offCount = 0;
                    spokenStep = -1;
                    if (first) say(s("Ruta je izračunata.", "Route calculated."));
                }
                changed();
            }
        });
    }

    // ---- Progress ----------------------------------------------------------------------------

    @Override
    public void onLocationUpdate(LocationTracker t) {
        Location l = t.location();
        if (route == null || l == null || !t.hasFix()) return;
        double lat = l.getLatitude(), lon = l.getLongitude();

        // Project onto the route, searching a window ahead of the last position.
        int best = pos;
        double bestD = Double.MAX_VALUE, bestAlong = done;
        int end = Math.min(route.lat.length - 1, pos + 400);
        for (int i = Math.max(0, pos - 2); i < end; i++) {
            double[] pr = project(lat, lon, route.lat[i], route.lon[i], route.lat[i + 1], route.lon[i + 1]);
            if (pr[0] < bestD) {
                bestD = pr[0];
                best = i;
                bestAlong = route.along[i] + pr[1] * (route.along[i + 1] - route.along[i]);
            }
        }
        if (bestD > OFF_ROUTE_M) {
            if (++offCount >= 3 && !routing && SystemClock.elapsedRealtime() - lastReroute > 12000) {
                say(s("Ruta se ponovo izračunava.", "Recalculating."));
                calculate();
            }
            changed();
            return;
        }
        offCount = 0;
        pos = best;
        done = Math.max(done, bestAlong);

        while (next >= 0 && next < route.steps.size() && route.along[route.steps.get(next).index] < done - 15) next++;
        if (next >= route.steps.size()) next = -1;

        if (OsmApi.meters(lat, lon, destLat, destLon) < ARRIVED_M || remaining() < ARRIVED_M) {
            arrived = true;
            say(s("Stigli ste na odredište.", "You have arrived."));
            route = null;
            LocationTracker.get(app).removeListener(this);
        } else {
            prompt(l.getSpeed());
        }
        changed();
    }

    /** Distance from p to segment a-b (metres) and the fraction along it. */
    private static double[] project(double lat, double lon, double alat, double alon, double blat, double blon) {
        double k = Math.cos(Math.toRadians(lat)) * 111320, m = 110540;
        double ax = (alon - lon) * k, ay = (alat - lat) * m, bx = (blon - lon) * k, by = (blat - lat) * m;
        double dx = bx - ax, dy = by - ay, len = dx * dx + dy * dy;
        double f = len == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len));
        double px = ax + f * dx, py = ay + f * dy;
        return new double[]{Math.sqrt(px * px + py * py), f};
    }

    // ---- Instructions ------------------------------------------------------------------------

    /** Short instruction for the manoeuvre box, e.g. "Skrenite desno". */
    public String instruction(OsmApi.Step st) {
        if (st == null) return "";
        String m = st.modifier;
        switch (st.type) {
            case "arrive": return s("Odredište", "Destination");
            case "depart": return s("Krenite", "Start");
            case "roundabout":
            case "rotary":
            case "roundabout turn":
                return st.exit > 0 ? s("Kružni tok, " + st.exit + ". izlaz", "Roundabout, exit " + st.exit)
                        : s("Kružni tok", "Roundabout");
            case "merge": return s("Uključite se", "Merge");
            case "on ramp": return s("Uđite na put", "Take the ramp");
            case "off ramp": return side(m, s("Izađite levo", "Exit left"), s("Izađite desno", "Exit right"), s("Izađite", "Exit"));
            case "fork": return side(m, s("Držite se levo", "Keep left"), s("Držite se desno", "Keep right"), s("Nastavite", "Continue"));
            case "end of road": return side(m, s("Na kraju puta levo", "At the end, left"), s("Na kraju puta desno", "At the end, right"), s("Nastavite", "Continue"));
            default:
                if ("uturn".equals(m)) return s("Polukružno okretanje", "Make a U-turn");
                if ("sharp left".equals(m)) return s("Oštro levo", "Sharp left");
                if ("sharp right".equals(m)) return s("Oštro desno", "Sharp right");
                if ("slight left".equals(m)) return s("Blago levo", "Bear left");
                if ("slight right".equals(m)) return s("Blago desno", "Bear right");
                if ("left".equals(m)) return s("Skrenite levo", "Turn left");
                if ("right".equals(m)) return s("Skrenite desno", "Turn right");
                return s("Nastavite pravo", "Continue straight");
        }
    }

    private static String side(String m, String left, String right, String other) {
        if (m.contains("left")) return left;
        if (m.contains("right")) return right;
        return other;
    }

    public static String distance(double m) {
        if (m >= 10000) return String.format(Locale.US, "%.0f km", m / 1000);
        if (m >= 950) return String.format(Locale.US, "%.1f km", m / 1000);
        if (m >= 100) return (Math.round(m / 50.0) * 50) + " m";
        return (Math.round(m / 10.0) * 10) + " m";
    }

    // ---- Voice -------------------------------------------------------------------------------

    private void initTts() {
        if (tts != null || !Prefs.bool(app, P_VOICE, true)) return;
        tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) return;
                boolean en = "en".equals(Prefs.str(app, Prefs.LANGUAGE, "sr"));
                Locale[] wanted = en ? new Locale[]{Locale.ENGLISH}
                        : new Locale[]{new Locale("sr", "RS"), new Locale("sr"), new Locale("hr", "HR"), new Locale("bs")};
                for (Locale loc : wanted) {
                    if (tts.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) {
                        tts.setLanguage(loc);
                        ttsReady = true;
                        break;
                    }
                }
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            }
        });
    }

    private void say(String text) {
        if (!ttsReady || !Prefs.bool(app, P_VOICE, true)) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), "nav");
    }

    /** Early warning (~600 m or ~25 s ahead) and a "now" prompt just before the manoeuvre. */
    private void prompt(float speed) {
        OsmApi.Step st = nextStep();
        if (st == null || "arrive".equals(st.type)) return;
        double d = toNext();
        if (next != spokenStep) {
            spokenStep = next;
            spokenStage = 0;
        }
        double early = Math.max(400, speed * 25);
        double now = Math.max(60, speed * 6);
        String street = st.name.length() > 0 ? s(", " + st.name, ", onto " + st.name) : "";
        if (spokenStage < 1 && d < early && d > now * 2) {
            spokenStage = 1;
            say(s("Za " + spoken(d) + ", ", "In " + spoken(d) + ", ") + instruction(st).toLowerCase(Locale.ROOT) + street);
        } else if (spokenStage < 2 && d <= now) {
            spokenStage = 2;
            say(s("Sada ", "Now ") + instruction(st).toLowerCase(Locale.ROOT));
        }
    }

    private String spoken(double m) {
        if (m >= 1000) return String.format(Locale.US, "%.1f", m / 1000).replace(".0", "").replace('.', ',') + s(" kilometara", " kilometres");
        return (Math.round(m / 50.0) * 50) + s(" metara", " metres");
    }

    // ---- Recent destinations -----------------------------------------------------------------

    private void remember(String name, double lat, double lon) {
        StringBuilder sb = new StringBuilder();
        sb.append(name.replace("|", " ").replace(";", " ")).append('|').append(lat).append('|').append(lon);
        int n = 1;
        for (String e : Prefs.str(app, P_RECENT, "").split(";")) {
            if (e.length() == 0 || e.startsWith(name.replace("|", " ").replace(";", " ") + "|")) continue;
            if (n++ >= 8) break;
            sb.append(';').append(e);
        }
        Prefs.put(app, P_RECENT, sb.toString());
    }

    public java.util.List<OsmApi.Place> recent() {
        java.util.List<OsmApi.Place> out = new java.util.ArrayList<>();
        for (String e : Prefs.str(app, P_RECENT, "").split(";")) {
            String[] f = e.split("\\|");
            if (f.length < 3) continue;
            try {
                out.add(new OsmApi.Place(f[0], "", Double.parseDouble(f[1]), Double.parseDouble(f[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
