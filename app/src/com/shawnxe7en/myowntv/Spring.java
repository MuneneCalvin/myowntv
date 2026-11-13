package com.shawnxe7en.myowntv;

/** Damped spring, like macOS animations: response (s) and damping (1 = no bounce). */
final class Spring {
    float value, velocity, target;
    private final float response, damping;

    Spring(float response, float damping, float initial) {
        this.response = response;
        this.damping = damping;
        value = target = initial;
    }

    void snap(float v) {
        value = target = v;
        velocity = 0;
    }

    /** Advances `dt` seconds; returns true while still moving. */
    boolean step(float dt) {
        if (settled()) {
            value = target;
            velocity = 0;
            return false;
        }
        float omega = (float) (2 * Math.PI / response);
        float stiffness = omega * omega, friction = 2 * damping * omega;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float accel = -stiffness * (value - target) - friction * velocity;
            velocity += accel * h;
            value += velocity * h;
        }
        return true;
    }

    boolean settled() {
        return Math.abs(value - target) < 0.0008f && Math.abs(velocity) < 0.004f;
    }

    /** cubic-bezier(0.2, 0.8, 0.2, 1), Couch TV's easing: t in [0,1] (time) to progress in [0,1]. */
    static float easeOut(float t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        float s = t;   // solve x(s) = t, Newton from s = t
        for (int i = 0; i < 8; i++) {
            float u = 1 - s;
            float x = 3 * u * u * s * 0.2f + 3 * u * s * s * 0.2f + s * s * s - t;
            float dx = 0.6f * u * u + 2.4f * s * s;   // dx/ds
            if (Math.abs(x) < 1e-6f || Math.abs(dx) < 1e-6f) break;
            s -= x / dx;
        }
        s = Math.max(0, Math.min(1, s));
        float u = 1 - s;
        return 3 * u * u * s * 0.8f + 3 * u * s * s + s * s * s;
    }
}
