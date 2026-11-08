package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remote helpers that need system-wide access. Both are switchable in res/values/config.xml.
 *
 * 1. Media-key shortcut (remap_media_keys_to_stremio): the purple/colour/media buttons open
 *    Stremio. A key is only consumed when Stremio is installed and actually opened, so play/pause
 *    keeps working in other apps when it isn't.
 *
 * 2. Fire TV home override (firetv_home_override): Fire OS won't let another app become the
 *    default home or disable Amazon's launcher, and it handles the Home key before accessibility
 *    services see it. So whenever Amazon's home screen comes to the front, we bring this launcher
 *    back. Only active when Amazon's launcher is installed; other TVs are unaffected.
 */
public class TvAccessibilityService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String STREMIO = "com.stremio.one";

    // Fire OS opens its Settings by briefly showing Amazon's HomeActivity (220-300 ms), then
    // MainSettingsActivity on top. When this launcher opens a settings screen it calls
    // letAmazonHomeThrough(), so that one pass isn't redirected. Every other appearance of Amazon's
    // home is redirected at once, without a window animation, so it barely shows. (Fire OS keeps the
    // Home key itself from accessibility services, so its window appearing is the earliest signal.)
    /** Touched from the launcher's main thread and the service's, which are the same thread. */
    private static final LetThrough letThrough = new LetThrough();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean homeOverride;
    private boolean remapMediaKeys;
    private String frontWindow = "";
    /** When Back was last released (uptime ms), to tell a Back return from a Home press. */
    private long lastBackUp = HomeReturn.NEVER;
    /** When the current Amazon home window appeared. */
    private long amazonHomeAt;

    /** The black cover over Amazon's home while the launcher comes back (log-triggered Home only). */
    private View cover;
    private Thread logWatch;
    private volatile Process logcat;
    private volatile boolean watching;
    private static final long COVER_MAX_MS = 1500;
    private final Runnable hideCover = this::hideCover;

    private final Runnable redirectIfStillAmazonHome = () -> {
        if (isAmazonHome(frontWindow)) goHome();
    };

    /** Called by the launcher right before it opens a system settings screen. */
    static void letAmazonHomeThrough() {
        letThrough.arm(SystemClock.uptimeMillis());
    }

    /** The start that armed letAmazonHomeThrough() failed: redirect Amazon's home again right away. */
    static void cancelAmazonHomeThrough() {
        letThrough.cancel();
    }

    private static TvAccessibilityService running;

    /** The package of the app on screen, or null if the service isn't running. */
    static String frontPackage() {
        return running == null ? null : running.frontWindow.split("/", 2)[0];
    }

    /** Puts the stick to sleep, which turns the television off over HDMI-CEC. */
    static void standby() {
        if (running != null) running.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        WakeSchedule.arm(this);   // this runs after every boot and reinstall, so the alarm is never lost
        remapMediaKeys = getResources().getBoolean(R.bool.remap_media_keys_to_stremio);
        homeOverride = getResources().getBoolean(R.bool.firetv_home_override) && isInstalled(AMAZON_LAUNCHER);
        if (homeOverride && checkSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED) {
            startLogWatch();
        }
    }

    @Override
    public void onDestroy() {
        running = null;
        watching = false;
        Process p = logcat;
        if (p != null) p.destroy();
        hideCover();
        super.onDestroy();
    }

    /**
     * Follows the system log for Amazon's home being started (HomeLog) and reacts at once: black cover, launcher
     * back. The accessibility window event still follows as before, as the fallback. Restarts the reader if it dies.
     */
    private void startLogWatch() {
        if (watching) return;
        watching = true;
        logWatch = new Thread(() -> {
            while (watching) {
                try {
                    logcat = new ProcessBuilder("logcat", "-b", "system", "-v", "brief", "-T", "1",
                            "ActivityTaskManager:I", "*:S").redirectErrorStream(true).start();
                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(logcat.getInputStream()));
                    String line;
                    while (watching && (line = in.readLine()) != null) {
                        if (HomeLog.isAmazonHomeStart(line)) handler.post(this::onAmazonHomeLogged);
                    }
                } catch (Exception ignored) {
                }
                if (watching) SystemClock.sleep(2000);
            }
        }, "home-log");
        logWatch.setDaemon(true);
        logWatch.start();
    }

    private void onAmazonHomeLogged() {
        long now = SystemClock.uptimeMillis();
        if (letThrough.isArmed(now)) return;   // a Fire OS settings screen on its way: the window path decides
        amazonHomeAt = now;
        showCover();
        goHome();
    }

    private void showCover() {
        handler.removeCallbacks(hideCover);
        handler.postDelayed(hideCover, COVER_MAX_MS);
        if (cover != null) return;
        cover = new View(this);
        cover.setBackgroundColor(0xFF000000);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.OPAQUE);
        try {
            ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).addView(cover, lp);
        } catch (Exception e) {
            cover = null;
        }
    }

    private void hideCover() {
        handler.removeCallbacks(hideCover);
        if (cover == null) return;
        try {
            ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (Exception ignored) {
        }
        cover = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!homeOverride || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        // Ignore non-activity windows (toasts, dialogs): they keep the previous front window.
        if (!cls.toString().contains(".")) return;
        String previous = frontWindow;
        frontWindow = pkg + "/" + cls;
        // The launcher is in front again: lift the cover once its first frame is up.
        if (cover != null && frontWindow.startsWith(getPackageName() + "/")) handler.postDelayed(hideCover, 80);
        // An Amazon settings screen has arrived: the let-through pass is used up. (Fire OS first
        // shows an invisible helper from com.amazon.tv.settings.v2, which must not count.)
        if (frontWindow.startsWith(AMAZON_LAUNCHER + "/") && frontWindow.contains("Settings")) letThrough.cancel();
        // A repeated event for the same Amazon home window is the same appearance: keep its pending check.
        if (isAmazonHome(frontWindow) && frontWindow.equals(previous)) return;
        handler.removeCallbacks(redirectIfStillAmazonHome);
        if (isAmazonHome(frontWindow)) {
            amazonHomeAt = SystemClock.uptimeMillis();
            handler.postDelayed(redirectIfStillAmazonHome, letThrough.redirectDelay(amazonHomeAt));
        }
    }

    private static boolean isAmazonHome(String window) {
        return window.startsWith(AMAZON_LAUNCHER + "/") && window.contains("HomeActivity");
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            lastBackUp = event.getEventTime();   // same uptime clock as SystemClock.uptimeMillis()
        }
        if (isTvButton(event.getKeyCode())) {
            // The remote's TV / Live / Guide button: Fire OS sends it to Amazon's Live TV, which is disabled here.
            // Open the IPTV channel picker instead, when one is configured.
            String playlist = Shortcut.channelPlaylist(getResources().getStringArray(R.array.shortcuts));
            if (playlist == null) return super.onKeyEvent(event);
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                Intent i = new Intent(this, ChannelsActivity.class).putExtra(ChannelsActivity.EXTRA_URL, playlist)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception ignored) {}
            }
            return true;
        }
        if (remapMediaKeys && isPurpleOrMediaKey(event.getKeyCode())) {
            // Consume both down and up, but only if Stremio is there to open.
            Intent stremio = stremioIntent();
            if (stremio == null) return super.onKeyEvent(event);
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                try { startActivity(stremio); } catch (Exception ignored) {}
            }
            return true;
        }
        return super.onKeyEvent(event);
    }

    private boolean isTvButton(int code) {
        for (int k : getResources().getIntArray(R.array.tv_button_keys)) if (k == code) return true;
        return false;
    }

    private boolean isPurpleOrMediaKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_DVR:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_BUTTON_1:
            case KeyEvent.KEYCODE_BUTTON_2:
            case KeyEvent.KEYCODE_BUTTON_3:
            case KeyEvent.KEYCODE_BUTTON_4:
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_PROG_GREEN:
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_PROG_BLUE:
            case KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU:
            case KeyEvent.KEYCODE_LAST_CHANNEL:
            case 284:
            case 285:
            case 286:
            case 287:
                return true;
            default:
                return false;
        }
    }

    private Intent stremioIntent() {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(STREMIO);
        if (i == null) i = pm.getLaunchIntentForPackage(STREMIO);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void goHome() {
        if (WakeSchedule.pageWanted()) {   // Amazon's home appeared because the scheduled alarm woke the TV
            try { startActivity(new Intent(this, TvPageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
            return;
        }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        i.putExtra(HomeReturn.EXTRA_VIA_BACK, HomeReturn.isBackReturn(lastBackUp, amazonHomeAt));
        try { startActivity(i); } catch (Exception ignored) {}
    }
}
