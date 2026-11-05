package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneAppsTest {
    @Test public void aPhoneOnlyAppYouInstalledShows() {
        assertTrue(AppAdapter.showPhoneApp(false, false, false));
    }

    @Test public void systemPhoneAppsStayHidden() {
        // com.amazon.venezia, com.amazon.ssm...: phone LAUNCHER entries of Fire OS itself.
        assertFalse(AppAdapter.showPhoneApp(true, false, false));
    }

    @Test public void anAppAlreadyShownAsTvAppIsNotRepeated() {
        assertFalse(AppAdapter.showPhoneApp(false, true, false));
    }

    @Test public void updatedSystemAppsStayHiddenToo() {
        assertFalse(AppAdapter.showPhoneApp(false, false, true));
    }
}
