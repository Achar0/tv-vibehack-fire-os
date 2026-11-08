package com.gathin.tvhome;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The phone page's small protocol, without any Android or network code. */
final class PhoneProtocol {
    private PhoneProtocol() {}

    static final class Tile {
        final String id, label;
        final boolean hidden;

        Tile(String id, String label, boolean hidden) {
            this.id = id;
            this.label = label;
            this.hidden = hidden;
        }
    }

    /** The phone page checks in while it's on screen; after this long without it, it's gone. */
    private static final long PAGE_GONE_MS = 45_000L;

    /** Whether the phone page counts as open, given when it was last heard from (0 = never). */
    static boolean pageOpen(long lastSeen, long now) {
        return lastSeen != 0 && now - lastSeen < PAGE_GONE_MS;
    }

    static String path(String target) {
        int q = target.indexOf('?');
        return q < 0 ? target : target.substring(0, q);
    }

    /** Whether the request carries the code from the QR; compared in constant time. */
    static boolean tokenOk(String target, String token) {
        int q = target.indexOf('?');
        if (q < 0) return false;
        String given = form(target.substring(q + 1)).get("t");
        return given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> form(String body) {
        Map<String, String> out = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (UnsupportedEncodingException | IllegalArgumentException ignored) {   // a malformed pair is skipped
            }
        }
        return out;
    }

    static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    static String tilesJson(List<Tile> tiles) {
        StringBuilder b = new StringBuilder("[");
        for (Tile t : tiles) {
            if (b.length() > 1) b.append(',');
            b.append("{\"id\":").append(jsonString(t.id)).append(",\"label\":").append(jsonString(t.label))
                    .append(",\"hidden\":").append(t.hidden).append('}');
        }
        return b.append(']').toString();
    }

    /** Your own tile names, stored as "id TAB name" lines. */
    static Map<String, String> decodeNames(Set<String> stored) {
        Map<String, String> out = new HashMap<>();
        for (String line : stored) {
            int tab = line.indexOf('\t');
            if (tab > 0) out.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return out;
    }

    /** {@code stored} with the name of {@code id} changed; an empty name goes back to the app's own. */
    static Set<String> withName(Set<String> stored, String id, String name) {
        Set<String> out = new HashSet<>();
        for (String line : stored) if (!line.startsWith(id + "\t")) out.add(line);
        String clean = name.replaceAll("\\s+", " ").trim();
        if (!clean.isEmpty()) out.add(id + "\t" + clean);
        return out;
    }
}
