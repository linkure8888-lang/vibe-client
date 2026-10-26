package com.vibe.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Neon glow primitives for the launcher UI.
 *
 * Everything here is drawn with plain Java2D — no images, no shaders — because
 * the launcher ships with a trimmed JRE and must not grow a rendering
 * dependency. A glow is faked the way 2D glow always is: several concentric
 * strokes with falling alpha, plus a soft radial bloom underneath.
 *
 * Alpha values are deliberately low. Neon that is too bright stops reading as
 * light and starts reading as a pasted-on shape; the goal is a rim of light
 * around an edge, not a coloured border.
 */
public final class Glow {

    /** How many concentric strokes make up one glow. More = smoother, slower. */
    private static final int LAYERS = 7;

    /** Peak alpha of the outermost bloom. */
    private static final int PEAK_ALPHA = 40;

    /**
     * Peak alpha of the background bloom.
     *
     * Kept low on purpose: this covers the whole window, so a value that looks
     * nice on one panel washes the entire UI blue. 22 is faint enough that the
     * gradient still reads as dark while the light is clearly present.
     */
    private static final int BLOOM_ALPHA = 22;

    private Glow() {
    }

    /**
     * Draws an outer glow around a rounded rectangle.
     *
     * [intensity] scales the whole effect, so a caller can fade a glow in and
     * out over time by passing an animated 0..1 value.
     */
    public static void around(Graphics2D g2, int x, int y, int w, int h, int radius,
                              Color color, float intensity) {
        if (intensity <= 0.01f) return;
        float clamped = Math.min(1f, intensity);

        // Work on a copy so the caller's clip, stroke and paint survive.
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = LAYERS; i >= 1; i--) {
            // Outer strokes are faint and wide, inner ones tight and brighter:
            // that falloff is what the eye reads as "light".
            float t = i / (float) LAYERS;
            int alpha = (int) (PEAK_ALPHA * (1f - t * 0.75f) * clamped);
            if (alpha <= 0) continue;

            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.setStroke(new BasicStroke(i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int spread = i;
            g.draw(new RoundRectangle2D.Float(
                x - spread, y - spread, w + spread * 2f, h + spread * 2f,
                radius * 2f + spread, radius * 2f + spread));
        }

        g.dispose();
    }

    /** A filled rounded rectangle in the accent colour at a given alpha. */
    public static void fill(Graphics2D g2, int x, int y, int w, int h, int radius,
                            Color color, int alpha) {
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.max(0, Math.min(255, alpha))));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, radius * 2f, radius * 2f));
        g.dispose();
    }

    /**
     * A large soft bloom, used as a background light source.
     *
     * [pulse] is the 0..1 animated intensity. The gradient is transparent at
     * the rim, so the bloom fades into whatever is behind it with no hard edge.
     */
    public static void bloom(java.awt.Component target, Graphics2D g2,
                             float centerX, float centerY, float radius,
                             Color color, float pulse) {
        if (pulse <= 0.01f) return;
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        float r = Math.max(1f, radius);
        int alpha = (int) (BLOOM_ALPHA * Math.min(1f, pulse));

        Paint paint = new RadialGradientPaint(
            new Point2D.Float(centerX, centerY),
            r,
            new float[]{0f, 0.45f, 1f},
            new Color[]{
                new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha),
                new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha / 3),
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)
            },
            MultipleGradientPaint.CycleMethod.NO_CYCLE);

        g.setPaint(paint);
        g.fillRect(0, 0, target.getWidth(), target.getHeight());
        g.dispose();
    }

    /**
     * A vertical "sheen" used on the header.
     *
     * Two stops rather than a flat tint: a gradient across the accent band
     * gives it a lit-from-one-side look that a solid colour cannot.
     */
    public static Paint sheen(int height, Color from, Color to) {
        return new LinearGradientPaint(
            new Point2D.Float(0, 0), new Point2D.Float(0, height),
            new float[]{0f, 1f}, new Color[]{from, to});
    }

    /**
     * Eases a value toward a target with a smooth step.
     *
     * Used by every animated element instead of a plain lerp: the soft start
     * and stop is what separates a designed transition from a mechanical one.
     */
    public static float ease(float current, float target, float speed) {
        float delta = target - current;
        if (Math.abs(delta) < 0.002f) return target;
        return current + delta * speed;
    }

    /** A sine wave in 0..1, for pulsing effects. */
    public static float pulse(long periodMs, long nowMs, float phase) {
        double angle = (nowMs % periodMs) / (double) periodMs * Math.PI * 2 + phase;
        return (float) ((Math.sin(angle) + 1.0) / 2.0);
    }
}
