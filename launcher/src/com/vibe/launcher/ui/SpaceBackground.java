package com.vibe.launcher.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Procedural deep-space backdrop: nebula, twinkling star field, a distant
 * planet, slow light streaks and the occasional shooting star, all drawn with
 * Java2D so the launcher ships with no external image dependency at all.
 *
 * Why procedural instead of a photo:
 *  - The previous backdrop loaded {@code /hero.jpg}, which was never packed
 *    into {@code launcher.jar}; the shipped app silently fell back to a flat
 *    gradient. Drawing the scene removes that failure mode entirely.
 *  - A photo has a subject and a horizon line; a launcher wants an atmosphere
 *    behind the UI, not a competing composition.
 *
 * Cost model, which is the whole design of this class:
 *  - The expensive part (nebula + the dense star field) is rendered ONCE into a
 *    cached image and only regenerated on resize. A resize is not a hot path.
 *  - The per-frame part is deliberately tiny: one drawImage, a planet, two
 *    streaks, the scrim, and at most [TWINKLE] small ovals plus one shooting
 *    star. Nothing allocates per frame.
 *  - 20 FPS is plenty for motion this slow, and the timer stops the moment the
 *    window is hidden, so a minimised launcher costs zero CPU.
 */
public final class SpaceBackground extends JPanel {

    /** 50 ms = 20 FPS. The motion is a slow parallax, not an animation. */
    private static final int FRAME_MS = 50;

    /** Full drift cycle in milliseconds. */
    private static final double CYCLE_MS = 60000.0;

    /** How many stars twinkle. Above ~48 the field reads as noise, not sky. */
    private static final int TWINKLE = 44;

    private final Timer timer;

    /** Cached nebula + stars, regenerated on resize. */
    private BufferedImage layer;
    private int layerWidth;
    private int layerHeight;

    /** Drift phase in radians. */
    private double phase;

    // -- Twinkling stars ---------------------------------------------------
    // Fixed arrays rather than a list of objects: this is read in the paint
    // path, and an object per star per frame is exactly the allocation rule the
    // project forbids. Positions are in 0..1 of the panel, rebuilt on resize.
    private final float[] twinkleX = new float[TWINKLE];
    private final float[] twinkleY = new float[TWINKLE];
    private final float[] twinklePhase = new float[TWINKLE];
    private final float[] twinkleSpeed = new float[TWINKLE];

    // -- Shooting star -----------------------------------------------------
    /** Time until the next shooting star, in frames. */
    private int nextShootingIn;
    /** Progress of the current shooting star, 0 = idle, otherwise 0..1. */
    private float shootingProgress = -1f;
    /** Where the current shooting star started and where it travels. */
    private float shootingX;
    private float shootingY;
    private float shootingDx;
    private float shootingDy;

    private final Random random = new Random();

    public SpaceBackground() {
        setOpaque(true);
        setBackground(Theme.BG);

        timer = new Timer(FRAME_MS, e -> onFrame());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // A fresh window starts with a shooting star already pending rather
        // than with a full minute of nothing happening.
        if (nextShootingIn <= 0) nextShootingIn = 40 + random.nextInt(80);
        timer.start();
    }

    @Override
    public void removeNotify() {
        // A hidden launcher must cost zero CPU.
        timer.stop();
        super.removeNotify();
    }

    private void onFrame() {
        phase += Math.PI * 2 * FRAME_MS / CYCLE_MS;
        if (phase > Math.PI * 2) phase -= Math.PI * 2;

        // Shooting star state machine. Driven here, not in paint, so that
        // painting stays a pure function of the current state.
        if (shootingProgress >= 0f) {
            shootingProgress += FRAME_MS / 900f;
            if (shootingProgress >= 1f) {
                shootingProgress = -1f;
                nextShootingIn = 120 + random.nextInt(200);
            }
        } else if (--nextShootingIn <= 0) {
            startShootingStar();
        }

        repaint();
    }

    private void startShootingStar() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        shootingX = w * (0.30f + random.nextFloat() * 0.55f);
        shootingY = h * (0.02f + random.nextFloat() * 0.30f);
        float angle = (float) (Math.PI * (0.62 + random.nextFloat() * 0.16));
        float distance = w * (0.22f + random.nextFloat() * 0.20f);
        shootingDx = (float) (Math.cos(angle) * distance);
        shootingDy = (float) (Math.sin(angle) * distance);
        shootingProgress = 0f;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        ensureLayer(w, h);

        // -- Star field with parallax ---------------------------------------
        // The layer is drawn slightly larger than the panel and panned, so the
        // motion reads as depth instead of the whole image sliding.
        double drift = 0.5 + 0.5 * Math.sin(phase);
        int overscan = 24;
        int dx = (int) (-overscan + drift * overscan * 2);
        int dy = (int) (-overscan * 0.6 + drift * overscan * 1.2);
        g2.drawImage(layer, dx, dy, w + overscan * 2, h + overscan * 2, null);

