package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

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

    /** Developer Options: the component from config.xml when set, else the standard action. */
    static Intent developerOptionsIntent(Context ctx) {
        String c = ctx.getString(R.string.developer_options_component).trim();
        ComponentName cn = c.isEmpty() ? null : ComponentName.unflattenFromString(c);
        if (cn != null) return new Intent().setComponent(cn);
        return new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
    }
}
