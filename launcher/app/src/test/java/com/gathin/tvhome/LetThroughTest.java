package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LetThroughTest {
    @Test public void unarmedAmazonHomeIsRedirectedAtOnce() {
        assertEquals(0, new LetThrough().redirectDelay(10_000));
    }

    @Test public void armedPassLetsAmazonHomeShowBrieflyThenRechecks() {
        LetThrough lt = new LetThrough();
        lt.arm(10_000);
        assertEquals(LetThrough.RECHECK_MS, lt.redirectDelay(10_300));
    }

    @Test public void thePassIsUsedOnceSoASecondAppearanceIsRedirected() {
        // Wi-Fi opens without Amazon's home; Back 1 s later uncovers it: it must not stay on screen.
        LetThrough lt = new LetThrough();
        lt.arm(10_000);
        lt.redirectDelay(10_200);
        assertEquals(0, lt.redirectDelay(11_000));
    }

    @Test public void anExpiredPassRedirectsAtOnce() {
        LetThrough lt = new LetThrough();
        lt.arm(10_000);
        assertEquals(0, lt.redirectDelay(10_000 + LetThrough.WINDOW_MS + 1));
    }

    @Test public void aCancelledPassRedirectsAtOnce() {
        LetThrough lt = new LetThrough();
        lt.arm(10_000);
        lt.cancel();
        assertEquals(0, lt.redirectDelay(10_100));
    }
}
