package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.FocusFinder;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener, SettingsPanel.Host {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    /** Focus scale of the top-bar pills. */
    private static final float PILL_SCALE = 1.06f;
    /** Set by setLanguage(): after recreate(), open Settings again on the Language row. */
    private static final String REOPEN_LANGUAGE = "reopen_settings_language";
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnWifi;
    private ImageView wallpaper;
    private int currentWallpaperRes = 0;
    private boolean isDockPeeking = false;
    /** The dock's vertical position; a spring, so a reversed Up/Down continues from where it is. */
    private SpringAnimation dockSpring;
    private float peekOffset;
    private String peekDepthPref = "ultra";
    private boolean isTopBarShowText = true;
    private SettingsPanel settingsPanel;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            updateWallpaper();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        peekDepthPref = prefs.getString("dock_peek_depth", "ultra");
        updatePeekOffset();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        wallpaper = findViewById(R.id.wallpaper);
        updateWallpaper();

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        // tvOS layout: 2-row dock sits at the bottom, giving maximum space for the wallpaper above.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int want = Math.max(0, h - (int) ((225 + 24) * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: smooth, stable, non-bouncy springs on highlight position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(600f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        }

        dockSpring = new SpringAnimation(grid, DynamicAnimation.TRANSLATION_Y);
        dockSpring.setSpring(new SpringForce().setStiffness(Motion.DOCK).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        dockSpring.addEndListener((anim, canceled, value, velocity) -> {
            if (canceled || isDockPeeking) return;
            // Dock fully up: put the glass on the focused tile now that it has stopped moving.
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(lastFocused);
            View card = vh != null ? vh.itemView.findViewById(R.id.card) : null;
            if (card != null && card.isFocused()) onTileFocused(lastFocused, card);
        });

        View btnSettings = findViewById(R.id.btnSettings);
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        settingsPanel = new SettingsPanel(this, findViewById(R.id.settingsStub), this);
        btnSettings.setOnClickListener(v -> settingsPanel.show());
        if (prefs.getBoolean(REOPEN_LANGUAGE, false)) {
            prefs.edit().remove(REOPEN_LANGUAGE).apply();
            // Posted twice so it runs after onResume's first-launch focus (also posted), not before it.
            grid.post(() -> grid.post(() -> settingsPanel.show(1, R.id.rowLanguage)));
        }
        isTopBarShowText = prefs.getBoolean("top_bar_show_text", true);
        applyTopBarStyle(isTopBarShowText);

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnFocusChangeListener pillFocus = (v, has) -> Motion.focus(v, has, PILL_SCALE, 0f, false);
        btnWifi.setOnFocusChangeListener(pillFocus);
        btnSettings.setOnFocusChangeListener(pillFocus);
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (Motion.isSelect(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) Motion.press(v, true, PILL_SCALE);
                else if (event.getAction() == KeyEvent.ACTION_UP) Motion.press(v, false, PILL_SCALE);
                return false;
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnWifi) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setDockPeek(false, true);
                int target = (lastFocused >= 0 && lastFocused < COLUMNS) ? lastFocused : 0;
                focusTile(target);
                return true;
            }
            return false;
        };
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass/hero and peek the dock when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) {
                hideGlass();
                hideHero();
                setDockPeek(true, true);
            } else {
                setDockPeek(false, true);
            }
        });
    }

    @Override
    public void onInputsTileClicked() {
        showInputs();
    }

    private boolean isInitialLaunch = true;

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);

        if (adapter.getItemCount() == 0) {
            adapter.reload();
            grid.scheduleLayoutAnimation();
        }

        updateGreeting();
        updateWifiLabel();
        updateWallpaper();
        if (settingsPanel != null) settingsPanel.refresh();

        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Only move focus to top bar and set peek mode on initial cold launch
        if (isInitialLaunch) {
            isInitialLaunch = false;
            grid.post(() -> {
                View btn = findViewById(R.id.btnWifi);
                if (btn != null) btn.requestFocus();
                setDockPeek(true, false);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    /** The Settings overlay when it is on screen, or null. */
    private View openOverlay() {
        if (settingsPanel != null && settingsPanel.isShowing()) return settingsPanel.root();
        return null;
    }

    static boolean isInside(View parent, View v) {
        for (View p = v; p != null; p = (p.getParent() instanceof View) ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }

    /**
     * While an overlay is open, D-pad focus stays inside it: it can never fall through to the
     * home screen behind. If there's nothing further in that direction, focus just stays put.
     */
    private boolean trapFocusInOverlay(KeyEvent event) {
        View overlay = openOverlay();
        if (overlay == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int dir;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = View.FOCUS_RIGHT; break;
            default: return false;
        }
        View cur = getCurrentFocus();
        if (cur == null || !isInside(overlay, cur)) {
            overlay.requestFocus(View.FOCUS_DOWN);   // first focusable item in the overlay
            return true;
        }
        View next = cur.focusSearch(dir);             // respects the layout's nextFocus* hints
        if (next == null || !isInside(overlay, next)) {
            next = FocusFinder.getInstance().findNextFocus((ViewGroup) overlay, cur, dir);
        }
        if (next != null && isInside(overlay, next)) next.requestFocus(dir);
        return true;                                  // never let the key reach the home screen
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int k = event.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                    || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                Motion.lastDirection = k;   // the next focused tile leans this way
            }
        }
        if (trapFocusInOverlay(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (settingsPanel != null && settingsPanel.onBack()) {
            if (!settingsPanel.isShowing()) {
                View btn = findViewById(R.id.btnSettings);
                if (btn != null) btn.requestFocus();
            }
            return;
        }
        View focused = getCurrentFocus();
        if (focused != null && focused.getId() == R.id.card) {
            if (lastFocused >= COLUMNS * 2) {
                // Return from apps grid to the dock
                focusTile(0);
            } else {
                // Return from dock to top bar (peek mode, full photo view)
                View btn = findViewById(R.id.btnWifi);
                if (btn != null) btn.requestFocus();
            }
        }
    }

    private void setDockPeek(boolean peek, boolean animate) {
        if (isDockPeeking == peek && !dockSpring.isRunning()) return;
        isDockPeeking = peek;
        final float targetY = peek ? peekOffset : 0f;
        if (peek) {
            grid.scrollToPosition(0);
        }
        if (!animate || !Motion.enabled()) {
            dockSpring.cancel();
            grid.setTranslationY(targetY);
            return;
        }
        if (!dockSpring.isRunning() && Math.abs(grid.getTranslationY() - targetY) < 1f) return;
        dockSpring.animateToFinalPosition(targetY);
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        // Tile rect in root coordinates, normalized by subtracting current grid translation.
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f - grid.getTranslationY();

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(16f * density * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        int row = position / COLUMNS;
        if (row == 0) {
            // Row 0 (Favorites Bar): smoothly park grid at top padding
            RecyclerView.ViewHolder vh0 = grid.findViewHolderForAdapterPosition(0);
            int dockTop = (vh0 != null) ? vh0.itemView.getTop() : item.getTop();
            int dy = dockTop - (int) (6 * density) - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
        } else {
            // Row 1+ (All Apps): smooth, natural scroll without violent jumps
            Rect want = new Rect(0, -(int) (12 * density), item.getWidth(), item.getHeight() + (int) (26 * density));
            grid.requestChildRectangleOnScreen(item, want, false);
        }
        showHero(position, 0f);
    }

    // ---- top shelf & ambient ---------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        if (heroName != null) {
            heroName.setVisibility(View.GONE);
        }
        int color = adapter.ambientColorAt(position);
        if (!heroShown) {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (color != ambientColor) {
            ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                setAmbient(color);
                ambient.animate().alpha(1f).setDuration(500).start();
            }).start();
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (heroName != null) {
            heroName.setVisibility(View.GONE);
        }
        if (!heroShown) return;
        heroShown = false;
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    private void updateWallpaper() {
        SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        String themePref = prefs.getString("wallpaper_theme", "auto");
        boolean isDay;
        if ("day".equals(themePref)) {
            isDay = true;
        } else if ("night".equals(themePref)) {
            isDay = false;
        } else {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int minute = now.get(Calendar.MINUTE);
            int timeInMinutes = hour * 60 + minute;
            // Day cycle: 07:00 (420 min) to 19:30 (1170 min)
            isDay = (timeInMinutes >= 420 && timeInMinutes < 1170);
        }
        int targetRes = isDay ? R.drawable.wallpaper_day : R.drawable.wallpaper_night;

        if (wallpaper == null) return;

        if (currentWallpaperRes == 0) {
            currentWallpaperRes = targetRes;
            wallpaper.setImageResource(targetRes);
            wallpaper.setAlpha(1f);
        } else if (currentWallpaperRes != targetRes) {
            currentWallpaperRes = targetRes;
            wallpaper.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                wallpaper.setImageResource(targetRes);
                wallpaper.animate().alpha(1f).setDuration(600).start();
            }).start();
        }
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        if (!isTopBarShowText) {
            if (btnWifi != null) btnWifi.setText("");
            return;
        }
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        if (btnWifi != null) btnWifi.setText(text);
    }

    private void open(Intent i) {
        if (!tryOpen(i)) Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    /** Starts {@code i}; false when nothing answers it or it is not exported to us. */
    private boolean tryOpen(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Fire OS opens its settings screens via Amazon's home; tell the Home override to let it through.
        if (i.getAction() != null && i.getAction().startsWith("android.settings.")) {
            TvAccessibilityService.letAmazonHomeThrough();
        }
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            TvAccessibilityService.cancelAmazonHomeThrough();
            return false;
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (240 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAtLocation(root, Gravity.CENTER, 0, 0);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- Settings & Top Bar Preferences ----------------------------------------------------------

    private void updatePeekOffset() {
        if ("none".equals(peekDepthPref)) {
            peekOffset = 0f;
        } else if ("balanced".equals(peekDepthPref)) {
            peekOffset = 140f * density;
        } else {
            peekOffset = 220f * density; // default: ultra low peek (wallpaper takes ~95% of screen)
        }
    }

    private void applyTopBarStyle(boolean showText) {
        isTopBarShowText = showText;
        TextView btnSettings = findViewById(R.id.btnSettings);

        int padStart = (int) ((showText ? 16 : 11) * density);
        int padEnd = (int) ((showText ? 20 : 11) * density);
        int drawPad = (int) ((showText ? 9 : 0) * density);

        TextView[] pills = new TextView[]{btnWifi, btnSettings};
        for (TextView p : pills) {
            if (p != null) {
                p.setPadding(padStart, 0, padEnd, 0);
                p.setCompoundDrawablePadding(drawPad);
            }
        }

        if (btnSettings != null) btnSettings.setText(showText ? getString(R.string.settings) : "");
        updateWifiLabel();
    }

    /** Home pressed while the launcher is open: always show the clean home screen. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (settingsPanel != null) settingsPanel.hide(false);
    }

    // ---- SettingsPanel.Host --------------------------------------------------------------------

    @Override
    public boolean topBarShowsText() {
        return isTopBarShowText;
    }

    @Override
    public String peekDepth() {
        return peekDepthPref;
    }

    @Override
    public String wallpaperTheme() {
        return getSharedPreferences("tvhome_prefs", MODE_PRIVATE).getString("wallpaper_theme", "auto");
    }

    @Override
    public boolean tryOpenSystem(Intent intent) {
        return tryOpen(intent);
    }

    @Override
    public String language() {
        return LauncherLocale.current(this);
    }

    @Override
    public void setLanguage(String code) {
        LauncherLocale.save(this, code);
        // Rebuild in the new language and come back to the Language row, so it is clear what changed.
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putBoolean(REOPEN_LANGUAGE, true).apply();
        recreate();
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    public void setTopBarStyle(boolean showText) {
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putBoolean("top_bar_show_text", showText).apply();
        applyTopBarStyle(showText);
    }

    @Override
    public void setPeekDepth(String depth) {
        peekDepthPref = depth;
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("dock_peek_depth", depth).apply();
        updatePeekOffset();
        if (isDockPeeking) {
            setDockPeek(true, true);
        }
    }

    @Override
    public void setWallpaperTheme(String theme) {
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("wallpaper_theme", theme).apply();
        updateWallpaper();
    }
}
