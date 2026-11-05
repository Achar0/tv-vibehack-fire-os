package com.gathin.tvhome;

/**
 * The one pass the Home override gives Amazon's home when the launcher opens a Fire OS settings screen
 * (Fire OS opens Settings through Amazon's home). Armed right before the start, it lets Amazon's home
 * appear once; if it is still in front {@link #RECHECK_MS} later it is redirected after all.
 *
 * Screens such as Wi-Fi open without Amazon's home, so the pass used to stay armed for its whole
 * window: Back or Home within it uncovered Amazon's home, which then stayed on screen.
 */
final class LetThrough {
    /** How long an armed pass waits for Amazon's home. */
    static final long WINDOW_MS = 3000;
    /** Amazon's home let through is redirected if nothing replaced it after this long (Settings replaces it in 0.1-0.3 s). */
    static final long RECHECK_MS = 600;

    private long until;

    void arm(long now) { until = now + WINDOW_MS; }

    void cancel() { until = 0; }

    /** True while the pass is armed, without using it. */
    boolean isArmed(long now) { return now < until; }

    /** Amazon's home just appeared at {@code now}: how long to wait before redirecting it. Uses the pass. */
    long redirectDelay(long now) {
        if (now < until) {
            until = 0;
            return RECHECK_MS;
        }
        return 0;
    }
}
