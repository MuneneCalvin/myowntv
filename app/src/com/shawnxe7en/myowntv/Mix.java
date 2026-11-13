package com.shawnxe7en.myowntv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Ordering helpers with no Android imports. */
final class Mix {
    private Mix() {}

    /**
     * The items one group at a time in turn (the first of each group, then the second of each...), the groups in the
     * order they first appear and each group's own order kept. With picks by popularity this keeps one streaming
     * service from taking every slot. A null key is a group too.
     */
    static <T> List<T> byGroup(List<T> in, Function<T, String> keyOf) {
        Map<String, List<T>> groups = new LinkedHashMap<>();
        for (T t : in) groups.computeIfAbsent(keyOf.apply(t), k -> new ArrayList<>()).add(t);
        if (groups.size() < 2) return in;
        List<T> out = new ArrayList<>(in.size());
        for (int i = 0; out.size() < in.size(); i++) {
            for (List<T> g : groups.values()) if (i < g.size()) out.add(g.get(i));
        }
        return out;
    }
}
