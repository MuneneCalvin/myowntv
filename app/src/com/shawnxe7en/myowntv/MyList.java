package com.shawnxe7en.myowntv;

import java.util.ArrayList;
import java.util.List;

/** "My list": the titles saved from Search, newest first. One line each (kind|TMDB id|title|year), kept in Prefs. */
final class MyList {
    static final int MAX = 60;

    static final class Entry {
        final String kind;      // "movie" or "tv"
        final int id;           // TMDB's
        final String title, year;

        Entry(String kind, int id, String title, String year) {
            this.kind = kind;
            this.id = id;
            this.title = clean(title);
            this.year = clean(year);
        }
    }

    /** No | or line break: they would split the line. */
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[|\\r\\n]+", " ").trim();
    }

    static List<Entry> parse(String text) {
        List<Entry> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length != 4 || !(p[0].equals("movie") || p[0].equals("tv")) || p[2].isEmpty()) continue;
            try {
                out.add(new Entry(p[0], Integer.parseInt(p[1]), p[2], p[3]));
            } catch (NumberFormatException ignored) {   // a damaged line
            }
        }
        return out;
    }

    static String serialize(List<Entry> list) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) sb.append(e.kind).append('|').append(e.id).append('|').append(e.title).append('|').append(e.year).append('\n');
        return sb.toString();
    }

    static boolean contains(List<Entry> list, String kind, int id) {
        for (Entry e : list) if (e.id == id && e.kind.equals(kind)) return true;
        return false;
    }

    /** A new list with `e` first (saved again, it moves up); the oldest fall off past MAX. */
    static List<Entry> add(List<Entry> list, Entry e) {
        List<Entry> out = remove(list, e.kind, e.id);
        out.add(0, e);
        while (out.size() > MAX) out.remove(out.size() - 1);
        return out;
    }

    static List<Entry> remove(List<Entry> list, String kind, int id) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : list) if (!(e.id == id && e.kind.equals(kind))) out.add(e);
        return out;
    }
}
