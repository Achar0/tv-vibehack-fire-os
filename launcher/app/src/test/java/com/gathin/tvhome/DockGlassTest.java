package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockGlassTest {
    @Test public void glassWaitsWhileTheDockIsStillMoving() {
        assertFalse(DockGlass.canShow(true, 0f));
    }

    @Test public void glassWaitsWhileTheDockIsStillLowered() {
        assertFalse(DockGlass.canShow(false, 480f));
    }

    @Test public void glassShowsOnceTheDockHasSettled() {
        assertTrue(DockGlass.canShow(false, 0f));
        assertTrue(DockGlass.canShow(false, 0.4f));
    }

    @Test public void glassTopIgnoresTheDockOffset() {
        // Layout rect (no translation in it) centred at y=600, glass 200 tall: top is 500 whatever the dock offset.
        assertEquals(500f, DockGlass.top(600f, 200f), 0.001f);
    }

    @Test public void dockAlreadyThereNeedsNoMove() {
        assertFalse(DockGlass.dockNeedsMove(true, false, 440f, 440f));
    }

    @Test public void newPeekDepthMovesALoweredDock() {
        // Settings > Dock changed while the dock is lowered: same state, new target.
        assertTrue(DockGlass.dockNeedsMove(true, false, 440f, 280f));
    }

    @Test public void stateChangeOrRunningSpringAlwaysMoves() {
        assertTrue(DockGlass.dockNeedsMove(false, false, 0f, 0f));
        assertTrue(DockGlass.dockNeedsMove(true, true, 440f, 440f));
    }
}
