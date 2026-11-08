package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IPTV channel picker: the playlist's groups on the left, its channels on the right. OK plays the channel in the
 * player app (config.xml, iptv_player), Right switches to the channel's next source when one doesn't load.
 * The playlist is cached and shown at once; it is downloaded again when older than {@link #FRESH_MS}.
 * This download is the launcher's only network use.
 */
public class ChannelsActivity extends Activity {

    static final String EXTRA_URL = "playlist_url";
    private static final long FRESH_MS = 6 * 60 * 60 * 1000L;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final float ROW_SCALE = 1.03f, ROW_LIFT_DP = 6f;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<String> groups = new ArrayList<>();
    private final List<M3u.Channel> channels = new ArrayList<>();
    /** Source picked per channel (Right cycles it), kept while the screen is open. */
    private final Map<String, Integer> sourceOf = new HashMap<>();
    private M3u list = M3u.parse("");
    private RecyclerView groupView, channelView;
    private TextView status;
    private int currentGroup = -1;
    private float lift;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channels);
        lift = ROW_LIFT_DP * getResources().getDisplayMetrics().density;
        status = findViewById(R.id.channelsStatus);
        groupView = findViewById(R.id.channelGroups);
        channelView = findViewById(R.id.channelList);
        groupView.setLayoutManager(new LinearLayoutManager(this));
        channelView.setLayoutManager(new LinearLayoutManager(this));
        groupView.setItemAnimator(null);
        channelView.setItemAnimator(null);
        groupView.setAdapter(new GroupAdapter());
        channelView.setAdapter(new ChannelAdapter());

        String url = getIntent().getStringExtra(EXTRA_URL);
        File cache = new File(getCacheDir(), "iptv.m3u8");
        boolean haveCache = cache.isFile() && cache.length() > 0;
        if (haveCache) show(read(cache));
        if (url != null && (!haveCache || System.currentTimeMillis() - cache.lastModified() > FRESH_MS)) {
            if (!haveCache) status.setText(R.string.channels_loading);
            io.execute(() -> {
                String text = download(url, cache);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (text != null) show(text);
                    else if (groups.isEmpty()) status.setText(R.string.channels_error);
                });
            });
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    /** Back from the channel list goes to the groups first, then closes. */
    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        if (f != null && channelView.findContainingItemView(f) != null && currentGroup >= 0) {
            RecyclerView.ViewHolder vh = groupView.findViewHolderForAdapterPosition(currentGroup);
            if (vh != null) {
                vh.itemView.requestFocus();
                return;
            }
        }
        super.onBackPressed();
    }

    private void show(String text) {
        M3u parsed = M3u.parse(text);
        if (parsed.groups().isEmpty()) {
            if (groups.isEmpty()) status.setText(R.string.channels_error);
            return;
        }
        boolean first = groups.isEmpty();
        list = parsed;
        groups.clear();
        groups.addAll(list.groups());
        status.setText(R.string.channels_hint);
        groupView.getAdapter().notifyDataSetChanged();
        selectGroup(Math.max(0, Math.min(currentGroup, groups.size() - 1)), true);
        if (first) groupView.post(() -> {
            RecyclerView.ViewHolder vh = groupView.findViewHolderForAdapterPosition(0);
            if (vh != null) vh.itemView.requestFocus();
        });
    }

    private void selectGroup(int idx, boolean force) {
        if (idx == currentGroup && !force) return;
        int old = currentGroup;
        currentGroup = idx;
        if (old >= 0 && old < groups.size()) groupView.getAdapter().notifyItemChanged(old);
        groupView.getAdapter().notifyItemChanged(idx);
        channels.clear();
        channels.addAll(list.channels(groups.get(idx)));
        channelView.getAdapter().notifyDataSetChanged();
        channelView.scrollToPosition(0);
    }

    private void play(M3u.Channel c) {
        String src = c.sources.get(source(c));
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(src), "video/*")
                .setPackage(getString(R.string.iptv_player));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private int source(M3u.Channel c) {
        Integer s = sourceOf.get(c.name);
        return s == null ? 0 : s % c.sources.size();
    }

    private static String download(String url, File cache) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10_000);
            con.setReadTimeout(15_000);
            if (con.getResponseCode() != 200) return null;
            byte[] body = readAll(con.getInputStream());
            String text = new String(body, StandardCharsets.UTF_8);
            if (M3u.parse(text).groups().isEmpty()) return null;
            File tmp = new File(cache.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(body);
            }
            if (!tmp.renameTo(cache)) tmp.delete();
            return text;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new java.io.IOException("playlist too large");
            }
            return out.toByteArray();
        }
    }

    private final class GroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel_group, parent, false);
            RecyclerView.ViewHolder h = new RecyclerView.ViewHolder(v) {};
            v.setOnFocusChangeListener((view, has) -> {
                Motion.focus(view, has, ROW_SCALE, lift, false);
                int pos = h.getBindingAdapterPosition();
                // Focus can move during the list's own layout (scrolling to a new row), when changing its rows crashes
                if (has && pos != RecyclerView.NO_POSITION) groupView.post(() -> selectGroup(pos, false));
            });
            v.setOnKeyListener((view, k, e) -> Motion.handlePress(view, k, e, ROW_SCALE));
            v.setOnClickListener(view -> {
                RecyclerView.ViewHolder first = channelView.findViewHolderForAdapterPosition(0);
                if (first != null) first.itemView.requestFocus();
            });
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            String g = groups.get(pos);
            ((TextView) h.itemView).setText(g.isEmpty() ? getString(R.string.channels_other) : g);
            h.itemView.setSelected(pos == currentGroup);
        }

        @Override
        public int getItemCount() { return groups.size(); }
    }

    private final class ChannelAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
            RecyclerView.ViewHolder h = new RecyclerView.ViewHolder(v) {};
            v.setOnFocusChangeListener((view, has) -> Motion.focus(view, has, ROW_SCALE, lift, false));
            v.setOnClickListener(view -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) play(channels.get(pos));
            });
            v.setOnKeyListener((view, k, e) -> {
                int pos = h.getBindingAdapterPosition();
                if (k == KeyEvent.KEYCODE_DPAD_RIGHT && pos != RecyclerView.NO_POSITION) {
                    // Right: the channel's next source, shown on the row; stays on the row.
                    if (e.getAction() == KeyEvent.ACTION_DOWN) {
                        M3u.Channel c = channels.get(pos);
                        sourceOf.put(c.name, (source(c) + 1) % c.sources.size());
                        notifyItemChanged(pos);
                    }
                    return true;
                }
                return Motion.handlePress(view, k, e, ROW_SCALE);
            });
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            M3u.Channel c = channels.get(pos);
            ((TextView) h.itemView.findViewById(R.id.channelName)).setText(c.name);
            TextView src = h.itemView.findViewById(R.id.channelSource);
            src.setText(c.sources.size() > 1
                    ? getString(R.string.channels_source, source(c) + 1, c.sources.size()) : "");
        }

        @Override
        public int getItemCount() { return channels.size(); }
    }
}
