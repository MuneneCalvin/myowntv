package com.shawnxe7en.myowntv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
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
    private Paint nowPaint, nowSub;
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
        nowPaint = Ui.text(Ui.semibold, 28 * k, 0xF2FFFFFF);
        nowPaint.setTextAlign(Paint.Align.LEFT);
        nowSub = Ui.text(Ui.regular, 21 * k, 0xC0FFFFFF);
        nowSub.setTextAlign(Paint.Align.LEFT);
        authorPaint = Ui.text(Ui.regular, 22 * k, 0xB3FFFFFF);
        authorPaint.setTextAlign(Paint.Align.CENTER);
        for (Paint p : new Paint[] {datePaint, weatherPaint, sayPaint, authorPaint, nowPaint, nowSub}) p.setShadowLayer(16 * k, 0, 2 * k, 0xA6000000);   // legible on a pale scene
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
        if (nowPaint != null && Prefs.nowPlaying() && (nowPlaying.track() != null || NowPlaying.last(getContext()) != null)) {
            drawNowPlaying(c, up);   // something is playing (or was, today): its pill at the top right, beside the quote
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

    private final Paint cardFill = new Paint(Paint.ANTI_ALIAS_FLAG), cardLine = new Paint(Paint.ANTI_ALIAS_FLAG),
            cardImage = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final android.graphics.RectF cardRect = new android.graphics.RectF(), cardArt = new android.graphics.RectF(),
            cardTmp = new android.graphics.RectF();
    private final android.graphics.Path cardPath = new android.graphics.Path();
    private final android.graphics.Rect cardSrc = new android.graphics.Rect();
    private String cardFitFor = "", cardTitleFit = "", cardSubFit = "";
    private float cardFitRoom;
    private Bitmap cardIcon;
    private String cardIconPkg = "";

    /** The app's own icon, decoded once, for a track that has no art. */
    private Bitmap appIcon(String pkg) {
        if (pkg == null) return null;
        if (!pkg.equals(cardIconPkg)) {
            cardIconPkg = pkg;
            cardIcon = null;
            try {
                android.graphics.drawable.Drawable d = getContext().getPackageManager().getApplicationIcon(pkg);
                int side = Math.round(120 * k);
                Bitmap b = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, side, side);
                d.draw(new Canvas(b));
                cardIcon = b;
            } catch (Exception e) {
                cardIcon = null;
            }
        }
        return cardIcon;
    }

    private android.graphics.LinearGradient cardSheen;
    private float cardSheenTop = -1;

    /**
     * What is playing, as a compact glass pill at the top right (the quote stays at the bottom): the art (or the app's
     * icon), the title, the artist and app, and a thin line for how far along. No controls here: any key wakes the screen.
     * Paused, or only the last played: dimmer, with a play mark.
     */
    private void drawNowPlaying(Canvas c, long up) {
        NowPlaying.Track t = nowPlaying.track();
        String title, sub, pkg = t == null ? NowPlaying.lastPackage(getContext()) : t.pkg;
        boolean live = t != null && t.playing;
        if (t != null) {
            title = t.title.isEmpty() ? t.artist : t.title;
            sub = t.title.isEmpty() ? t.app : (t.artist.isEmpty() ? t.app : t.artist + " \u00b7 " + t.app);
        } else {
            title = NowPlaying.last(getContext());
            sub = Ui.tr("Last played");
        }
        float cw = 560 * k, ch = 104 * k, left = w - 96 * k - cw, top = 158 * k, pad = 16 * k, r = 34 * k;
        cardRect.set(left, top, left + cw, top + ch);
        float art = ch - 2 * pad;
        cardArt.set(left + pad, top + pad, left + pad + art, top + pad + art);
        float tx = cardArt.right + 18 * k, room = left + cw - pad - tx;
        if (!(title + "\n" + sub).equals(cardFitFor) || room != cardFitRoom) {
            cardTitleFit = android.text.TextUtils.ellipsize(title, new android.text.TextPaint(nowPaint), room,
                    android.text.TextUtils.TruncateAt.END).toString();
            cardSubFit = android.text.TextUtils.ellipsize(sub, new android.text.TextPaint(nowSub), room,
                    android.text.TextUtils.TruncateAt.END).toString();
            cardFitFor = title + "\n" + sub;
            cardFitRoom = room;
        }
        float dim = live ? 1f : 0.62f;
        // Glass over the scene: the art's colour (slate without art), translucent, with a soft light along the top.
        int tint = t == null || t.tint == 0 ? 0xFF3A3F4B : t.tint;
        cardFill.setShader(null);
        cardFill.setColor(Ui.alpha(tint, 0.58f));
        c.drawRoundRect(cardRect, r, r, cardFill);
        if (cardSheen == null || cardSheenTop != top) {
            cardSheen = new android.graphics.LinearGradient(0, top, 0, top + ch, 0x40FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP);
            cardSheenTop = top;
        }
        cardFill.setShader(cardSheen);
        c.drawRoundRect(cardRect, r, r, cardFill);
        cardFill.setShader(null);
        cardLine.setStyle(Paint.Style.STROKE);
        cardLine.setStrokeWidth(1.5f * k);
        cardLine.setColor(0x47FFFFFF);
        c.drawRoundRect(cardRect, r, r, cardLine);
        Bitmap img = t == null ? null : t.art;
        cardPath.reset();
        cardPath.addRoundRect(cardArt, 20 * k, 20 * k, android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(cardPath);
        cardFill.setColor(0xFF2C2C34);
        c.drawRect(cardArt, cardFill);
        Bitmap shown = img != null ? img : appIcon(pkg);
        if (shown != null) {
            int side = Math.min(shown.getWidth(), shown.getHeight());
            cardSrc.set((shown.getWidth() - side) / 2, (shown.getHeight() - side) / 2, (shown.getWidth() + side) / 2, (shown.getHeight() + side) / 2);
            cardImage.setAlpha(Math.round(255 * dim));
            cardTmp.set(cardArt);
            if (img == null) cardTmp.inset(art * 0.14f, art * 0.14f);
            c.drawBitmap(shown, cardSrc, cardTmp, cardImage);
        }
        if (!live) {   // a play mark on the art
            float cx = cardArt.centerX(), cy = cardArt.centerY(), s = art * 0.17f;
            cardFill.setColor(0xE6FFFFFF);
            cardPath.reset();
            cardPath.moveTo(cx - s * 0.7f, cy - s);
            cardPath.lineTo(cx - s * 0.7f, cy + s);
            cardPath.lineTo(cx + s, cy);
            cardPath.close();
            c.drawPath(cardPath, cardFill);
        }
        c.restore();
        nowPaint.setAlpha(Math.round(0xF2 * dim));
        nowSub.setAlpha(Math.round(0xC0 * dim));
        c.drawText(cardTitleFit, tx, top + 44 * k, nowPaint);
        c.drawText(cardSubFit, tx, top + 76 * k, nowSub);
        float p = t == null ? -1 : NowPlayingControls.progress(t.position, t.duration, t.updatedAt, t.speed,
                SystemClock.elapsedRealtime(), t.playing);
        if (p >= 0) {
            float ly = top + ch - 9 * k, lx = left + pad + 4 * k, lw = cw - 2 * pad - 8 * k, lh = 3 * k;
            cardTmp.set(lx, ly - lh / 2, lx + lw, ly + lh / 2);
            cardFill.setColor(0x2EFFFFFF);
            c.drawRoundRect(cardTmp, lh / 2, lh / 2, cardFill);
            cardTmp.right = lx + lw * p;
            cardFill.setColor(Ui.alpha(0xFFFFFFFF, 0.85f * dim));
            if (cardTmp.width() > 0) c.drawRoundRect(cardTmp, lh / 2, lh / 2, cardFill);
        }
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
