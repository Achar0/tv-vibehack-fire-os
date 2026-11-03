package com.gathin.tvhome;

/** Cycling through a setting's values with OK: the value after the current one, wrapping round. */
final class Choices {
    private Choices() {}

    /** The value after {@code current} in {@code order}; an unknown or null value starts at the first. */
    static String next(String[] order, String current) {
        int i = indexOf(order, current);
        return order[i == 0 && !order[0].equals(current) ? 0 : (i + 1) % order.length];
    }

    /** Position of {@code value} in {@code order}; an unknown or null value counts as the first. */
    static int indexOf(String[] order, String value) {
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(value)) return i;
        }
        return 0;
    }
}
