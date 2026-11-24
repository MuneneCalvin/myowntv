package com.shawnxe7en.myowntv;

public class NowPlayingControlsTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.001f; }
    public static void main(String[] a) {
        check(NowPlayingControls.canNext(32), "next bit");
        check(!NowPlayingControls.canNext(0), "no next bit");
        check(NowPlayingControls.canPrevious(16), "previous bit");
        check(!NowPlayingControls.canPrevious(32), "next is not previous");
        check(NowPlayingControls.canToggle(4) && NowPlayingControls.canToggle(2) && NowPlayingControls.canToggle(512), "play, pause or play-pause allow the toggle");
        check(!NowPlayingControls.canToggle(32), "next alone doesn't allow the toggle");
        check(NowPlayingControls.isPlaying(NowPlayingText.PLAYING) && NowPlayingControls.isPlaying(NowPlayingText.BUFFERING)
                && NowPlayingControls.isPlaying(NowPlayingText.CONNECTING) && !NowPlayingControls.isPlaying(NowPlayingText.PAUSED), "playing states");
        check(near(NowPlayingControls.progress(30_000, 120_000, 1000, 1f, 1000 + 30_000, true), 0.5f), "progress moves with time while playing");
        check(near(NowPlayingControls.progress(30_000, 120_000, 1000, 1f, 1000 + 30_000, false), 0.25f), "paused ignores elapsed time");
        check(NowPlayingControls.progress(30_000, 0, 1000, 1f, 2000, true) == -1, "no duration, no progress");
        check(NowPlayingControls.progress(30_000, -1, 1000, 1f, 2000, true) == -1, "negative duration, no progress");
        check(near(NowPlayingControls.progress(100_000, 120_000, 0, 1f, 90_000, true), 1f), "clamped to 1");
        check(near(NowPlayingControls.progress(-5, 120_000, 0, 1f, 0, false), 0f), "clamped to 0");
        check(near(NowPlayingControls.progress(0, 100_000, 0, 2f, 10_000, true), 0.2f), "speed scales elapsed time");
        check(near(NowPlayingControls.progress(10_000, 100_000, 5_000, 1f, 1_000, true), 0.1f), "a clock behind the update adds nothing");

        java.util.List<NowPlayingText.Session> l = new java.util.ArrayList<>();
        l.add(new NowPlayingText.Session(NowPlayingText.PAUSED, "a", "x", "A", "p"));
        check("a".equals(NowPlayingText.pickCurrent(l).title), "a paused session is current when nothing plays");
        l.add(new NowPlayingText.Session(NowPlayingText.PLAYING, "b", "x", "B", "q"));
        check("b".equals(NowPlayingText.pickCurrent(l).title), "a playing session beats a paused one");
        check(NowPlayingText.pickCurrent(new java.util.ArrayList<>()) == null, "no sessions, no current");
        java.util.List<NowPlayingText.Session> stopped = new java.util.ArrayList<>();
        stopped.add(new NowPlayingText.Session(1, "s", "x", "S", "r"));
        check(NowPlayingText.pickCurrent(stopped) == null, "stopped alone is not current");
        check(NowPlayingControls.isPlaying(NowPlayingText.SKIPPING_NEXT) && NowPlayingControls.isPlaying(NowPlayingText.SKIPPING_PREVIOUS), "skipping counts as playing (the card must not flip)");
        System.out.println("PASS NowPlayingControlsTest");
    }
}
