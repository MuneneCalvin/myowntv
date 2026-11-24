package com.shawnxe7en.myowntv;

import java.util.List;

/**
 * The rules behind Now Playing, apart from Android: which session counts as playing, the one-line text, and the last
 * line kept for after a restart (the sessions are gone then).
 */
final class NowPlayingText {
    private NowPlayingText() {}

    /** PlaybackState's values. */
    static final int PAUSED = 2, PLAYING = 3, BUFFERING = 6, CONNECTING = 8;
    static final long KEEP_MS = 24L * 3600_000;
    static final int MAX_CHARS = 80;

    static final class Session {
        final int state;
        final String title, artist, app, pkg;

        Session(int state, String title, String artist, String app, String pkg) {
            this.state = state;
            this.title = title;
            this.artist = artist;
            this.app = app;
            this.pkg = pkg;
        }
    }

    static final class Saved {
        final String line, pkg;
        final long at;

        Saved(String line, String pkg, long at) {
            this.line = line;
            this.pkg = pkg;
            this.at = at;
        }
    }

    /** The first session that is playing, buffering or connecting; a paused one is not "now playing". */
    static Session pick(List<Session> sessions) {
        for (Session s : sessions) {
            if (s.state == PLAYING || s.state == BUFFERING || s.state == CONNECTING) return s;
        }
        return null;
    }

    /** The one to show and control: what is playing, else a paused session (it can be resumed); null if neither. */
    static Session pickCurrent(List<Session> sessions) {
        Session playing = pick(sessions);
        if (playing != null) return playing;
        for (Session s : sessions) if (s.state == PAUSED) return s;
        return null;
    }

    /** "Title · Artist · App", one clean line, or null when there's neither a title nor an artist. */
    static String line(Session s) {
        String title = clean(s.title), artist = clean(s.artist), app = clean(s.app);
        if (title.isEmpty() && artist.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String part : new String[] {title, artist, app}) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part);
        }
        return sb.length() <= MAX_CHARS ? sb.toString() : sb.substring(0, MAX_CHARS - 1) + "…";
    }

    /**
     * YouTube keeps no address for what it played, only the title and artist, so "resume" is a search for them (the same
     * video comes up first, focused): the last line without its app name, as a YouTube search address. Null if empty.
     */
    static String youtubeSearch(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        String[] parts = line.split(" \u00b7 ");
        int n = parts.length > 1 ? parts.length - 1 : 1;   // the last part is the app
        StringBuilder q = new StringBuilder();
        for (int i = 0; i < n; i++) q.append(i == 0 ? "" : " ").append(parts[i].trim());
        try {
            return "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(q.toString(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }

    private static final java.util.regex.Pattern VIDEO = java.util.regex.Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"");

    /** The first video in a YouTube results page (not an ad or a short), or null. */
    static String firstVideoId(String html) {
        if (html == null) return null;
        java.util.regex.Matcher m = VIDEO.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    static String youtubeWatch(String id) {
        return "https://www.youtube.com/watch?v=" + id;
    }

    static String pack(String line, String pkg, long at) {
        return clean(line) + "\t" + clean(pkg) + "\t" + at;
    }

    /** What pack saved, or null when it's missing, damaged or older than a day. */
    static Saved unpack(String packed, long now) {
        if (packed == null) return null;
        String[] f = packed.split("\t", -1);
        if (f.length != 3 || f[0].isEmpty()) return null;
        try {
            long at = Long.parseLong(f[2]);
            return now - at > KEEP_MS ? null : new Saved(f[0], f[1], at);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
    }
}
