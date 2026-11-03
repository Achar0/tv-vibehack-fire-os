package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import java.util.Locale;

/**
 * The launcher's own display language (Settings > Appearance > Language), independent of the TV's:
 * English, Italian or Simplified Chinese. Applied by wrapping the activity's and screensaver's context.
 * Until chosen, it follows the TV's language when that is one of the three, else English.
 */
final class LauncherLocale {
    private LauncherLocale() {}

    static final String[] CODES = {"en", "it", "zh"};
    private static final String PREF = "launcher_language";

    static String current(Context ctx) {
        String saved = ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE).getString(PREF, null);
        if (saved != null) return saved;
        String system = Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        for (String c : CODES) if (c.equals(system)) return c;
        return "en";
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

    /** {@code base} with the launcher language applied; use from attachBaseContext. */
    static Context wrap(Context base) {
        Locale l = locale(current(base));
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocales(new LocaleList(l));
        return base.createConfigurationContext(cfg);
    }
}
