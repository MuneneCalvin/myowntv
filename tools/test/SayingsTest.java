package com.shawnxe7en.myowntv;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SayingsTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    static List<Sayings.Saying> deck(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("Author | quote ").append(i).append(" | source\n");
        return Sayings.parse(sb.toString());
    }
    public static void main(String[] a) throws Exception {
        List<Sayings.Saying> three = Sayings.parse("A One | first | Book 1\nB Two | second | Book 2\nC Three | third | Book 3\n");
        check(three.size() == 3 && three.get(1).author.equals("B Two") && three.get(1).text.equals("second") && three.get(1).source.equals("Book 2"), "parse reads author, quote and source");
        check(Sayings.parse("\n# a note\nA | only two\nA |  | src\nA | q | s | extra\n   \n").isEmpty(), "bad lines are skipped");

        Sayings s = new Sayings(deck(50), new Random(1));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) check(seen.add(s.next().text), "no repeat within one pass");
        check(seen.size() == 50, "one pass shows all 50");
        seen.clear();
        for (int i = 0; i < 50; i++) seen.add(s.next().text);
        check(seen.size() == 50, "the deck refills");

        for (long seed = 0; seed < 20; seed++) {
            Sayings two = new Sayings(deck(2), new Random(seed));
            Sayings.Saying last = null;
            for (int i = 0; i < 20; i++) {
                Sayings.Saying x = two.next();
                check(x != last, "never the same quote twice in a row (seed " + seed + ")");
                last = x;
            }
        }
        check(new Sayings(new ArrayList<>(), new Random(1)).next() == null, "empty list: null");

        Random r = new Random(7);
        Set<Long> delays = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long d = Sayings.nextDelayMs(r);
            check(d >= 60_000 && d <= 300_000, "delay within 1 to 5 minutes: " + d);
            delays.add(d);
        }
        check(delays.size() > 10, "delay varies");

        List<Sayings.Saying> all = Sayings.parse(new String(Files.readAllBytes(Paths.get("app/assets/sayings.txt")), "UTF-8"));
        check(all.size() == 50, "sayings.txt has 50 entries, got " + all.size());
        Set<String> texts = new HashSet<>();
        for (String au : new String[] {"Mahmoud Darwish", "Franz Kafka", "Fyodor Dostoevsky", "Khalil Gibran", "Oscar Wilde"}) {
            int n = 0;
            for (Sayings.Saying x : all) if (x.author.equals(au)) n++;
            check(n == 10, au + " has 10, got " + n);
        }
        for (Sayings.Saying x : all) {
            check(x.text.length() <= Sayings.MAX_CHARS, "too long: " + x.text);
            check(texts.add(x.text), "duplicate: " + x.text);
            check(!x.source.isEmpty(), "no source: " + x.text);
        }
        System.out.println("PASS SayingsTest");
    }
}
