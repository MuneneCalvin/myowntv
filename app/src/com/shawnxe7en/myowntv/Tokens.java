package com.shawnxe7en.myowntv;

/** Design values taken from Couch TV's look: colours and the spacing the screens already share. No Android imports. */
public final class Tokens {
    private Tokens() {}

    public static final int BG = 0xFF0B0B0E, TEXT = 0xFFF5F5F7, TILE = 0xFF1C1C21;
    public static final int TEXT2 = alpha(TEXT, 0.62f), TEXT3 = alpha(TEXT, 0.42f);
    public static final float SAFE_X = 80, SAFE_Y = 60, GAP = 40;

    // Glass: a smoky tint, a white veil from top (24%) through the middle (6%) to the bottom (12%), more colour, a blur.
    public static final int GLASS_TINT = 0xFF14141C;
    public static final float GLASS_TINT_ALPHA = 0.22f, GLASS_SATURATE = 1.7f, GLASS_BLUR_PX = 28;
    public static final float VEIL_TOP = 0.24f, VEIL_MID = 0.06f, VEIL_BOTTOM = 0.12f;

    /** The white veil's strength down the glass (t: 0 top, 1 bottom). The liquid-glass shader repeats these stops. */
    public static float veil(float t) {
        return t < 0.5f ? VEIL_TOP + (VEIL_MID - VEIL_TOP) * (t * 2) : VEIL_MID + (VEIL_BOTTOM - VEIL_MID) * ((t - 0.5f) * 2);
    }

    public static int alpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0xFFFFFF);
    }
}
