package com.shawnxe7en.myowntv;

import java.util.List;

public class MyListTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        check(MyList.parse("").isEmpty() && MyList.parse(null).isEmpty(), "empty text is an empty list");

        List<MyList.Entry> l = MyList.parse("");
        l = MyList.add(l, new MyList.Entry("movie", 603, "The Matrix", "1999"));
        l = MyList.add(l, new MyList.Entry("tv", 1399, "Game of Thrones", "2011"));
        check(l.size() == 2 && l.get(0).id == 1399, "the newest comes first");
        check(MyList.contains(l, "movie", 603) && !MyList.contains(l, "tv", 603), "contains looks at kind and id");

        l = MyList.add(l, new MyList.Entry("movie", 603, "The Matrix", "1999"));
        check(l.size() == 2 && l.get(0).id == 603, "saving again moves it to the front, no twice");

        String text = MyList.serialize(l);
        List<MyList.Entry> back = MyList.parse(text);
        check(back.size() == 2 && back.get(0).title.equals("The Matrix") && back.get(1).year.equals("2011"), "round trip");

        List<MyList.Entry> odd = MyList.add(MyList.parse(""), new MyList.Entry("movie", 7, "A|B\nC", "2001"));
        check(MyList.parse(MyList.serialize(odd)).size() == 1 && MyList.parse(MyList.serialize(odd)).get(0).id == 7,
                "a title with | or a newline can't break the list");

        check(MyList.parse("movie|x|bad|1\nnonsense\nmovie|5|Ok|2000\ntv|9||").size() == 1, "bad lines are skipped");
        check(MyList.parse("film|5|Wrong kind|2000").isEmpty(), "only movie and tv");

        l = MyList.remove(l, "movie", 603);
        check(l.size() == 1 && !MyList.contains(l, "movie", 603), "remove");

        List<MyList.Entry> big = MyList.parse("");
        for (int i = 1; i <= MyList.MAX + 20; i++) big = MyList.add(big, new MyList.Entry("movie", i, "T" + i, ""));
        check(big.size() == MyList.MAX && big.get(0).id == MyList.MAX + 20, "the oldest fall off past the cap");
        System.out.println("PASS MyListTest");
    }
}
