package com.shawnxe7en.myowntv;

/** Design values taken from Couch TV's look: colours and the spacing the screens already share. No Android imports. */
public final class Tokens {
    private Tokens() {}

    public static final int BG = 0xFF0B0B0E, TEXT = 0xFFF5F5F7, TILE = 0xFF1C1C21;
    public static final int TEXT2 = alpha(TEXT, 0.62f), TEXT3 = alpha(TEXT, 0.42f);
    public static final float SAFE_X = 80, SAFE_Y = 60, GAP = 40;

    public static int alpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0xFFFFFF);
    }
}
