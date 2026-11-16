package com.shawnxe7en.myowntv;

import android.graphics.Canvas;
import android.graphics.Paint;

/** The weather effects (rain, snow, storm, fog, darkening) and the holiday art, drawn over a scene; shared by the home screen and the screensaver. */
final class FxPainter {
    private final Paint dim = new Paint(), line = new Paint(Paint.ANTI_ALIAS_FLAG), flash = new Paint();
    private final float[] px = new float[WeatherFx.MAX_PARTICLES], py = new float[WeatherFx.MAX_PARTICLES],
            pv = new float[WeatherFx.MAX_PARTICLES], pts = new float[WeatherFx.MAX_PARTICLES * 4];
    private final java.util.Random rnd = new java.util.Random();
    private int kind, count;
    private HolidayArt art;
    private boolean decor;
    private long holidayDay = -1;
    private float dark;
    private long lastMs, flashAt = -1;
    private float flashLevel;

    FxPainter() {
        dim.setColor(0xFF000000);
        line.setStrokeCap(Paint.Cap.ROUND);
        flash.setColor(0xFFFFFFFF);
    }

    /** Reads the weather (WMO code, negative if unknown) and the two settings; true while something is drawn and so a frame is due. */
    boolean update(int weatherCode, boolean weatherFxOn, boolean holidaysOn) {
        long today = System.currentTimeMillis() / 3_600_000L;   // looked up again each hour
        if (today != holidayDay) {
            holidayDay = today;
            int holiday = Holidays.on(java.time.LocalDate.now());
            art = holiday == Holidays.NONE ? null : new HolidayArt(holiday);
        }
        decor = holidaysOn && art != null;
        int code = weatherFxOn ? weatherCode : -1;
        int k = WeatherFx.kind(code);
        if (k != kind || WeatherFx.particles(k, WeatherFx.intensity(code)) != count) {
            kind = k;
            float in = WeatherFx.intensity(code);
            count = WeatherFx.particles(k, in);
            dark = WeatherFx.darken(k, in);
            for (int i = 0; i < count; i++) {
                px[i] = rnd.nextFloat();
                py[i] = rnd.nextFloat();
                pv[i] = 0.6f + rnd.nextFloat() * 0.8f;
            }
        }
        return kind != WeatherFx.NONE || decor;
    }

    /** The darkening, the holiday art, the particles and the storm flash over a w x h canvas. */
    void draw(Canvas c, int w, int h, long now) {
        float dt = lastMs == 0 ? 0.033f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        dim.setAlpha(Math.round(255 * dark));
        c.drawRect(0, 0, w, h, dim);
        if (art != null && decor) art.draw(c, w, h, now, dt);
        if (count == 0) return;
        float k = w / 1920f;
        if (kind == WeatherFx.SNOW) {
            line.setColor(0xCCFFFFFF);
            line.setStrokeWidth(5 * k);
            for (int i = 0; i < count; i++) {
                py[i] += dt * 0.09f * pv[i];
                px[i] += dt * 0.012f * (float) Math.sin(now / 900.0 + i);
                if (py[i] > 1) { py[i] -= 1; px[i] = rnd.nextFloat(); }
                pts[2 * i] = (px[i] % 1) * w;
                pts[2 * i + 1] = py[i] * h;
            }
            c.drawPoints(pts, 0, count * 2, line);
        } else {
            line.setColor(0x88DCE8F5);
            line.setStrokeWidth(2.2f * k);
            float len = 38 * k;
            for (int i = 0; i < count; i++) {
                py[i] += dt * 1.1f * pv[i];
                if (py[i] > 1.05f) { py[i] -= 1.1f; px[i] = rnd.nextFloat(); }
                float x = px[i] * w, y = py[i] * h;
                pts[4 * i] = x;
                pts[4 * i + 1] = y;
                pts[4 * i + 2] = x - len * 0.18f;
                pts[4 * i + 3] = y + len;
            }
            c.drawLines(pts, 0, count * 4, line);
        }
        if (kind == WeatherFx.STORM) {   // a flash every 6 to 14 seconds, gone in a quarter of a second
            if (flashAt < 0) flashAt = now + 6000 + rnd.nextInt(8000);
            if (now >= flashAt) {
                flashLevel = 0.35f;
                flashAt = now + 6000 + rnd.nextInt(8000);
            }
            if (flashLevel > 0.01f) {
                flash.setAlpha(Math.round(255 * flashLevel));
                c.drawRect(0, 0, w, h, flash);
                flashLevel -= dt * 1.4f;
            }
        }
    }
}
