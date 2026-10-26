package com.vibe.launcher.ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The left navigation column: brand mark on top, one row per screen with
 * unicode icons, the signed-in account pinned to the bottom with an avatar.
 */
public final class SidebarNav extends JPanel {

    /** One navigation entry with an icon and label. */
    public static final class Item {
        final String id;
        final String icon;
        final String label;

        public Item(String id, String label) {
            this.id = id;
            this.label = label;
            this.icon = defaultIcon(id);
        }

        public Item(String id, String icon, String label) {
            this.id = id;
            this.icon = icon;
            this.label = label;
        }

        private static String defaultIcon(String id) {
            if (id == null) return "•";
            return switch (id.toLowerCase()) {
                case "home" -> "\u2302";     // ⌂
                case "versions" -> "\uD83C\uDFAE"; // 🎮
                case "log" -> "\uD83D\uDCCB";      // 📋
                case "settings" -> "\u2699";  // ⚙
                case "mods" -> "\uD83E\uDDE9";     // 🧩
                case "cosmetics" -> "\uD83D\uDC55";// 👕
                case "server", "vibe" -> "\uD83C\uDF10"; // 🌐
                case "store" -> "\uD83D\uDED2";   // 🛒
                default -> "\u25CF";
            };
        }
    }

    private static final int WIDTH = 220;
    private static final int ROW_H = 44;
    private static final int PAD_X = 14;

    private final List<Item> items = new ArrayList<>();
    private final Consumer<String> onSelect;

    private String selected = "home";
    private int hoverIndex = -1;

    private final JLabel userName = new JLabel("Not signed in");
    private final JLabel userStatus = new JLabel("offline");
    private final AvatarPanel avatarPanel = new AvatarPanel();

    private Runnable onAccount;

    public SidebarNav(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(WIDTH, 100));
        setMinimumSize(new Dimension(WIDTH, 100));

        add(buildBrand(), BorderLayout.NORTH);
        add(buildUserCard(), BorderLayout.SOUTH);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = indexAt(e.getY());
                if (index != hoverIndex) {
                    hoverIndex = index;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int index = indexAt(e.getY());
                if (index < 0) return;
                selected = items.get(index).id;
                repaint();
                onSelect.accept(selected);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        repaint();
    }

    public void setSelected(String id) {
        selected = id;
        repaint();
    }

    public void setAccount(String name, String status) {
        userName.setText(name == null || name.isBlank() ? "Not signed in" : name);
        userStatus.setText(status == null || status.isBlank() ? "offline" : status);
        avatarPanel.setInitial(name != null && !name.isBlank() ? name.substring(0, 1).toUpperCase() : "?");
        avatarPanel.repaint();
    }

    public void setOnAccount(Runnable onAccount) {
        this.onAccount = onAccount;
    }

    private JComponent buildBrand() {
        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.setBorder(Theme.padding(20, PAD_X, 16, PAD_X));

        VibeLogo mark = new VibeLogo(32);
        JLabel text = new JLabel("VIBE");
        text.setFont(Theme.TITLE);
        text.setForeground(Theme.ACCENT);

        brand.add(mark, BorderLayout.WEST);
        brand.add(text, BorderLayout.CENTER);
        return brand;
    }

    private JComponent buildUserCard() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(Theme.padding(0, 12, 16, 12));

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                g2.setColor(Theme.GLASS);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(139, 92, 246, 70));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(10, 0));
        card.setBorder(Theme.padding(12, 12, 12, 12));

        avatarPanel.setPreferredSize(new Dimension(36, 36));
        card.add(avatarPanel, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        userName.setFont(Theme.BODY_BOLD);
        userName.setForeground(Theme.TEXT);
        userName.setAlignmentX(LEFT_ALIGNMENT);

        userStatus.setFont(Theme.SMALL);
        userStatus.setForeground(Theme.TEXT_DIM);
        userStatus.setAlignmentX(LEFT_ALIGNMENT);

        info.add(userName);
        info.add(Box.createVerticalStrut(2));
        info.add(userStatus);

        card.add(info, BorderLayout.CENTER);

        RoundedButton switchAccount = RoundedButton.secondary("Account");
        switchAccount.addActionListener(e -> {
            if (onAccount != null) onAccount.run();
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(Theme.padding(8, 0, 0, 0));
        bottom.add(switchAccount, BorderLayout.CENTER);

        JPanel mainWrapper = new JPanel(new BorderLayout());
        mainWrapper.setOpaque(false);
        mainWrapper.add(card, BorderLayout.CENTER);
        mainWrapper.add(bottom, BorderLayout.SOUTH);

        return mainWrapper;
    }

    private static final class AvatarPanel extends JPanel {
        private String initial = "V";

        void setInitial(String s) {
            if (s != null && !s.isEmpty()) {
                this.initial = s.substring(0, 1).toUpperCase();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.prepare(g);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.ACCENT_DIM);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(Theme.ACCENT);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            g2.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 16));
            g2.setColor(Color.WHITE);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int sw = fm.stringWidth(initial);
            int sh = fm.getAscent();
            g2.drawString(initial, (w - sw) / 2, (h + sh) / 2 - 2);
            g2.dispose();
        }
    }

    private int indexAt(int y) {
        int top = rowsTop();
        if (y < top) return -1;
        int index = (y - top) / ROW_H;
        return index >= 0 && index < items.size() ? index : -1;
    }

    private int rowsTop() {
        return 20 + 32 + 16;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        int w = getWidth();
        int h = getHeight();

        g2.setColor(new Color(8, 9, 16, 210));
        g2.fillRect(0, 0, w, h);
        g2.setColor(Theme.BORDER);
        g2.drawLine(w - 1, 0, w - 1, h);

        int y = rowsTop();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean active = item.id.equals(selected);
            boolean hovered = i == hoverIndex && !active;

            if (active) {
                g2.setColor(new Color(139, 92, 246, 40));
                g2.fillRoundRect(10, y + 2, w - 20, ROW_H - 4, 10, 10);
                g2.setColor(new Color(139, 92, 246, 180));
                g2.drawRoundRect(10, y + 2, w - 21, ROW_H - 5, 10, 10);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(10, y + 8, 4, ROW_H - 16, 4, 4);
            } else if (hovered) {
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fillRoundRect(10, y + 2, w - 20, ROW_H - 4, 10, 10);
            }

            g2.setFont(Theme.BODY);
            g2.setColor(active ? Theme.ACCENT_2 : Theme.TEXT_DIM);
            int textY = y + (ROW_H + g2.getFontMetrics().getAscent()) / 2 - 2;
            g2.drawString(item.icon, PAD_X + 4, textY);

            g2.setFont(active ? Theme.BODY_BOLD : Theme.BODY);
            g2.setColor(active ? Color.WHITE : Theme.TEXT_DIM);
            g2.drawString(item.label, PAD_X + 32, textY);

            y += ROW_H;
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
