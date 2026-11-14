package com.shawnxe7en.myowntv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The animated scenes behind the clock (Couch TV's five generated ones): a photo, a blurred copy that fills the clock's
 * glass digits, and for most a cut-out of the scenery drawn over the clock. One scene at a time is held, as GPU
 * bitmaps; the next is loaded in the background shortly before the switch and held only through the crossfade.
 * A scene that is missing or can't be decoded is skipped; with none usable, draw() returns false and the caller keeps
 * its own background. Everything but the background loading runs on the main thread.
 */
final class SceneLayer {
    static final long PRELOAD_MS = 6000;
    private final long periodMs = Prefs.sceneMinutes() * 60_000L;

    private static final class Entry {
        final int index;
        Bitmap photo, glass, cutout;
        boolean quick;   // a small stand-in photo, shown until the full scene has loaded
        int[] glassPx;   // the glass copy as GW x GH pixels (software), for the shelf's glass

        Entry(int index) {
            this.index = index;
        }

        void release() {
            for (Bitmap b : new Bitmap[] {photo, glass, cutout}) if (b != null) b.recycle();
            photo = glass = cutout = null;
        }
    }

    private final Context context;
    private final String[] names = SceneChoice.enabled(SceneChoice.ALL, Prefs.scenesOff());   // the scenes turned on
    private final SceneCycle cycle = new SceneCycle(names.length, periodMs);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private final boolean[] failed = new boolean[names.length];
    private Entry cur, next;
    private boolean fresh;       // a scene finished loading since the last draw
    private int loading = -1, generation;
    private int width = 1920, height = 1080, sample = 1;

    SceneLayer(Context context) {
        this.context = context.getApplicationContext();
    }

    /** The canvas the scenes are drawn to; a small one (720p UI) decodes the photos at half size to save memory. */
    void attach(int width, int height) {
        this.width = width;
        this.height = height;
        sample = width <= 1280 ? 2 : 1;
        dst.set(0, 0, width, height);
    }

    /** Draws the scene photo (crossfading into the next). False if no scene is usable. */
    boolean draw(Canvas c, long nowMs) {
        long elapsed = System.currentTimeMillis();   // by the wall clock: the same scene at the same time, not the first one at every start
        fresh = false;
        int idx = cycle.indexAt(elapsed);
        if (cur == null || cur.index != idx) {
            if (next != null && next.index == idx) {
                if (cur != null) cur.release();
                cur = next;
                next = null;
            } else {
                request(idx);
            }
        }
        long into = elapsed % periodMs;
        if (next == null && into > periodMs - PRELOAD_MS && cur != null) request(cycle.nextIndex(cur.index));
        if (cur == null || cur.photo == null) return false;
        c.drawBitmap(cur.photo, null, dst, null);
        float fade = cycle.fade(elapsed);
        if (fade > 0 && next != null && next.photo != null) {
            paint.setAlpha(Math.round(255 * fade));
            c.drawBitmap(next.photo, null, dst, paint);
        }
        return true;
    }

    /** True while the picture changes: a crossfade, a scene due, or one just loaded. For callers that redraw on demand. */
    boolean needsFrame(long nowMs) {
        if (fresh || cur == null || cur.photo == null) return true;
        long elapsed = System.currentTimeMillis();
        return cur.index != cycle.indexAt(elapsed) || cycle.fade(elapsed) > 0;
    }

    /** Mid-crossfade: callers redraw every frame. */
    boolean fading(long nowMs) {
        return cycle.fade(System.currentTimeMillis()) > 0;
    }

    /** The scenery cut-out, to be drawn after the clock (nothing for a scene without one). */
    void drawForeground(Canvas c) {
        if (cur == null || cur.cutout == null) return;
        c.drawBitmap(cur.cutout, null, dst, null);
    }

    /** The current scene's blurred copy as GW x GH pixels (the shelf's glass), or null; the same array while the scene holds. */
    int[] glassPixels() {
        return cur == null ? null : cur.glassPx;
    }

    /** The current scene's blurred copy (the clock's glass fill), or null. */
    Bitmap glassCopy() {
        return cur == null ? null : cur.glass;
    }

    /** Memory is short: drop what is held; the next draw() loads again. */
    void trim() {
        generation++;
        loading = -1;
        if (cur != null) cur.release();
        if (next != null) next.release();
        cur = next = null;
    }

    /** The owner is going away: drop the bitmaps and stop the loader. */
    void release() {
        trim();
        worker.shutdownNow();
    }

    private void request(int index) {
        if (loading == index || failed[index]) {
            if (failed[index] && cur == null) request(cycle.nextIndex(index));   // skip a broken scene
            return;
        }
        if (loading >= 0) return;
        loading = index;
        final int gen = generation;
        final boolean first = cur == null;
        worker.execute(() -> {
            if (first) {   // a small decode is quick: the screen has a picture while the full one loads
                Entry q = new Entry(index);
                q.quick = true;
                q.photo = decode(names[index] + ".jpg", 8 * sample);
                main.post(() -> {
                    if (q.photo != null && gen == generation && cur == null) {
                        cur = q;
                        fresh = true;
                    } else {
                        q.release();
                    }
                });
            }
            Entry e = load(index);
            main.post(() -> {
                if (gen != generation) {
                    if (e != null) e.release();
                    return;
                }
                loading = -1;
                fresh = true;
                if (e == null) {
                    failed[index] = true;
                } else if (cur == null || cur.quick && cur.index == index) {
                    if (cur != null) cur.release();
                    cur = e;
                } else {
                    if (next != null) next.release();
                    next = e;
                }
            });
        });
    }

    private Entry load(int index) {
        Entry e = new Entry(index);
        e.photo = decode(names[index] + ".jpg", sample);
        if (e.photo == null) return null;
        e.glass = decode(names[index] + "-glass.jpg", 2 * sample);
        e.glassPx = pixels(names[index] + "-glass.jpg");
        e.cutout = decode(names[index] + "-fg.webp", sample);   // null for a scene without one
        return e;
    }

    /** The glass copy at Backdrop.GW x GH (1920 / 4 x 1080 / 4) as pixels: what Scene.setGlassImage takes. */
    private int[] pixels(String file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inSampleSize = 4;
        try (InputStream in = context.getAssets().open("scenes/" + file)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b == null) return null;
            if (b.getWidth() != Backdrop.GW || b.getHeight() != Backdrop.GH) {
                Bitmap scaled = Bitmap.createScaledBitmap(b, Backdrop.GW, Backdrop.GH, true);
                b.recycle();
                b = scaled;
            }
            int[] px = new int[Backdrop.GW * Backdrop.GH];
            b.getPixels(px, 0, Backdrop.GW, 0, 0, Backdrop.GW, Backdrop.GH);
            b.recycle();
            return px;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private Bitmap decode(String file, int sampleSize) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.HARDWARE;
        o.inSampleSize = sampleSize;
        try (InputStream in = context.getAssets().open("scenes/" + file)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (IOException | RuntimeException ex) {
            if (!file.endsWith("-fg.webp")) AppLog.w("scene " + file + ": " + ex.getMessage());
            return null;
        }
    }

    static long now() {
        return SystemClock.uptimeMillis();
    }
}
