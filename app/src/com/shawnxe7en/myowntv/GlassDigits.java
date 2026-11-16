package com.shawnxe7en.myowntv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

/** The glass clock's digits as bitmaps (what Shaders.GLASS_TEXT reads): built by makeGlass, shown by GlassClock. */
final class GlassDigits {
    private GlassDigits() {}

    /** One rendering of the digits, made once a minute in the background. */
    static final class Glass {
        final String text;
        final Bitmap slope, cover, shadow;
        BitmapShader slopeShader, coverShader;   // made once, on the main thread (GlassClock), as the shader's inputs
        final int x, y;
        final float inkTop, inkHeight;

        Glass(String text, Bitmap slope, Bitmap cover, Bitmap shadow, int x, int y, float inkTop, float inkHeight) {
            this.text = text;
            this.slope = slope;
            this.cover = cover;
            this.shadow = shadow;
            this.x = x;
            this.y = y;
            this.inkTop = inkTop;
            this.inkHeight = inkHeight;
        }
    }


    static BitmapShader nearest(Bitmap b) {
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Frost.filter(s, BitmapShader.FILTER_MODE_NEAREST);
        return s;
    }

    /**
     * The sharp rendering of the digits, the surface height (the blurred rendering: 0.5 at the edge, 1 in the middle of
     * the strokes), its slope (for the lens and the highlight) and the shadow.
     */
    static Glass makeGlass(String text, Paint paint, float right, float top, float k) {
        Rect ink = new Rect();
        paint.getTextBounds(text, 0, text.length(), ink);
        int pad = Math.round(40 * k);
        return make(text, paint, ink, pad, Math.round(right - ink.width()) - pad, Math.round(top) - pad, k, 0, 0);
    }

    /**
     * The digits and, apart from them, the colon (null without one): the digits' glass with the colon's columns cleared,
     * and the colon alone at its place, so GlassClock can draw the colon bigger and smaller (the heartbeat).
     */
    static Glass[] makeParts(String text, Paint paint, float right, float top, float k) {
        int ci = text.indexOf(':');
        if (ci < 0) return new Glass[] {makeGlass(text, paint, right, top, k), null};
        Rect ink = new Rect(), colonInk = new Rect();
        paint.getTextBounds(text, 0, text.length(), ink);
        paint.getTextBounds(":", 0, 1, colonInk);
        int pad = Math.round(40 * k);
        int x = Math.round(right - ink.width()) - pad, y = Math.round(top) - pad;
        float prefix = paint.measureText(text, 0, ci), advance = paint.measureText(text, ci, ci + 1);
        int from = Math.round(pad - ink.left + prefix), to = Math.round(pad - ink.left + prefix + advance);
        Glass digits = make(text, paint, ink, pad, x, y, k, from, to);
        // The colon alone: its text origin sits where it does in the whole text.
        float originX = x + pad - ink.left + prefix, baseline = y + pad - ink.top;
        int cx = Math.round(originX + colonInk.left) - pad, cy = Math.round(baseline + colonInk.top) - pad;
        return new Glass[] {digits, make(":", paint, colonInk, pad, cx, cy, k, 0, 0)};
    }

    /** One rendering; columns [clearFrom, clearTo) of the sharp rendering are left empty (none when clearTo <= clearFrom). */
    private static Glass make(String text, Paint paint, Rect ink, int pad, int atX, int atY, float k, int clearFrom, int clearTo) {
        int w = ink.width() + 2 * pad, h = ink.height() + 2 * pad;
        Bitmap cover = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cover);
        canvas.drawText(text, pad - ink.left, pad - ink.top, paint);
        if (clearTo > clearFrom) {
            Paint erase = new Paint();
            erase.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            canvas.drawRect(Math.max(0, clearFrom), 0, Math.min(w, clearTo), h, erase);
        }
        int[] px = new int[w * h];
        cover.getPixels(px, 0, w, 0, 0, w, h);
        int[] alpha = new int[px.length];
        for (int i = 0; i < px.length; i++) alpha[i] = (px[i] >>> 24) * 16;
        int[] height = blur(alpha, w, h, Math.max(2, Math.round(6 * k)), 3);
        int[] soft = blur(alpha, w, h, Math.max(3, Math.round(12 * k)), 3);
        float max = 1;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                float gx = height[i + 1] - height[i - 1], gy = height[i + w] - height[i - w];
                max = Math.max(max, (float) Math.sqrt(gx * gx + gy * gy));
            }
        }
        int[] slope = new int[px.length], shadow = new int[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float gx = x > 0 && x < w - 1 ? height[i + 1] - height[i - 1] : 0;
                float gy = y > 0 && y < h - 1 ? height[i + w] - height[i - w] : 0;
                int r = 128 + Math.round(127 * gx / max), g = 128 + Math.round(127 * gy / max);
                int b = Math.min(255, height[i] / 16);
                slope[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                shadow[i] = Math.min(255, soft[i] / 16) << 24;
            }
        }
        int x = atX, y = atY;
        return new Glass(text, Bitmap.createBitmap(slope, w, h, Bitmap.Config.ARGB_8888), cover,
                Bitmap.createBitmap(shadow, w, h, Bitmap.Config.ARGB_8888), x, y, pad, Math.max(1, ink.height()));
    }

    /** Box blur (passes of radius r in both directions); anything outside the image counts as empty. */
    private static int[] blur(int[] src, int w, int h, int r, int passes) {
        int[] a = src.clone(), b = new int[src.length];
        for (int pass = 0; pass < passes; pass++) {
            box(a, b, w, h, r, true);
            box(b, a, w, h, r, false);
        }
        return a;
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w, step = horizontal ? 1 : w, lineStep = horizontal ? w : 1;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep, sum = 0;
            for (int i = 0; i <= r && i < n; i++) sum += in[base + i * step];
            for (int i = 0; i < n; i++) {
                out[base + i * step] = (sum + div / 2) / div;
                int add = i + r + 1, sub = i - r;
                if (add < n) sum += in[base + add * step];
                if (sub >= 0) sum -= in[base + sub * step];
            }
        }
    }
}
