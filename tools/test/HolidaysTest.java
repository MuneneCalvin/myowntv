package com.shawnxe7en.myowntv;

import java.time.LocalDate;

public class HolidaysTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    static int on(int y, int m, int d) { return Holidays.on(LocalDate.of(y, m, d)); }
    public static void main(String[] a) {
        check(Holidays.easter(2026).equals(LocalDate.of(2026, 4, 5)), "Easter 2026 is April 5");
        check(Holidays.easter(2027).equals(LocalDate.of(2027, 3, 28)), "Easter 2027 is March 28");
        check(Holidays.easter(2025).equals(LocalDate.of(2025, 4, 20)), "Easter 2025 is April 20");
        check(on(2026, 11, 14) == Holidays.NONE, "an ordinary day: nothing");
        check(on(2026, 12, 25) == Holidays.CHRISTMAS && on(2026, 12, 15) == Holidays.CHRISTMAS && on(2026, 12, 27) == Holidays.NONE, "Christmas Dec 15 to 26");
        check(on(2026, 12, 31) == Holidays.NEW_YEAR && on(2027, 1, 1) == Holidays.NEW_YEAR && on(2027, 1, 3) == Holidays.NONE, "New Year Dec 30 to Jan 2");
        check(on(2026, 2, 14) == Holidays.VALENTINE && on(2026, 2, 15) == Holidays.NONE, "Valentine's Feb 13 and 14");
        check(on(2026, 10, 31) == Holidays.HALLOWEEN && on(2026, 10, 24) == Holidays.NONE, "Halloween Oct 25 to 31");
        check(on(2026, 4, 3) == Holidays.EASTER && on(2026, 4, 6) == Holidays.EASTER && on(2026, 4, 7) == Holidays.NONE && on(2026, 4, 2) == Holidays.NONE, "Easter: Good Friday to Easter Monday");
        check(on(2026, 11, 8) == Holidays.DIWALI && on(2026, 11, 6) == Holidays.DIWALI && on(2026, 11, 11) == Holidays.NONE, "Diwali 2026 is Nov 8, two days either side");
        check(on(2026, 3, 20) == Holidays.RAMADAN, "Eid al-Fitr 2026 (about Mar 20) is in the Ramadan / Eid window");
        check(on(2026, 3, 1) == Holidays.RAMADAN, "Ramadan 2026 starts about Feb 18: Mar 1 is in it");
        check(on(2026, 5, 27) == Holidays.RAMADAN, "Eid al-Adha 2026 (about May 27)");
        check(on(2026, 7, 10) == Holidays.NONE, "July: nothing");
        check(on(2040, 6, 1) != -1, "far years don't crash");
        System.out.println("PASS");
    }
}
