package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class AdbSwitchTest {
    @Test public void withoutComponentTriesDeveloperOptionsThenSettings() {
        List<String[]> t = AdbSwitch.developerOptionsTargets("");
        assertEquals(2, t.size());
        assertEquals(AdbSwitch.ACTION_DEVELOPMENT, t.get(0)[0]);
        assertNull(t.get(0)[1]);
        assertEquals(AdbSwitch.ACTION_SETTINGS, t.get(1)[0]);
    }

    @Test public void componentComesFirstThenFallbacks() {
        List<String[]> t = AdbSwitch.developerOptionsTargets(" com.amazon.tv.settings.v2/.Dev ");
        assertEquals(3, t.size());
        assertEquals("com.amazon.tv.settings.v2/.Dev", t.get(0)[1]);
        assertEquals(AdbSwitch.ACTION_DEVELOPMENT, t.get(1)[0]);
        assertEquals(AdbSwitch.ACTION_SETTINGS, t.get(2)[0]);
    }

    /** The Home override only lets Amazon's home through for android.settings.* actions. */
    @Test public void everyTargetCarriesASettingsAction() {
        for (String[] target : AdbSwitch.developerOptionsTargets("a.b/.C")) {
            assertTrue(target[0].startsWith("android.settings."));
        }
    }
}
