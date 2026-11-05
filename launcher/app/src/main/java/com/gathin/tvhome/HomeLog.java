package com.gathin.tvhome;

/**
 * Recognises Amazon's home being started in the system log. Fire OS logs it within milliseconds of the Home key,
 * well before Amazon's home window reaches accessibility services, so the launcher can cover the screen and come
 * back before Amazon's home is ever drawn. Needs READ_LOGS, granted once over ADB.
 */
final class HomeLog {
    private HomeLog() {}

    static boolean isAmazonHomeStart(String line) {
        return line != null && line.contains("START u0") && line.contains("cat=[android.intent.category.HOME]")
                && line.contains("cmp=com.amazon.tv.launcher/");
    }
}
