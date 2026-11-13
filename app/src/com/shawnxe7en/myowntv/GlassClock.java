package com.shawnxe7en.myowntv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Couch TV's big glass clock over a scene (the same shader and digit bitmaps as the screensaver's): centered, top at
 * 172 px at 1080p. The digits are built in the background once a minute; draw() shows the scene's blurred copy through
 * them. Without AGSL (Android 8 to 12) available() is false and the caller keeps its flat clock.
 */
final class GlassClock {
    static final float TOP = 172, SIZE = 330;   // 1080p px; Couch TV's .gclock

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Paint paint = new Paint();
    private final Paint shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private RuntimeShader shader;
    private GlassDigits.Glass glass;
    private String builtFor = "", building = "";
    private Bitmap boundCopy;
    private int generation;

    GlassClock(Context c) {
        try {
            shader = new RuntimeShader(Shaders.GLASS_TEXT);
            paint.setShader(shader);
        } catch (RuntimeException | LinkageError e) {
            AppLog.w("glass clock unavailable: " + e.getMessage());
            shader = null;
        }
    }

    boolean available() {
        return shader != null;
    }

    /** The digits are built and ready to draw. */
    boolean ready() {
        return glass != null;
    }

    /** Builds the digits for `text` if they aren't (or aren't being) built; `ready` runs on the main thread when they are. */
    void update(String text, Paint font, int w, int h, float k, Runnable ready) {
        if (shader == null || text.isEmpty() || text.equals(builtFor) || text.equals(building)) return;
        building = text;
        final Paint p = new Paint(font);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(SIZE * k);
        p.setColor(0xFFFFFFFF);
        p.clearShadowLayer();
        Rect ink = new Rect();
        p.getTextBounds(text, 0, text.length(), ink);
        final float right = w / 2f + ink.width() / 2f, top = TOP * k;
        final int gen = generation;
        worker.execute(() -> {
            GlassDigits.Glass g = GlassDigits.makeGlass(text, p, right, top, k);
            main.post(() -> {
                if (gen != generation) return;
                glass = g;
                builtFor = text;
                building = "";
                shader.setInputShader("slope", GlassDigits.nearest(g.slope));
                shader.setInputShader("cover", GlassDigits.nearest(g.cover));
                ready.run();
            });
        });
    }

    /** The shadow and the glass digits over what is drawn so far; `copy` is the scene's blurred copy. */
    void draw(Canvas c, Bitmap copy, int w, float k) {
        GlassDigits.Glass g = glass;
        if (g == null || shader == null || copy == null) return;
        if (copy != boundCopy) {
            boundCopy = copy;
            BitmapShader bs = new BitmapShader(copy, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setInputShader("imageA", bs);
            shader.setInputShader("imageB", bs);
        }
        shadowPaint.setAlpha(140);
        c.drawBitmap(g.shadow, g.x, g.y + 6 * k, shadowPaint);
        float scale = (w / (float) copy.getWidth()) / Backdrop.PHOTO_FROST;   // the shader multiplies by PHOTO_FROST
        shader.setFloatUniform("placeA", 0, 0, scale * Backdrop.PHOTO_FROST);
        shader.setFloatUniform("placeB", 0, 0, scale * Backdrop.PHOTO_FROST);
        shader.setFloatUniform("mixB", 0);
        shader.setFloatUniform("origin", g.x, g.y);
        shader.setFloatUniform("ink", g.inkTop, g.inkHeight);
        shader.setFloatUniform("refraction", 14 * k);
        shader.setFloatUniform("specular", 1);
        shader.setFloatUniform("alpha", 1);
        c.drawRect(g.x, g.y, g.x + g.cover.getWidth(), g.y + g.cover.getHeight(), paint);
    }

    /** The digits are stale (another minute, another size): forget them so update() rebuilds. */
    void reset() {
        generation++;
        builtFor = building = "";
        glass = null;
        boundCopy = null;
    }

    void release() {
        reset();
        worker.shutdownNow();
    }
}
