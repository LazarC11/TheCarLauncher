package com.thecarlauncher.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The launcher's language: Serbian (Latin), English, Polish or Turkish. "Automatic" (the default)
 * follows the unit's region: its Android language first, then its time zone (many head units run
 * in English wherever they are sold), else English.
 * Texts are written in the code as Serbian / English pairs; Polish and Turkish come from
 * assets/lang/&lt;code&gt;.tsv, keyed by the English text (missing ones stay English).
 */
public final class Lang {
    public static final String AUTO = "auto", SR = "sr", EN = "en", PL = "pl", TR = "tr";
    /** Choices in Setup: code, name. */
    public static final String[][] CHOICES = {
            {SR, "Srpski (latinica)"}, {EN, "English"}, {PL, "Polski"}, {TR, "Türkçe"}};

    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();
    private static String sCached, sCachedPref;

    private Lang() {}

    /** The language in use: sr, en, pl or tr. */
    public static String current(Context c) {
        String pref = Prefs.str(c, Prefs.LANGUAGE, AUTO);
        if (pref != null && !AUTO.equals(pref)) return pref;
        if (sCached == null || !AUTO.equals(sCachedPref)) {
            sCached = detect(Locale.getDefault(), TimeZone.getDefault().getID());
            sCachedPref = AUTO;
        }
        return sCached;
    }

    /** True when the user picked "Automatic" (or never chose). */
    public static boolean automatic(Context c) {
        String pref = Prefs.str(c, Prefs.LANGUAGE, AUTO);
        return pref == null || AUTO.equals(pref);
    }

    /** Language for a locale / time zone; public for tests. */
    public static String detect(Locale locale, String timeZone) {
        String l = locale == null ? "" : locale.getLanguage();
        if (l.equals("sr") || l.equals("hr") || l.equals("bs") || l.equals("me") || l.equals("cnr")) return SR;
        if (l.equals("pl")) return PL;
        if (l.equals("tr")) return TR;
        String c = locale == null ? "" : locale.getCountry();
        if (c.equals("RS") || c.equals("BA") || c.equals("ME") || c.equals("HR")) return SR;
        if (c.equals("PL")) return PL;
        if (c.equals("TR")) return TR;
        if (l.length() > 0 && !l.equals("en")) return EN; // another language the launcher does not have
        String z = timeZone == null ? "" : timeZone;
        if (z.equals("Europe/Belgrade") || z.equals("Europe/Sarajevo") || z.equals("Europe/Podgorica")
                || z.equals("Europe/Zagreb")) return SR;
        if (z.equals("Europe/Warsaw")) return PL;
        if (z.equals("Europe/Istanbul") || z.equals("Asia/Istanbul")) return TR;
        return EN;
    }

    /** The text for the current language, from the code's Serbian / English pair. */
    public static String text(Context c, String sr, String en) {
        String lang = current(c);
        if (SR.equals(lang)) return sr;
        if (EN.equals(lang) || en == null) return en;
        String t = table(c, lang).get(en);
        return t != null ? t : en;
    }

    /** True when the language is not Serbian (most non-text choices then follow English). */
    public static boolean notSerbian(Context c) { return !SR.equals(current(c)); }

    /** Locale for voice guidance, street names and similar. */
    public static Locale locale(Context c) {
        String l = current(c);
        if (SR.equals(l)) return new Locale("sr", "RS");
        if (PL.equals(l)) return new Locale("pl", "PL");
        if (TR.equals(l)) return new Locale("tr", "TR");
        return Locale.ENGLISH;
    }

    /** Accept-Language for online map searches (Nominatim). */
    public static String acceptLanguage(Context c) {
        String l = current(c);
        if (SR.equals(l)) return "sr-Latn,sr,hr,bs,en";
        if (EN.equals(l)) return "en";
        return l + ",en";
    }

    private static synchronized Map<String, String> table(Context c, String lang) {
        Map<String, String> m = TABLES.get(lang);
        if (m != null) return m;
        m = new HashMap<>();
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    c.getApplicationContext().getAssets().open("lang/" + lang + ".tsv"), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                m.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
            }
            in.close();
        } catch (Exception ignored) {
        }
        TABLES.put(lang, m);
        return m;
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }
}
