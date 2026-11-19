package com.thecarlauncher.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The launcher's language: Serbian (Latin), English, Turkish and every official language of the
 * European Union. "Automatic" (the default) follows the unit's region: its Android language first,
 * then its country, then its time zone (many head units run in English wherever they are sold),
 * else English.
 * Texts are written in the code as Serbian / English pairs; the other languages come from
 * assets/lang/&lt;code&gt;.tsv, keyed by the English text (missing ones stay English).
 */
public final class Lang {
    public static final String AUTO = "auto", SR = "sr", EN = "en", PL = "pl", TR = "tr";
    /** Choices in Setup: code, name (in that language). */
    public static final String[][] CHOICES = {
            {SR, "Srpski (latinica)"}, {EN, "English"},
            {"bg", "Български"}, {"cs", "Čeština"}, {"da", "Dansk"}, {"de", "Deutsch"},
            {"el", "Ελληνικά"}, {"es", "Español"}, {"et", "Eesti"}, {"fi", "Suomi"},
            {"fr", "Français"}, {"ga", "Gaeilge"}, {"hr", "Hrvatski"}, {"hu", "Magyar"},
            {"it", "Italiano"}, {"lt", "Lietuvių"}, {"lv", "Latviešu"}, {"mt", "Malti"},
            {"nl", "Nederlands"}, {PL, "Polski"}, {"pt", "Português"}, {"ro", "Română"},
            {"sk", "Slovenčina"}, {"sl", "Slovenščina"}, {"sv", "Svenska"}, {TR, "Türkçe"}};

    /** Country (ISO) → language, for units whose Android language is not one of ours. */
    private static final String[][] COUNTRIES = {
            {"RS", SR}, {"BA", SR}, {"ME", SR}, {"HR", "hr"}, {"PL", PL}, {"TR", TR},
            {"BG", "bg"}, {"CZ", "cs"}, {"DK", "da"}, {"DE", "de"}, {"AT", "de"}, {"LI", "de"},
            {"GR", "el"}, {"CY", "el"}, {"ES", "es"}, {"EE", "et"}, {"FI", "fi"}, {"FR", "fr"},
            {"MC", "fr"}, {"HU", "hu"}, {"IT", "it"}, {"SM", "it"}, {"LT", "lt"}, {"LV", "lv"},
            {"NL", "nl"}, {"PT", "pt"}, {"RO", "ro"}, {"MD", "ro"}, {"SK", "sk"}, {"SI", "sl"},
            {"SE", "sv"}};

    /** Time zone → language. */
    private static final String[][] ZONES = {
            {"Europe/Belgrade", SR}, {"Europe/Sarajevo", SR}, {"Europe/Podgorica", SR},
            {"Europe/Zagreb", "hr"}, {"Europe/Warsaw", PL}, {"Europe/Istanbul", TR}, {"Asia/Istanbul", TR},
            {"Europe/Sofia", "bg"}, {"Europe/Prague", "cs"}, {"Europe/Copenhagen", "da"},
            {"Europe/Berlin", "de"}, {"Europe/Vienna", "de"}, {"Europe/Athens", "el"},
            {"Asia/Nicosia", "el"}, {"Europe/Madrid", "es"}, {"Europe/Tallinn", "et"},
            {"Europe/Helsinki", "fi"}, {"Europe/Paris", "fr"}, {"Europe/Budapest", "hu"},
            {"Europe/Rome", "it"}, {"Europe/Vilnius", "lt"}, {"Europe/Riga", "lv"},
            {"Europe/Amsterdam", "nl"}, {"Europe/Lisbon", "pt"}, {"Europe/Bucharest", "ro"},
            {"Europe/Chisinau", "ro"}, {"Europe/Bratislava", "sk"}, {"Europe/Ljubljana", "sl"},
            {"Europe/Stockholm", "sv"}};

    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();
    private static String sCached, sCachedPref;

    private Lang() {}

    /** The language in use (one of the CHOICES codes). */
    public static String current(Context c) {
        String pref = Prefs.str(c, Prefs.LANGUAGE, AUTO);
        if (pref != null && !AUTO.equals(pref) && known(pref)) return pref;
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

    private static boolean known(String code) {
        for (String[] l : CHOICES) if (l[0].equals(code)) return true;
        return false;
    }

    /** Language for a locale / time zone; public for tests. */
    public static String detect(Locale locale, String timeZone) {
        String l = locale == null ? "" : locale.getLanguage();
        if (l.equals("bs") || l.equals("me") || l.equals("cnr")) return SR;
        if (l.equals("in")) l = "id";
        // English is often just the unit's default: the country and time zone may say more.
        if (known(l) && !l.equals(EN)) return l;
        String c = locale == null ? "" : locale.getCountry();
        for (String[] e : COUNTRIES) if (e[0].equals(c)) return e[1];
        if (l.length() > 0 && !l.equals("en")) return EN; // another language the launcher does not have
        String z = timeZone == null ? "" : timeZone;
        for (String[] e : ZONES) if (e[0].equals(z)) return e[1];
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
        if (EN.equals(l)) return Locale.ENGLISH;
        String country = l.equals("el") ? "GR" : l.equals("da") ? "DK" : l.equals("et") ? "EE" : l.equals("sv") ? "SE"
                : l.equals("sl") ? "SI" : l.equals("cs") ? "CZ" : l.equals("ga") ? "IE" : l.toUpperCase(Locale.ROOT);
        return new Locale(l, country);
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
