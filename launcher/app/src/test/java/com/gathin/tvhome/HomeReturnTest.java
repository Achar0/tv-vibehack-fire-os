package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeReturnTest {
    @Test public void backJustBeforeAmazonHomeIsABackReturn() {
        assertTrue(HomeReturn.isBackReturn(10_000, 10_300));
    }

    @Test public void backAtTheWindowEdgeStillCounts() {
        assertTrue(HomeReturn.isBackReturn(10_000, 10_000 + HomeReturn.BACK_WINDOW_MS));
    }

    @Test public void oldBackIsAHomePress() {
        assertFalse(HomeReturn.isBackReturn(10_000, 10_001 + HomeReturn.BACK_WINDOW_MS));
    }

    @Test public void noBackEverIsAHomePress() {
        assertFalse(HomeReturn.isBackReturn(HomeReturn.NEVER, 10_000));
    }

    @Test public void backAfterTheRedirectDoesNotCount() {
        assertFalse(HomeReturn.isBackReturn(10_500, 10_000));
    }

    @Test public void settingsReopenOnlyAfterBackFromAScreenItOpened() {
        assertTrue(HomeReturn.keepSettings(true, true));
        assertFalse(HomeReturn.keepSettings(false, true));   // Home pressed inside Fire TV settings
        assertFalse(HomeReturn.keepSettings(true, false));   // Back elsewhere, Settings wasn't the opener
    }
}
