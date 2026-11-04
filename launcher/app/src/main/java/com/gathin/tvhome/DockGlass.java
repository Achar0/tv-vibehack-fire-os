package com.gathin.tvhome;

/** Where and when the glass highlight goes on a dock tile. */
final class DockGlass {
    private DockGlass() {}

    /**
     * The glass is a separate layer, so it only goes on once the dock has stopped at its raised
     * position; otherwise it would sit where the tile will be while the tile is still rising.
     */
    static boolean canShow(boolean dockMoving, float dockTranslationY) {
        return !dockMoving && Math.abs(dockTranslationY) < 1f;
    }

    /**
     * Top of the glass for a tile whose layout rect is centred at {@code tileCenterY}. Layout rects
     * (offsetDescendantRectToMyCoords) never include the dock's translation, so none is subtracted.
     */
    static float top(float tileCenterY, float glassHeight) {
        return tileCenterY - glassHeight / 2f;
    }
}
