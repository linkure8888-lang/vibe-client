package com.vibe.launcher.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A flat, rounded button with a hover animation.
 *
 * Swing's stock buttons paint the platform look and feel — grey gradients and
 * bevels from another decade. This one paints itself: a rounded rectangle, a
 * label, and a smooth colour transition on hover. That single change is what
 * stops the launcher from looking like a 2005 installer.
 *
 * The animation is driven by a Swing [Timer] that only runs while a hover
 * transition is in flight, so idle buttons cost nothing.
 */
public final class RoundedButton extends JButton {

    /** How far the hover animation has progressed, 0 = idle, 1 = fully hovered. */
    private float hover = 0f;
    /** Drives the idle neon pulse; independent of hover. */
    private float pulsePhase = 0f;
    private final Timer animator;
    private final Timer pulseTimer;

    /** Mutable so a button can be promoted/demoted (e.g. the active tab). */
    private Color base;
    private Color hoverColor;
    private final int radius;
    private final boolean filled;

    /** Neon rim colour; null disables the glow for this button. */
    private Color glowColor;

    /** A filled accent button — the primary action. */
    public static RoundedButton primary(String text) {
        RoundedButton button =
            new RoundedButton(text, Theme.ACCENT, Theme.ACCENT_HOVER, 10, true);
        button.glowColor = Theme.ACCENT;
        return button;
    }

    /** A dark outlined button — secondary actions. */
    public static RoundedButton secondary(String text) {
        return new RoundedButton(text, Theme.PANEL_LIGHT, Theme.PANEL_HOVER, 10, false);
    }

    /** A compact dark button for inline actions. */
    public static RoundedButton small(String text) {
        return new RoundedButton(text, Theme.PANEL_LIGHT, Theme.PANEL_HOVER, 8, false);
    }

    private RoundedButton(String text, Color base, Color hoverColor, int radius,
                          boolean filled) {
        super(text);
        this.base = base;
        this.hoverColor = hoverColor;
        this.radius = radius;
        this.filled = filled;

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(Theme.BODY_BOLD);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        animator = new Timer(16, e -> step());
        animator.setRepeats(true);

        // A slow pulse keeps a primary button's rim visibly alive even when the
        // mouse is elsewhere, which is what makes the UI read as neon rather
        // than as a static coloured border.
        pulseTimer = new Timer(50, e -> {
            if (glowColor == null) return;
            pulsePhase += 0.09f;
            repaint();
        });
        pulseTimer.setRepeats(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isEnabled()) return;
                animator.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isEnabled()) return;
                animator.start();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (glowColor != null) pulseTimer.start();
    }

    @Override
    public void removeNotify() {
        pulseTimer.stop();
        animator.stop();
        super.removeNotify();
    }

    /**
     * Repaints this button in the accent style, or back to the secondary one.
     * Used by the tab switcher to mark which card is visible.
     */
    public void setAccent(boolean accent) {
        this.base = accent ? Theme.ACCENT : Theme.PANEL_LIGHT;
        this.hoverColor = accent ? Theme.ACCENT_HOVER : Theme.PANEL_HOVER;
        repaint();
    }

    /** Advances the hover value toward its target and stops when it settles. */
    private void step() {
        float target = getModel().isRollover() ? 1f : 0f;
        float delta = target - hover;
        if (Math.abs(delta) < 0.03f) {
            hover = target;
            animator.stop();
        } else {
            hover += delta * 0.25f;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);

        int w = getWidth();
        int h = getHeight();

        // Neon rim first, so it sits *behind* the plate and looks like light
        // escaping from under it rather than a border drawn on top.
        if (glowColor != null) {
            float idle = 0.35f + 0.25f * ((float) Math.sin(pulsePhase) * 0.5f + 0.5f);
            float intensity = Math.max(idle, hover);
            Glow.around(g2, 1, 1, w - 2, h - 2, radius, glowColor, intensity);
        }

        // Disabled buttons keep the shape but lose the colour, so the state is
        // readable without a second style.
        Color fill = isEnabled()
            ? blend(base, hoverColor, hover)
            : blend(base, Theme.BG, 0.5f);

        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w, h, radius * 2, radius * 2);

        if (!filled) {
            g2.setColor(isEnabled() ? blend(Theme.BORDER, Theme.BORDER_STRONG, hover)
                : Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, radius * 2, radius * 2);
        }

        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        // Extra padding: rounded corners need breathing room, and the glow is
        // drawn outside the plate, so without room here it gets clipped.
        int glowRoom = glowColor != null ? 6 : 0;
        return new Dimension(size.width + 22 + glowRoom * 2,
            Math.max(size.height + 8 + glowRoom * 2, 34 + glowRoom * 2));
    }

    /** Per-channel RGB blend; alpha is taken from [from]. */
    private static Color blend(Color from, Color to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * f);
        int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * f);
        int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * f);
        int a = (int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * f);
        return new Color(clamp(r), clamp(g), clamp(b), clamp(a));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
