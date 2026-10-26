package com.vibe.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The landing screen, rebuilt in the Lunar / Badlion register.
 *
 * One large scene as the hero - the procedural cosmos painted behind the
 * brand, with the Vibe mark, the big "VIBE CLIENT" wordmark and the tagline
 * sitting on top - then the centred glowing PLAY button, the version and
 * account selectors underneath, then the Quick Info strip and the changelog
 * below.
 *
 * The screen owns no launcher state; it renders what it is told and reports
 * clicks through [Listener].
 */
public final class HomeScreen extends JPanel {

    public static final String CARD_VERSIONS = "versions";
    public static final String CARD_MODS = "mods";
    public static final String CARD_SETTINGS = "settings";
    public static final String CARD_STORE = "store";
    public static final String CARD_SERVER = "server";

    public interface Listener {
        void onPlay();
        void onOpenVersions();
        void onAccount();
        void onNavigate(String cardId);
    }

    private static final int ARC = 14;
    private static final String TAGLINE = "Play your way";
    private static final String COMING_SOON = "COMING SOON";
    private static final String LATEST_VERSION = "1.21.4";
    private static final String HERO_PATH = "/compressed/bg2.jpg";

    private final Listener listener;

    private final JLabel versionValue = new JLabel();
    private final JLabel accountValue = new JLabel();
    private final JLabel gameFolder = new JLabel(" ");
    private final RoundedButton playButton = RoundedButton.primary("PLAY");
    private final JLabel cosmeticValue = new JLabel("None");
    private final JPanel heroPanel;

    private final JLabel infoVersionValue = new JLabel(LATEST_VERSION);
    private final JLabel infoServerValue = new JLabel(COMING_SOON);
    private final JLabel infoAccountValue = new JLabel("Not signed in");

    public HomeScreen(Listener listener) {
        this.listener = listener;
        setOpaque(false);
        setLayout(new BorderLayout());

        playButton.addActionListener(e -> this.listener.onPlay());

        heroPanel = buildHero();

        JScrollPane scroll = new JScrollPane(buildContent());
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
    }

    // =======================================================================
    // State
    // =======================================================================

    public void setVersion(String version) {
        versionValue.setText(version == null || version.isBlank() ? "Not selected" : version);
        infoVersionValue.setText(version == null || version.isBlank() ? LATEST_VERSION : version);
    }

    public void setAccount(String name, String status) {
        accountValue.setText(name == null || name.isBlank() ? "Not signed in" : name);
        accountValue.setToolTipText(status);
        infoAccountValue.setText(accountValue.getText());
    }

    public void setGameFolder(String path) {
        gameFolder.setText(path == null || path.isBlank() ? " " : "Game folder: " + path);
    }

    public void setPlayEnabled(boolean enabled) {
        playButton.setEnabled(enabled);
    }

    public void setCosmetic(String id) {
        cosmeticValue.setText(id == null || id.isBlank() ? "None" : id);
    }

    // =======================================================================
    // Layout
    // =======================================================================

    private JComponent buildContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        content.add(heroPanel, c);

        c.gridy = 1;
        c.insets = new Insets(16, 0, 0, 0);
        content.add(buildQuickInfo(), c);

        c.gridy = 2;
        c.insets = new Insets(16, 0, 0, 0);
        content.add(buildChangelog(), c);

        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        content.add(filler, c);

