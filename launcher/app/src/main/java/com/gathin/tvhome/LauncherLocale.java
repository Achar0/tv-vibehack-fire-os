package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import java.util.Locale;

/**
 * The launcher's own display language (Settings > Appearance > Language), independent of the TV's:
 * English, Italian or Simplified Chinese. Applied by wrapping the activity's and screensaver's context.
 *
 * Until one is chosen it follows the TV when the TV uses one of the three; any other TV language
 * (German ships its own strings, Traditional Chinese has none) is left alone.
 */
final class LauncherLocale {
    private LauncherLocale() {}

    static final String[] CODES = {"en", "it", "zh"};
    private static final String PREF = "launcher_language";

    /**
     * The launcher language for a TV set to {@code language}/{@code country}/{@code script}, or null to keep
     * the TV's own language. Chinese is chosen only when it is Simplified (not Taiwan, Hong Kong, Macau, Hant).
     */
    static String defaultFor(String language, String country, String script) {
        if ("zh".equals(language)) {
            boolean traditional = "Hant".equals(script) || "TW".equals(country) || "HK".equals(country)
                    || "MO".equals(country);
            return traditional ? null : "zh";
        }
        for (String c : CODES) if (c.equals(language)) return c;
        return null;
    }

    /** The chosen language, else the TV's when supported, else null (follow the TV). */
    private static String effective(Context ctx) {
        String saved = ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE).getString(PREF, null);
        if (saved != null) return saved;
        Locale sys = Resources.getSystem().getConfiguration().getLocales().get(0);
        return defaultFor(sys.getLanguage(), sys.getCountry(), sys.getScript());
    }

    /** What Settings shows and cycles from: the effective language, English when following another TV language. */
    static String current(Context ctx) {
        String code = effective(ctx);
        return code != null ? code : "en";
    }

    static void save(Context ctx, String code) {
        ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE).edit().putString(PREF, code).apply();
    }

    static Locale locale(String code) {
        switch (code) {
            case "it": return Locale.ITALY;
            case "zh": return Locale.SIMPLIFIED_CHINESE;
            default: return Locale.UK;
        }
    }

    /**
     * {@code base} with the launcher language applied; use from attachBaseContext. Only the locale is
     * overridden, so font scale, density and UI mode still follow the TV. The process default locale is
     * set too, because TextClock names days and months with Locale.getDefault().
     */
    static Context wrap(Context base) {
        String code = effective(base);
        if (code == null) return base;
        Locale l = locale(code);
        Locale.setDefault(l);
        Configuration override = new Configuration();
        override.setLocales(new LocaleList(l));
        return base.createConfigurationContext(override);
    }
}
