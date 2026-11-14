package com.shawnxe7en.myowntv;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/** The holiday a day belongs to, for the home screen's decorations. Pure; the moving ones are worked out, not listed. */
final class Holidays {
    static final int NONE = 0, NEW_YEAR = 1, VALENTINE = 2, EASTER = 3, HALLOWEEN = 4, CHRISTMAS = 5, RAMADAN = 6, DIWALI = 7;

    /** Diwali's main day (Lakshmi Puja) for 2025 to 2030; it follows the lunar calendar. Outside these years: no Diwali. */
    private static final int[][] DIWALI_DAYS = {{2025, 10, 21}, {2026, 11, 8}, {2027, 10, 29}, {2028, 10, 17}, {2029, 11, 5}, {2030, 10, 26}};

    private Holidays() {}

    static int on(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if (m == 12 && day >= 30 || m == 1 && day <= 2) return NEW_YEAR;
        if (m == 12 && day >= 15 && day <= 26) return CHRISTMAS;
        if (m == 2 && day >= 13 && day <= 14) return VALENTINE;
        if (m == 10 && day >= 25) return HALLOWEEN;
        LocalDate easter = easter(d.getYear());
        if (!d.isBefore(easter.minusDays(2)) && !d.isAfter(easter.plusDays(1))) return EASTER;
        for (int[] x : DIWALI_DAYS) {
            if (x[0] == d.getYear() && Math.abs(d.toEpochDay() - LocalDate.of(x[0], x[1], x[2]).toEpochDay()) <= 2) return DIWALI;
        }
        HijrahDate h = HijrahDate.from(d);
        int hm = h.get(ChronoField.MONTH_OF_YEAR), hd = h.get(ChronoField.DAY_OF_MONTH);
        if (hm == 9 || hm == 10 && hd <= 3 || hm == 12 && hd >= 10 && hd <= 12) return RAMADAN;   // Ramadan, Eid al-Fitr, Eid al-Adha
        return NONE;
    }

    /** Western Easter Sunday (the anonymous Gregorian algorithm). */
    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(y, month, day);
    }
}
