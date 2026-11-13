package com.shawnxe7en.myowntv;

import java.util.Random;

public class ClearIconTest {
    static final int N = 192;

    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }

    static int[] fill(int argb) {
        int[] d = new int[N * N];
        java.util.Arrays.fill(d, argb);
        return d;
    }

    static void disc(int[] d, int cx, int cy, int r, int argb) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int dx = x - cx, dy = y - cy;
            if (dx * dx + dy * dy <= r * r) d[y * N + x] = argb;
        }
    }

    static int[] cut(int[] d) {
        return ClearIcon.cut(d, N, 0, 0, N, N);
    }

    public static void main(String[] a) {
        // 1. a white disc on a flat red square: the disc is the glyph
        int[] red = fill(0xFFE50914);
        disc(red, 96, 96, 50, 0xFFFFFFFF);
        int[] g = cut(red);
        check(g != null, "flat background: split");
        check(g[96 * N + 96] > 240, "disc centre is glyph");
        check(g[5 * N + 5] < 10, "corner is background");
        check(g[96 * N + 150] < 10 && g[96 * N + 60] > 240, "inside the disc yes, outside no");

        // 2. a vertical gradient background (orange to red) with a white disc
        int[] grad = new int[N * N];
        for (int y = 0; y < N; y++) {
            int r = 255, gg = 180 - y * 120 / N, b = 40;
            for (int x = 0; x < N; x++) grad[y * N + x] = 0xFF000000 | (r << 16) | (gg << 8) | b;
        }
        disc(grad, 96, 96, 45, 0xFFFFFFFF);
        g = cut(grad);
        check(g != null && g[96 * N + 96] > 240 && g[10 * N + 10] < 10 && g[180 * N + 100] < 10, "gradient background splits");

        // 3. noise (a photo): refused
        Random rnd = new Random(7);
        int[] photo = new int[N * N];
        for (int i = 0; i < photo.length; i++) photo[i] = 0xFF000000 | rnd.nextInt(0xFFFFFF);
        check(cut(photo) == null, "a photo is refused");

        // 4. a glyph that fills nearly the whole icon: refused
        int[] full = fill(0xFFE50914);
        for (int y = 3; y < N - 3; y++) for (int x = 3; x < N - 3; x++) full[y * N + x] = 0xFFFFFFFF;
        check(cut(full) == null, "a glyph that fills the icon is refused");

        // 5. a blue disc on a transparent square with a white hole: the disc is the glyph, the hole stays a hole
        int[] shape = fill(0x00000000);
        disc(shape, 96, 96, 80, 0xFF2060E0);
        disc(shape, 96, 96, 25, 0xFFFFFFFF);
        g = cut(shape);
        check(g != null, "shape on transparent: split");
        check(g[96 * N + 150] > 240, "the disc is glyph");
        check(g[96 * N + 96] < 20, "the hole is a hole");
        check(g[3 * N + 3] < 10, "outside is empty");

        // 6. an empty (fully transparent) icon: refused
        check(cut(fill(0x00000000)) == null, "empty icon is refused");

        System.out.println("PASS");
    }
}
