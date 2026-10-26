package com.vibe.launcher.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A thin progress bar painted by hand.
 *
 * Why not JProgressBar: Nimbus paints its own orange fill and its own bevelled
 * track, and it ignores setForeground for the bar itself. The result was a
 * bright orange stripe in the middle of a violet UI. Replacing the painter is
 * the only way to get rid of it, and at that point painting the whole control
 * is simpler than fighting the look-and-feel.
 *
 * The API deliberately mirrors the slice of JProgressBar the launcher uses
 * (value/maximum/indeterminate), so swapping the field type is the whole
 * change at the call sites.
 */
public final class VibeProgressBar extends JComponent {

    private int value = 0;
    private int maximum = 100;
    private boolean indeterminate = false;
    private boolean stringPainted = false;

    /** Phase for the indeterminate sweep, advanced by the paint clock. */
    private double sweep = 0;
    private long lastPaintAt = 0L;

    /**
     * Drives repaints while the bar is indeterminate.
     *
     * Painting alone cannot animate: Swing only repaints when something asks
     * it to, so an indeterminate bar without a timer froze on one frame and
     * looked like a broken progress bar rather than a moving one. The timer is
     * started and stopped by [setIndeterminate] and by [removeNotify], so a
     * hidden or idle bar costs nothing.
     */
    private final javax.swing.Timer sweepTimer;

    public VibeProgressBar() {
        setOpaque(false);
        sweepTimer = new javax.swing.Timer(33, e -> repaint());
        sweepTimer.setRepeats(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (indeterminate) sweepTimer.start();
    }

    @Override
    public void removeNotify() {
        sweepTimer.stop();
        super.removeNotify();
    }

    public void setValue(int v) {
        int clamped = Math.max(0, Math.min(maximum, v));
        if (clamped == value) return;
        value = clamped;
        repaint();
    }

    public int getValue() {
        return value;
    }

    public void setMaximum(int max) {
        this.maximum = Math.max(1, max);
        repaint();
    }

    public int getMaximum() {
        return maximum;
    }

    public void setIndeterminate(boolean flag) {
        boolean changed = this.indeterminate != flag;
        this.indeterminate = flag;
        if (changed) {
            lastPaintAt = 0L;
            sweep = 0;
            if (flag) {
                sweepTimer.start();
            } else {
                sweepTimer.stop();
            }
        }
        repaint();
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public void setStringPainted(boolean flag) {
        this.stringPainted = flag;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(280, 16);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        int w = getWidth();
        int h = getHeight();
        int arc = h;

        // Track: a recessed well, slightly darker than the panel it sits on.
        g2.setColor(new Color(0, 0, 0, 110));
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
        g2.setColor(new Color(255, 255, 255, 18));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));

        if (indeterminate) {
            paintIndeterminate(g2, w, h, arc);
        } else {
            float ratio = value / (float) maximum;
            int fillW = Math.round(w * Math.max(0f, Math.min(1f, ratio)));
            if (fillW > 0) paintRamp(g2, w, h, arc, 0, fillW);
        }

        if (stringPainted) {
            String text = indeterminate ? "" : Math.round(100f * value / maximum) + " %";
            if (!text.isEmpty()) {
                g2.setFont(Theme.SMALL);
                g2.setColor(Theme.TEXT);
                int tw = g2.getFontMetrics().stringWidth(text);
                int th = g2.getFontMetrics().getAscent();
                g2.drawString(text, (w - tw) / 2, (h + th) / 2 - 1);
            }
        }

        g2.dispose();
    }

    /** A violet -> cyan bar segment, clipped to the rounded track. */
    private void paintRamp(Graphics2D g2, int w, int h, int arc, int fromX, int toX) {
        Graphics2D clip = (Graphics2D) g2.create();
        clip.setClip(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
        for (int x = fromX; x < toX; x++) {
            float t = w <= 1 ? 0f : x / (float) (w - 1);
            clip.setColor(blend(Theme.ACCENT, Theme.ACCENT_2, t));
            clip.drawLine(x, 0, x, h);
        }
        clip.dispose();
    }

    /**
     * A moving highlight for work with unknown total size (asset extraction).
     *
     * Driven by the system clock rather than a timer: an indeterminate bar that
     * needed its own thread would keep a minimised launcher awake.
     */
    private void paintIndeterminate(Graphics2D g2, int w, int h, int arc) {
        long now = System.currentTimeMillis();
        if (lastPaintAt != 0L) {
            sweep += (now - lastPaintAt) / 900.0;
        }
        lastPaintAt = now;
        if (sweep > 2.0) sweep -= 2.0;

        int bandW = Math.max(40, w / 3);
        int x = (int) ((w + bandW) * (sweep / 2.0)) - bandW;
        paintRamp(g2, w, h, arc, Math.max(0, x), Math.min(w, x + bandW));
    }

    /** Per-channel RGB blend; alpha comes from [from]. */
    private static Color blend(Color from, Color to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        return new Color(
            (int) (from.getRed() + (to.getRed() - from.getRed()) * f),
            (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * f),
            (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * f),
            from.getAlpha());
    }
}
