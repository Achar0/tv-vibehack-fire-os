package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewStub;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Two-pane Settings: categories on the left (Device, Appearance, About), the chosen category's rows on
 * the right. Inflated from a ViewStub on first open, so it costs nothing until it is used.
 *
 * Right (or OK) on a category enters its pane, Left or Back returns to the category, Back on a category
 * closes the panel. MainActivity's focus trap keeps the D-pad inside; nextFocus* hints steer it.
 */
final class SettingsPanel {

    /** What the panel changes in the launcher. MainActivity implements it. */
    interface Host {
        boolean topBarShowsText();
        void setTopBarStyle(boolean showText);
        String peekDepth();
        void setPeekDepth(String depth);
        String wallpaperTheme();
        void setWallpaperTheme(String theme);
        /** Starts the screen if it exists; false (and no message) when it can't be opened. */
        boolean tryOpenSystem(Intent intent);
        String language();
        /** Saves the launcher language and rebuilds the screen in it. */
        void setLanguage(String code);
    }

    static final String[] WALLPAPERS = {"auto", "day", "night"};
    static final String[] DOCKS = {"ultra", "balanced", "none"};
    static final String[] TOP_BARS = {"text", "icon"};

    private static final long IN_MS = 220, OUT_MS = 160;
    /** Focused row / category: a small lift, like tvOS lists. */
    private static final float ROW_SCALE = 1.03f, ROW_LIFT_DP = 6f;
    /** The panel arrives from slightly smaller, so it reads as a surface rather than a fade. */
    private static final float PANEL_FROM_SCALE = 0.98f;

    private final Activity activity;
    private final ViewStub stub;
    private final Host host;

    private View root;
    private View[] cats;
    private View[] panes;
    /** First row of each pane (About has none). */
    private View[] firstRows;
    private TextView valWallpaper, valDock, valTopBar, valLanguage, adbState;
    /** The no-permission hint is shown once per launcher run. */
    private boolean adbHintShown;
    private int current = -1;
    /** False as soon as hide() starts, so the fade-out no longer traps focus. */
    private boolean showing;

    SettingsPanel(Activity activity, ViewStub stub, Host host) {
        this.activity = activity;
        this.stub = stub;
        this.host = host;
    }

    boolean isShowing() { return showing; }

    View root() { return root; }

    void show() {
        show(0, 0);
    }

    /** Opens on category {@code cat}, focusing row {@code rowId} when non-zero (after a language change). */
    void show(int cat, int rowId) {
        if (root == null) inflate();
        showing = true;
        refreshValues();
        root.animate().cancel();
        root.setAlpha(0f);
        root.setScaleX(PANEL_FROM_SCALE);
        root.setScaleY(PANEL_FROM_SCALE);
        root.setVisibility(View.VISIBLE);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(IN_MS).setInterpolator(Motion.EASE_OUT).start();
        current = -1;   // always reset the pane fully, even after a hide mid-animation
        for (View c : cats) c.setSelected(false);
        selectCategory(cat, false);
        View row = rowId != 0 ? root.findViewById(rowId) : null;
        if (row != null) {
            cats[cat].setSelected(true);
            row.requestFocus();
        } else {
            cats[cat].requestFocus();
        }
    }

    /** Re-reads live values (ADB can change in Amazon's settings while the panel is open). */
    void refresh() {
        if (showing) refreshValues();
    }

    void hide(boolean animate) {
        if (root == null || !showing) return;
        showing = false;
        root.animate().cancel();
        if (!animate) {
            root.setVisibility(View.GONE);
            return;
        }
        root.animate().alpha(0f).scaleX(PANEL_FROM_SCALE).scaleY(PANEL_FROM_SCALE).setDuration(OUT_MS)
                .withEndAction(() -> root.setVisibility(View.GONE)).start();
    }

    /** Back: from a row to its category, from a category out of Settings. Returns true if consumed. */
    boolean onBack() {
        if (!showing) return false;
        View f = activity.getCurrentFocus();
        if (current >= 0 && f != null && MainActivity.isInside(panes[current], f)) {
            cats[current].requestFocus();
        } else {
            hide(true);
        }
        return true;
    }

    private void inflate() {
        root = stub.inflate();
        cats = new View[]{find(R.id.catDevice), find(R.id.catAppearance), find(R.id.catAbout)};
        panes = new View[]{find(R.id.paneDevice), find(R.id.paneAppearance), find(R.id.paneAbout)};
        firstRows = new View[]{find(R.id.rowAdb), find(R.id.rowWallpaper), null};
        valWallpaper = find(R.id.valWallpaper);
        valDock = find(R.id.valDock);
        valTopBar = find(R.id.valTopBar);
        valLanguage = find(R.id.valLanguage);
        adbState = find(R.id.adbState);

        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            cats[i].setOnFocusChangeListener((v, has) -> {
                Motion.focus(v, has, ROW_SCALE, liftPx(), false);
                if (!has) return;
                for (View c : cats) c.setSelected(false);
                selectCategory(idx, true);
            });
            cats[i].setOnClickListener(v -> enterPane(idx));
            // Right enters this category's pane; with no rows (About) focus stays put.
            View first = firstRows[i];
            cats[i].setNextFocusRightId(first != null ? first.getId() : cats[i].getId());
        }

