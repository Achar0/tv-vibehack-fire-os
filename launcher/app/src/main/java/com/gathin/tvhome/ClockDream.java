package com.gathin.tvhome;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Random;

/**
 * Screensaver: the NAS's /tv page, full-screen. Until it has loaded, and whenever the NAS can't be
 * reached, the launcher's thin clock and date on black, over a slow ambient glow. Every minute the
 * clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 */
public class ClockDream extends DreamService {

    /** The idle page. Plain HTTP to this address only is allowed in network_security_config.xml. */
    static final String TV_URL = "http://192.168.1.14:3001/tv";
    private static final long LOAD_TIMEOUT_MS = 10_000L;
    private static final long MOVE_MS = 60_000L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private View ambient;
    private GradientDrawable glow;
    private int colorIndex;
    private WebView web;

    private final Runnable giveUpOnPage = this::destroyWeb;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** The launcher's language (Settings > Appearance > Language) for the date line too. */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.postDelayed(tick, MOVE_MS);
        loadPage();
    }

    /** Loads the page behind the clock; it only appears once it has finished loading. */
    private void loadPage() {
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(false);
        web.setVisibility(View.INVISIBLE);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (view != web) return;   // already given up
                handler.removeCallbacks(giveUpOnPage);
                handler.removeCallbacks(tick);   // the clock is covered: stop moving it
                setScreenBright(true);
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) destroyWeb();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) destroyWeb();
            }
        });
        ((ViewGroup) content.getParent()).addView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(TV_URL);
        handler.postDelayed(giveUpOnPage, LOAD_TIMEOUT_MS);
    }

    /** Drops the page, leaving the clock underneath. */
    private void destroyWeb() {
        handler.removeCallbacks(giveUpOnPage);
        if (web == null) return;
        WebView old = web;
        web = null;
        ((ViewGroup) old.getParent()).removeView(old);
        old.destroy();
    }

    @Override
    public void onDreamingStopped() {
        destroyWeb();
        handler.removeCallbacks(tick);
        if (content != null) content.animate().cancel();
        if (ambient != null) ambient.animate().cancel();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (content != null) content.animate().cancel();
        if (ambient != null) ambient.animate().cancel();
        super.onDetachedFromWindow();
    }

    /** Once a minute: fade clock and glow out, move them and change the colour, fade back in.
     *  Only alpha is animated (composited by the GPU); the gradient is redrawn once, while invisible. */
    private void moveClock() {
        ambient.animate().alpha(0f).setDuration(900).start();
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            colorIndex = (colorIndex + 1) % PALETTE.length;
            setGlow(PALETTE[colorIndex]);
            placeClock();
            ambient.animate().alpha(1f).setDuration(1200).start();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void setGlow(int color) {
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
