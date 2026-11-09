package com.thecarlauncher.util;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * Which build fits the unit the launcher runs on, from the rules in units.json: Software update
 * installs that build. The release carries the newest rules (so units added after this version
 * are still recognised); the launcher keeps a copy in its assets for offline use.
 */
public final class UnitKind {
    private static final String P_RULES = "unit_rules";
    private static JSONObject sRules;

    private UnitKind() {}

    /** Build id for this unit ("qf", "hzbhd", "carsvc", "syu", or one added later in units.json). */
    public static String detect(Context c) {
        JSONObject rules = rules(c);
        try {
            JSONArray units = rules.getJSONArray("units");
            for (int i = 0; i < units.length(); i++) {
                JSONObject u = units.getJSONObject(i);
                JSONArray match = u.optJSONArray("match");
                if (match == null) continue;
                for (int k = 0; k < match.length(); k++) {
                    JSONObject m = match.optJSONObject(k);
                    if (m != null && matches(c, m)) return u.getString("id");
                }
            }
            return rules.optString("default", "qf");
        } catch (Exception e) {
            return "qf";
        }
    }

    /** Release asset holding the build for this unit. */
    public static String assetName(Context c, String id) {
        JSONObject u = unit(c, id);
        if (u != null && u.optString("asset").length() > 0) return u.optString("asset");
        return "qf".equals(id) ? "TheCarLauncher.apk" : "TheCarLauncher_" + id + ".apk";
    }

    public static String name(Context c, String id) {
        JSONObject u = unit(c, id);
        return u != null && u.optString("name").length() > 0 ? u.optString("name") : id;
    }

    /**
     * Rules from a release (units.json). Kept when they are valid and not older than what the
     * launcher has; returns true when they are now in use.
     */
    public static synchronized boolean offerRules(Context c, String json) {
        try {
            JSONObject r = new JSONObject(json);
            r.getJSONArray("units").getJSONObject(0).getString("id");
            if (r.optInt("rulesVersion") < rules(c).optInt("rulesVersion")) return false;
            Prefs.put(c, P_RULES, json);
            sRules = r;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Rules in use: the newer of the saved release copy and the launcher's own. */
    static synchronized JSONObject rules(Context c) {
        if (sRules != null) return sRules;
        JSONObject own = null, saved = null;
        try {
            InputStream in = c.getApplicationContext().getAssets().open("units.json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            own = new JSONObject(bo.toString("UTF-8"));
        } catch (Exception ignored) {
        }
        try {
            String s = Prefs.str(c, P_RULES, null);
            if (s != null) saved = new JSONObject(s);
        } catch (Exception ignored) {
        }
        if (own == null && saved == null) {
            try {
                own = new JSONObject("{\"default\":\"qf\",\"units\":[]}");
            } catch (Exception ignored) {
            }
        }
        sRules = saved != null && (own == null || saved.optInt("rulesVersion") >= own.optInt("rulesVersion")) ? saved : own;
        return sRules;
    }

    private static JSONObject unit(Context c, String id) {
        JSONArray units = rules(c).optJSONArray("units");
        if (units == null) return null;
        for (int i = 0; i < units.length(); i++) {
            JSONObject u = units.optJSONObject(i);
            if (u != null && id.equals(u.optString("id"))) return u;
        }
        return null;
    }

    /** True when every condition of the entry holds; unknown conditions never hold. */
    static boolean matches(Context c, JSONObject m) {
        if (m.length() == 0) return false;
        Iterator<String> keys = m.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            String v = m.optString(k);
            boolean ok;
            switch (k) {
                case "package":
                    ok = installed(c, v);
                    break;
                case "service":
                    ok = service(v);
                    break;
                case "file":
                    ok = new java.io.File(v).exists();
                    break;
                case "prop": {
                    String p = prop(v);
                    if (m.has("equals")) ok = m.optString("equals").equals(p);
                    else if (m.has("startsWith")) ok = p.startsWith(m.optString("startsWith"));
                    else ok = p.length() > 0;
                    break;
                }
                case "equals":
                case "startsWith":
                    ok = m.has("prop");
                    break;
                case "hardware":
                    ok = contains(Build.HARDWARE, v);
                    break;
                case "manufacturer":
                    ok = contains(Build.MANUFACTURER, v);
                    break;
                case "model":
                    ok = contains(Build.MODEL, v);
                    break;
                default:
                    ok = false;
            }
            if (!ok) return false;
        }
        return true;
    }

    private static boolean contains(String field, String v) {
        return field != null && v.length() > 0 && field.toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT));
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean service(String name) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            return sm.getMethod("checkService", String.class).invoke(null, name) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String prop(String name) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class).invoke(null, name);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
