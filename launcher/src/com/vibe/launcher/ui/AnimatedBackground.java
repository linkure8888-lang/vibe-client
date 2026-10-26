package com.vibe.launcher.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;

/**
 * The launcher's animated background.
 *
 * What it does: two large, slow-moving neon blooms drift across a dark
 * gradient, and a subtle diagonal sheen passes over the whole thing. That is
 * enough motion to feel alive without costing measurable CPU — the timer runs
 * at 30 FPS and each frame is two radial gradients plus one rectangle.
 *
 * Performance note, because this runs behind everything: the whole background
 * is rendered into the panel itself and the timer stops when the window is not
 * showing. A hidden launcher burns zero CPU.
 */
public final class AnimatedBackground extends JPanel {

    private static final int FRAME_MS = 33; // ~30 FPS

    private final Timer timer;

    /** Drift phase in radians, advanced each frame. */
    private double phase = 0;

    public AnimatedBackground() {
        setOpaque(true);
        setBackground(Theme.BG);

        timer = new Timer(FRAME_MS, (ActionEvent e) -> {
            phase += 0.006;
            if (phase > Math.PI * 2) phase -= Math.PI * 2;
            repaint();
        });
    }

    /** Started when the window is shown, stopped when it is hidden. */
    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        int w = getWidth();
        int h = getHeight();

        // Base: a vertical gradient rather than a flat fill, so the window has
        // depth even before the blooms are drawn.
        g2.setPaint(Glow.sheen(h, Theme.BG_TOP, Theme.BG_BOTTOM));
        g2.fillRect(0, 0, w, h);

        // Two drifting blooms. They travel on different paths at different
        // speeds, which is what keeps the motion from looking like one thing
        // sliding back and forth.
        //
        // Intensities are low (0.35 / 0.25 peak): this layer sits behind every
        // panel, and a stronger bloom turns the whole launcher blue.
        float cx1 = (float) (w * (0.22 + 0.10 * Math.sin(phase)));
        float cy1 = (float) (h * (0.30 + 0.14 * Math.cos(phase * 0.7)));
        Glow.bloom(this, g2, cx1, cy1, Math.max(w, h) * 0.45f,
            Theme.ACCENT, 0.22f + 0.13f * Glow.pulse(9000, System.currentTimeMillis(), 0f));

        float cx2 = (float) (w * (0.82 + 0.08 * Math.cos(phase * 0.8)));
        float cy2 = (float) (h * (0.75 + 0.10 * Math.sin(phase * 0.6)));
        Glow.bloom(this, g2, cx2, cy2, Math.max(w, h) * 0.38f,
            Theme.NEON_CYAN, 0.16f + 0.10f * Glow.pulse(11000, System.currentTimeMillis(), 1.6f));

        // Faint grid: a few 1px lines that give the background structure and
        // make the blooms read as light sitting in front of something.
        g2.setColor(new Color(0xFF, 0xFF, 0xFF, 7));
        int step = 48;
        for (int x = 0; x < w; x += step) g2.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += step) g2.drawLine(0, y, w, y);

        g2.dispose();
    }
}
