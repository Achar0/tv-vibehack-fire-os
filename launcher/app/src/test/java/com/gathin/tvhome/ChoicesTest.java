package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChoicesTest {
    private static final String[] WALL = {"auto", "day", "night"};

    @Test public void nextMovesToFollowingValue() { assertEquals("day", Choices.next(WALL, "auto")); }
    @Test public void nextWrapsAround() { assertEquals("auto", Choices.next(WALL, "night")); }
    @Test public void unknownValueStartsAtFirst() { assertEquals("auto", Choices.next(WALL, "bogus")); }
    @Test public void nullValueStartsAtFirst() { assertEquals("auto", Choices.next(WALL, null)); }
    @Test public void indexOfFindsValue() { assertEquals(2, Choices.indexOf(WALL, "night")); }
    @Test public void indexOfUnknownIsFirst() { assertEquals(0, Choices.indexOf(WALL, "bogus")); }
    @Test public void indexOfNullIsFirst() { assertEquals(0, Choices.indexOf(WALL, null)); }
}
