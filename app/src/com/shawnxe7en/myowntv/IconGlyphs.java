package com.shawnxe7en.myowntv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * An app's launcher icon, and its clear glyph (ClearIcon: the icon without its background, white) for the dock's glass
 * tiles. The glyph is worked out once per app version, in the background, and kept in files/glyphs (an empty file means
 * "won't split cleanly": the dock keeps its other drawing for that app).
 */
final class IconGlyphs {
    private IconGlyphs() {}

    static final int N = 192;

    /** The launcher icon full-bleed on an N x N square (an adaptive icon without its mask), or null. */
    static Bitmap icon(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            Drawable d = pm.getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            if (d instanceof AdaptiveIconDrawable) {
                // 108 dp layers with the visible 72 dp in the middle: draw them 1.5x so the visible part fills the square
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
                int pad = N / 4;
                for (Drawable layer : new Drawable[] {a.getBackground(), a.getForeground()}) {
                    if (layer == null) continue;
                    layer.setBounds(-pad, -pad, N + pad, N + pad);
                    layer.draw(canvas);
                }
            } else {
                d.setBounds(0, 0, N, N);
                d.draw(canvas);
            }
            return b;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /** The glyph (white, alpha from the cut), from the cache or, with `extract`, worked out now; null if there is none. */
    static Bitmap glyph(Context c, String pkg, Bitmap icon, boolean extract) {
        File file = cacheFile(c, pkg);
        if (file.exists()) return file.length() == 0 ? null : BitmapFactory.decodeFile(file.getPath());
        if (!extract || icon == null) return null;
        int[] px = new int[N * N];
        icon.getPixels(px, 0, N, 0, 0, N, N);
        int[] alpha = ClearIcon.cut(px, N, 0, 0, N, N);
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();
        if (alpha == null) {
            try {
                file.createNewFile();   // empty: refused, don't try again for this version
            } catch (IOException ignored) {
            }
            AppLog.w("clear icon: " + pkg + " won't split, keeping the logo");
            return null;
        }
        for (int i = 0; i < alpha.length; i++) alpha[i] = (alpha[i] << 24) | 0xFFFFFF;
        Bitmap out = Bitmap.createBitmap(alpha, N, N, Bitmap.Config.ARGB_8888);
        try (FileOutputStream os = new FileOutputStream(file)) {
            out.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (IOException e) {
            AppLog.w("clear icon: couldn't keep " + pkg + ": " + e.getMessage());
        }
        return out;
    }

    private static File cacheFile(Context c, String pkg) {
        long version = 0;
        try {
            version = c.getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return new File(new File(c.getFilesDir(), "glyphs"), pkg + "." + version + ".v1.png");
    }
}
