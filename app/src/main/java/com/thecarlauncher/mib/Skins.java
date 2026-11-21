package com.thecarlauncher.mib;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.thecarlauncher.util.Prefs;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skins: sets of the factory MIB pictures from different software trains. Every picture the
 * launcher draws from the skin has a fixed name (ui_*); a skin is a folder of PNGs with those
 * names (and a skin.json with its name). A picture a skin does not have stays the built-in one.
 *
 * Skins come with the app (assets/skins/&lt;id&gt;/) or are copied onto the unit or a USB stick
 * as MibDroid/skins/&lt;id&gt;/. mib/tools/build_skin.py makes one from a train's images.mcf.
 * The launcher's resources ({@link Res}) hand out the active skin's pictures, so every screen
 * uses them without knowing about skins.
 */
public final class Skins {
    public static final String P_SKIN = "skin";
    public static final String BUILT_IN = "";

    /** A skin the launcher can use. */
    public static final class Skin {
        public final String id, name, where;
        final File dir;          // a folder on the unit or USB, or
        final String asset;      // a folder in the app's assets

        Skin(String id, String name, String where, File dir, String asset) {
            this.id = id;
            this.name = name;
            this.where = where;
            this.dir = dir;
            this.asset = asset;
        }
    }

    private static Skin active;
    private static String activeFor;
    private static final Map<String, Boolean> HAS = new HashMap<>();

    private Skins() {}

    /** Folders searched for skins copied onto the unit or a USB stick. */
    static List<File> roots() {
        List<File> out = new ArrayList<>();
        File ext = android.os.Environment.getExternalStorageDirectory();
        if (ext != null) out.add(new File(ext, "MibDroid/skins"));
        File storage = new File("/storage");
        File[] vols = storage.listFiles();
        if (vols != null) for (File v : vols) out.add(new File(v, "MibDroid/skins"));
        File mnt = new File("/mnt");
        File[] m = mnt.listFiles();
        if (m != null) for (File v : m) out.add(new File(v, "MibDroid/skins"));
        return out;
    }

    /** The built-in skin first, then the app's own, then the ones found on the unit / USB. */
    public static List<Skin> all(Context c) {
        List<Skin> out = new ArrayList<>();
        out.add(new Skin(BUILT_IN, "MIB 2 · MHI2 P1101", Mib.t(c, "ugrađen", "built in"), null, null));
        try {
            String[] ids = c.getAssets().list("skins");
            if (ids != null) for (String id : ids) {
                out.add(new Skin(id, name(read(c.getAssets().open("skins/" + id + "/skin.json")), id),
                        Mib.t(c, "u aplikaciji", "in the app"), null, "skins/" + id));
            }
        } catch (Exception ignored) {
        }
        for (File root : roots()) {
            File[] dirs = root.listFiles();
            if (dirs == null) continue;
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                boolean dup = false;
                for (Skin s : out) dup |= s.id.equals(d.getName());
                if (dup) continue;
                String n = d.getName();
                try {
                    File j = new File(d, "skin.json");
                    if (j.exists()) n = name(read(new FileInputStream(j)), n);
                } catch (Exception ignored) {
                }
                out.add(new Skin(d.getName(), n, d.getParent(), d, null));
            }
        }
        return out;
    }

    private static String name(String json, String def) {
        try {
            return new JSONObject(json).optString("name", def);
        } catch (Exception e) {
            return def;
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    /** The chosen skin (null: the built-in pictures). */
    static Skin active(Context c) {
        String id = Prefs.str(c, P_SKIN, BUILT_IN);
        if (id.equals(activeFor)) return active;
        activeFor = id;
        active = null;
        HAS.clear();
        if (!BUILT_IN.equals(id)) for (Skin s : all(c)) if (s.id.equals(id)) active = s;
        return active;
    }

    public static String activeName(Context c) {
        Skin s = active(c);
        return s == null ? all(c).get(0).name : s.name;
    }

    /** Switches skin; the caller rebuilds the screens. */
    public static void choose(Context c, String id) {
        Prefs.put(c, P_SKIN, id);
        activeFor = null;
        Mib.clearSkinCache();
    }

    /** The active skin's picture for a resource name, or null to use the built-in one. */
    static InputStream open(Context c, String name) {
        Skin s = active(c);
        if (s == null || !name.startsWith("ui_")) return null;
        Boolean has = HAS.get(name);
        if (has != null && !has) return null;
        InputStream in = null;
        try {
            if (s.dir != null) {
                File f = new File(s.dir, name + ".png");
                if (f.exists()) in = new FileInputStream(f);
            } else if (s.asset != null) {
                in = c.getAssets().open(s.asset + "/" + name + ".png");
            }
        } catch (Exception ignored) {
        }
        HAS.put(name, in != null);
        return in;
    }

    /**
     * The launcher's resources: pictures named ui_* come from the active skin when it has them
     * (at their own pixel size, like the built-in drawable-nodpi pictures).
     */
    @SuppressWarnings("deprecation")
    public static final class Res extends Resources {
        private final Context c;
        private final Map<Integer, String> names = new HashMap<>();

        public Res(Context c, Resources base) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
            this.c = c;
        }

        private String nameOf(int id) {
            String n = names.get(id);
            if (n == null) {
                try {
                    n = "drawable".equals(getResourceTypeName(id)) ? getResourceEntryName(id) : "";
                } catch (Exception e) {
                    n = "";
                }
                names.put(id, n);
            }
            return n;
        }

        private Bitmap skinned(int id) {
            String n = nameOf(id);
            if (n.isEmpty()) return null;
            InputStream in = open(c, n);
            if (in == null) return null;
            try {
                Bitmap b = BitmapFactory.decodeStream(in);
                if (b != null) b.setDensity(Bitmap.DENSITY_NONE);
                return b;
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public Drawable getDrawable(int id, Theme theme) throws NotFoundException {
            Bitmap b = skinned(id);
            if (b != null) return new BitmapDrawable(this, b);
            return super.getDrawable(id, theme);
        }

        @Override
        public Drawable getDrawableForDensity(int id, int density, Theme theme) {
            Bitmap b = skinned(id);
            if (b != null) return new BitmapDrawable(this, b);
            return super.getDrawableForDensity(id, density, theme);
        }

        @Override
        public InputStream openRawResource(int id, TypedValue value) throws NotFoundException {
            String n = nameOf(id);
            InputStream in = n.isEmpty() ? null : open(c, n);
            if (in != null) {
                getValue(id, value, true);
                value.density = TypedValue.DENSITY_NONE;
                return in;
            }
            return super.openRawResource(id, value);
        }
    }
}
