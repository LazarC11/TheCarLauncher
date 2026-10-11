package com.thecarlauncher.nav;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Free OpenStreetMap services, used the way their usage policies ask (one request at a time,
 * an identifying User-Agent, attribution on the map):
 * Nominatim for address search, OSRM (FOSSGIS server) for routes, Overpass for nearby places.
 */
public final class OsmApi {
    public interface Callback<T> { void done(T result, String error); }

    /** Shows the loading banner until the callback runs. */
    private static <T> Callback<T> loading(final String key, String sr, String en, final Callback<T> cb) {
        com.thecarlauncher.mib.LoadingBanner.start(key, com.thecarlauncher.mib.LoadingBanner.t(sr, en));
        return new Callback<T>() {
            @Override
            public void done(T result, String error) {
                com.thecarlauncher.mib.LoadingBanner.stop(key);
                cb.done(result, error);
            }
        };
    }

    /** A search result or point of interest. */
    public static final class Place {
        public final String name, detail;
        public final double lat, lon;
        public float distanceM = -1;

        public Place(String name, String detail, double lat, double lon) {
            this.name = name;
            this.detail = detail == null ? "" : detail;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** One manoeuvre of a route. */
    public static final class Step {
        public final String type, modifier, name;
        public final int exit;
        public final double lat, lon, distance;
        /** Index into the route geometry where the manoeuvre happens. */
        public int index;

        Step(String type, String modifier, String name, int exit, double lat, double lon, double distance) {
            this.type = type;
            this.modifier = modifier;
            this.name = name;
            this.exit = exit;
            this.lat = lat;
            this.lon = lon;
            this.distance = distance;
        }
    }

    /** A driving route: geometry, totals and manoeuvres. */
    public static final class Route {
        public final double[] lat, lon;
        /** Distance from the start to each geometry point, metres. */
        public final double[] along;
        public final double distance, duration;
        public final List<Step> steps;

        Route(double[] lat, double[] lon, double distance, double duration, List<Step> steps) {
            this.lat = lat;
            this.lon = lon;
            this.distance = distance;
            this.duration = duration;
            this.steps = steps;
            along = new double[lat.length];
            for (int i = 1; i < lat.length; i++) along[i] = along[i - 1] + meters(lat[i - 1], lon[i - 1], lat[i], lon[i]);
            // Attach every manoeuvre to its nearest geometry point, walking forward.
            int from = 0;
            for (Step s : steps) {
                int best = from;
                double bd = Double.MAX_VALUE;
                for (int i = from; i < lat.length; i++) {
                    double d = meters(lat[i], lon[i], s.lat, s.lon);
                    if (d < bd) {
                        bd = d;
                        best = i;
                    }
                    if (bd < 1) break;
                }
                s.index = best;
                from = best;
            }
        }
    }

    private static final String UA = "TheCarLauncher/2.7 (personal Android head-unit launcher)";
    private static final String NOMINATIM = "https://nominatim.openstreetmap.org/search";
    private static final String[] OSRM = {
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/",
            "https://router.project-osrm.org/route/v1/driving/",
    };
    private static final String OVERPASS = "https://overpass-api.de/api/interpreter";

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static long lastNominatim;

    private OsmApi() {}

    // ---- Search (Nominatim) ------------------------------------------------------------------

    /** Address / place search, biased towards the current position. */
    public static void search(final String query, final double nearLat, final double nearLon, final String lang,
                              Callback<List<Place>> callback) {
        final Callback<List<Place>> cb = loading("nav-search", "Traženje adrese…", "Searching for the address…", callback);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder u = new StringBuilder(NOMINATIM).append("?format=jsonv2&limit=10&addressdetails=1&q=")
                            .append(URLEncoder.encode(query, "UTF-8"));
                    if (!Double.isNaN(nearLat)) {
                        // Prefer results within ~150 km, but allow anything.
                        u.append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=0",
                                nearLon - 1.5, nearLat + 1.0, nearLon + 1.5, nearLat - 1.0));
                    }
                    throttleNominatim();
                    JSONArray arr = new JSONArray(get(u.toString(), lang));
                    List<Place> out = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        out.add(new Place(title(o), detail(o), o.getDouble("lat"), o.getDouble("lon")));
                    }
                    // Keep Nominatim's relevance order; just note how far each result is.
                    if (!Double.isNaN(nearLat)) for (Place p : out) p.distanceM = (float) meters(nearLat, nearLon, p.lat, p.lon);
                    post(cb, out, null);
                } catch (Exception e) {
                    post(cb, null, e.getClass().getSimpleName());
                }
            }
        });
    }

    private static synchronized void throttleNominatim() throws InterruptedException {
        long wait = lastNominatim + 1100 - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastNominatim = System.currentTimeMillis();
    }

    /** "Street 12" or the place name, like a factory destination list. */
    private static String title(JSONObject o) {
        JSONObject a = o.optJSONObject("address");
        String name = o.optString("name", "");
        if (a != null) {
            String road = first(a, "road", "pedestrian", "street", "square");
            String num = a.optString("house_number", "");
            if (name.length() == 0 && road.length() > 0) return num.length() > 0 ? road + " " + num : road;
            if (name.length() == 0) name = first(a, "village", "town", "city", "municipality", "county");
        }
        if (name.length() == 0) {
            String d = o.optString("display_name", "");
            int c = d.indexOf(',');
            name = c > 0 ? d.substring(0, c) : d;
        }
        return name;
    }

    private static String detail(JSONObject o) {
        JSONObject a = o.optJSONObject("address");
        if (a == null) return "";
        String road = first(a, "road", "pedestrian", "street");
        String num = a.optString("house_number", "");
        String place = first(a, "city", "town", "village", "municipality", "suburb");
        String name = o.optString("name", "");
        StringBuilder sb = new StringBuilder();
        if (name.length() > 0 && road.length() > 0) sb.append(road).append(num.length() > 0 ? " " + num : "");
        if (place.length() > 0) sb.append(sb.length() > 0 ? ", " : "").append(place);
        String country = a.optString("country", "");
        if (country.length() > 0 && !"rs".equals(a.optString("country_code"))) sb.append(", ").append(country);
        return sb.toString();
    }

    private static String first(JSONObject a, String... keys) {
        for (String k : keys) {
            String v = a.optString(k, "");
            if (v.length() > 0) return v;
        }
        return "";
    }

    // ---- Routing (OSRM) ----------------------------------------------------------------------

    /** Driving route with turn-by-turn steps; tries the FOSSGIS server, then the OSRM demo. */
    public static void route(final double fromLat, final double fromLon, final float bearing,
                             final double toLat, final double toLon, Callback<Route> callback) {
        final Callback<Route> cb = loading("nav-route", "Izračunavanje rute…", "Calculating the route…", callback);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                String err = null;
                for (String base : OSRM) {
                    try {
                        String coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", fromLon, fromLat, toLon, toLat);
                        String q = "?overview=full&geometries=polyline6&steps=true";
                        // Start in the direction we are driving, so no U-turn is proposed.
                        if (bearing >= 0) q += String.format(Locale.US, "&bearings=%d,60;", Math.round(bearing) % 360);
                        JSONObject res = new JSONObject(get(base + coords + q, null));
                        if (!"Ok".equals(res.optString("code"))) {
                            err = res.optString("message", res.optString("code"));
                            continue;
                        }
                        post(cb, parseRoute(res.getJSONArray("routes").getJSONObject(0)), null);
                        return;
                    } catch (Exception e) {
                        err = e.getClass().getSimpleName();
                    }
                }
                post(cb, null, err);
            }
        });
    }

    private static Route parseRoute(JSONObject r) throws Exception {
        List<double[]> pts = decode(r.getString("geometry"), 1e6);
        double[] la = new double[pts.size()], lo = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            la[i] = pts.get(i)[0];
            lo[i] = pts.get(i)[1];
        }
        List<Step> steps = new ArrayList<>();
        JSONArray legs = r.getJSONArray("legs");
        for (int l = 0; l < legs.length(); l++) {
            JSONArray ss = legs.getJSONObject(l).getJSONArray("steps");
            for (int i = 0; i < ss.length(); i++) {
                JSONObject s = ss.getJSONObject(i);
                JSONObject m = s.getJSONObject("maneuver");
                JSONArray loc = m.getJSONArray("location");
                String name = s.optString("name", "");
                String ref = s.optString("ref", "");
                if (name.length() == 0) name = ref;
                steps.add(new Step(m.optString("type"), m.optString("modifier", ""), name, m.optInt("exit", 0),
                        loc.getDouble(1), loc.getDouble(0), s.optDouble("distance", 0)));
            }
        }
        return new Route(la, lo, r.optDouble("distance"), r.optDouble("duration"), steps);
    }

    /** Google encoded-polyline decoder (OSRM polyline6 uses precision 1e6). */
    static List<double[]> decode(String s, double precision) {
        List<double[]> out = new ArrayList<>();
        int i = 0, lat = 0, lon = 0;
        while (i < s.length()) {
            int[] v = new int[2];
            for (int k = 0; k < 2; k++) {
                int shift = 0, result = 0, b;
                do {
                    b = s.charAt(i++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                v[k] = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            }
            lat += v[0];
            lon += v[1];
            out.add(new double[]{lat / precision, lon / precision});
        }
        return out;
    }

    // ---- Nearby places (Overpass) ------------------------------------------------------------

    /** Places with an OSM tag (e.g. "amenity=fuel") within radius metres, nearest first. */
    public static void nearby(final String tag, final double lat, final double lon, final int radius,
                              Callback<List<Place>> callback) {
        final Callback<List<Place>> cb = loading("nav-nearby", "Traženje mesta u blizini…", "Finding places nearby…", callback);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] kv = tag.split("=");
                    String q = String.format(Locale.US, "[out:json][timeout:20];nwr[\"%s\"=\"%s\"](around:%d,%.6f,%.6f);out center 40;",
                            kv[0], kv[1], radius, lat, lon);
                    JSONObject res = new JSONObject(post(OVERPASS, "data=" + URLEncoder.encode(q, "UTF-8")));
                    JSONArray els = res.getJSONArray("elements");
                    List<Place> out = new ArrayList<>();
                    for (int i = 0; i < els.length(); i++) {
                        JSONObject e = els.getJSONObject(i);
                        JSONObject t = e.optJSONObject("tags");
                        double la, lo;
                        if (e.has("lat")) {
                            la = e.getDouble("lat");
                            lo = e.getDouble("lon");
                        } else if (e.has("center")) {
                            la = e.getJSONObject("center").getDouble("lat");
                            lo = e.getJSONObject("center").getDouble("lon");
                        } else continue;
                        String name = t == null ? "" : t.optString("name", t.optString("brand", t.optString("operator", "")));
                        String street = t == null ? "" : t.optString("addr:street", "");
                        String num = t == null ? "" : t.optString("addr:housenumber", "");
                        out.add(new Place(name, street.length() > 0 ? street + (num.length() > 0 ? " " + num : "") : "", la, lo));
                    }
                    sortByDistance(out, lat, lon);
                    while (out.size() > 15) out.remove(out.size() - 1);
                    post(cb, out, null);
                } catch (Exception e) {
                    post(cb, null, e.getClass().getSimpleName());
                }
            }
        });
    }

    // ---- HTTP helpers ------------------------------------------------------------------------

    private static String get(String url, String lang) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(8000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", UA);
            if (lang != null) con.setRequestProperty("Accept-Language", lang);
            return read(con);
        } finally {
            con.disconnect();
        }
    }

    private static String post(String url, String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(8000);
            con.setReadTimeout(25000);
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", UA);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream os = con.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            return read(con);
        } finally {
            con.disconnect();
        }
    }

    private static String read(HttpURLConnection con) throws Exception {
        int code = con.getResponseCode();
        if (code != 200) throw new java.io.IOException("HTTP " + code);
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
        return sb.toString();
    }

    private static <T> void post(final Callback<T> cb, final T result, final String error) {
        main.post(new Runnable() {
            @Override
            public void run() {
                cb.done(result, error);
            }
        });
    }

    // ---- Geometry ----------------------------------------------------------------------------

    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static void sortByDistance(List<Place> list, double lat, double lon) {
        if (Double.isNaN(lat)) return;
        for (Place p : list) p.distanceM = (float) meters(lat, lon, p.lat, p.lon);
        Collections.sort(list, new Comparator<Place>() {
            @Override
            public int compare(Place a, Place b) {
                return Float.compare(a.distanceM, b.distanceM);
            }
        });
    }
}
