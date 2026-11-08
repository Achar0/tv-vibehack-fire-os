package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * The NAS page, opened by the schedule. Any remote key leaves it and cancels the automatic standby; if nobody
 * touches the remote, the TV goes back to standby. While the NAS or the Wi-Fi isn't reachable (the
 * stick has only just woken up) it shows the clock and keeps retrying.
 */
public class TvPageActivity extends Activity {

    private static final long STANDBY_AFTER_MS = 90 * 60_000L;
    private static final long LOAD_TIMEOUT_MS = 10_000L;
    private static final long RETRY_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable standby = () -> {
        finish();
        TvAccessibilityService.standby();
    };
    private final Runnable load = this::load;
    private final Runnable giveUp = this::failed;
    private WebView web;
    private boolean loadFailed;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTurnScreenOn(true);

        setContentView(R.layout.dream_clock);   // the clock, visible whenever the page isn't
        View clock = findViewById(R.id.dreamContent);
        FrameLayout.LayoutParams centred = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        clock.setLayoutParams(centred);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(false);
        web.setVisibility(View.INVISIBLE);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (loadFailed) return;
                handler.removeCallbacks(giveUp);
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) failed();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) failed();
            }
        });
        addContentView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        load();
        handler.postDelayed(standby, STANDBY_AFTER_MS);
    }

    private void load() {
        loadFailed = false;
        web.loadUrl(ClockDream.TV_URL);
        handler.postDelayed(giveUp, LOAD_TIMEOUT_MS);
    }

    /** Back to the clock, and another try in a while; the standby timer keeps running. */
    private void failed() {
        loadFailed = true;
        handler.removeCallbacks(giveUp);
        handler.removeCallbacks(load);
        web.stopLoading();
        web.setVisibility(View.INVISIBLE);
        handler.postDelayed(load, RETRY_MS);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        finish();
        return true;
    }

    /** Fire OS's Home key never reaches apps, so leaving by Home is seen here. */
    @Override
    protected void onStop() {
        super.onStop();
        if (!WakeSchedule.pageWanted()) finish();   // waking up covers it for a moment: not a user leaving
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }
}
