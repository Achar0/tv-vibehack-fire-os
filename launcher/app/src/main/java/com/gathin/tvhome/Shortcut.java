package com.gathin.tvhome;

import java.util.Locale;

/**
 * A tile that opens an address in a chosen app: a web page in a browser, a stream or playlist in a player.
 * One config.xml line, {@code Label|address|app package|optional type}, e.g.
 * {@code News|https://example.org/live|com.phlox.tvwebbrowser} or
 * {@code IPTV|https://…/list.m3u8|org.videolan.vlc|audio/x-mpegurl}.
 */
final class Shortcut {
    final String label, uri, pkg, mime;
    /** Stable name for dock_apps / hidden_apps: "shortcut." + the label in lower case, spaces as dashes. */
    final String id;

    private Shortcut(String label, String uri, String pkg, String mime) {
        this.label = label;
        this.uri = uri;
        this.pkg = pkg;
        this.mime = mime;
        this.id = "shortcut." + label.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    /** Drawable used as the tile's banner when the app ships one (res/drawable-nodpi/shortcut_<label>.jpg). */
    String bannerName() {
        return id.replace('.', '_').replace('-', '_');
    }

    /** The shortcut a config line describes, or null when a field is missing or the address has no scheme. */
    static Shortcut parse(String line) {
        if (line == null) return null;
        String[] f = line.split("\\|", -1);
        if (f.length < 3) return null;
        String label = f[0].trim(), uri = f[1].trim(), pkg = f[2].trim();
        String mime = f.length > 3 && !f[3].trim().isEmpty() ? f[3].trim() : null;
        if (label.isEmpty() || pkg.isEmpty() || !uri.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.+")) return null;
        return new Shortcut(label, uri, pkg, mime);
    }
}
