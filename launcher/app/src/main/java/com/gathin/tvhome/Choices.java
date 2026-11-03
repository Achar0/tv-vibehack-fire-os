package com.gathin.tvhome;

/** Cycling through a setting's values with OK: the value after the current one, wrapping round. */
final class Choices {
    private Choices() {}

    /** The value after {@code current} in {@code order}; an unknown or null value starts at the first. */
    static String next(String[] order, String current) {
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(current)) return order[(i + 1) % order.length];
        }
        return order[0];
    }
}
