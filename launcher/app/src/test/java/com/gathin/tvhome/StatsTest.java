package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StatsTest {
    @Test public void megabytesBelowAGigabyte() {
        assertEquals("412 MB", Stats.human(412L * 1024 * 1024));
    }

    @Test public void gigabytesWithOneDecimal() {
        assertEquals("3.1 GB", Stats.human((long) (3.1 * 1024 * 1024 * 1024)));
    }

    @Test public void zeroAndNegativeAreZero() {
        assertEquals("0 MB", Stats.human(0));
        assertEquals("0 MB", Stats.human(-5));
    }
}
