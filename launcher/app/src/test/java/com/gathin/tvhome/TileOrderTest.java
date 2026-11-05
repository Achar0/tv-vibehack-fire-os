package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class TileOrderTest {
    private static final List<String> DEFAULT = Arrays.asList("yt", "nas", "bro", "vlc", "prime");

    @Test public void noSavedOrderKeepsTheDefault() {
        assertEquals(DEFAULT, TileOrder.apply(DEFAULT, Collections.emptyList()));
    }

    @Test public void savedOrderWinsAndNewAppsGoAtTheEnd() {
        List<String> saved = Arrays.asList("vlc", "yt", "gone-app", "nas");
        assertEquals(Arrays.asList("vlc", "yt", "nas", "bro", "prime"), TileOrder.apply(DEFAULT, saved));
    }

    @Test public void moveShiftsTheOthers() {
        assertEquals(Arrays.asList("nas", "bro", "yt", "vlc", "prime"), TileOrder.move(DEFAULT, 0, 2));
        assertEquals(Arrays.asList("vlc", "yt", "nas", "bro", "prime"), TileOrder.move(DEFAULT, 3, 0));
    }

    @Test public void moveOutOfRangeChangesNothing() {
        assertEquals(DEFAULT, TileOrder.move(DEFAULT, 0, 5));
        assertEquals(DEFAULT, TileOrder.move(DEFAULT, -1, 2));
    }

    @Test public void targetOfAnArrowStaysInsideTheGrid() {
        // 5 tiles, 4 per row: from 1, Down goes to 4 (last tile); from 4, Right stays.
        assertEquals(4, TileOrder.target(1, 4, 5, 0, 1));
        assertEquals(4, TileOrder.target(4, 4, 5, 1, 0));
        assertEquals(0, TileOrder.target(0, 4, 5, -1, 0));
        assertEquals(4, TileOrder.target(4, 4, 5, -1, 0));   // first of its row: Left does not wrap up
        assertEquals(0, TileOrder.target(4, 4, 5, 0, -1));
    }

    @Test public void savedListRoundTrips() {
        assertEquals(DEFAULT, TileOrder.decode(TileOrder.encode(DEFAULT)));
        assertEquals(Collections.emptyList(), TileOrder.decode(null));
    }
}
