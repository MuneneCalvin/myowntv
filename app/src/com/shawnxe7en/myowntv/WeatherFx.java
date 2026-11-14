package com.shawnxe7en.myowntv;

/** What the home screen does for the weather: which effect an Open-Meteo WMO code asks for, how strong, how dark. Pure. */
final class WeatherFx {
    static final int NONE = 0, RAIN = 1, SNOW = 2, STORM = 3, FOG = 4;
    static final int MAX_PARTICLES = 90;   // the TV's GPU draws them every frame

    private WeatherFx() {}

    static int kind(int code) {
        if (code >= 95) return STORM;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return RAIN;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return SNOW;
        return NONE;
    }

    /** 0 to 1: light drizzle to a downpour. */
    static float intensity(int code) {
        switch (code) {
            case 51: case 71: case 77: return 0.35f;
            case 53: case 56: case 61: case 80: case 73: case 85: return 0.55f;
            case 55: case 57: case 63: case 66: case 81: return 0.75f;
            case 65: case 67: case 82: case 75: case 86: return 1f;
            default: return kind(code) == NONE ? 0 : 1f;
        }
    }

    /** How much black goes over the scene (0 to 0.5: the clock stays readable). */
    static float darken(int kind, float intensity) {
        switch (kind) {
            case RAIN: return 0.12f + 0.26f * intensity;
            case STORM: return 0.45f;
            case SNOW: return 0.06f + 0.12f * intensity;
            case FOG: return 0.1f;
            default: return 0;
        }
    }

    static int particles(int kind, float intensity) {
        switch (kind) {
            case RAIN: return Math.round(20 + 60 * intensity);
            case STORM: return MAX_PARTICLES;
            case SNOW: return Math.round(25 + 45 * intensity);
            default: return 0;
        }
    }
}
