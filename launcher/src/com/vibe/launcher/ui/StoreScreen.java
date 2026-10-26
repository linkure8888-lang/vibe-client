package com.vibe.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The cosmetics store: capes and looks, local-first.
 *
 * Every item here is owned by us and rendered locally. There is no server to
 * buy from: an item is "installed" by writing its id into the launcher
 * config, and the in-game client reads the same id and draws the cape with
 * its own procedural geometry. One id, two renderers, no asset downloads,
 * nothing to license.
 *
 * The catalogue itself is procedural too. Each cape is painted from a small
 * recipe (base colour, gradient partner, pattern) rather than a PNG: the
 * preview in the launcher and the cape in game are generated from the same
 * numbers, so they can never drift apart, and the whole store costs a few
 * kilobytes of code instead of megabytes of art.
 */
public final class StoreScreen extends JPanel {

    /** One purchasable look. Everything needed to draw it on both sides. */
    public static final class Cosmetic {
        public final String id;
        public final String name;
        public final String rarity;
        public final int price;
        /** True when the price is zero and the item can be equipped at will. */
        public final boolean free;
        public final Color base;
        public final Color accent;
        public final Pattern pattern;

        Cosmetic(String id, String name, String rarity, int price,
                 Color base, Color accent, Pattern pattern) {
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            this.price = price;
            this.free = price == 0;
            this.base = base;
            this.accent = accent;
            this.pattern = pattern;
        }
    }

    /** The pattern stamped over the cape's base gradient. */
    public enum Pattern {
        NONE, STAR, STREAK, GRID, WAVE
    }

    /** Reports which cosmetic id the user equipped; null means "unequipped". */
    public interface Listener {
        void onEquip(String cosmeticId);
    }

    /** The whole catalogue. Order is the display order. */
    static List<Cosmetic> catalogue() {
        List<Cosmetic> list = new ArrayList<>();
        list.add(new Cosmetic("vibe-classic", "Vibe Classic", "Starter", 0,
            new Color(0x8B5CF6), new Color(0x22D3EE), Pattern.STREAK));
        list.add(new Cosmetic("obsidian-dusk", "Obsidian Dusk", "Common", 0,
            new Color(0x3B4266), new Color(0x8B5CF6), Pattern.WAVE));
        list.add(new Cosmetic("aurora-bloom", "Aurora Bloom", "Rare", 490,
            new Color(0x22D3EE), new Color(0xF472B6), Pattern.WAVE));
        list.add(new Cosmetic("nova-flare", "Nova Flare", "Rare", 490,
            new Color(0xF472B6), new Color(0xFFD166), Pattern.STAR));
        list.add(new Cosmetic("starfield", "Starfield", "Epic", 990,
            new Color(0x101326), new Color(0xE7E9FF), Pattern.STAR));
        list.add(new Cosmetic("signal-grid", "Signal Grid", "Epic", 990,
            new Color(0x1B2A4A), new Color(0x22D3EE), Pattern.GRID));
        list.add(new Cosmetic("vibe-aurora", "Vibe Aurora", "Legendary", 1490,
            new Color(0x8B5CF6), new Color(0x22D3EE), Pattern.WAVE));
        list.add(new Cosmetic("solar-crown", "Solar Crown", "Legendary", 1490,
            new Color(0xF59E0B), new Color(0xF472B6), Pattern.STAR));
        return list;
    }

    private final Listener listener;
    private final JLabel equippedLabel = new JLabel(" ");

    /** Row of item cards; rebuilt when the equipped id changes. */
    private JPanel grid;

