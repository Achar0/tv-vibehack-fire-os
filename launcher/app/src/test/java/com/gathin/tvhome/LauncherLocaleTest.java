package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LauncherLocaleTest {
    @Test public void italianTvPicksItalian() { assertEquals("it", LauncherLocale.defaultFor("it", "IT", "")); }
    @Test public void englishTvPicksEnglish() { assertEquals("en", LauncherLocale.defaultFor("en", "US", "")); }
    @Test public void simplifiedChinesePicksChinese() { assertEquals("zh", LauncherLocale.defaultFor("zh", "CN", "")); }
    @Test public void hansScriptPicksChinese() { assertEquals("zh", LauncherLocale.defaultFor("zh", "", "Hans")); }
    /** Traditional Chinese readers are not handed Simplified by default. */
    @Test public void taiwanIsNotSimplified() { assertNull(LauncherLocale.defaultFor("zh", "TW", "")); }
    @Test public void hongKongIsNotSimplified() { assertNull(LauncherLocale.defaultFor("zh", "HK", "")); }
    @Test public void hantScriptIsNotSimplified() { assertNull(LauncherLocale.defaultFor("zh", "", "Hant")); }
    /** Other languages (German ships its own strings) keep the TV's language: null means "don't override". */
    @Test public void germanTvIsLeftAlone() { assertNull(LauncherLocale.defaultFor("de", "DE", "")); }
}
