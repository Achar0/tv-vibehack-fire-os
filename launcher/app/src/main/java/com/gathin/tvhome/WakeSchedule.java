package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.util.Calendar;

/** When the TV turns itself on and opens the NAS page. Set in config.xml (wake_hour, wake_minute, wake_days). */
final class WakeSchedule {

    private static final int LATE_MINUTES = 10;
    /** Waking up brings Amazon's home forward; for this long the launcher must not cover the page in reaction. */
    private static final long PAGE_WANTED_MS = 15_000L;
    static final long NEVER = Long.MIN_VALUE;
    private static volatile long firedAt = NEVER;

    private final int hour;
    private final int minute;
    private final int[] days;

    private WakeSchedule(int hour, int minute, int[] days) {
        this.hour = hour;
        this.minute = minute;
        this.days = days;
    }

    /** A schedule, or null when none is configured. {@code days} are Calendar.DAY_OF_WEEK values. */
    static WakeSchedule of(int hour, int minute, int[] days) {
        return hour < 0 || days.length == 0 ? null : new WakeSchedule(hour, minute, days);
    }

    private static WakeSchedule from(Context context) {
        return of(context.getResources().getInteger(R.integer.wake_hour),
                context.getResources().getInteger(R.integer.wake_minute),
                context.getResources().getIntArray(R.array.wake_days));
    }

    private boolean isDay(Calendar c) {
        for (int day : days) if (c.get(Calendar.DAY_OF_WEEK) == day) return true;
        return false;
    }

    /** The first scheduled moment strictly after {@code now}. */
    Calendar next(Calendar now) {
        Calendar at = (Calendar) now.clone();
        at.set(Calendar.HOUR_OF_DAY, hour);
        at.set(Calendar.MINUTE, minute);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        while (!at.after(now) || !isDay(at)) at.add(Calendar.DAY_OF_MONTH, 1);
        return at;
    }

    /**
     * Whether a firing alarm is really on schedule. The clock starts wrong after a reboot, so an alarm
     * set then can fire as soon as the time syncs, at any hour; a real one fires on time, or a little late.
     */
    boolean isDueNow(Calendar now) {
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) - (hour * 60 + minute);
        return isDay(now) && minutes >= 0 && minutes < LATE_MINUTES;
    }

    /** Whether this alarm should go ahead. */
    static boolean isDueNow(Context context) {
        WakeSchedule schedule = from(context);
        return schedule != null && schedule.isDueNow(Calendar.getInstance());
    }

    /** Sets the one alarm for the next scheduled moment, replacing any earlier one. */
    static void arm(Context context) {
        WakeSchedule schedule = from(context);
        if (schedule == null) return;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent fire = PendingIntent.getBroadcast(context, 0, new Intent(context, WakeReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, schedule.next(Calendar.getInstance()).getTimeInMillis(), fire);
    }

    /** The page may open unless someone is using another app on the stick (a film must not be interrupted). */
    static boolean takesOver(boolean awake, String frontPackage) {
        return !awake || frontPackage == null || frontPackage.isEmpty()
                || frontPackage.equals("com.gathin.tvhome") || frontPackage.equals("com.amazon.tv.launcher");
    }

    static void markFired() {
        firedAt = SystemClock.elapsedRealtime();
    }

    static boolean pageWanted() {
        return pageWanted(firedAt, SystemClock.elapsedRealtime());
    }

    static boolean pageWanted(long firedAt, long now) {
        return firedAt != NEVER && now - firedAt < PAGE_WANTED_MS;
    }
}