    public StoreScreen(Listener listener) {
        this.listener = listener;
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

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

    /** Redraws the grid with [equippedId] marked as worn. */
    public void setEquipped(String equippedId) {
        equippedLabel.setText(equippedId == null || equippedId.isBlank()
            ? "No cape equipped" : "Equipped: " + nameOf(equippedId));
        rebuildGrid(equippedId);
    }

    private static String nameOf(String id) {
        for (Cosmetic c : catalogue()) {
            if (c.id.equals(id)) return c.name;
        }
        return id;
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
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        content.add(buildHeader(), c);

        c.gridy = 1;
        c.insets = new java.awt.Insets(14, 0, 0, 0);
        grid = new JPanel(new GridLayout(0, 4, 14, 14));
        grid.setOpaque(false);
        content.add(grid, c);

        c.gridy = 2;
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

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 4)) {
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
        header.setOpaque(false);
        header.setBorder(Theme.padding(16, 18, 16, 18));

        JLabel title = new JLabel("Cosmetics");
        title.setFont(Theme.TITLE);
        title.setForeground(Theme.TEXT);

        equippedLabel.setFont(Theme.BODY);
        equippedLabel.setForeground(Theme.ACCENT_2);

        JPanel left = new JPanel(new GridLayout(0, 1, 0, 2));
        left.setOpaque(false);
        left.add(title);
        left.add(new JLabel(" ") {
            @Override protected void paintComponent(Graphics g) { }
        });
        // The one-line brief lives as a plain label so the header stays a
        // single row tall even when the window is narrow.
        JLabel brief = new JLabel("Local cosmetics — capes are drawn by the client, no downloads.");
        brief.setFont(Theme.SMALL);
        brief.setForeground(Theme.TEXT_FAINT);
        left.add(brief);
        left.add(equippedLabel);

        header.add(left, BorderLayout.CENTER);

        RoundedButton unequip = RoundedButton.secondary("Unequip");
        unequip.addActionListener(e -> {
            if (listener != null) listener.onEquip(null);
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(unequip);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private void rebuildGrid(String equippedId) {
        grid.removeAll();
        for (Cosmetic item : catalogue()) {
            grid.add(new ItemCard(item, item.id.equals(equippedId), id -> {
                if (listener != null) listener.onEquip(id);
            }));
        }
        grid.revalidate();
        grid.repaint();
    }

    // =======================================================================
    // Item card
    // =======================================================================

    /**
     * One catalogue entry: preview, name, rarity chip, price and an equip
     * button. The preview is the same procedural recipe the in-game renderer
     * uses, so what the user equips here is exactly what appears in game.
     */
    private static final class ItemCard extends JPanel {

        private final Cosmetic item;
        private final boolean equipped;
        private boolean hovered;

        ItemCard(Cosmetic item, boolean equipped, Consumer<String> onEquip) {
            this.item = item;
            this.equipped = equipped;
            setOpaque(false);
            setLayout(new BorderLayout(0, 8));
            setBorder(Theme.padding(10, 10, 12, 10));

            CapePreview preview = new CapePreview(item);
            add(preview, BorderLayout.CENTER);

            JPanel meta = new JPanel(new GridLayout(0, 1, 0, 2));
            meta.setOpaque(false);

            JLabel name = new JLabel(item.name);
            name.setFont(Theme.BODY_BOLD);
            name.setForeground(Theme.TEXT);
            meta.add(name);

            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            chips.setOpaque(false);
            chips.add(rarityChip(item.rarity));
            if (item.free) {
                chips.add(priceChip("Free", Theme.OK));
            } else {
                chips.add(priceChip(item.price + " V", Theme.ACCENT));
            }
            meta.add(chips);

            RoundedButton action = equipped
                ? RoundedButton.secondary("Worn")
                : RoundedButton.primary("Equip");
            action.setEnabled(!equipped);
            if (!equipped) {
                action.addActionListener(e -> onEquip.accept(item.id));
            }
            meta.add(action);

            add(meta, BorderLayout.SOUTH);

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
                    if (!equipped) onEquip.accept(item.id);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        private boolean inside(MouseEvent e) {
            return e.getX() >= 0 && e.getX() < getWidth()
                && e.getY() >= 0 && e.getY() < getHeight();
        }

        @Override
        public void addImpl(java.awt.Component component, Object constraints, int index) {
            super.addImpl(component, constraints, index);
            if (component instanceof JComponent child) {
                child.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.prepare(g);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(Theme.GLASS);
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            if (hovered) {
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fillRoundRect(0, 0, w, h, 14, 14);
            }

            g2.setPaint(new java.awt.GradientPaint(14, 1,
                new Color(255, 255, 255, hovered ? 30 : 18),
                w - 14, 1, new Color(255, 255, 255, 0)));
            g2.fillRect(14, 1, w - 28, 1);

            g2.setColor(equipped
                ? new Color(0x22D3EEE & 0x00FFFFFF | (170 << 24))
                : hovered ? new Color(139, 92, 246, 150) : Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

            // Equipped cards wear a small accent tick in the corner so the
            // state is visible even before the button text is read.
            if (equipped) {
                g2.setColor(Theme.ACCENT_2);
                g2.fillOval(w - 22, 10, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** The rarity chip, colour-coded like the in-game quality tiers. */
    private static JLabel rarityChip(String text) {
        Color tint = switch (text) {
            case "Common" -> new Color(0x9CA3AF);
            case "Rare" -> new Color(0x22D3EE);
            case "Epic" -> new Color(0x8B5CF6);
            case "Legendary" -> new Color(0xF472B6);
            default -> Theme.TEXT_DIM;
        };
        return chip(text, tint);
    }

    private static JLabel priceChip(String text, Color tint) {
        return chip(text, tint);
    }

    /** One small rounded chip label. */
    private static JLabel chip(String text, Color tint) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 36));
                g2.fillRoundRect(0, 0, w, h, h, h);
                g2.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 150));
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(Theme.SMALL);
        label.setForeground(tint);
        label.setBorder(Theme.padding(1, 8, 1, 8));
        return label;
    }

    // =======================================================================
    // Cape preview
    // =======================================================================

    /**
     * Draws the cape itself: base gradient, pattern stamp, soft inner light
     * and a swinging-motion gradient so a static preview still reads as cloth.
     *
     * The geometry mirrors the in-game cape model (10x16 units at 64x32
     * texture scale), squashed into the card. The recipe (base, accent,
     * pattern) is the whole item definition; there is no image anywhere.
     */
    private static final class CapePreview extends JComponent {

        private final Cosmetic item;

        CapePreview(Cosmetic item) {
            this.item = item;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 150);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.prepare(g);
            int w = getWidth();
            int h = getHeight();

            // The cloth: a rounded slab wider than tall, proportions of the
            // vanilla cape model, with a collar notch cut from the top.
            int clothW = Math.min(w - 8, 120);
            int clothH = Math.min(h - 8, 150);
            int x = (w - clothW) / 2;
            int y = (h - clothH) / 2;

            Path2D cloth = new Path2D.Float(Path2D.WIND_NON_ZERO);
            cloth.moveTo(x + 6, y);
            cloth.lineTo(x + clothW - 6, y);
            cloth.curveTo(x + clothW, y, x + clothW, y + 4, x + clothW, y + 8);
            cloth.lineTo(x + clothW, y + clothH - 10);
            cloth.curveTo(x + clothW, y + clothH - 4, x + clothW - 4, y + clothH,
                x + clothW - 10, y + clothH);
            cloth.lineTo(x + 10, y + clothH);
            cloth.curveTo(x + 4, y + clothH, x, y + clothH - 4, x, y + clothH - 10);
            cloth.lineTo(x, y + 8);
            cloth.curveTo(x, y + 4, x, y, x + 6, y);
            cloth.closePath();

            // Base gradient: the item colour shading toward obsidian.
            g2.setPaint(new java.awt.GradientPaint(x, y, item.base,
                x, y + clothH, new Color(0x0A0B12)));
            g2.fill(cloth);

            // Pattern stamp, clipped to the cloth.
            g2.clip(cloth);
            switch (item.pattern) {
                case STAR -> drawStars(g2, x, y, clothW, clothH);
                case STREAK -> drawStreaks(g2, x, y, clothW, clothH);
                case GRID -> drawGrid(g2, x, y, clothW, clothH);
                case WAVE -> drawWaves(g2, x, y, clothW, clothH);
                case NONE -> { }
            }

            // Accent tint across the lower third, the "light from below" cue.
            g2.setPaint(new java.awt.GradientPaint(
                x, y + clothH * 0.55f, new Color(item.accent.getRed(),
                    item.accent.getGreen(), item.accent.getBlue(), 0),
                x, y + clothH, new Color(item.accent.getRed(),
                    item.accent.getGreen(), item.accent.getBlue(), 90)));
            g2.fill(cloth);

            // Diagonal sheen, reading as the fold of the cloth.
            g2.setPaint(new java.awt.LinearGradientPaint(
                x, y, x + clothW, y + clothH,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 255, 255, 0),
                    new Color(255, 255, 255, 26),
                    new Color(255, 255, 255, 0)}));
            g2.fill(cloth);

            g2.dispose();
            super.paintComponent(g);
        }

        private void drawStars(Graphics2D g2, int x, int y, int w, int h) {
            // Deterministic pseudo-scatter: a fixed lattice with a hash-based
            // drop, so the same item always previews identically.
            java.util.Random rng = new java.util.Random(item.id.hashCode());
            g2.setColor(new Color(item.accent.getRed(), item.accent.getGreen(),
                item.accent.getBlue(), 170));
            for (int i = 0; i < 26; i++) {
                int sx = x + rng.nextInt(Math.max(1, w));
                int sy = y + rng.nextInt(Math.max(1, h));
                int r = 1 + rng.nextInt(2);
                g2.fillOval(sx, sy, r, r);
            }
            // One larger accent star as the focal point.
            int fx = x + w / 2 - 6;
            int fy = y + h / 3 - 6;
            GeneralPath star = new GeneralPath();
            star.moveTo(fx + 6, fy);
            star.lineTo(fx + 7.4f, fy + 4.1f);
            star.lineTo(fx + 11.7f, fy + 4.7f);
            star.lineTo(fx + 8.6f, fy + 7.8f);
            star.lineTo(fx + 9.5f, fy + 12f);
            star.lineTo(fx + 6, fy + 10f);
            star.lineTo(fx + 2.5f, fy + 12f);
            star.lineTo(fx + 3.4f, fy + 7.8f);
            star.lineTo(fx + 0.3f, fy + 4.7f);
            star.lineTo(fx + 4.6f, fy + 4.1f);
            star.closePath();
            g2.fill(star);
        }

        private void drawStreaks(Graphics2D g2, int x, int y, int w, int h) {
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
            for (int i = 0; i < 3; i++) {
                g2.setColor(new Color(item.accent.getRed(), item.accent.getGreen(),
                    item.accent.getBlue(), 60 + i * 30));
                int sx = x + 8 + i * (w - 20) / 3;
                GeneralPath streak = new GeneralPath();
                streak.moveTo(sx, y + 2);
                streak.quadTo(sx + 10, y + h / 2f, sx - 4, y + h - 2);
                g2.setStroke(new java.awt.BasicStroke(3f - i));
                g2.draw(streak);
            }
        }

        private void drawGrid(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(new Color(item.accent.getRed(), item.accent.getGreen(),
                item.accent.getBlue(), 70));
            g2.setStroke(new java.awt.BasicStroke(1f));
            for (int gx = x + 6; gx < x + w; gx += 10) {
                g2.drawLine(gx, y, gx, y + h);
            }
            for (int gy = y + 6; gy < y + h; gy += 10) {
                g2.drawLine(x, gy, x + w, gy);
            }
        }

        private void drawWaves(Graphics2D g2, int x, int y, int w, int h) {
            g2.setStroke(new java.awt.BasicStroke(2.5f));
            for (int i = 0; i < 4; i++) {
                g2.setColor(new Color(item.accent.getRed(), item.accent.getGreen(),
                    item.accent.getBlue(), 50 + i * 25));
                GeneralPath wave = new GeneralPath();
                float wy = y + 14 + i * (h - 28) / 3f;
                wave.moveTo(x, wy);
                wave.quadTo(x + w / 4f, wy - 7, x + w / 2f, wy);
                wave.quadTo(x + 3f * w / 4f, wy + 7, x + w, wy);
                g2.draw(wave);
            }
        }
    }
}
