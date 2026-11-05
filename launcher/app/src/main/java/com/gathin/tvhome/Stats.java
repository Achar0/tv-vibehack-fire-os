package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.util.Locale;

/** The optional line under the clock (Settings > Appearance > Stats): free RAM and free storage. */
final class Stats {
    private Stats() {}

    static String human(long bytes) {
        long mb = Math.max(0, bytes) / (1024 * 1024);
        if (mb < 1024) return mb + " MB";
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
    }

    static String line(Context ctx) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        long storage = new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
        return ctx.getString(R.string.stats_line, human(mi.availMem), human(storage));
    }
}
