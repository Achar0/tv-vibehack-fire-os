package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An IPTV playlist (#EXTM3U) as groups of channels. The same channel listed several times (one line per
 * source) becomes one channel with its sources in playlist order, so a dead source can be swapped for the next.
 */
final class M3u {
    static final class Channel {
        final String name;
        final List<String> sources = new ArrayList<>();

        Channel(String name) { this.name = name; }
    }

    private static final Pattern GROUP = Pattern.compile("group-title=\"([^\"]*)\"");

    /** group -> (channel name -> channel), both in first-seen order. */
    private final Map<String, Map<String, Channel>> groups = new LinkedHashMap<>();

    static M3u parse(String text) {
        M3u out = new M3u();
        if (text == null || !text.trim().startsWith("#EXTM3U")) return out;
        String name = null, group = "";
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                Matcher m = GROUP.matcher(line);
                group = m.find() ? m.group(1).trim() : "";
            } else if (!line.startsWith("#") && name != null) {
                Map<String, Channel> g = out.groups.computeIfAbsent(group, k -> new LinkedHashMap<>());
                String shown = name.isEmpty() ? line : name;
                g.computeIfAbsent(key(shown), k -> new Channel(shown)).sources.add(line);
                name = null;
            }
        }
        return out;
    }

    /** "CCTV1", "CCTV-1" and "cctv 1" are the same channel; the first spelling seen is the one shown. */
    static String key(String name) {
        return name.toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s\\-_]", "");
    }

    /** Groups with the most channels first; equal sizes keep playlist order. */
    List<String> groups() {
        List<String> out = new ArrayList<>(groups.keySet());
        out.sort((a, b) -> groups.get(b).size() - groups.get(a).size());
        return out;
    }

    List<Channel> channels(String group) {
        Map<String, Channel> g = groups.get(group);
        return g == null ? Collections.emptyList() : new ArrayList<>(g.values());
    }
}
