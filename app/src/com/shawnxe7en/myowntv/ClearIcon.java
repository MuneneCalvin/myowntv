package com.shawnxe7en.myowntv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clear app icons, after Apple's "clear" look, ported from Couch TV's clearicon.js: an app's icon with its background
 * taken away, so only the glyph is left (drawn white on a glass tile). The colours along the icon's edges are the
 * background (one colour, or the steps of a gradient); each pixel is scored by how far it is from the nearest of them,
 * and that score is the glyph's alpha. An icon that will not split cleanly (a photo, a glyph that fills nearly all of
 * it) is refused: cut() returns null and the caller keeps its other drawing. No Android imports.
 */
final class ClearIcon {
    private ClearIcon() {}

    private static final int T0 = 16, T1 = 42;   // Lab distance: under T0 is background, over T1 is fully glyph
    private static final int[] REFUSE = new int[0];

    /**
     * `d`: the icon's ARGB pixels on an n x n canvas, drawn fit and centred at (ox, oy) with size dw x dh (a landscape
     * banner keeps its shape). Returns the glyph's alpha (0..255) per pixel of the canvas, or null when it won't split.
     */
    static int[] cut(int[] d, int n, int ox, int oy, int dw, int dh) {
        int[] shape = shapeCut(d, n, ox, oy, dw, dh);
        if (shape != null) return shape == REFUSE ? null : shape;

        // the background: colours in a band just inside the edges, quantised, the common ones kept
        int band = Math.max(3, Math.round(Math.min(dw, dh) * 0.04f)), inner = Math.round(Math.min(dw, dh) * 0.12f);
        int x0 = ox, y0 = oy, x1 = ox + dw - 1, y1 = oy + dh - 1;
        Map<Integer, Integer> bins = new HashMap<>();
        int total = 0;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                int e = Math.min(Math.min(px - x0, x1 - px), Math.min(py - y0, y1 - py));
                if (e < 1 || e > band) continue;
                int c = d[py * n + px];
                if ((c >>> 24) < 200) continue;
                bins.merge(key(c, 3), 1, Integer::sum);
                total++;
            }
        }
        if (total < 50) return null;
        List<double[]> pal = new ArrayList<>();
        for (Map.Entry<Integer, Integer> en : bins.entrySet()) {
            if (en.getValue() / (double) total < 0.004) continue;
            int k = en.getKey();
            pal.add(lab((((k >> 10) & 31) * 8 + 4), (((k >> 5) & 31) * 8 + 4), ((k & 31) * 8 + 4)));
        }
        if (pal.isEmpty() || pal.size() > 600) return null;

        // each pixel's distance to the nearest background colour
        float[] dbg = new float[n * n];
        Map<Integer, Float> memo = new HashMap<>();
        for (int j = 0; j < n * n; j++) {
            int c = d[j];
            if ((c >>> 24) < 128) { dbg[j] = -1; continue; }
            int k = key(c, 2);
            Float v = memo.get(k);
            if (v == null) {
                v = (float) nearest(lab((c >> 16) & 255, (c >> 8) & 255, c & 255), pal);
                memo.put(k, v);
            }
            dbg[j] = v;
        }
        // the glyph's own colours (one for most, a few for a multi colour logo), most used first. A background with a
        // pale glow near a white glyph needs a looser "clearly not background" before any glyph pixels count.
        List<double[]> gpal = new ArrayList<>();
        for (int lim : new int[] {T1, 28, 22}) {
            Map<Integer, Integer> gbins = new HashMap<>();
            int gtotal = 0;
            for (int j = 0; j < n * n; j++) {
                if (dbg[j] <= lim) continue;
                gbins.merge(key(d[j], 4), 1, Integer::sum);
                gtotal++;
            }
            if (gtotal < n * n * 0.004) continue;
            List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(gbins.entrySet());
            sorted.sort((u, w) -> w.getValue() - u.getValue());
            for (Map.Entry<Integer, Integer> en : sorted) {
                if (en.getValue() / (double) gtotal < 0.03) continue;
                int k = en.getKey();
                gpal.add(lab((((k >> 8) & 15) * 16 + 8), (((k >> 4) & 15) * 16 + 8), ((k & 15) * 16 + 8)));
            }
            break;
        }
        if (gpal.isEmpty() || gpal.size() > 12) return null;
        // colours close to the main one count as it (shading, a gradient in the letters)
        boolean[] mainSet = new boolean[gpal.size()];
        for (int m = 0; m < gpal.size(); m++) mainSet[m] = dist(gpal.get(m), gpal.get(0)) < 30;

        // alpha: which side a pixel is nearer, the background or the glyph (soft at the edges), so a hole cut in the
        // glyph in a colour close to the background stays a hole
        int[] out = new int[n * n];
        double sum = 0;
        int cnt = 0, edgeHits = 0, edgeN = 0;
        Map<Integer, double[]> memo2 = new HashMap<>();
        for (int j = 0; j < n * n; j++) {
            double a = 0, db0 = dbg[j];
            if (db0 > T0 * 0.5) {
                int c = d[j], k = key(c, 2);
                double[] dg = memo2.get(k);
                if (dg == null) {
                    double[] l2 = lab((c >> 16) & 255, (c >> 8) & 255, c & 255);
                    int bi = 0;
                    double bd = 1e18;
                    for (int m = 0; m < gpal.size(); m++) {
                        double e1 = l2[0] - gpal.get(m)[0], e2 = l2[1] - gpal.get(m)[1], e3 = l2[2] - gpal.get(m)[2];
                        double ed = e1 * e1 + e2 * e2 + e3 * e3;
                        if (ed < bd) { bd = ed; bi = m; }
                    }
                    dg = new double[] {Math.sqrt(bd), bi};
                    memo2.put(k, dg);
                }
                double r = db0 / (db0 + dg[0] + 1e-6);
                a = Math.max(0, Math.min(1, (r - 0.36) / 0.28));
                a = a * a * (3 - 2 * a);
                // a second glyph colour shows at part strength, so the detail inside a glyph is not lost
                if (!mainSet[(int) dg[1]]) a *= 0.42;
            }
            out[j] = (int) Math.round(a * 255);
            int jx = j % n, jy = j / n;
            int ee = Math.min(Math.min(jx - x0, x1 - jx), Math.min(jy - y0, y1 - jy));
            if (ee >= inner) { sum += a; cnt++; }
            else if (ee >= 0 && ee < band) { edgeN++; if (a > 0.5) edgeHits++; }
        }
        double cover = cnt > 0 ? sum / cnt : 0;
        // a glyph is neither a speck nor the whole icon, and the outer edge is background
        if (cover < 0.006 || cover > 0.62 || (edgeN > 0 && edgeHits / (double) edgeN > 0.25)) return null;
        return out;
    }

    /**
     * A logo that is itself a shape on a see-through square (YouTube's red rounded box): the clear version keeps the
     * shape as glass and cuts the mark out of it as a hole. When the edges are mostly see-through, the most used opaque
     * colour is the glyph and every other colour inside it is a hole. null: not that kind of icon; REFUSE: it is, but
     * won't split.
     */
    private static int[] shapeCut(int[] d, int n, int ox, int oy, int dw, int dh) {
        int band = Math.max(3, Math.round(Math.min(dw, dh) * 0.04f)), clear = 0, count = 0;
        int x0 = ox, y0 = oy, x1 = ox + dw - 1, y1 = oy + dh - 1;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                if (Math.min(Math.min(px - x0, x1 - px), Math.min(py - y0, y1 - py)) >= band) continue;
                count++;
                if ((d[py * n + px] >>> 24) < 128) clear++;
            }
        }
        if (count == 0 || clear / (double) count < 0.6) return null;
        Map<Integer, Integer> bins = new HashMap<>();
        int best = -1, bestN = 0;
        for (int j = 0; j < n * n; j++) {
            if ((d[j] >>> 24) < 200) continue;
            int k = key(d[j], 4), v = bins.merge(k, 1, Integer::sum);
            if (v > bestN) { bestN = v; best = k; }
        }
        if (best < 0) return REFUSE;
        double[] dark = lab((((best >> 8) & 15) * 16 + 8), (((best >> 4) & 15) * 16 + 8), ((best & 15) * 16 + 8));
        int[] out = new int[n * n];
        Map<Integer, Double> memo = new HashMap<>();
        double sum = 0;
        for (int j = 0; j < n * n; j++) {
            int c = d[j], k = key(c, 2);
            Double dd = memo.get(k);
            if (dd == null) {
                dd = dist(lab((c >> 16) & 255, (c >> 8) & 255, c & 255), dark);
                memo.put(k, dd);
            }
            double a = Math.max(0, Math.min(1, (45 - dd) / 25)) * ((c >>> 24) / 255.0);
            out[j] = (int) Math.round(a * 255);
            sum += a;
        }
        double cover = sum / (n * n);
        return cover < 0.05 || cover > 0.95 ? REFUSE : out;
    }

    /** The colour quantised to `drop` low bits per channel, packed (5+5+5 bits for 3, 6+6+6 for 2, 4+4+4 for 4). */
    private static int key(int argb, int drop) {
        int bits = 8 - drop;
        return (((argb >> 16) & 255) >> drop) << (2 * bits) | (((argb >> 8) & 255) >> drop) << bits | ((argb & 255) >> drop);
    }

    private static double nearest(double[] l, List<double[]> set) {
        double best = 1e18;
        for (double[] s : set) {
            double dl = l[0] - s[0], da = l[1] - s[1], db = l[2] - s[2], dd = dl * dl + da * da + db * db;
            if (dd < best) best = dd;
        }
        return Math.sqrt(best);
    }

    private static double dist(double[] a, double[] b) {
        double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private static double[] lab(int ri, int gi, int bi) {
        double r = ri / 255.0, g = gi / 255.0, b = bi / 255.0;
        r = r > 0.04045 ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = g > 0.04045 ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = b > 0.04045 ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
        x = f(x);
        y = f(y);
        z = f(z);
        return new double[] {116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }
}
