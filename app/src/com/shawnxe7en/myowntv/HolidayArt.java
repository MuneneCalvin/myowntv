package com.shawnxe7en.myowntv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Random;

/** The home screen's holiday decorations (Holidays): a tint and a few dozen moving shapes, drawn over the scene. */
final class HolidayArt {
    private static final int N = 40;
    private static final int[] CONFETTI = {0xFFFFD54A, 0xFFFFFFFF, 0xFFFF6FA5, 0xFF5CD6FF, 0xFFB58CFF};
    private static final int[] PASTEL = {0xFFFFC1D9, 0xFFC9F0B8, 0xFFBEE3FF, 0xFFFFF1A8, 0xFFE2C8FF};
    private static final int[] BULBS = {0xFFFF4D4D, 0xFFFFC94A, 0xFF58D68D, 0xFF5DADE2};

    private final int holiday;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), tint = new Paint();
    private final Random rnd = new Random();
    private final float[] x = new float[N], y = new float[N], v = new float[N];
    private final Path path = new Path(), heart = new Path(), bat = new Path(), crescent = new Path();
    private final RectF oval = new RectF();

    HolidayArt(int holiday) {
        this.holiday = holiday;
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            v[i] = 0.6f + rnd.nextFloat() * 0.8f;
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        heart.moveTo(0, 0.9f);
        heart.cubicTo(-1.5f, -0.1f, -0.6f, -1.1f, 0, -0.4f);
        heart.cubicTo(0.6f, -1.1f, 1.5f, -0.1f, 0, 0.9f);
        bat.moveTo(0, 0);
        bat.cubicTo(-0.4f, -0.9f, -1.4f, -0.8f, -2f, -0.2f);
        bat.cubicTo(-1.5f, -0.3f, -1.2f, 0.2f, -1f, 0.5f);
        bat.cubicTo(-0.7f, 0.1f, -0.3f, 0.2f, 0, 0.7f);
        bat.cubicTo(0.3f, 0.2f, 0.7f, 0.1f, 1f, 0.5f);
        bat.cubicTo(1.2f, 0.2f, 1.5f, -0.3f, 2f, -0.2f);
        bat.cubicTo(1.4f, -0.8f, 0.4f, -0.9f, 0, 0);
        Path a = new Path(), b = new Path();
        a.addCircle(0, 0, 1, Path.Direction.CW);
        b.addCircle(0.42f, -0.18f, 0.86f, Path.Direction.CW);
        crescent.op(a, b, Path.Op.DIFFERENCE);
    }

    void draw(Canvas c, int w, int h, long now, float dt) {
        float k = w / 1920f;
        switch (holiday) {
            case Holidays.NEW_YEAR: wash(c, w, h, 0x10FFC857); fall(c, w, h, k, dt, now, CONFETTI, 12 * k, 0.16f, 34); break;
            case Holidays.VALENTINE: wash(c, w, h, 0x14FF4F7B); hearts(c, w, h, k, dt, now); break;
            case Holidays.EASTER: wash(c, w, h, 0x12BFE8B0); eggs(c, w, h, k, dt, now); break;
            case Holidays.HALLOWEEN: wash(c, w, h, 0x26FF6A00); bats(c, w, h, k, dt, now); break;
            case Holidays.CHRISTMAS: lights(c, w, k, now); fall(c, w, h, k, dt, now, new int[] {0xDDFFFFFF}, 6 * k, 0.09f, 26); break;
            case Holidays.RAMADAN: wash(c, w, h, 0x24102A6B); moon(c, w, h, k, now); break;
            case Holidays.DIWALI: wash(c, w, h, 0x1AFF9A1F); sparks(c, w, h, k, dt, now); break;
            default: break;
        }
    }

    private void wash(Canvas c, int w, int h, int argb) {
        tint.setColor(argb);
        c.drawRect(0, 0, w, h, tint);
    }

    /** Pieces falling and swaying: confetti, or the snow of Christmas. */
    private void fall(Canvas c, int w, int h, float k, float dt, long now, int[] colors, float size, float speed, int n) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size);
        for (int i = 0; i < n; i++) {
            y[i] += dt * speed * v[i];
            if (y[i] > 1.04f) { y[i] -= 1.08f; x[i] = rnd.nextFloat(); }
            float px = (x[i] + 0.012f * (float) Math.sin(now / 800.0 + i * 1.7)) * w, py = y[i] * h;
            paint.setColor(colors[i % colors.length]);
            if (colors.length == 1) c.drawPoint(px, py, paint);
            else c.drawLine(px, py, px + size * 0.5f, py + size * 1.6f * (float) Math.cos(now / 400.0 + i), paint);
        }
    }

    private void hearts(Canvas c, int w, int h, float k, float dt, long now) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 18; i++) {
            y[i] -= dt * 0.07f * v[i];
            if (y[i] < -0.06f) { y[i] += 1.12f; x[i] = rnd.nextFloat(); }
            float s = (14 + 16 * v[i]) * k;
            paint.setColor(0x99FF5C8A);
            c.save();
            c.translate((x[i] + 0.015f * (float) Math.sin(now / 1100.0 + i)) * w, y[i] * h);
            c.scale(s, s);
            c.drawPath(heart, paint);
            c.restore();
        }
    }

    private void eggs(Canvas c, int w, int h, float k, float dt, long now) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 14; i++) {
            y[i] += dt * 0.05f * v[i];
            if (y[i] > 1.05f) { y[i] -= 1.1f; x[i] = rnd.nextFloat(); }
            float cx = (x[i] + 0.01f * (float) Math.sin(now / 1000.0 + i)) * w, cy = y[i] * h, s = (11 + 8 * v[i]) * k;
            oval.set(cx - s * 0.75f, cy - s, cx + s * 0.75f, cy + s);
            paint.setColor(PASTEL[i % PASTEL.length] & 0xDDFFFFFF);
            c.drawOval(oval, paint);
            paint.setColor(0x66FFFFFF);
            c.drawRect(cx - s * 0.7f, cy - s * 0.1f, cx + s * 0.7f, cy + s * 0.12f, paint);
        }
    }

    private void bats(Canvas c, int w, int h, float k, float dt, long now) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xEE0B0710);
        for (int i = 0; i < 6; i++) {
            x[i] += dt * 0.07f * v[i];
            if (x[i] > 1.1f) { x[i] = -0.1f; y[i] = 0.08f + 0.5f * rnd.nextFloat(); }
            float flap = 0.55f + 0.45f * (float) Math.sin(now / 90.0 + i * 2), s = (22 + 12 * v[i]) * k;
            c.save();
            c.translate(x[i] * w, (y[i] % 0.6f + 0.05f) * h + 18 * k * (float) Math.sin(now / 700.0 + i));
            c.scale(s, s * flap);
            c.drawPath(bat, paint);
            c.restore();
        }
    }

    /** A string of coloured bulbs along the top edge, twinkling. */
    private void lights(Canvas c, int w, float k, long now) {
        int n = 26;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * k);
        paint.setColor(0xAA222222);
        path.rewind();
        path.moveTo(0, 8 * k);
        for (int i = 1; i <= n; i++) path.quadTo((i - 0.5f) * w / n, 46 * k, i * (float) w / n, 8 * k);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float cx = (i + 0.5f) * w / n, cy = 8 * k + 19 * k;
            int col = BULBS[i % BULBS.length];
            float glow = 0.55f + 0.45f * (float) Math.sin(now / 500.0 + i * 1.3);
            paint.setColor((col & 0x00FFFFFF) | (Math.round(70 * glow) << 24));
            c.drawCircle(cx, cy, 15 * k, paint);
            paint.setColor((col & 0x00FFFFFF) | (Math.round(255 * (0.7f + 0.3f * glow)) << 24));
            c.drawCircle(cx, cy, 7 * k, paint);
        }
    }

    private void moon(Canvas c, int w, int h, float k, long now) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xEEFFE08A);
        c.save();
        c.translate(0.1f * w, 0.28f * h);
        c.scale(46 * k, 46 * k);
        c.rotate(-20);
        c.drawPath(crescent, paint);
        c.restore();
        for (int i = 0; i < 16; i++) {
            float tw = 0.4f + 0.6f * (float) Math.sin(now / 700.0 + i * 1.9);
            paint.setColor((0xFFFFF2B0 & 0x00FFFFFF) | (Math.round(220 * Math.max(0.15f, tw)) << 24));
            c.drawCircle((0.03f + 0.94f * ((i * 0.618f) % 1)) * w, (0.05f + 0.35f * ((i * 0.37f) % 1)) * h, (2 + 2 * (i % 3)) * k, paint);
        }
    }

    private void sparks(Canvas c, int w, int h, float k, float dt, long now) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 34; i++) {
            y[i] -= dt * 0.05f * v[i];
            if (y[i] < -0.03f) { y[i] += 1.06f; x[i] = rnd.nextFloat(); }
            float tw = 0.35f + 0.65f * (float) Math.abs(Math.sin(now / 600.0 + i * 1.4));
            paint.setColor((0xFFFFC04A & 0x00FFFFFF) | (Math.round(230 * tw) << 24));
            c.drawCircle(x[i] * w, y[i] * h, (2.5f + 3 * v[i]) * k, paint);
        }
    }
}
