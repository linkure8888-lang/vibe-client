package com.vibe.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractAction;
import javax.swing.Timer;

/**
 * The "Vibe Network" screen: our own Minecraft server, later phase.
 *
 * This screen exists to announce the server before it exists — the same slot
 * Badlion and Lunar reserve for their networks — but it must never pretend
 * to be usable. Every interactive-looking element here is explicitly marked
 * "in development": the connect panel is a disabled mock, the news list is
 * closed with the same notice, and the address line is intentionally not
 * copyable text.
 *
 * The planet is drawn, not loaded: same palette and rim-light trick as the
 * backdrop's planet, animated with one slow timer that stops when the window
 * is hidden (removeNotify), so a background launcher costs nothing.
 */
public final class ServerScreen extends JPanel {

    public ServerScreen() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        content.add(buildHero(), c);

        c.gridy = 1;
        c.insets = new java.awt.Insets(16, 0, 0, 0);
        content.add(buildConnectPanel(), c);

        c.gridy = 2;
        c.insets = new java.awt.Insets(14, 0, 0, 0);
        content.add(buildNewsPanel(), c);

        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        content.add(filler, c);

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        setBorder(Theme.padding(16, 18, 16, 18));
    }

    // =======================================================================
    // Hero
    // =======================================================================

    /** Brand + the development notice, over an animated planet. */
    private JComponent buildHero() {
        JPanel hero = new JPanel(new BorderLayout(0, 4)) {
            private float phase = 0f;
            private Timer timer;

            @Override
            public void addNotify() {
                super.addNotify();
                if (timer == null) {
                    timer = new Timer(40, new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            phase += 0.012f;
                            repaint();
                        }
                    });
                    timer.setRepeats(true);
                }
                timer.start();
            }

            @Override
            public void removeNotify() {
                if (timer != null) timer.stop();
                super.removeNotify();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(Theme.GLASS);
                g2.fillRoundRect(0, 0, w, h, 14, 14);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
                drawPlanet(g2, w, h);
                g2.dispose();
                super.paintComponent(g);
            }

            /** A slowly ringed planet in the upper right, matching the
             *  backdrop's subject so the screen reads as part of the client. */
            private void drawPlanet(Graphics2D g2, int w, int h) {
                int r = (int) (h * 0.42f);
                int cx = w - r - 34;
                int cy = r + 26;

                // Body: near-black sphere with a violet limb.
                g2.setPaint(new java.awt.RadialGradientPaint(
                    new java.awt.geom.Point2D.Float(cx - r / 3f, cy - r / 3f), r,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0x24, 0x27, 0x3A), new Color(0x0B, 0x0D, 0x15)}));
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);

                // Lit rim, animated so the light slowly travels the edge.
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
                double angle = phase * Math.PI * 2;
                int rimAlpha = 120 + (int) (60 * Math.sin(angle));
                g2.setColor(new Color(0x8B, 0x5C, 0xF6, Math.max(60, rimAlpha)));
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);

                // Tilted ring: an ellipse squashed to a line and rotated.
                java.awt.geom.AffineTransform old = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(-0.42);
                g2.scale(1, 0.24);
                g2.setColor(new Color(0x22, 0xD3, 0xEE, 90));
                g2.setStroke(new java.awt.BasicStroke(3.5f));
                g2.drawOval(-(int) (r * 1.45), -(int) (r * 1.45),
                    (int) (r * 2.9), (int) (r * 2.9));
                g2.setTransform(old);
            }
        };
        hero.setOpaque(false);
        hero.setBorder(Theme.padding(26, 26, 26, 26));

        JPanel text = new JPanel(new GridBagLayout());
        text.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;

        JLabel badge = new JLabel("IN DEVELOPMENT") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(new Color(0xFB, 0xBF, 0x24, 34));
                g2.fillRoundRect(0, 0, w, h, h, h);
                g2.setColor(new Color(0xFB, 0xBF, 0x24, 170));
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        badge.setForeground(Theme.WARN);
        badge.setBorder(Theme.padding(2, 10, 2, 10));

        JLabel title = new JLabel("Vibe Network");
        title.setFont(Theme.TITLE);
        title.setForeground(Theme.TEXT);

        JLabel subtitle = new JLabel(
            "<html><body style='width:420px'>Our own Minecraft server with Vibe-native modes, "
                + "cosmetics and progression. The launcher and the client are built "
                + "first; the network is the next phase.</body></html>");
        subtitle.setFont(Theme.BODY);
        subtitle.setForeground(Theme.TEXT_DIM);

        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 8, 0);
        text.add(badge, c);
        c.gridy = 1;
        c.insets = new java.awt.Insets(0, 0, 6, 0);
        text.add(title, c);
        c.gridy = 2;
        text.add(subtitle, c);

        hero.add(text, BorderLayout.CENTER);
        return hero;
    }

    // =======================================================================
    // Connect panel
    // =======================================================================

    /**
     * The connect row: deliberately inert.
     *
     * The address is shown as a label, not a text field, so it cannot be
     * selected, copied or misread as live. The Join button exists because a
     * connect panel without one looks broken, but it is disabled and its
     * tooltip says exactly why.
     */
    private JComponent buildConnectPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(Theme.GLASS);
                g2.fillRoundRect(0, 0, w, h, 14, 14);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(Theme.padding(16, 18, 16, 18));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);

        JLabel address = new JLabel("play.vibenetwork.gg") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(new Color(0x0E, 0x10, 0x13));
                g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        address.setFont(Theme.MONO);
        address.setForeground(Theme.TEXT_FAINT);
        address.setBorder(Theme.padding(6, 12, 6, 12));
        row.add(address);

        JLabel offline = new JLabel("offline • opening soon");
        offline.setFont(Theme.SMALL);
        offline.setForeground(Theme.TEXT_FAINT);
        row.add(offline);

        panel.add(row, BorderLayout.CENTER);

        RoundedButton join = RoundedButton.primary("Join");
        join.setEnabled(false);
        join.setToolTipText("The server is still in development.");
        panel.add(join, BorderLayout.EAST);
        return panel;
    }

    // =======================================================================
    // News panel
    // =======================================================================

    private JComponent buildNewsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(Theme.GLASS);
                g2.fillRoundRect(0, 0, w, h, 14, 14);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(Theme.padding(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout(0, 2));
        top.setOpaque(false);
        JLabel title = new JLabel("What is coming");
        title.setFont(Theme.BODY_BOLD);
        title.setForeground(Theme.TEXT);
        top.add(title, BorderLayout.NORTH);

        RoundedButton follow = RoundedButton.secondary("Follow progress");
        follow.setToolTipText("Development log ships with every launcher build.");
        top.add(follow, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        JPanel list = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        list.setOpaque(false);
        list.add(newsLine("1.  Vibe-native mini-modes built around the client's HUD."));
        list.add(newsLine("2.  Server-side progression for cosmetics and rank."));
        list.add(newsLine("3.  One-click join from this launcher when the network opens."));
        panel.add(list, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel newsLine(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.BODY);
        label.setForeground(Theme.TEXT_DIM);
        return label;
    }
}
