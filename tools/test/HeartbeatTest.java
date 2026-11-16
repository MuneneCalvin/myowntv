package com.shawnxe7en.myowntv;

public class HeartbeatTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        long p = Heartbeat.PERIOD_MS;
        check(Heartbeat.glow(0) == 0f, "no glow at the start of a beat");
        check(Math.abs(Heartbeat.glow(p) - Heartbeat.glow(0)) < 1e-6, "periodic");
        check(Math.abs(Heartbeat.glow(7 * p + 123) - Heartbeat.glow(123)) < 1e-4, "the same beat every period");
        float lub = 0, dub = 0, max = 0, min = 9;
        for (long t = 0; t < p; t++) {
            float g = Heartbeat.glow(t);
            max = Math.max(max, g);
            min = Math.min(min, g);
            if (t < p * 0.18) lub = Math.max(lub, g);
            else if (t > p * 0.2 && t < p * 0.38) dub = Math.max(dub, g);
        }
        check(lub > 0.98 && lub <= 1f, "the first beat is full: " + lub);
        check(dub > 0.65 && dub < lub, "the second beat is softer: " + dub);
        check(max <= 1f + 1e-6, "never above full glow");
        check(min >= 0f, "never below no glow");
        check(Heartbeat.glow((long) (p * 0.6)) == 0f && Heartbeat.glow((long) (p * 0.9)) == 0f, "rests between beats");
        check(Heartbeat.glow(-1) >= 0f, "a negative time does not break it");
        System.out.println("PASS HeartbeatTest");
    }
}
