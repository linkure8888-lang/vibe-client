package com.vibe.launcher.ui;

/**
 * Easing and interpolation for the launcher's animated UI.
 *
 * Why a dedicated class: every animated element in this launcher used to do its
 * own lerp, and each one drifted toward a slightly different feel. A shared set
 * of curves is what makes separate components read as one product instead of a
 * collection of independently animated widgets.
 *
 * All functions are pure and allocation-free, because most of them are called
 * from paint paths that run at 20-60 FPS.
 */
public final class Motion {

    /** How close is "close enough"; below this a value snaps and stops animating. */
    public static final float EPSILON = 0.001f;

    private Motion() {
    }

    // =======================================================================
    // Easing curves (t is clamped to 0..1)
    // =======================================================================

    /** Accelerates from rest. For elements leaving the screen. */
    public static float easeIn(float t) {
        float x = clamp01(t);
        return x * x * x;
    }

    /** Decelerates to rest. The default for elements arriving on screen. */
    public static float easeOut(float t) {
        float x = clamp01(t);
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    /** Slow at both ends. For moves that stay on screen (sliding pills). */
    public static float easeInOut(float t) {
        float x = clamp01(t);
        return x < 0.5f
            ? 4f * x * x * x
            : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    /**
     * Overshoots slightly past the target then settles.
     *
     * Used sparingly: on a knob or a pill it reads as physical weight, on text
     * or panels it reads as a bug. That is the whole rule for this curve.
     */
    public static float easeOutBack(float t) {
        float x = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float inv = x - 1f;
        return 1f + c3 * inv * inv * inv + c1 * inv * inv;
    }

    // =======================================================================
    // Frame-rate independent smoothing
    // =======================================================================

    /**
     * Moves [current] toward [target] with an exponential decay that is
     * independent of the frame rate.
     *
     * A plain "current += (target-current) * 0.2" gets faster on a faster
     * machine, so the same button animates differently on a 60 Hz and a 144 Hz
     * display. This version takes the elapsed time, so the motion is identical
     * everywhere. [halfLifeMs] is the time to cover half the remaining distance.
     */
    public static float approach(float current, float target, float halfLifeMs,
                                 float deltaMs) {
        if (Math.abs(target - current) < EPSILON) return target;
        if (halfLifeMs <= 0f) return target;
        // 0.5^(dt/halfLife) == exp(ln(0.5) * dt / halfLife)
        float factor = (float) Math.exp(Math.log(0.5) * Math.max(0f, deltaMs) / halfLifeMs);
        float next = target + (current - target) * factor;
        return Math.abs(next - target) < EPSILON ? target : next;
    }

    /** Same as [approach], for an int-valued position such as a scroll offset. */
    public static int approach(int current, int target, float halfLifeMs, float deltaMs) {
        return Math.round(approach((float) current, (float) target, halfLifeMs, deltaMs));
    }

    /** A 0..1 sine pulse. [phase] is in radians. */
    public static float pulse(float phase) {
        return (float) (Math.sin(phase) * 0.5 + 0.5);
    }

    /** Per-channel colour blend; alpha is blended too. */
    public static java.awt.Color mix(java.awt.Color from, java.awt.Color to, float t) {
        float f = clamp01(t);
        int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * f);
        int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * f);
        int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * f);
        int a = (int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * f);
        return new java.awt.Color(clamp255(r), clamp255(g), clamp255(b), clamp255(a));
    }

    /** The same colour at a different alpha (0..1). */
    public static java.awt.Color alpha(java.awt.Color color, float a) {
        return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(),
            clamp255((int) (clamp01(a) * 255)));
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
