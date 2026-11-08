package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

/** A made-up schedule: Tuesday, Thursday and Saturday at 18:30. */
public class WakeScheduleTest {

    private static final WakeSchedule S = WakeSchedule.of(18, 30,
            new int[]{Calendar.TUESDAY, Calendar.THURSDAY, Calendar.SATURDAY});

    /** "Tue 12:00" in the week of Monday 2 November 2026. */
    private static Calendar when(String text) {
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String[] parts = text.split("[ :]");
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 2 + Arrays.asList(days).indexOf(parts[0]),
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        return c;
    }

    private static String next(String text) {
        return new SimpleDateFormat("EEE HH:mm", Locale.ENGLISH).format(S.next(when(text)).getTime());
    }

    @Test public void noHourMeansNoSchedule()     { assertNull(WakeSchedule.of(-1, 0, new int[]{Calendar.TUESDAY})); }
    @Test public void noDaysMeansNoSchedule()     { assertNull(WakeSchedule.of(18, 30, new int[0])); }

    @Test public void beforeTheTimeIsToday()      { assertEquals("Tue 18:30", next("Tue 12:00")); }
    @Test public void exactlyAtTheTimeMovesOn()   { assertEquals("Thu 18:30", next("Tue 18:30")); }
    @Test public void skipsDaysNotListed()        { assertEquals("Tue 18:30", next("Sun 09:00")); }
    @Test public void wrapsToNextWeek()           { assertEquals("Tue 18:30", next("Sat 20:00")); }

    // After a reboot the clock starts wrong, so an alarm can fire at any time once it syncs
    @Test public void dueAtTheTime()              { assertTrue(S.isDueNow(when("Tue 18:30"))); }
    @Test public void aLateAlarmStillCounts()     { assertTrue(S.isDueNow(when("Sat 18:39"))); }
    @Test public void tooLateIsNotDue()           { assertFalse(S.isDueNow(when("Tue 18:40"))); }
    @Test public void tooEarlyIsNotDue()          { assertFalse(S.isDueNow(when("Tue 18:29"))); }
    @Test public void afterRebootAtNightIsNotDue() { assertFalse(S.isDueNow(when("Tue 03:00"))); }
    @Test public void anUnlistedDayIsNotDue()     { assertFalse(S.isDueNow(when("Wed 18:30"))); }

    @Test public void asleepTakesOver()           { assertTrue(WakeSchedule.takesOver(false, "org.videolan.vlc")); }
    @Test public void launcherTakesOver()         { assertTrue(WakeSchedule.takesOver(true, "com.gathin.tvhome")); }
    @Test public void amazonHomeTakesOver()       { assertTrue(WakeSchedule.takesOver(true, "com.amazon.tv.launcher")); }
    @Test public void unknownWindowTakesOver()    { assertTrue(WakeSchedule.takesOver(true, null)); }
    @Test public void aFilmIsNeverInterrupted()   { assertFalse(WakeSchedule.takesOver(true, "org.videolan.vlc")); }

    @Test public void pageIsWantedRightAfterTheAlarm() { assertTrue(WakeSchedule.pageWanted(1_000_000L, 1_014_999L)); }
    @Test public void pageIsNotWantedOnceSettled()     { assertFalse(WakeSchedule.pageWanted(1_000_000L, 1_015_000L)); }
    @Test public void pageIsNotWantedBeforeAnyAlarm()  { assertFalse(WakeSchedule.pageWanted(WakeSchedule.NEVER, 1_000_000L)); }
}
