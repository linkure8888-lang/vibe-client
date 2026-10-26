package com.vibe.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrolling grid of version cards with artwork, in the Lunar style.
 *
 * Why a custom component and not JList: JList can only paint one column of
 * uniform rows. The design calls for a responsive grid of image tiles, and
 * forcing that through a list renderer means fighting cell sizes, selection
 * painting and scroll units at every step. A JComponent that lays its own cells
 * out is less code and behaves exactly as drawn.
 *
 * Layout: cards are a fixed 208x117 (16:9), the grid has a fixed gutter, and
 * the number of columns is computed from the width so the grid reflows when the
 * window is resized. The height comes from the row count so the scroll pane
 * knows how far it can scroll.
 *
 * Painting: the image is drawn clipped to a rounded rect, then a scrim, then
 * the labels. Hover and selection both brighten the card, with selection using
 * the accent colour and hover using white, so "where is my mouse" and "what is
 * chosen" never look the same.
 */
public final class VersionGrid extends JComponent implements Scrollable {

    private static final int CARD_W = 208;
    private static final int CARD_H = 117;
    private static final int GAP = 10;
    private static final int PAD = 4;
    private static final int ARC = 10;

    private final List<VersionCard> cards = new ArrayList<>();

    /** Index of the selected card, or -1. */
    private int selected = -1;
    /** Index under the pointer, or -1. */
    private int hovered = -1;

    private Consumer<VersionCard> onSelect;

    public VersionGrid() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int next = indexAt(e.getX(), e.getY());
                if (next != hovered) {
                    hovered = next;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hovered != -1) {
                    hovered = -1;
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int index = indexAt(e.getX(), e.getY());
                if (index < 0) return;
                select(index);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Called whenever the user picks a card. */
    public void setOnSelect(Consumer<VersionCard> listener) {
        this.onSelect = listener;
    }

    /** Replaces the whole grid; keeps the selection if that id still exists. */
    public void setCards(List<VersionCard> newCards) {
        String keep = selected >= 0 && selected < cards.size() ? cards.get(selected).id : null;
        cards.clear();
        cards.addAll(newCards);
        selected = -1;
        if (keep != null) {
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).id.equals(keep)) {
                    selected = i;
                    break;
                }
            }
        }
        revalidate();
        repaint();
    }

    /** Selects the card with [id] if present, without firing the callback. */
    public void selectById(String id) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id.equals(id)) {
                selected = i;
                repaint();
                return;
            }
        }
    }

    private void select(int index) {
        if (index < 0 || index >= cards.size()) return;
        selected = index;
        repaint();
        if (onSelect != null) onSelect.accept(cards.get(index));
    }

    // -- Geometry ---------------------------------------------------------

    private int columns() {
        int usable = Math.max(CARD_W, getWidth() - PAD * 2);
        return Math.max(1, (usable + GAP) / (CARD_W + GAP));
    }

    private Rectangle boundsOf(int index) {
        int cols = columns();
        int col = index % cols;
        int row = index / cols;
        return new Rectangle(
            PAD + col * (CARD_W + GAP),
            PAD + row * (CARD_H + GAP),
            CARD_W, CARD_H);
    }

    private int indexAt(int x, int y) {
        for (int i = 0; i < cards.size(); i++) {
            if (boundsOf(i).contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        int cols = columns();
        int rows = Math.max(1, (cards.size() + cols - 1) / cols);
        return new Dimension(
            PAD * 2 + cols * CARD_W + (cols - 1) * GAP,
            PAD * 2 + rows * CARD_H + (rows - 1) * GAP);
    }

    // -- Painting ---------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        for (int i = 0; i < cards.size(); i++) {
            paintCard(g2, i, cards.get(i));
        }
        g2.dispose();
    }

    private void paintCard(Graphics2D g2, int index, VersionCard card) {
        Rectangle r = boundsOf(index);
        boolean isSelected = index == selected;
        boolean isHovered = index == hovered;

        // Shadow, then the image clipped to the rounded shape.
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillRoundRect(r.x, r.y + 2, r.width, r.height, ARC, ARC);

        Graphics2D clip = (Graphics2D) g2.create();
        clip.setClip(new java.awt.geom.RoundRectangle2D.Float(
            r.x, r.y, r.width, r.height, ARC, ARC));

        BufferedImage art = card.art;
        if (art != null) {
            clip.drawImage(art, r.x, r.y, r.width, r.height, null);
        } else {
            clip.setColor(Theme.PANEL_LIGHT);
            clip.fillRect(r.x, r.y, r.width, r.height);
        }

        // Bottom scrim so the version name stays readable on any art.
        for (int y = 0; y < r.height; y++) {
            float t = y / (float) Math.max(1, r.height - 1);
            int a = (int) (Math.max(0f, (t - 0.45f) / 0.55f) * 215);
            if (a <= 0) continue;
            clip.setColor(new Color(6, 7, 14, a));
            clip.drawLine(r.x, r.y + y, r.x + r.width, r.y + y);
        }

        // Hover / selection wash.
        if (isSelected) {
            clip.setColor(new Color(139, 92, 246, 70));
            clip.fillRect(r.x, r.y, r.width, r.height);
        } else if (isHovered) {
            clip.setColor(new Color(255, 255, 255, 26));
            clip.fillRect(r.x, r.y, r.width, r.height);
        }
        clip.dispose();

        // Border: accent when selected, bright when hovered, hairline otherwise.
        g2.setColor(isSelected ? Theme.ACCENT
            : isHovered ? Theme.BORDER_STRONG : Theme.BORDER);
        g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, ARC, ARC);

        // Labels.
        g2.setFont(Theme.BODY_BOLD);
        g2.setColor(Theme.TEXT);
        g2.drawString(card.label, r.x + 10, r.y + r.height - 22);

        if (card.suffix != null && !card.suffix.isEmpty()) {
            g2.setFont(Theme.SMALL);
            g2.setColor(isSelected ? Theme.ACCENT_2 : Theme.TEXT_DIM);
            g2.drawString(card.suffix, r.x + 10, r.y + r.height - 9);
        }
    }

    // -- Scrollable -------------------------------------------------------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        // One card row per wheel notch feels right; a pixel unit makes the grid
        // crawl and a page unit overshoots the row the user aimed at.
        return orientation == SwingConstants.VERTICAL ? CARD_H + GAP : 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // Reflow to the viewport width so the grid is always centred and never
        // scrolls horizontally.
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /** Wraps this grid in a borderless scroll pane with a transparent viewport. */
    public JScrollPane wrap() {
        JScrollPane scroll = new JScrollPane(this);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(CARD_H + GAP);
        scroll.getVerticalScrollBar().setOpaque(false);
        return scroll;
    }
}
