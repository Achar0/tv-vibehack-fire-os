package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** The scheduled moment: wakes the stick (HDMI-CEC then turns the television on) and opens the NAS page. */
public class WakeReceiver extends BroadcastReceiver {

    private static final long WAKE_MS = 10_000L;

    @Override
    @SuppressWarnings("deprecation")   // these flags are the way to wake the screen from a receiver
    public void onReceive(Context context, Intent intent) {
        WakeSchedule.arm(context);
        if (!WakeSchedule.isDueNow(context)) return;   // an alarm set while the clock was wrong
        PowerManager power = context.getSystemService(PowerManager.class);
        if (!WakeSchedule.takesOver(power.isInteractive(), TvAccessibilityService.frontPackage())) return;
        power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tvhome:wake")
                .acquire(WAKE_MS);
        WakeSchedule.markFired();
        context.startActivity(new Intent(context, TvPageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
