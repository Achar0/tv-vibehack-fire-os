package com.gathin.tvhome;

/**
 * Tells "came back with Back" apart from "pressed Home" when Amazon's home bounces us back.
 *
 * Fire OS keeps the Home key from accessibility services, but Back reaches them. Leaving a Fire OS
 * screen with Back uncovers Amazon's home just like Home does, so a Back released shortly before
 * Amazon's home appears means "go back": Settings reopens if it opened that screen.
 */
final class HomeReturn {
    private HomeReturn() {}

    /** Back released this recently (ms) before Amazon's home appears counts as a Back return. */
    static final long BACK_WINDOW_MS = 1000;
    static final long NEVER = Long.MIN_VALUE;
    /** Extra on the redirect intent: true when it followed a Back press. */
    static final String EXTRA_VIA_BACK = "com.gathin.tvhome.VIA_BACK";

    static boolean isBackReturn(long backUpAt, long amazonHomeAt) {
        if (backUpAt == NEVER || backUpAt > amazonHomeAt) return false;
        return amazonHomeAt - backUpAt <= BACK_WINDOW_MS;
    }

    /** Keep the Settings panel open on return only after Back from a screen it opened itself. */
    static boolean keepSettings(boolean viaBack, boolean settingsOpenedIt) {
        return viaBack && settingsOpenedIt;
    }
}
