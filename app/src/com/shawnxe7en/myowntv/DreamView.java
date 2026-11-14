package com.shawnxe7en.myowntv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * The screensaver, in the same style as the home screen: one of the scenes (SceneLayer) full screen, the date and the
 * weather on one line, the big glass clock over the scenery (GlassClock) and the prices small at the bottom. With Scenes
 * off, or while the first scene loads, the big clock over the night aurora. Any key exits.
 */
final class DreamView extends View implements Choreographer.FrameCallback, HomeData.Listener {
    private static final double DRIFT_PERIOD_S = 600;
    private static final float SCENE_CLOCK_TOP = GlassClock.TOP;

    private final HomeData data;
    private final GlassClock glassClock;
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
    private Paint datePaint, clockPaint, weatherPaint, quotePaint, stalePaint;
    private String staleLine = "", staleFor = "";
    private int staleGeneration = -1;

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
        quotePaint = Ui.numbers(Ui.medium, 24 * k, 0x99FFFFFF);
        stalePaint = Ui.text(Ui.regular, 20 * k, 0x59FFFFFF);
        stalePaint.setTextAlign(Paint.Align.CENTER);
        for (Paint p : new Paint[] {datePaint, weatherPaint, quotePaint}) p.setShadowLayer(16 * k, 0, 2 * k, 0xA6000000);   // legible on a pale scene
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
        glassClock.update(clock, clockPaint, w, h, k, this::invalidate);
        glassClock.draw(c, scenes.glassCopy(), w, k);
        scenes.drawForeground(c);
        if (dirty) {
            record();
        }
        content.setTranslationX(0);
        content.setTranslationY(0);
        content.setAlpha(1);
        content.draw(c);
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

        // Prices near the bottom edge: small white logo + value, all on one line.
        float cap = Ui.cap(quotePaint), spacing = 56 * k, gap = 10 * k;
        int[] coins = HomeData.active();
        String[] values = new String[HomeData.coinCount()];
        float total = spacing * (values.length - 1);
        for (int i = 0; i < values.length; i++) {
            values[i] = HomeData.price(data.prices[coins[i]]);
            total += Glyphs.width(getContext(), HomeData.COINS[coins[i]], cap) + gap + quotePaint.measureText(values[i]);
        }
        float x = cx - total / 2, base = h - 92 * k;
        for (int i = 0; i < values.length; i++) {
            float gw = Glyphs.width(getContext(), HomeData.COINS[coins[i]], cap);
            Glyphs.coin(getContext(), c, HomeData.COINS[coins[i]], x + gw / 2, base - cap / 2, cap, 0x80FFFFFF);
            x += gw + gap;
            c.drawText(values[i], x, base, quotePaint);
            x += quotePaint.measureText(values[i]) + spacing;
        }
        String stale = HomeData.staleness(data.pricesAt, 5 * 60_000L);
        if (!stale.isEmpty() && values.length > 0) {
            if (!stale.equals(staleFor) || Ui.generation != staleGeneration) {
                staleLine = Ui.trf("prices from {ago}", "ago", stale);
                staleFor = stale;
                staleGeneration = Ui.generation;
            }
            c.drawText(staleLine, cx, base + 38 * k, stalePaint);
        }
    }
}
