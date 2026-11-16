package com.shawnxe7en.myowntv;

public class HeartbeatTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        long p = Heartbeat.PERIOD_MS;
        check(Heartbeat.scale(0) == 1f, "at rest at the start of a beat");
        check(Math.abs(Heartbeat.scale(p) - 1f) < 1e-6, "periodic");
        check(Math.abs(Heartbeat.scale(7 * p + 123) - Heartbeat.scale(123)) < 1e-4, "the same beat every period");
        float lub = 0, dub = 0, max = 0, min = 9;
        for (long t = 0; t < p; t++) {
            float s = Heartbeat.scale(t);
            max = Math.max(max, s);
            min = Math.min(min, s);
            if (t < p * 0.18) lub = Math.max(lub, s);
            else if (t > p * 0.2 && t < p * 0.38) dub = Math.max(dub, s);
        }
        check(lub > 1.15 && lub <= Heartbeat.MAX_SCALE, "the first beat is the bigger one: " + lub);
        check(dub > 1.08 && dub < lub, "the second beat is smaller: " + dub);
        check(max <= Heartbeat.MAX_SCALE + 1e-4, "never above MAX_SCALE");
        check(min >= 1f - 1e-6, "never smaller than at rest");
        check(Heartbeat.scale((long) (p * 0.6)) == 1f && Heartbeat.scale((long) (p * 0.9)) == 1f, "rests between beats");
        check(Heartbeat.scale(-1) >= 1f, "a negative time does not break it");
        System.out.println("PASS HeartbeatTest");
    }
}
