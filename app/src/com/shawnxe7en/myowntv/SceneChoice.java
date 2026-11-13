package com.shawnxe7en.myowntv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Which of the scenes are in use: the choice is kept as the comma list of the ones turned off. No Android imports. */
final class SceneChoice {
    private SceneChoice() {}

    static final String[] ALL = {"night", "golden", "water", "fog", "dawn"};

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
