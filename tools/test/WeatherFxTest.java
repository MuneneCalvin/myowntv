package com.shawnxe7en.myowntv;

public class WeatherFxTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        check(WeatherFx.kind(0) == WeatherFx.NONE && WeatherFx.kind(3) == WeatherFx.NONE, "clear and cloudy: no effect");
        check(WeatherFx.kind(-1) == WeatherFx.NONE, "no weather data: no effect");
        check(WeatherFx.kind(53) == WeatherFx.RAIN && WeatherFx.kind(63) == WeatherFx.RAIN && WeatherFx.kind(81) == WeatherFx.RAIN, "drizzle, rain, showers: rain");
        check(WeatherFx.kind(73) == WeatherFx.SNOW && WeatherFx.kind(86) == WeatherFx.SNOW, "snow codes: snow");
        check(WeatherFx.kind(95) == WeatherFx.STORM && WeatherFx.kind(99) == WeatherFx.STORM, "thunderstorm: storm");
        check(WeatherFx.kind(45) == WeatherFx.FOG, "fog: veil");
        check(WeatherFx.intensity(65) > WeatherFx.intensity(61), "heavy rain is stronger than light rain");
        check(WeatherFx.intensity(0) == 0, "no effect, no intensity");
        check(WeatherFx.darken(WeatherFx.NONE, 0) == 0, "no effect, no darkening");
        check(WeatherFx.darken(WeatherFx.RAIN, 1) > WeatherFx.darken(WeatherFx.RAIN, 0.3f), "heavier rain darkens more");
        check(WeatherFx.darken(WeatherFx.STORM, 1) <= 0.5f, "never darker than half: the clock stays readable");
        check(WeatherFx.particles(WeatherFx.RAIN, 1) <= 90 && WeatherFx.particles(WeatherFx.STORM, 1) <= 90, "particle cap");
        check(WeatherFx.particles(WeatherFx.FOG, 1) == 0, "fog has no particles");
        System.out.println("PASS");
    }
}
