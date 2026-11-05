package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ShortcutTest {
    @Test public void webPageInABrowser() {
        Shortcut s = Shortcut.parse("News|https://example.org/live|com.phlox.tvwebbrowser");
        assertEquals("News", s.label);
        assertEquals("https://example.org/live", s.uri);
        assertEquals("com.phlox.tvwebbrowser", s.pkg);
        assertNull(s.mime);
        assertEquals("shortcut.news", s.id);
    }

    @Test public void playlistWithAType() {
        Shortcut s = Shortcut.parse(" TV Cina | https://x.org/list.m3u8 | org.videolan.vlc | audio/x-mpegurl ");
        assertEquals("TV Cina", s.label);
        assertEquals("audio/x-mpegurl", s.mime);
        assertEquals("shortcut.tv-cina", s.id);
    }

    @Test public void urlsMayContainPipesAfterTheThirdFieldOnlyAsType() {
        assertNull(Shortcut.parse("Live|https://a.org/x"));          // no app
        assertNull(Shortcut.parse("|https://a.org/x|com.a"));       // no label
        assertNull(Shortcut.parse("Live||com.a"));                    // no address
        assertNull(Shortcut.parse("Live|not a uri|com.a"));           // no scheme
        assertNull(Shortcut.parse(null));
    }

    @Test public void bannerDrawableIsNamedAfterTheShortcut() {
        assertEquals("shortcut_news", Shortcut.parse("News|https://a.org|com.a").bannerName());
        assertEquals("shortcut_tv_cina", Shortcut.parse("TV Cina|https://a.org|com.a").bannerName());
    }

    @Test public void channelsMeansTheBuiltInPicker() {
        assertEquals(true, Shortcut.parse("IPTV|https://a.org/l.m3u8|channels").opensChannelPicker());
        assertEquals(false, Shortcut.parse("Live|https://a.org|com.phlox.tvwebbrowser").opensChannelPicker());
    }

    @Test public void theTvButtonUsesTheFirstChannelPickerShortcut() {
        String[] lines = {"Live|https://a.org|com.phlox.tvwebbrowser", "IPTV|https://b.org/l.m3u8|channels",
                "More|https://c.org/m.m3u8|channels"};
        assertEquals("https://b.org/l.m3u8", Shortcut.channelPlaylist(lines));
        assertNull(Shortcut.channelPlaylist(new String[]{"Live|https://a.org|com.a"}));
    }
}
