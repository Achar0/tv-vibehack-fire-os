package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeLogTest {
    // Captured on the AFTKM (Fire OS 8.1.8.2) right after pressing Home in Prime Video.
    private static final String HOME_START = "I/ActivityTaskManager(  668): START u0 {act=android.intent.action.MAIN "
            + "cat=[android.intent.category.HOME] flg=0x10000100 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext "
            + "(has extras)} from uid 0";

    @Test public void amazonHomeStartedByHomeIsSeen() {
        assertTrue(HomeLog.isAmazonHomeStart(HOME_START));
    }

    @Test public void ourOwnStartIsNot() {
        assertFalse(HomeLog.isAmazonHomeStart("I/ActivityTaskManager(  668): START u0 {flg=0x14200000 "
                + "cmp=com.gathin.tvhome/.MainActivity} from uid 10100"));
    }

    @Test public void amazonSettingsAndOtherAppsAreNot() {
        assertFalse(HomeLog.isAmazonHomeStart("I/ActivityTaskManager(  668): START u0 {cmp=com.amazon.tv.launcher/"
                + ".ui.MainSettingsActivity} from uid 10100"));
        assertFalse(HomeLog.isAmazonHomeStart("I/ActivityTaskManager(  668): START u0 {act=android.intent.action.VIEW "
                + "cmp=com.phlox.tvwebbrowser/.activity.main.MainActivity} from uid 10100"));
        assertFalse(HomeLog.isAmazonHomeStart(null));
    }
}
