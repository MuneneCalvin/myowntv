package com.shawnxe7en.myowntv;

import java.util.ArrayList;
import java.util.List;

public class NowPlayingTextTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    static NowPlayingText.Session s(int state, String title, String artist, String app) {
        return new NowPlayingText.Session(state, title, artist, app, "p");
    }
    public static void main(String[] a) {
        check("Blinding Lights · The Weeknd · Spotify".equals(NowPlayingText.line(s(NowPlayingText.PLAYING, "Blinding Lights", "The Weeknd", "Spotify"))), "line joins title, artist and app");
        check("Title · App".equals(NowPlayingText.line(s(NowPlayingText.PLAYING, "Title", null, "App"))), "title only keeps the app");
        check("Artist · App".equals(NowPlayingText.line(s(NowPlayingText.PLAYING, "  ", "Artist", "App"))), "artist only keeps the app");
        check(NowPlayingText.line(s(NowPlayingText.PLAYING, " ", "", "App")) == null, "nothing usable gives no line");
        String messy = NowPlayingText.line(s(NowPlayingText.PLAYING, "A\nB\t  C", "X", "App"));
        check(messy != null && !messy.contains("\n") && !messy.contains("\t") && !messy.contains("  "), "control characters and runs of spaces collapse");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append('x');
        String longLine = NowPlayingText.line(s(NowPlayingText.PLAYING, sb.toString(), "A", "B"));
        check(longLine.length() <= NowPlayingText.MAX_CHARS && longLine.endsWith("…"), "a long line is cut with an ellipsis");

        List<NowPlayingText.Session> list = new ArrayList<>();
        list.add(s(NowPlayingText.PAUSED, "paused", "x", "A"));
        list.add(s(NowPlayingText.PLAYING, "a", "x", "A"));
        list.add(s(NowPlayingText.PLAYING, "b", "x", "B"));
        check("a".equals(NowPlayingText.pick(list).title), "the first playing session wins");
        List<NowPlayingText.Session> onlyPaused = new ArrayList<>();
        onlyPaused.add(s(NowPlayingText.PAUSED, "p", "x", "A"));
        check(NowPlayingText.pick(onlyPaused) == null, "paused alone is not shown");
        check(NowPlayingText.pick(new ArrayList<>()) == null, "no sessions gives null");
        List<NowPlayingText.Session> buf = new ArrayList<>();
        buf.add(s(NowPlayingText.BUFFERING, "b", "x", "A"));
        check(NowPlayingText.pick(buf) != null, "buffering counts");
        List<NowPlayingText.Session> con = new ArrayList<>();
        con.add(s(NowPlayingText.CONNECTING, "c", "x", "A"));
        check(NowPlayingText.pick(con) != null, "connecting counts");

        String packed = NowPlayingText.pack("T · A", "com.x", 1000);
        NowPlayingText.Saved ok = NowPlayingText.unpack(packed, 1000 + NowPlayingText.KEEP_MS - 1);
        check(ok != null && "T · A".equals(ok.line) && "com.x".equals(ok.pkg) && ok.at == 1000, "unpack returns what was packed");
        check(NowPlayingText.unpack(packed, 1000 + NowPlayingText.KEEP_MS + 1) == null, "older than a day is dropped");
        check(NowPlayingText.unpack("garbage", 0) == null, "garbage is ignored");
        check(NowPlayingText.unpack("", 0) == null, "empty is ignored");
        check(NowPlayingText.unpack(null, 0) == null, "null is ignored");
        check(NowPlayingText.unpack("a\tb\tc", 0) == null, "a bad time is ignored");
        NowPlayingText.Saved tabs = NowPlayingText.unpack(NowPlayingText.pack("a\tb\nc", "p\tq", 5), 5);
        check(tabs != null && !tabs.line.contains("\t") && !tabs.line.contains("\n") && !tabs.pkg.contains("\t"), "tabs and newlines can't break the packing");
        check("https://www.youtube.com/results?search_query=Ruger+-+Asiwaju+%28Official+Video%29+Ruger".equals(
                NowPlayingText.youtubeSearch("Ruger - Asiwaju (Official Video) · Ruger · YouTube")), "the search drops the app and keeps title and artist");
        check("https://www.youtube.com/results?search_query=Only+Title".equals(NowPlayingText.youtubeSearch("Only Title")), "a line without an app is searched whole");
        check("https://www.youtube.com/results?search_query=Title".equals(NowPlayingText.youtubeSearch("Title · YouTube")), "title and app: just the title");
        check(NowPlayingText.youtubeSearch(null) == null && NowPlayingText.youtubeSearch("  ") == null && NowPlayingText.youtubeSearch("") == null, "nothing to search for");
        check(NowPlayingText.youtubeSearch("Tom & Jerry? · Cartoons · YouTube").contains("Tom+%26+Jerry%3F"), "special characters are encoded");
        String html = "<script>var ytInitialData = {\"contents\":{\"adSlot\":{\"videoId\":\"SHORT000000\"},\"videoRenderer\":{\"videoId\":\"AbC-_123xyz\",\"thumbnail\":{}},\"videoRenderer\":{\"videoId\":\"second00000\"}}}</script>";
        check("AbC-_123xyz".equals(NowPlayingText.firstVideoId(html)), "the first video result, not other ids on the page");
        check(NowPlayingText.firstVideoId("<html>no results</html>") == null, "no video on the page");
        check(NowPlayingText.firstVideoId(null) == null && NowPlayingText.firstVideoId("") == null, "nothing to read");
        check(NowPlayingText.firstVideoId("{\"videoRenderer\":{\"videoId\":\"tooShort\"}}") == null, "an id of the wrong length is not a video id");
        check("https://www.youtube.com/watch?v=AbC-_123xyz".equals(NowPlayingText.youtubeWatch("AbC-_123xyz")), "the watch address");
        System.out.println("PASS NowPlayingTextTest");
    }
}
