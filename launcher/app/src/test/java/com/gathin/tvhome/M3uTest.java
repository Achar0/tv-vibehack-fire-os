package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class M3uTest {
    private static final String LIST = "#EXTM3U\n"
            + "#PLAYLIST: IPTV Channel List\n"
            + "#EXTINF:-1 tvg-name=\"CCTV1\" group-title=\"央视频道\" response-time=\"120ms\",CCTV1\n"
            + "http://a/1.m3u8\n"
            + "#EXTINF:-1 tvg-name=\"CCTV1\" group-title=\"央视频道\",CCTV1\r\n"
            + "http://b/1.m3u8\r\n"
            + "#EXTINF:-1 tvg-name=\"湖南卫视\" group-title=\"卫视频道\",湖南卫视\n"
            + "\n"
            + "https://c/hn.m3u8\n"
            + "#EXTINF:-1,No Group\n"
            + "http://d/x.m3u8\n"
            + "#EXTINF:-1 group-title=\"央视频道\",CCTV2\n"
            + "http://e/2.m3u8\n";

    @Test public void groupsKeepTheirOrder() {
        assertEquals(Arrays.asList("央视频道", "卫视频道", ""), M3u.parse(LIST).groups());
    }

    @Test public void duplicateChannelsBecomeSourcesInOrder() {
        List<M3u.Channel> cctv = M3u.parse(LIST).channels("央视频道");
        assertEquals(2, cctv.size());
        assertEquals("CCTV1", cctv.get(0).name);
        assertEquals(Arrays.asList("http://a/1.m3u8", "http://b/1.m3u8"), cctv.get(0).sources);
        assertEquals("CCTV2", cctv.get(1).name);
    }

    @Test public void blankLinesAndMissingGroupsAreTolerated() {
        M3u list = M3u.parse(LIST);
        assertEquals("https://c/hn.m3u8", list.channels("卫视频道").get(0).sources.get(0));
        assertEquals("No Group", list.channels("").get(0).name);
    }

    @Test public void notAPlaylistGivesNothing() {
        assertTrue(M3u.parse("<html>404</html>").groups().isEmpty());
        assertTrue(M3u.parse("").groups().isEmpty());
    }

    @Test public void spellingVariantsOfAChannelAreOneChannel() {
        String l = "#EXTM3U\n#EXTINF:-1 group-title=\"G\",CCTV1\nhttp://a\n#EXTINF:-1 group-title=\"G\",CCTV-1\nhttp://b\n"
                + "#EXTINF:-1 group-title=\"G\",cctv 1\nhttp://c\n";
        List<M3u.Channel> c = M3u.parse(l).channels("G");
        assertEquals(1, c.size());
        assertEquals("CCTV1", c.get(0).name);
        assertEquals(Arrays.asList("http://a", "http://b", "http://c"), c.get(0).sources);
    }

    @Test public void biggerGroupsComeFirst() {
        String l = "#EXTM3U\n#EXTINF:-1 group-title=\"Small\",A\nhttp://a\n"
                + "#EXTINF:-1 group-title=\"Big\",B\nhttp://b\n#EXTINF:-1 group-title=\"Big\",C\nhttp://c\n";
        assertEquals(Arrays.asList("Big", "Small"), M3u.parse(l).groups());
    }
}
