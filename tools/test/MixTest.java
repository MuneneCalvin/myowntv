package com.shawnxe7en.myowntv;

import java.util.Arrays;
import java.util.List;

public class MixTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    static String key(String s) { return s.startsWith("N") ? "netflix" : s.startsWith("A") ? "apple" : s.startsWith("P") ? "prime" : null; }

    public static void main(String[] a) {
        List<String> in = Arrays.asList("N1", "N2", "N3", "A1", "A2", "P1");
        check(Mix.byGroup(in, MixTest::key).equals(Arrays.asList("N1", "A1", "P1", "N2", "A2", "N3")), "one from each group in turn, order kept inside a group");
        check(Mix.byGroup(Arrays.asList("N1", "N2"), MixTest::key).equals(Arrays.asList("N1", "N2")), "a single group keeps its order");
        check(Mix.byGroup(Arrays.<String>asList(), MixTest::key).isEmpty(), "empty stays empty");
        check(Mix.byGroup(Arrays.asList("x", "N1", "y"), MixTest::key).equals(Arrays.asList("x", "N1", "y")), "no key: its own group, kept (null key is one group)");
        check(Mix.byGroup(in, MixTest::key).size() == in.size(), "nothing lost");
        System.out.println("PASS");
    }
}