        JPanel padded = new JPanel(new BorderLayout());
        padded.setOpaque(false);
        padded.setBorder(Theme.padding(16, 18, 16, 18));
        padded.add(content, BorderLayout.CENTER);
        return padded;
    }

    // =======================================================================
    // Hero: procedural cosmos, brand, PLAY, selectors
    // =======================================================================

    private JPanel buildHero() {
        JPanel hero = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();

                BufferedImage img = ImageStore.cover(HERO_PATH, w, h);
                if (img != null) {
                    g2.drawImage(img, 0, 0, null);
                } else {
                    g2.setPaint(new java.awt.GradientPaint(0, 0, Theme.HEADER_TOP,
                        0, h, Theme.HEADER_BOTTOM));
                    g2.fillRoundRect(0, 0, w, h, ARC, ARC);
                }

                g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(8, 9, 16, 60),
                    0, h, new Color(8, 9, 16, 215)));
                g2.fillRoundRect(0, 0, w, h, ARC, ARC);

                int rampX = (int) (w * 0.14);
                int rampW = (int) (w * 0.72);
                int steps = 24;
                for (int i = 0; i < steps; i++) {
                    int x0 = rampX + rampW * i / steps;
                    int x1 = rampX + rampW * (i + 1) / steps;
                    if (x1 <= x0) continue;
                    float f = i / (float) (steps - 1);
                    g2.setColor(new Color(
                        (int) (0x8B + (0x22 - 0x8B) * f),
                        (int) (0x5C + (0xD3 - 0x5C) * f),
                        (int) (0xF6 + (0xEE - 0xF6) * f)));
                    g2.fillRect(x0, h - 2, x1 - x0, 2);
                }

                g2.setColor(new Color(139, 92, 246, 80));
                g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(400, 380);
            }
        };
        hero.setOpaque(false);
        hero.setBorder(Theme.padding(20, 26, 20, 26));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.anchor = GridBagConstraints.CENTER;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 12, 0);
        hero.add(new VibeLogo(72), c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 2, 0);
        hero.add(buildWordmark(), c);

        c.gridy = 2;
        c.insets = new Insets(0, 0, 8, 0);
        hero.add(buildTagline(), c);

        c.gridy = 3;
        c.insets = new Insets(0, 0, 16, 0);
        hero.add(fixedSize(playButton, 340, 56), c);

        c.gridy = 4;
        c.insets = new Insets(0, 0, 12, 0);
        hero.add(buildVersionSelector(), c);

        c.gridy = 5;
        c.insets = new Insets(0, 0, 0, 0);
        hero.add(buildAccountSelector(), c);

        return hero;
    }

    private JComponent buildWordmark() {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);

        JLabel vibe = new JLabel("VIBE");
        vibe.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
        vibe.setForeground(Theme.ACCENT);

        JLabel client = new JLabel("CLIENT");
        client.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
        client.setForeground(Theme.TEXT);

        row.add(vibe);
        row.add(client);
        return row;
    }

    private JComponent buildTagline() {
        JLabel tagline = new JLabel(TAGLINE);
        tagline.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        tagline.setForeground(Theme.TEXT_DIM);
        return tagline;
    }

    // =======================================================================
    // Selectors
    // =======================================================================

    private JComponent buildVersionSelector() {
        return buildSelector("Version", versionValue,
            "Click to change the Minecraft version", listener::onOpenVersions);
    }

    private JComponent buildAccountSelector() {
        return buildSelector("Account", accountValue,
            "Click to sign in or switch account", listener::onAccount);
    }

    private JComponent buildSelector(String caption, JLabel value, String hint,
                                     Runnable onClick) {
        GlassCard card = new GlassCard(onClick);
        card.setLayout(new BorderLayout(0, 4));
        card.setBorder(Theme.padding(14, 16, 14, 16));

        JLabel title = new JLabel(caption.toUpperCase());
        title.setFont(Theme.SMALL);
        title.setForeground(Theme.ACCENT_2);

        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        value.setForeground(Theme.TEXT);

        JLabel hintLabel = new JLabel(hint);
        hintLabel.setFont(Theme.SMALL);
        hintLabel.setForeground(Theme.TEXT_FAINT);

        card.add(title, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        card.add(hintLabel, BorderLayout.SOUTH);
        return card;
    }

    // =======================================================================
    // Quick info + changelog
    // =======================================================================

    private JComponent buildQuickInfo() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BorderLayout(0, 12));

        JLabel title = new JLabel("Quick Info");
        title.setFont(Theme.BODY_BOLD);
        title.setForeground(Theme.TEXT);
        card.add(title, BorderLayout.NORTH);

        JPanel tiles = new JPanel(new GridLayout(1, 3, 12, 12));
        tiles.setOpaque(false);
        tiles.add(buildInfoTile("Latest version", infoVersionValue, Theme.TEXT));
        tiles.add(buildInfoTile("Server status", infoServerValue, Theme.WARN));
        tiles.add(buildInfoTile("Account", infoAccountValue, Theme.ACCENT_2));
        card.add(tiles, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildInfoTile(String caption, JLabel value, Color valueColor) {
        GlassCard tile = new GlassCard(null);
        tile.setLayout(new BorderLayout(0, 6));

        JLabel cap = new JLabel(caption.toUpperCase());
        cap.setFont(Theme.SMALL);
        cap.setForeground(Theme.ACCENT_2);

        JLabel val = new JLabel(value.getText());
        val.setFont(Theme.BODY_BOLD);
        val.setForeground(valueColor);

        tile.add(cap, BorderLayout.NORTH);
        tile.add(val, BorderLayout.CENTER);
        return tile;
    }

    private JComponent buildChangelog() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BorderLayout(0, 10));

        JLabel title = new JLabel("Changelog");
        title.setFont(Theme.BODY_BOLD);
        title.setForeground(Theme.TEXT);
        card.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel(new GridLayout(0, 1, 0, 6));
        list.setOpaque(false);
        list.add(changelogLine("1.1.0", "Motion Blur", "45"));
        list.add(changelogLine("1.0.9", "Nether Overhaul", "41"));
        list.add(changelogLine("1.0.8", "Cape Rendering", "39"));
        list.add(changelogLine("1.0.7", "Offline Mode", "38"));
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private JComponent changelogLine(String version, String name, String modules) {
        JLabel line = new JLabel(version + " - " + name + ", " + modules + " modules");
        line.setFont(Theme.BODY);
        line.setForeground(Theme.TEXT_DIM);
        return line;
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private static JComponent fixedSize(JComponent child, int width, int height) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setPreferredSize(new Dimension(width, height));
        holder.add(child, BorderLayout.CENTER);
        return holder;
    }

    // =======================================================================
    // Glass card
    // =======================================================================

    private static final class GlassCard extends JPanel {

        private final Runnable onClick;
        private boolean hovered;

        GlassCard(Runnable onClick) {
            this.onClick = onClick;
            setOpaque(false);
            if (onClick == null) return;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (inside(e)) return;
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) onClick.run();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private boolean inside(MouseEvent e) {
            return e.getX() >= 0 && e.getX() < getWidth()
                && e.getY() >= 0 && e.getY() < getHeight();
        }

        @Override
        public void addImpl(java.awt.Component component, Object constraints, int index) {
            super.addImpl(component, constraints, index);
            if (component instanceof JComponent child && onClick != null) {
                child.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.prepare(g);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(Theme.GLASS);
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
            if (hovered) {
                g2.setColor(new Color(139, 92, 246, 60));
                g2.fillRoundRect(0, 0, w, h, ARC, ARC);
            }

            g2.setColor(hovered ? new Color(139, 92, 246, 170) : new Color(139, 92, 246, 70));
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
