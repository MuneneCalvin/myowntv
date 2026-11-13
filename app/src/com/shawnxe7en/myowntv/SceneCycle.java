package com.shawnxe7en.myowntv;

/** Which scene shows when: one every `periodMs`, crossfading into the next over the last FADE_MS. No Android imports. */
final class SceneCycle {
    static final long FADE_MS = 1500;
    private final int count;
    private final long periodMs;

    SceneCycle(int count, long periodMs) {
        this.count = count;
        this.periodMs = periodMs;
    }

    int indexAt(long elapsedMs) {
        return count <= 0 ? 0 : (int) ((elapsedMs / periodMs) % count);
    }

    int nextIndex(int i) {
        return count <= 0 ? 0 : (i + 1) % count;
    }

    /** 0 until the last FADE_MS of a period, then rising to 1 as the next scene takes over. */
    float fade(long elapsedMs) {
        long into = elapsedMs % periodMs, start = periodMs - FADE_MS;
        return into < start ? 0 : (into - start) / (float) FADE_MS;
    }
}
