package com.vibe.launcher.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

/**
 * Flat dark arrows for a JSpinner.
 *
 * Why this exists: Nimbus paints the spinner's up/down arrows as two pale grey
 * bevelled buttons. On a near-black panel they were the brightest thing on
 * screen -- the eye went straight to a system widget instead of the setting it
 * belonged to. Replacing the UI delegate is the only way to remove them,
 * because Nimbus ignores every colour key for those two buttons.
 *
 * The arrows are drawn as plain triangles: no images, no new dependency, and
 * they stay crisp at any DPI.
 */
public final class VibeSpinnerUI extends BasicSpinnerUI {

    public static VibeSpinnerUI create() {
        return new VibeSpinnerUI();
    }

    @Override
    protected JComponent createNextButton() {
        JButton button = arrowButton(true);
        installNextButtonListeners(button);
        return button;
    }

    @Override
    protected JComponent createPreviousButton() {
        JButton button = arrowButton(false);
        installPreviousButtonListeners(button);
        return button;
    }

    /**
     * A button that only paints a triangle.
     *
     * Hover is a faint white wash rather than a colour change: the arrow is
     * already the accent colour, and brightening it would compete with the
     * primary buttons on the same screen.
     */
    private static JButton arrowButton(boolean up) {
        return new JButton() {
            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setPreferredSize(new Dimension(16, 9));
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(new Color(255, 255, 255, 22));
                    g2.fillRect(0, 0, w, h);
                }

                // A small triangle, inset so it never touches the edge.
                int pad = 4;
                int tw = Math.max(4, w - pad * 2);
                int th = Math.max(3, h - pad);
                int cx = w / 2;

                Polygon tri = new Polygon();
                if (up) {
                    tri.addPoint(cx, pad / 2);
                    tri.addPoint(cx - tw / 2, pad / 2 + th);
                    tri.addPoint(cx + tw / 2, pad / 2 + th);
                } else {
                    tri.addPoint(cx, h - pad / 2);
                    tri.addPoint(cx - tw / 2, h - pad / 2 - th);
                    tri.addPoint(cx + tw / 2, h - pad / 2 - th);
                }

                g2.setColor(isEnabled() ? Theme.TEXT_DIM : new Color(255, 255, 255, 40));
                g2.fillPolygon(tri);
                g2.dispose();
            }
        };
    }
}
