package com.shawnxe7en.myowntv;

/** What an app's media session offers (its action bits) and how far along it is, apart from Android. */
final class NowPlayingControls {
    private NowPlayingControls() {}

    /** PlaybackState's action bits. */
    static final long ACTION_PAUSE = 2, ACTION_PLAY = 4, ACTION_PREVIOUS = 16, ACTION_NEXT = 32, ACTION_PLAY_PAUSE = 512;

    static boolean canNext(long actions) { return (actions & ACTION_NEXT) != 0; }

    static boolean canPrevious(long actions) { return (actions & ACTION_PREVIOUS) != 0; }

    static boolean canToggle(long actions) { return (actions & (ACTION_PLAY | ACTION_PAUSE | ACTION_PLAY_PAUSE)) != 0; }

    static boolean isPlaying(int state) {
        return state == NowPlayingText.PLAYING || state == NowPlayingText.BUFFERING || state == NowPlayingText.CONNECTING;
    }

    /**
     * How far through the track, 0 to 1, from where the app said it was (`position` at `updatedAt`) and the time since,
     * so it moves without asking again. -1 when there's no length (a live stream). `updatedAt` and `now` are on one clock.
     */
    static float progress(long position, long duration, long updatedAt, float speed, long now, boolean playing) {
        if (duration <= 0) return -1;
        long elapsed = playing ? Math.max(0, now - updatedAt) : 0;
        float f = (position + elapsed * speed) / duration;
        return Math.max(0, Math.min(1, f));
    }
}
