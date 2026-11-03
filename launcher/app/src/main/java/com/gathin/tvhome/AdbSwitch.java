package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * ADB debugging on/off from the launcher. Writing needs WRITE_SECURE_SETTINGS, which only ADB can grant:
 *   adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
 * Without it the Settings row opens the TV's Developer Options instead.
 */
final class AdbSwitch {
    private AdbSwitch() {}

    static boolean isEnabled(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    static boolean canWrite(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Turns ADB on or off. False when the system refused (no permission). */
    static boolean set(Context ctx, boolean on) {
        try {
            return Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.ADB_ENABLED, on ? 1 : 0);
        } catch (SecurityException e) {
            return false;
        }
    }

    static final String ACTION_DEVELOPMENT = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS";
    static final String ACTION_SETTINGS = "android.settings.SETTINGS";

    /**
     * Where the ADB row goes without the permission, tried in order: Amazon's Developer Options component
     * (config.xml, when set), the standard Developer Options action, then the main settings screen.
     * Each entry is {action, component or null}. Every one carries an android.settings.* action, so
     * MainActivity.open() lets it pass through Amazon's home (Fire OS opens settings that way).
     */
    static List<String[]> developerOptionsTargets(String component) {
        List<String[]> out = new ArrayList<>();
        String c = component == null ? "" : component.trim();
        if (!c.isEmpty()) out.add(new String[]{ACTION_DEVELOPMENT, c});
        out.add(new String[]{ACTION_DEVELOPMENT, null});
        out.add(new String[]{ACTION_SETTINGS, null});
        return out;
    }

    static List<Intent> developerOptionsIntents(Context ctx) {
        List<Intent> out = new ArrayList<>();
        for (String[] t : developerOptionsTargets(ctx.getString(R.string.developer_options_component))) {
            Intent i = new Intent(t[0]);
            ComponentName cn = t[1] == null ? null : ComponentName.unflattenFromString(t[1]);
            if (t[1] != null && cn == null) continue;   // malformed config value: skip it
            if (cn != null) i.setComponent(cn);
            out.add(i);
        }
        return out;
    }
}
