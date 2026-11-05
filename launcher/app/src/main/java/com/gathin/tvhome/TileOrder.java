package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The tile order you set with Move (hold OK on a tile). The first row is the dock. Saved as a list of ids. */
final class TileOrder {
    private TileOrder() {}

    /** {@code defaults} reordered by {@code saved}; ids not saved yet (new apps) keep their default order at the end. */
    static List<String> apply(List<String> defaults, List<String> saved) {
        Set<String> present = new LinkedHashSet<>(defaults);
        List<String> out = new ArrayList<>();
        for (String id : saved) if (present.remove(id)) out.add(id);
        out.addAll(present);
        return out;
    }

    /** A copy of {@code order} with the item at {@code from} moved to {@code to}. */
    static List<String> move(List<String> order, int from, int to) {
        List<String> out = new ArrayList<>(order);
        if (from < 0 || to < 0 || from >= out.size() || to >= out.size()) return out;
        out.add(to, out.remove(from));
        return out;
    }

    /** Where an arrow (dx, dy) takes the tile at {@code pos} in a grid of {@code count} tiles, kept inside it. */
    static int target(int pos, int columns, int count, int dx, int dy) {
        int t = pos + dx + dy * columns;
        if (dx != 0 && t / columns != pos / columns) return pos;   // no wrapping to the next row
        return Math.max(0, Math.min(count - 1, t));
    }

    static String encode(List<String> ids) {
        return String.join("\n", ids);
    }

    static List<String> decode(String saved) {
        if (saved == null || saved.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(Arrays.asList(saved.split("\n")));
    }
}