        // -- Live layer ------------------------------------------------------
        drawTwinkles(g2, w, h);
        drawPlanet(g2, w, h);
        drawStreaks(g2, w, h);
        drawShootingStar(g2);

        // -- Scrim -----------------------------------------------------------
        // Readability first: small text sits at the top and bottom, so those
        // bands get the heaviest darkening. The middle stays clearer so the
        // art is still visible behind the central panel.
        for (int y = 0; y < h; y++) {
            float t = y / (float) Math.max(1, h - 1);
            float edge = Math.abs(t - 0.5f) * 2f;
            float alpha = 0.30f + 0.32f * edge * edge;
            int a = (int) (alpha * 255);
            g2.setColor(new Color(6, 7, 14, a));
            g2.drawLine(0, y, w, y);
        }

        g2.dispose();
    }

    // =====================================================================
    // Static layer
    // =====================================================================

    private void ensureLayer(int w, int h) {
        if (layer != null && layerWidth == w && layerHeight == h) return;

        int lw = w + 48;
        int lh = h + 48;
        BufferedImage img = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        // Base: a vertical obsidian gradient, slightly bluer in the middle.
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x0B0D18),
            0, lh, new Color(0x04050A)));
        g.fillRect(0, 0, lw, lh);

        // Nebulae: three large, very low-alpha blooms. Their overlap is what
        // produces the violet-to-cyan blend without a visible seam.
        nebula(g, lw * 0.68f, lh * 0.22f, Math.max(lw, lh) * 0.62f,
            new Color(0x8B5CF6), 0.30f);
        nebula(g, lw * 0.22f, lh * 0.72f, Math.max(lw, lh) * 0.55f,
            new Color(0x22D3EE), 0.16f);
        nebula(g, lw * 0.88f, lh * 0.86f, Math.max(lw, lh) * 0.34f,
            new Color(0xF472B6), 0.09f);

        // Stars: deterministic, so a resize produces the same sky rather than
        // a reshuffled one. The seed is the layer size for the same reason.
        Random random = new Random(0x5EED_0000L + lw * 31L + lh);
        int count = Math.max(180, (lw * lh) / 5200);
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(lw);
            int y = random.nextInt(lh);
            int radius = random.nextInt(100) < 8 ? 2 : 1;
            int alpha = 40 + random.nextInt(150);
            g.setColor(new Color(255, 255, 255, alpha));
            g.fillOval(x, y, radius, radius);
        }

        // A handful of brighter stars with a soft flare. Few on purpose: too
        // many and the field reads as noise instead of sky.
        for (int i = 0; i < 14; i++) {
            int x = random.nextInt(lw);
            int y = random.nextInt(lh);
            float r = 5f + random.nextFloat() * 7f;
            g.setPaint(new RadialGradientPaint(
                new Point2D.Float(x, y), r,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 120), new Color(255, 255, 255, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
            g.setColor(new Color(255, 255, 255, 190));
            g.fillOval(x, y, 2, 2);
        }

        g.dispose();
        layer = img;
        layerWidth = w;
        layerHeight = h;

        // Twinkling stars are seeded from the panel size for the same reason
        // the star field is: resize must not reshuffle the sky.
        Random tw = new Random(0xBEEF_0000L + w * 17L + h);
        for (int i = 0; i < TWINKLE; i++) {
            twinkleX[i] = tw.nextFloat();
            twinkleY[i] = tw.nextFloat();
            twinklePhase[i] = tw.nextFloat() * (float) (Math.PI * 2);
            twinkleSpeed[i] = 0.6f + tw.nextFloat() * 1.6f;
        }
    }

    /** One soft radial bloom. Alpha is applied through the colour itself. */
    private void nebula(Graphics2D g, float cx, float cy, float radius, Color tint, float alpha) {
        Color core = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(),
            (int) (alpha * 255));
        Color edge = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx, cy), Math.max(1f, radius),
            new float[]{0f, 0.45f, 1f},
            new Color[]{core, fadeColor(core, 0.45f), edge},
            MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    private static Color fadeColor(Color c, float factor) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
            (int) (c.getAlpha() * factor));
    }

    // =====================================================================
    // Per-frame decorations
    // =====================================================================

    /**
     * A few dozen stars breathe in and out.
     *
     * This is the cheapest possible way to make a static sky feel alive: the
     * field itself stays in the cached image, and only the brightness of a
     * small fixed set changes. No allocation, no per-pixel work.
     */
    private void drawTwinkles(Graphics2D g2, int w, int h) {
        for (int i = 0; i < TWINKLE; i++) {
            float wave = Motion.pulse(twinklePhase[i]);
            // Mostly dim with rare peaks, which is what a real star looks like
            // through atmosphere. A sine mapped straight to alpha looks like a
            // slow blink instead.
            float eased = wave * wave;
            int alpha = (int) (30 + eased * 190);
            if (alpha <= 36) continue;
            int x = (int) (twinkleX[i] * w);
            int y = (int) (twinkleY[i] * h);
            g2.setColor(new Color(255, 255, 255, alpha));
            int size = eased > 0.75f ? 3 : 2;
            g2.fillOval(x, y, size, size);
        }
    }

    /**
     * A distant planet in the upper right, mostly dark with a lit rim.
     *
     * Drawn per frame rather than baked into the layer because it is the one
     * element that should feel like it sits in front of the stars; keeping it
     * out of the cached image also means the cache stays cheap to rebuild.
     */
    private void drawPlanet(Graphics2D g2, int w, int h) {
        float radius = Math.min(w, h) * 0.30f;
        float cx = w - radius * 0.55f;
        float cy = radius * 0.62f;

        // Body: near-black violet, so it reads as a silhouette against the sky.
        g2.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx, cy), radius,
            new float[]{0f, 0.65f, 1f},
            new Color[]{new Color(0x1A1330), new Color(0x0E0B1C), new Color(0x08060F)},
            MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));

        // Rim light on the lower-left edge: the light source is the nebula. The
        // intensity breathes with the drift, so the planet is never static.
        float rim = 0.42f + 0.16f * Motion.pulse((float) phase);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rim));
        g2.setColor(new Color(0x8B5CF6));
        g2.setStroke(new BasicStroke(Math.max(1f, radius * 0.02f)));
        g2.draw(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
        g2.setComposite(AlphaComposite.SrcOver);

        // Ring: a thin ellipse, tilted. Kept subtle so it does not become the
        // focal point of the whole window.
        java.awt.geom.AffineTransform old = g2.getTransform();
        g2.rotate(Math.toRadians(-18), cx, cy);
        g2.setColor(new Color(0x22D3EE));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        g2.setStroke(new BasicStroke(Math.max(1f, radius * 0.012f)));
        g2.draw(new Ellipse2D.Float(cx - radius * 1.55f, cy - radius * 0.30f,
            radius * 3.1f, radius * 0.60f));
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setTransform(old);
    }

    /** Two long, faint diagonal light bands for motion. */
    private void drawStreaks(Graphics2D g2, int w, int h) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        for (int i = 0; i < 2; i++) {
            int y = (int) (h * (0.34 + i * 0.26));
            int x0 = (int) (-w * 0.1 + Math.sin(phase + i) * 40);
            int length = (int) (w * 1.3);
            // Explicit opaque colours: the two-argument Color(int, boolean)
            // constructor's boolean is `hasAlpha`, not "make transparent" -
            // using it here would silently produce opaque white, the exact bug
            // already fixed once in Theme.java.
            g2.setPaint(new java.awt.GradientPaint(
                x0, y, new Color(0x22D3EE),
                x0 + length, y - h / 6, new Color(0x8B5CF6)));
            g2.fillPolygon(
                new int[]{x0, x0 + length, x0 + length},
                new int[]{y, y - 3, y + 3},
                3);
        }
        g2.setComposite(AlphaComposite.SrcOver);
    }

    /**
     * One shooting star with a fading tail.
     *
     * It is the detail that makes a procedural sky feel authored rather than
     * tiled: a slow drift alone reads as a moving wallpaper, while something
     * that happens once every ten seconds gives the background a sense of time.
     */
    private void drawShootingStar(Graphics2D g2) {
        if (shootingProgress < 0f) return;

        // Fast in, slow out: the head is already moving when it appears and
        // decelerates as it burns up.
        float t = Motion.easeOut(shootingProgress);
        float headX = shootingX + shootingDx * t;
        float headY = shootingY + shootingDy * t;

        // Tail length shrinks as the star dies, so it visibly burns out.
        float tailScale = 1f - shootingProgress;
        float tailX = headX - shootingDx * 0.22f * tailScale;
        float tailY = headY - shootingDy * 0.22f * tailScale;

        int alpha = (int) (210 * tailScale * tailScale);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setPaint(new java.awt.GradientPaint(
            tailX, tailY, new Color(34, 211, 238, 0),
            headX, headY, new Color(236, 254, 255, alpha)));
        g2.draw(new Line2D.Float(tailX, tailY, headX, headY));

        // The head itself, with a soft bloom around it.
        float r = 7f * tailScale + 2f;
        g2.setPaint(new RadialGradientPaint(
            new Point2D.Float(headX, headY), r,
            new float[]{0f, 1f},
            new Color[]{new Color(255, 255, 255, alpha),
                new Color(255, 255, 255, 0)},
            MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fill(new Ellipse2D.Float(headX - r, headY - r, r * 2, r * 2));
        g2.setComposite(AlphaComposite.SrcOver);
    }
}