        // While focus is in a pane, its category stays highlighted so you can see where you are.
        View.OnFocusChangeListener rowFocus = (v, has) -> {
            Motion.focus(v, has, ROW_SCALE, liftPx(), false);
            if (has && current >= 0) cats[current].setSelected(true);
        };
        // OK sinks the focused row a little on key-down and lifts it back on key-up; the click still fires.
        View.OnKeyListener pressKeys = (v, keyCode, event) -> Motion.handlePress(v, keyCode, event, ROW_SCALE);
        int[] rows = {R.id.rowAdb, R.id.rowFireSettings, R.id.rowWifi, R.id.rowWallpaper, R.id.rowDock, R.id.rowTopBar,
                R.id.rowLanguage};
        for (int id : rows) {
            find(id).setOnFocusChangeListener(rowFocus);
            find(id).setOnKeyListener(pressKeys);
        }
        for (View c : cats) c.setOnKeyListener(pressKeys);

        find(R.id.rowAdb).setOnClickListener(v -> toggleAdb());
        find(R.id.rowFireSettings).setOnClickListener(v -> openOrSay(new Intent(Settings.ACTION_SETTINGS)));
        find(R.id.rowWifi).setOnClickListener(v -> openOrSay(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        find(R.id.rowWallpaper).setOnClickListener(v -> {
            host.setWallpaperTheme(Choices.next(WALLPAPERS, host.wallpaperTheme()));
            refreshValues();
        });
        find(R.id.rowDock).setOnClickListener(v -> {
            host.setPeekDepth(Choices.next(DOCKS, host.peekDepth()));
            refreshValues();
        });
        find(R.id.rowLanguage).setOnClickListener(v ->
                host.setLanguage(Choices.next(LauncherLocale.CODES, host.language())));
        find(R.id.rowTopBar).setOnClickListener(v -> {
            String next = Choices.next(TOP_BARS, host.topBarShowsText() ? "text" : "icon");
            host.setTopBarStyle("text".equals(next));
            refreshValues();
        });

        TextView version = find(R.id.aboutVersion);
        try {
            String name = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            version.setText(activity.getString(R.string.settings_about_version, name));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    /** Flips ADB when allowed; otherwise opens Developer Options. The badge always shows the re-read state. */
    private void toggleAdb() {
        if (AdbSwitch.canWrite(activity) && AdbSwitch.set(activity, !AdbSwitch.isEnabled(activity))) {
            refreshAdb();
            return;
        }
        refreshAdb();   // a refused write leaves the real state on the badge, then falls back below
        if (!adbHintShown) {
            adbHintShown = true;
            Toast.makeText(activity, R.string.adb_no_permission_toast, Toast.LENGTH_LONG).show();
        }
        for (Intent i : AdbSwitch.developerOptionsIntents(activity)) {
            if (host.tryOpenSystem(i)) return;
        }
        Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    private void refreshAdb() {
        adbState.setText(AdbSwitch.isEnabled(activity) ? R.string.adb_on : R.string.adb_off);
    }

    private void openOrSay(Intent i) {
        if (!host.tryOpenSystem(i)) Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    private void enterPane(int idx) {
        if (firstRows[idx] != null) firstRows[idx].requestFocus();
    }

    /** Shows pane {@code idx}: a short fade and slide in, the others hidden. */
    private void selectCategory(int idx, boolean animate) {
        if (idx == current) return;
        float from = (idx > current ? 1 : -1) * 24f * activity.getResources().getDisplayMetrics().density;
        current = idx;
        for (int i = 0; i < panes.length; i++) {
            View p = panes[i];
            p.animate().cancel();
            if (i != idx) {
                p.setVisibility(View.GONE);
                continue;
            }
            p.setVisibility(View.VISIBLE);
            if (animate) {
                p.setAlpha(0f);
                p.setTranslationY(from);
                p.animate().alpha(1f).translationY(0f).setDuration(IN_MS).setInterpolator(Motion.EASE_OUT).start();
            } else {
                p.setAlpha(1f);
                p.setTranslationY(0f);
            }
        }
    }

    private void refreshValues() {
        refreshAdb();
        valWallpaper.setText(label(host.wallpaperTheme(), WALLPAPERS, new int[]{
                R.string.settings_wallpaper_auto, R.string.settings_wallpaper_day, R.string.settings_wallpaper_night}));
        valDock.setText(label(host.peekDepth(), DOCKS, new int[]{
                R.string.settings_peek_ultra, R.string.settings_peek_balanced, R.string.settings_peek_none}));
        valTopBar.setText(host.topBarShowsText() ? R.string.settings_top_bar_text : R.string.settings_top_bar_icon);
        valLanguage.setText(label(host.language(), LauncherLocale.CODES, new int[]{
                R.string.lang_en, R.string.lang_it, R.string.lang_zh}));
    }

    /** The label for {@code value}, the first one when the value is unknown. */
    private String label(String value, String[] order, int[] labels) {
        return activity.getString(labels[Choices.indexOf(order, value)]);
    }

    private float liftPx() {
        return ROW_LIFT_DP * activity.getResources().getDisplayMetrics().density;
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T find(int id) {
        return (T) root.findViewById(id);
    }
}
