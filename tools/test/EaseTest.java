package com.shawnxe7en.myowntv;

public class EaseTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        check(Math.abs(Spring.easeOut(0)) < 1e-4, "easeOut(0)==0");
        check(Math.abs(Spring.easeOut(1) - 1) < 1e-4, "easeOut(1)==1");
        float prev = 0;
        for (int i = 1; i <= 100; i++) {
            float v = Spring.easeOut(i / 100f);
            check(v >= prev - 1e-6f, "monotonic at " + i);
            prev = v;
        }
        check(Spring.easeOut(0.5f) > 0.5f, "fast start: easeOut(0.5) > 0.5");
        check(Spring.easeOut(-1) == 0 && Spring.easeOut(2) == 1, "clamped outside 0..1");
        System.out.println("PASS");
    }
}
