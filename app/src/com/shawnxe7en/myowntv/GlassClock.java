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
    private GlassDigits.Glass glass, colon, bound;   // the digits, the colon apart from them, and what the shader's inputs hold now
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
            GlassDigits.Glass[] parts = GlassDigits.makeParts(text, p, right, top, k);
            main.post(() -> {
                if (gen != generation) return;
                for (GlassDigits.Glass part : parts) {
                    if (part == null) continue;
                    part.slopeShader = GlassDigits.nearest(part.slope);
                    part.coverShader = GlassDigits.nearest(part.cover);
                }
                glass = parts[0];
                colon = parts[1];
                bound = null;
                builtFor = text;
                building = "";
                ready.run();
            });
        });
    }

    /**
     * The shadow and the glass digits over what is drawn so far; `copy` is the scene's blurred copy, `nowMs` the time for
     * the colon's heartbeat (Heartbeat).
     */
    void draw(Canvas c, Bitmap copy, int w, float k, long nowMs) {
        GlassDigits.Glass g = glass, cg = colon;
        if (g == null || shader == null || copy == null) return;
        if (copy != boundCopy) {
            boundCopy = copy;
            BitmapShader bs = new BitmapShader(copy, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setInputShader("imageA", bs);
            shader.setInputShader("imageB", bs);
        }
        drawPart(c, g, g, copy, w, k);
        if (cg == null) return;
        float s = Heartbeat.scale(nowMs);
        c.save();
        c.scale(s, s, cg.x + cg.cover.getWidth() / 2f, cg.y + cg.cover.getHeight() / 2f);
        drawPart(c, cg, g, copy, w, k);
        c.restore();
    }

    /** One piece (the digits, or the colon), shaded as the digits are: `ref` is the digits' glass (the same ink height for both). */
    private void drawPart(Canvas c, GlassDigits.Glass g, GlassDigits.Glass ref, Bitmap copy, int w, float k) {
        if (bound != g) {
            shader.setInputShader("slope", g.slopeShader);
            shader.setInputShader("cover", g.coverShader);
            bound = g;
        }
        shadowPaint.setAlpha(140);
        c.drawBitmap(g.shadow, g.x, g.y + 6 * k, shadowPaint);
        float scale = (w / (float) copy.getWidth()) / Backdrop.PHOTO_FROST;   // the shader multiplies by PHOTO_FROST
        shader.setFloatUniform("placeA", 0, 0, scale * Backdrop.PHOTO_FROST);
        shader.setFloatUniform("placeB", 0, 0, scale * Backdrop.PHOTO_FROST);
        shader.setFloatUniform("mixB", 0);
        shader.setFloatUniform("origin", g.x, g.y);
        shader.setFloatUniform("ink", ref.y + ref.inkTop - g.y, ref.inkHeight);
        shader.setFloatUniform("refraction", 14 * k);
        shader.setFloatUniform("specular", 1);
        shader.setFloatUniform("alpha", 1);
        c.drawRect(g.x, g.y, g.x + g.cover.getWidth(), g.y + g.cover.getHeight(), paint);
    }

    /** Where the colon can be drawn at its biggest (with its shadow); false until the digits are built. For redrawing just that. */
    boolean colonBounds(Rect out, float k) {
        GlassDigits.Glass cg = colon;
        if (cg == null || glass == null) return false;
        float hw = cg.cover.getWidth() / 2f * Heartbeat.MAX_SCALE + 4 * k, hh = cg.cover.getHeight() / 2f * Heartbeat.MAX_SCALE + 10 * k;
        float cx = cg.x + cg.cover.getWidth() / 2f, cy = cg.y + cg.cover.getHeight() / 2f;
        out.set((int) (cx - hw), (int) (cy - hh), (int) Math.ceil(cx + hw), (int) Math.ceil(cy + hh));
        return true;
    }

    /** The digits are stale (another minute, another size): forget them so update() rebuilds. */
    void reset() {
        generation++;
        builtFor = building = "";
        glass = colon = bound = null;
        boundCopy = null;
    }

    void release() {
        reset();
        worker.shutdownNow();
    }
}
