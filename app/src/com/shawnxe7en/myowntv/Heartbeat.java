package com.shawnxe7en.myowntv;

/** The clock colon's heartbeat: how big it is at a moment, a quick big beat, a smaller one right after, then a rest. */
final class Heartbeat {
    private Heartbeat() {}

    static final long PERIOD_MS = 1300;
    static final float MAX_SCALE = 1.25f;
    private static final float LUB = 0.22f, DUB = 0.15f;   // how much bigger the two beats grow

    /** 1 at rest; up to 1 + 0.22 on the first beat and 1 + 0.15 on the second. */
    static float scale(long ms) {
        float phase = (float) Math.floorMod(ms, PERIOD_MS) / PERIOD_MS;
        return 1 + LUB * bump(phase / 0.16f) + DUB * bump((phase - 0.2f) / 0.16f);
    }

    /** A smooth rise and fall over 0..1, nothing outside it. */
    private static float bump(float x) {
        if (x <= 0 || x >= 1) return 0;
        float s = (float) Math.sin(Math.PI * x);
        return s * s;
    }
}
