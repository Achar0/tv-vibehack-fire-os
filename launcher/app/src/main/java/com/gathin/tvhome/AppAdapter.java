package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);

        void onInputsTileClicked();
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Set for a shortcut tile: what it opens instead of the app's own start screen. */
        Intent target;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Shadow depth of a focused tile. */
    static final float LIFT_DP = 10f;

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (res/values/config.xml, dock_apps). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        // TVs without HDMI/tuner inputs (e.g. streaming sticks) can hide this tile via hidden_apps.
        if (!hidden.contains("com.gathin.tvhome.inputs")) fresh.add(new Entry(ctx.getString(R.string.inputs), new ComponentName("com.gathin.tvhome.inputs", "InputsActivity"),
                ctx.getDrawable(R.drawable.banner_inputs), ctx.getDrawable(R.drawable.ic_input)));

        // Shortcut tiles (config.xml, shortcuts): shown with the target app's icon, skipped when that app is missing.
        for (String line : ctx.getResources().getStringArray(R.array.shortcuts)) {
            Shortcut sc = Shortcut.parse(line);
            if (sc == null || hidden.contains(sc.id)) continue;
            Drawable appIcon;
            try {
                appIcon = sc.opensChannelPicker() ? ctx.getApplicationInfo().loadIcon(pm) : pm.getApplicationIcon(sc.pkg);
            } catch (PackageManager.NameNotFoundException notInstalled) {
                continue;
            }
            int bannerRes = ctx.getResources().getIdentifier(sc.bannerName(), "drawable", ctx.getPackageName());
            Drawable banner = bannerRes != 0 ? ctx.getDrawable(bannerRes) : null;
            Entry e = new Entry(sc.label, new ComponentName(sc.id, sc.pkg), banner, appIcon);
            Intent i;
            if (sc.opensChannelPicker()) {
                i = new Intent(ctx, ChannelsActivity.class).putExtra(ChannelsActivity.EXTRA_URL, sc.uri);
            } else {
                i = new Intent(Intent.ACTION_VIEW).setPackage(sc.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (sc.mime != null) i.setDataAndType(Uri.parse(sc.uri), sc.mime);
                else i.setData(Uri.parse(sc.uri));
            }
            e.target = i;
            fresh.add(e);
        }

        // Row 0: Dock Core Apps
        List<Entry> row0 = new ArrayList<>();
        List<Entry> otherApps = new ArrayList<>();
        for (String fav : favourites) {
            for (Entry e : fresh) {
                if (fav.equals(e.pkg) && !row0.contains(e)) {
                    row0.add(e);
                    break;
                }
            }
        }
        for (Entry e : fresh) {
            if (!row0.contains(e)) otherApps.add(e);
        }
        Collections.sort(otherApps, (a, b) -> a.label.compareToIgnoreCase(b.label));

        // Guarantee row0 has exactly COLUMNS items so rows stay aligned
        while (row0.size() < MainActivity.COLUMNS && !otherApps.isEmpty()) {
            row0.add(otherApps.remove(0));
        }
        while (row0.size() > MainActivity.COLUMNS) {
            otherApps.add(0, row0.remove(row0.size() - 1));
        }

        items.clear();
        items.addAll(row0);
        items.addAll(otherApps);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        boolean focused = h.card.isFocused();
        float density = h.itemView.getResources().getDisplayMetrics().density;
        h.name.setVisibility(View.VISIBLE);
        h.name.setAlpha(focused ? 1.0f : 0.0f);
        h.name.setTranslationY(focused ? 0f : -density * 4f);
        h.name.setScaleX(focused ? 1.04f : 1.0f);
        h.name.setScaleY(focused ? 1.04f : 1.0f);
        Motion.reset(h.card, focused ? FOCUS_SCALE : 1f);
        if (focused) h.card.setTranslationZ(LIFT_DP * density);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> {
            if ("com.gathin.tvhome.inputs".equals(e.pkg)) {
                if (listener != null) listener.onInputsTileClicked();
            } else {
                launch(e);
            }
        });
        h.card.setOnLongClickListener(v -> {
            if ("com.gathin.tvhome.inputs".equals(e.pkg) || e.target != null) return true;
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Fire OS opens app info via Amazon's home; let the Home override pass it once.
            TvAccessibilityService.letAmazonHomeThrough();
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException | SecurityException ignored) {
                TvAccessibilityService.cancelAmazonHomeThrough();
            }
            return true;
        });
    }

    private void launch(Entry e) {
        Intent i = e.target != null ? e.target : new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    /** Rebuild the tiles when there are none yet or an app was installed, removed or changed. */
    static boolean needsReload(int count, boolean appsChanged) {
        return count == 0 || appsChanged;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if ("com.gathin.tvhome.inputs".equals(e.pkg)) return 0xFF2563EB;
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = density * 16f;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                // Press-in on OK: the tile sinks on key-down and comes back on key-up (the click still fires).
                if (Motion.isSelect(keyCode)) return Motion.handlePress(view, keyCode, event, FOCUS_SCALE);
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                // tvOS lift: critically damped spring, deeper shadow, a lean toward the D-pad direction.
                Motion.focus(view, focused, FOCUS_SCALE, LIFT_DP * density, true);
                name.animate()
                        .alpha(focused ? 1.0f : 0.0f)
                        .translationY(focused ? 0f : -density * 4f)
                        .scaleX(focused ? 1.04f : 1.0f)
                        .scaleY(focused ? 1.04f : 1.0f)
                        .setDuration(focused ? 200 : 150).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
