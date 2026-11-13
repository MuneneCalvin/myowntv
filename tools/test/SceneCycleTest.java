package com.shawnxe7en.myowntv;

public class SceneCycleTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        SceneCycle c = new SceneCycle(5, 20000);
        check(c.indexAt(0) == 0, "indexAt(0)==0");
        check(c.indexAt(19999) == 0, "indexAt(19999)==0");
        check(c.indexAt(20000) == 1, "indexAt(20000)==1");
        check(c.indexAt(100000) == 0, "wraps after five periods");
        check(c.nextIndex(4) == 0, "nextIndex(4)==0");
        check(c.nextIndex(1) == 2, "nextIndex(1)==2");
        check(c.fade(0) == 0 && c.fade(18000) == 0, "no fade before the last 1500 ms");
        check(c.fade(19999) > 0.99f, "fade(19999) ~ 1");
        check(Math.abs(c.fade(19250) - 0.5f) < 0.01f, "fade halfway");
        check(c.fade(20000) == 0, "fade restarts at the next period");
        check(new SceneCycle(1, 20000).nextIndex(0) == 0, "one scene: next is itself");
        check(new SceneCycle(0, 20000).indexAt(5) == 0, "no scenes: index 0 (caller skips drawing)");
        System.out.println("PASS");
    }
}
