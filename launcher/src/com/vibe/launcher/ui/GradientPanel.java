package com.vibe.launcher.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * A panel that paints a vertical gradient instead of a flat colour.
 *
 * Flat blocks of colour are the main reason a Swing app reads as dated; a soft
 * gradient behind the header and the action bar is what gives the launcher the
 * modern "cards on a dark page" look without pulling in a UI toolkit.
 */
public final class GradientPanel extends JPanel {

    private final Color top;
    private final Color bottom;

    public GradientPanel(Color top, Color bottom) {
        this.top = top;
        this.bottom = bottom;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
