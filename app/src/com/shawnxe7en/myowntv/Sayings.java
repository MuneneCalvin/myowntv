package com.shawnxe7en.myowntv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The screensaver's quotes: a shuffled deck (every quote once before any repeats) and how long each one stays. */
final class Sayings {
    static final int MAX_CHARS = 160;
    private static final long MIN_MS = 60_000, MAX_MS = 300_000;

    static final class Saying {
        final String author, text, source;

        Saying(String author, String text, String source) {
            this.author = author;
            this.text = text;
            this.source = source;
        }
    }

    private final List<Saying> all;
    private final Random rnd;
    private final List<Saying> deck = new ArrayList<>();
    private Saying last;

    Sayings(List<Saying> all, Random rnd) {
        this.all = all;
        this.rnd = rnd;
    }

    /** Lines of "Author | quote | source"; blank lines, # comments and lines without three filled fields are skipped. */
    static List<Saying> parse(String file) {
        List<Saying> out = new ArrayList<>();
        for (String line : file.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\|", -1);
            if (f.length != 3) continue;
            String author = f[0].trim(), text = f[1].trim(), source = f[2].trim();
            if (author.isEmpty() || text.isEmpty() || source.isEmpty()) continue;
            out.add(new Saying(author, text, source));
        }
        return out;
    }

    /** The next quote, or null with none. The first of a fresh deck is never the one just shown. */
    Saying next() {
        if (all.isEmpty()) return null;
        if (deck.isEmpty()) {
            deck.addAll(all);
            Collections.shuffle(deck, rnd);
            if (all.size() > 1 && deck.get(deck.size() - 1) == last) Collections.swap(deck, deck.size() - 1, 0);
        }
        last = deck.remove(deck.size() - 1);
        return last;
    }

    /** A quote stays on screen for 1 to 5 minutes, drawn fresh each time. */
    static long nextDelayMs(Random rnd) {
        return MIN_MS + (long) (rnd.nextDouble() * (MAX_MS - MIN_MS));
    }
}
