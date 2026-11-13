package com.shawnxe7en.myowntv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Which of the scenes are in use: the choice is kept as the comma list of the ones turned off. No Android imports. */
final class SceneChoice {
    private SceneChoice() {}

    /** The scenes, in the order the settings list them (the first five came with Couch TV, the rest are Pexels photos). */
    static final String[] ALL = {"night", "golden", "water", "fog", "dawn", "amber-dusk", "amber-ridges", "sea-sunset",
            "golden-peaks", "aurora-coast", "aurora-lake", "mountain-mist", "dunes"};

    /** How long each picture stays, in minutes. */
    static final int[] MINUTES = {5, 10, 30, 60, 90, 150, 180};
    static final int DEFAULT_MINUTES = 30;

    /** The next (dir 1) or previous (dir -1) choice, wrapping; a value that isn't a choice counts as the default. */
    static int stepMinutes(int current, int dir) {
        int at = indexOf(current);
        if (at < 0) at = indexOf(DEFAULT_MINUTES);
        return MINUTES[(at + dir + MINUTES.length) % MINUTES.length];
    }

    private static int indexOf(int minutes) {
        for (int i = 0; i < MINUTES.length; i++) if (MINUTES[i] == minutes) return i;
        return -1;
    }

    /** "5 min", "1 h", "1 h 30 min". */
    static String minutesLabel(int minutes) {
        if (minutes < 60) return minutes + " min";
        return minutes % 60 == 0 ? minutes / 60 + " h" : minutes / 60 + " h " + minutes % 60 + " min";
    }

    private static Set<String> parse(String offCsv) {
        Set<String> off = new LinkedHashSet<>();
        for (String s : offCsv.split(",")) {
            s = s.trim();
            for (String n : ALL) if (n.equals(s)) off.add(s);
        }
        return off;
    }

    static boolean isOff(String offCsv, String name) {
        return parse(offCsv).contains(name);
    }

    /** The scenes that are on, in `all`'s order; never empty (with all of them off, all of them are used). */
    static String[] enabled(String[] all, String offCsv) {
        Set<String> off = parse(offCsv);
        List<String> on = new ArrayList<>();
        for (String n : all) if (!off.contains(n)) on.add(n);
        return on.isEmpty() ? all : on.toArray(new String[0]);
    }

    /** The list with `name` turned off or back on; unchanged if that would turn off the last one on. */
    static String toggle(String offCsv, String name) {
        Set<String> off = parse(offCsv);
        if (off.contains(name)) off.remove(name);
        else if (off.size() + 1 < ALL.length) off.add(name);
        return String.join(",", off);
    }
}
