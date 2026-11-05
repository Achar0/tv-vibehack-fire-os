package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppListTest {
    @Test public void emptyListLoads() { assertTrue(AppAdapter.needsReload(0, false)); }

    @Test public void installOrUninstallReloads() { assertTrue(AppAdapter.needsReload(9, true)); }

    @Test public void unchangedListIsKept() { assertFalse(AppAdapter.needsReload(9, false)); }
}
