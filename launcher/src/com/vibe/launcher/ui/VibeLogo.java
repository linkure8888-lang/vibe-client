package com.vibe.launcher.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.Path2D;

/**
 * The premium Vibe Client logo rendered using a Path2D stylized letter V
 * with a gradient fill (#8B5CF6 -> #22D3EE).
 */
public final class VibeLogo extends JComponent {

    private final int size;

    public VibeLogo(int size) {
        this.size = size;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        int w = getWidth();
        int h = getHeight();

        // Outer cosmic glow / backdrop ring
        g2.setColor(new Color(139, 92, 246, 35));
        g2.fillOval(2, 2, w - 4, h - 4);

        // Stylized V Path2D shape
        Path2D path = new Path2D.Float();
        // Left wing top-left to bottom point
        path.moveTo(w * 0.18, h * 0.15);
        path.lineTo(w * 0.50, h * 0.82);
        // Right wing bottom point to top-right
        path.lineTo(w * 0.82, h * 0.15);
        // Inner notch of the V
        path.lineTo(w * 0.65, h * 0.15);
        path.lineTo(w * 0.50, h * 0.55);
        path.lineTo(w * 0.35, h * 0.15);
        path.closePath();

        // Gradient fill: #8B5CF6 -> #22D3EE
        LinearGradientPaint gradient = new LinearGradientPaint(
            0, 0, w, h,
            new float[]{0.0f, 1.0f},
            new Color[]{Theme.ACCENT, Theme.ACCENT_2}
        );
        g2.setPaint(gradient);
        g2.fill(path);

        // Subtle crystal border highlight
        g2.setColor(new Color(255, 255, 255, 120));
        g2.setStroke(new java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g2.draw(path);

        g2.dispose();
        super.paintComponent(g);
    }
}
