package com.shawnxe7en.myowntv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.view.Choreographer;
import android.view.View;

/**
 * The screensaver, in the same style as the home screen: one of the scenes (SceneLayer) full screen, the date and the
 * weather on one line, the big glass clock over the scenery (GlassClock), the weather effects and holiday art of the home
 * screen (FxPainter) and a quote from a book or poem small at the bottom (Sayings). With Scenes off, or while the first
 * scene loads, the big clock over the night aurora. Any key exits.
 */
final class DreamView extends View implements Choreographer.FrameCallback, HomeData.Listener {
    private static final double DRIFT_PERIOD_S = 600;
    private static final float SCENE_CLOCK_TOP = GlassClock.TOP;
    private static final long FADE_MS = 600;   // a quote fades out, then the next fades in

    private final HomeData data;
    private final GlassClock glassClock;
    private final FxPainter fx = new FxPainter();
    private final Random rnd = new Random();
    private final Scene scene = new Scene(false);                // the night aurora, when no scene is on screen
    private final Recording content = Recording.create("clock");   // date, weather, prices (and the aurora's clock)
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float timeOffset = (float) (Math.random() * 300);
    private SceneLayer scenes;      // null with Scenes off
    private boolean running, dirty = true, sceneShown;
    private int w, h, attachedW, attachedH;
    private float k = 1;
    private long startedAt, lastFrame, lastSky, minute = -1;
    private String clock = "", date = "";
    private Paint datePaint, clockPaint, weatherPaint, sayPaint, authorPaint;
    private Sayings sayings;
    private final NowPlaying.Watch nowPlaying = new NowPlaying.Watch(getContext(), this::invalidate);
    private Paint nowPaint;
    private Sayings.Saying saying;
    private final List<String> sayingLines = new ArrayList<>();
    private long sayingChangeAt, sayingFadeFrom = -1;
    private boolean sayingSwapped;
    private int shadowAlpha = -1;   // the quote's shadow as last set: it fades with the text

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            data.refreshIfStale();
            dirty = true;
            handler.postDelayed(this, 60_000);
        }
    };

    DreamView(Context c) {
        super(c);
        data = HomeData.get(c);
        glassClock = new GlassClock(c);
        Shaders.dream(scene.palette);
        scene.setMotion(0.4f, 0.32f);
        scene.applyPalette();
        content.setUseCompositingLayer(true, null);
    }

    void start() {
        if (running) return;
        running = true;
        startedAt = SystemClock.uptimeMillis();
        lastFrame = 0;
        scenes = Prefs.scenes() ? new SceneLayer(getContext()) : null;
        sceneShown = false;
        glassClock.reset();
        loadSayings();
        nowPlaying.start();
        data.addListener(this);
        data.refreshIfStale();
        handler.postDelayed(tick, 60_000);
        Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    void stop() {
        if (!running) return;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        handler.removeCallbacks(tick);
        data.removeListener(this);
        nowPlaying.stop();
        if (scenes != null) scenes.release();
        scenes = null;
        sceneShown = false;
        glassClock.reset();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        glassClock.release();
    }

    @Override public void onData() {
        dirty = true;
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;
        scene.setSize(w, h);
        content.setPosition(0, 0, w, h);
        datePaint = Ui.text(Ui.semibold, 36 * k, 0xD9FFFFFF);
        datePaint.setTextAlign(Paint.Align.CENTER);
        clockPaint = Ui.numbers(Ui.antonio, GlassClock.SIZE * k, 0xFFFFFFFF);
        clockPaint.setTextAlign(Paint.Align.CENTER);
        clockPaint.setLetterSpacing(-0.01f);
        weatherPaint = Ui.numbers(Ui.medium, 30 * k, 0xC7FFFFFF);
        sayPaint = Ui.text(Ui.cursive, 42 * k, 0xF2FFFFFF);
        sayPaint.setTextAlign(Paint.Align.CENTER);
        nowPaint = Ui.text(Ui.medium, 32 * k, 0xF2FFFFFF);
        nowPaint.setTextAlign(Paint.Align.LEFT);
        authorPaint = Ui.text(Ui.regular, 22 * k, 0xB3FFFFFF);
        authorPaint.setTextAlign(Paint.Align.CENTER);
        for (Paint p : new Paint[] {datePaint, weatherPaint, sayPaint, authorPaint, nowPaint}) p.setShadowLayer(16 * k, 0, 2 * k, 0xA6000000);   // legible on a pale scene
        layoutSaying();
        Symbols.warmWeather(getContext(), 32 * k);
        glassClock.reset();
        dirty = true;
    }

    // MARK: - Frames: 20 per second, locked to every 3rd display frame (60 Hz)

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (frameTimeNanos - lastFrame >= 45_000_000L) {
            lastFrame = frameTimeNanos;
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (w == 0) return;
        long up = SystemClock.uptimeMillis(), now = System.currentTimeMillis();
        if (now / 60_000 != minute) {
            minute = now / 60_000;
            clock = Ui.clock(now);
            date = Ui.date(now);
            dirty = true;
        }
        if (scenes != null && drawScene(canvas, up)) return;
        sceneShown = false;
        drawFallback(canvas, up);
    }

    /** The scene as the background, the big glass clock, the scenery over it, then the date, weather and prices. */
    private boolean drawScene(Canvas c, long up) {
        if (w != attachedW || h != attachedH) {
            scenes.attach(w, h);
            attachedW = w;
            attachedH = h;
            glassClock.reset();
        }
        boolean shown = scenes.draw(c, up);
        if (shown != sceneShown) {
            sceneShown = shown;
            dirty = true;
        }
        if (!shown) return false;
        glassClock.update(clock, clockPaint, w, h, k, () -> {   // the glass digits are ready: the cached text layer must stop drawing the flat ones
            dirty = true;
            invalidate();
        });
        glassClock.draw(c, scenes.glassCopy(), w, k, up);
        scenes.drawForeground(c);
        HomeData.Weather wx = data.weather;
        if (fx.update(wx != null && !Double.isNaN(wx.temp) ? wx.code : -1, Prefs.weatherFx(), Prefs.holidays())) fx.draw(c, w, h, up);
        if (dirty) {
            record();
        }
        content.setTranslationX(0);
        content.setTranslationY(0);
        content.setAlpha(1);
        content.draw(c);
        drawSaying(c, up);
        return true;
    }

    /** Scenes off, or none loaded yet: the big clock over the night aurora, drifting a little. */
    private void drawFallback(Canvas canvas, long up) {
        float t = (up - startedAt) / 1000f;
        if (up - lastSky >= 100) {
            lastSky = up;
            scene.advance(timeOffset + t);
        }
        scene.drawBackground(canvas);
        if (dirty) record();
        double phase = 2 * Math.PI * t / DRIFT_PERIOD_S;
        content.setTranslationX((float) (40 * k * Math.sin(phase)));
        content.setTranslationY((float) (26 * k * Math.sin(2 * phase + 0.7)));
        content.setAlpha(1);
        content.draw(canvas);
        drawSaying(canvas, up);
    }

    private void loadSayings() {
        List<Sayings.Saying> all;
        try (InputStream in = getContext().getAssets().open("sayings.txt")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            all = Sayings.parse(out.toString("UTF-8"));
        } catch (IOException e) {
            all = new ArrayList<>();
        }
        if (sayings == null) sayings = new Sayings(all, rnd);   // kept across start/stop: a restart never repeats the last quote
        saying = sayings.next();
        sayingChangeAt = SystemClock.uptimeMillis() + Sayings.INTERVAL_MS;
        sayingFadeFrom = -1;
        sayingSwapped = false;
        layoutSaying();
    }

    /** The current quote wrapped to 70% of the width. */
    private void layoutSaying() {
        sayingLines.clear();
        if (saying == null || sayPaint == null) return;
        float max = w * 0.7f;
        String t = saying.text;
        while (!t.isEmpty()) {
            int n = Math.max(1, sayPaint.breakText(t, true, max, null));
            if (n < t.length()) {
                int sp = t.lastIndexOf(' ', n);
                if (sp > 0) n = sp;
            }
            sayingLines.add(t.substring(0, n).trim());
            t = t.substring(n).trim();
        }
    }

    /** The quote and its author near the bottom edge; every 2 minutes it fades out and the next fades in. */
    private void drawSaying(Canvas c, long up) {
        String np = nowPaint == null ? null : NowPlaying.display(getContext(), nowPlaying);
        if (np != null) {   // something is playing (or was, today): it takes the quote's place
            drawNowPlaying(c, up, np);
            return;
        }
        if (!Prefs.sayings() || saying == null || sayingLines.isEmpty() || sayPaint == null) return;
        float alpha = 1;
        if (sayingFadeFrom < 0 && up >= sayingChangeAt) {
            sayingFadeFrom = up;
            sayingSwapped = false;
        }
        if (sayingFadeFrom >= 0) {
            long e = up - sayingFadeFrom;
            if (e < FADE_MS) {
                alpha = 1 - e / (float) FADE_MS;
            } else {
                if (!sayingSwapped) {
                    saying = sayings.next();
                    layoutSaying();
                    sayingSwapped = true;
                    if (sayingLines.isEmpty()) return;
                }
                if (e < 2 * FADE_MS) {
                    alpha = (e - FADE_MS) / (float) FADE_MS;
                } else {
                    sayingFadeFrom = -1;
                    sayingChangeAt = up + Sayings.INTERVAL_MS;
                }
            }
        }
        sayPaint.setAlpha(Math.round(0xF2 * alpha));
        authorPaint.setAlpha(Math.round(0xB3 * alpha));
        int shadow = Math.round(0xA6 * alpha);   // a shadow colour with its own alpha ignores the paint's: fade it too
        if (shadow != shadowAlpha) {
            shadowAlpha = shadow;
            for (Paint p : new Paint[] {sayPaint, authorPaint}) p.setShadowLayer(16 * k, 0, 2 * k, shadow << 24);
        }
        float cx = w / 2f, lineH = 54 * k, authorBase = h - 92 * k;
        float first = authorBase - 56 * k - (sayingLines.size() - 1) * lineH;
        for (int i = 0; i < sayingLines.size(); i++) c.drawText(sayingLines.get(i), cx, first + i * lineH, sayPaint);
        c.drawText(saying.author, cx, authorBase, authorPaint);
    }

    /** The level bars and "Title · Artist · App", centred where the quote goes; dimmed when it's only the last played. */
    private void drawNowPlaying(Canvas c, long up, String text) {
        boolean live = nowPlaying.line() != null;
        int alpha = live ? 0xF2 : 0x99;
        nowPaint.setAlpha(alpha);
        String fit = android.text.TextUtils.ellipsize(text, new android.text.TextPaint(nowPaint), w * 0.7f,
                android.text.TextUtils.TruncateAt.END).toString();
        float cap = Ui.cap(nowPaint), barsW = cap * 0.24f * 3 + cap * 0.24f * 0.7f * 2, gap = 22 * k;
        float x = (w - (barsW + gap + nowPaint.measureText(fit))) / 2, base = h - 92 * k;
        nowPaint.setAlpha(alpha);
        NowPlaying.bars(c, x, base, cap, nowPaint, nowPlaying.playing(), up);
        c.drawText(fit, x + barsW + gap, base, nowPaint);
    }

    private void record() {
        Canvas rc = content.beginRecording(w, h);
        try {
            drawContent(rc);
        } finally {
            content.endRecording();
        }
        dirty = false;
    }

    private void drawContent(Canvas c) {
        float cx = w / 2f;
        boolean sl = sceneShown;   // over a scene: the date and the weather share one line above the big clock
        float clockCap = Ui.cap(clockPaint);
        float clockBase = sl ? SCENE_CLOCK_TOP * k + clockCap : h * 0.5f + clockCap / 2 - 10 * k;
        float dateBase = clockBase - clockCap - 34 * k;
        HomeData.Weather wx = data.weather;
        boolean showWx = !Double.isNaN(wx.temp) && Prefs.weather();
        String symbol = showWx ? Symbols.weather(wx.code, wx.isDay) : "", now = showWx ? HomeData.degrees(wx.temp) : "";
        float sw = showWx ? Symbols.width(getContext(), symbol, 32 * k) : 0;
        float width = showWx ? sw + 12 * k + weatherPaint.measureText(now) : 0;
        float dateW = datePaint.measureText(date), lineW = dateW + 36 * k + width;
        c.drawText(date, sl && showWx ? cx - lineW / 2 + dateW / 2 : cx, dateBase, datePaint);
        if (!(sl && glassClock.ready())) c.drawText(clock, cx, clockBase, clockPaint);

        if (showWx) {   // symbol and temperature: on the date's line over a scene, under the clock over the aurora
            float base = sl ? dateBase : clockBase + 76 * k;
            float x = sl ? cx - lineW / 2 + dateW + 36 * k : cx - width / 2;
            Symbols.draw(getContext(), c, symbol, x + sw / 2, base - Ui.halfCap(weatherPaint), 32 * k, 0xC7FFFFFF);
            c.drawText(now, x + sw + 12 * k, base, weatherPaint);
        }
    }
}
