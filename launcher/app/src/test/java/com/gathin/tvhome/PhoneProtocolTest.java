package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

public class PhoneProtocolTest {
    @Test public void keyboardHiddenWhilePageIsSeen()  { assertTrue(PhoneProtocol.pageOpen(1_000L, 45_999L)); }
    @Test public void keyboardReturnsAfterThePageGoes() { assertFalse(PhoneProtocol.pageOpen(1_000L, 46_000L)); }
    @Test public void noPageSeenYetIsNotOpen()         { assertFalse(PhoneProtocol.pageOpen(0L, 1_000L)); }

    @Test public void rightTokenPasses()    { assertTrue(PhoneProtocol.tokenOk("/api/tiles?t=abc", "abc")); }
    @Test public void tokenAmongOtherParams() { assertTrue(PhoneProtocol.tokenOk("/x?a=1&t=abc", "abc")); }
    @Test public void wrongTokenFails()      { assertFalse(PhoneProtocol.tokenOk("/api/tiles?t=abd", "abc")); }
    @Test public void missingTokenFails()    { assertFalse(PhoneProtocol.tokenOk("/api/tiles", "abc")); }
    @Test public void prefixOfTokenFails()   { assertFalse(PhoneProtocol.tokenOk("/?t=ab", "abc")); }

    @Test public void pathDropsTheQuery()    { assertEquals("/api/tiles", PhoneProtocol.path("/api/tiles?t=abc")); }

    @Test public void formDecodesValues() {
        Map<String, String> f = PhoneProtocol.form("text=%E4%BD%A0%E5%A5%BD+a&enter=1");
        assertEquals("你好 a", f.get("text"));
        assertEquals("1", f.get("enter"));
    }

    @Test public void formToleratesEmptyAndBadInput() {
        assertTrue(PhoneProtocol.form("").isEmpty());
        assertEquals("", PhoneProtocol.form("a=").get("a"));
        assertTrue(PhoneProtocol.form("=&%zz=1").size() <= 1);
    }

    @Test public void jsonEscapesTheAwkwardCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", PhoneProtocol.jsonString("a\"b\\c\nd"));
    }

    @Test public void tileJsonKeepsOrderAndHidden() {
        assertEquals("[{\"id\":\"a\",\"label\":\"A\",\"hidden\":false},{\"id\":\"b\",\"label\":\"B\",\"hidden\":true}]",
                PhoneProtocol.tilesJson(Arrays.asList(new PhoneProtocol.Tile("a", "A", false),
                        new PhoneProtocol.Tile("b", "B", true))));
    }

    @Test public void namesRoundTrip() {
        Map<String, String> m = PhoneProtocol.decodeNames(new HashSet<>(Arrays.asList("a\tFilm", "b\tNews")));
        assertEquals("Film", m.get("a"));
        assertEquals("News", m.get("b"));
    }

    @Test public void settingANameReplacesTheOldOne() {
        assertEquals(new HashSet<>(Arrays.asList("a\tNew", "b\tNews")),
                PhoneProtocol.withName(new HashSet<>(Arrays.asList("a\tOld", "b\tNews")), "a", "New"));
    }

    @Test public void anEmptyNameRemovesIt() {
        assertEquals(new HashSet<>(Arrays.asList("b\tNews")),
                PhoneProtocol.withName(new HashSet<>(Arrays.asList("a\tOld", "b\tNews")), "a", "  "));
    }

    @Test public void aNameCannotBreakTheStoredFormat() {
        assertEquals("x y", PhoneProtocol.decodeNames(PhoneProtocol.withName(new HashSet<>(), "a", "x\ty")).get("a"));
    }
}
