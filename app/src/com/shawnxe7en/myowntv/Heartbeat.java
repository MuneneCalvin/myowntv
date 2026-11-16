package com.shawnxe7en.myowntv;

/** The clock colon's heartbeat: how strongly it glows at a moment: a full beat, a softer one right after, then a rest. */
final class Heartbeat {
    private Heartbeat() {}

    static final long PERIOD_MS = 1300;
    private static final float LUB = 1f, DUB = 0.7f;   // how strong each beat is

    /** 0 between beats; 1 at the top of the first beat and 0.7 at the top of the second. */
    static float glow(long ms) {
        float phase = (float) Math.floorMod(ms, PERIOD_MS) / PERIOD_MS;
        return LUB * bump(phase / 0.16f) + DUB * bump((phase - 0.2f) / 0.16f);
    }

    /** A smooth rise and fall over 0..1, nothing outside it. */
    private static float bump(float x) {
        if (x <= 0 || x >= 1) return 0;
        float s = (float) Math.sin(Math.PI * x);
        return s * s;
    }
}
