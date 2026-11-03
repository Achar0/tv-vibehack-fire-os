package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewStub;
import android.widget.TextView;

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
        void openSystem(Intent intent);
    }

    static final String[] WALLPAPERS = {"auto", "day", "night"};
    static final String[] DOCKS = {"ultra", "balanced", "none"};
    static final String[] TOP_BARS = {"text", "icon"};

    private static final long IN_MS = 220, OUT_MS = 180;

    private final Activity activity;
    private final ViewStub stub;
    private final Host host;

    private View root;
    private View[] cats;
    private View[] panes;
    /** First row of each pane (About has none). */
    private View[] firstRows;
    private TextView valWallpaper, valDock, valTopBar;
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
        if (root == null) inflate();
        showing = true;
        refreshValues();
        root.animate().cancel();
        root.setAlpha(0f);
        root.setVisibility(View.VISIBLE);
        root.animate().alpha(1f).setDuration(IN_MS).start();
        current = -1;   // always reset the pane fully, even after a hide mid-animation
        for (View c : cats) c.setSelected(false);
        selectCategory(0, false);
        cats[0].requestFocus();
    }

    void hide(boolean animate) {
        if (root == null || !showing) return;
        showing = false;
        root.animate().cancel();
        if (!animate) {
            root.setVisibility(View.GONE);
            return;
        }
        root.animate().alpha(0f).setDuration(OUT_MS).withEndAction(() -> root.setVisibility(View.GONE)).start();
    }

    /** Back: from a row to its category, from a category out of Settings. Returns true if consumed. */
    boolean onBack() {
        if (!showing) return false;
        View f = activity.getCurrentFocus();
        if (current >= 0 && f != null && isInside(panes[current], f)) {
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

        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            cats[i].setOnFocusChangeListener((v, has) -> {
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
            if (has && current >= 0) cats[current].setSelected(true);
        };
        int[] rows = {R.id.rowAdb, R.id.rowFireSettings, R.id.rowWifi, R.id.rowWallpaper, R.id.rowDock, R.id.rowTopBar};
        for (int id : rows) find(id).setOnFocusChangeListener(rowFocus);

        find(R.id.rowFireSettings).setOnClickListener(v -> host.openSystem(new Intent(Settings.ACTION_SETTINGS)));
        find(R.id.rowWifi).setOnClickListener(v -> host.openSystem(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        find(R.id.rowWallpaper).setOnClickListener(v -> {
            host.setWallpaperTheme(Choices.next(WALLPAPERS, host.wallpaperTheme()));
            refreshValues();
        });
        find(R.id.rowDock).setOnClickListener(v -> {
            host.setPeekDepth(Choices.next(DOCKS, host.peekDepth()));
            refreshValues();
        });
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

    private void enterPane(int idx) {
        if (firstRows[idx] != null) firstRows[idx].requestFocus();
    }

    /** Shows pane {@code idx}: a short fade and slide in, the others hidden. */
    private void selectCategory(int idx, boolean animate) {
        if (idx == current) return;
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
                p.setTranslationX(24f * activity.getResources().getDisplayMetrics().density);
                p.animate().alpha(1f).translationX(0f).setDuration(IN_MS).start();
            } else {
                p.setAlpha(1f);
                p.setTranslationX(0f);
            }
        }
    }

    private void refreshValues() {
        valWallpaper.setText(label(host.wallpaperTheme(), WALLPAPERS, new int[]{
                R.string.settings_wallpaper_auto, R.string.settings_wallpaper_day, R.string.settings_wallpaper_night}));
        valDock.setText(label(host.peekDepth(), DOCKS, new int[]{
                R.string.settings_peek_ultra, R.string.settings_peek_balanced, R.string.settings_peek_none}));
        valTopBar.setText(host.topBarShowsText() ? R.string.settings_top_bar_text : R.string.settings_top_bar_icon);
    }

    /** The label for {@code value}, the first one when the value is unknown. */
    private String label(String value, String[] order, int[] labels) {
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(value)) return activity.getString(labels[i]);
        }
        return activity.getString(labels[0]);
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T find(int id) {
        return (T) root.findViewById(id);
    }

    private static boolean isInside(View parent, View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }
}
