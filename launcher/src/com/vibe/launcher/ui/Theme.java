package com.vibe.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * One place for every colour, font and border.
 *
 * Direction: deep obsidian base, violet primary accent, cyan as its gradient
 * partner, pink kept for rare emphasis. Surfaces are translucent glass over the
 * animated backdrop so the window has depth instead of flat plates.
 */
public final class Theme {

    // -- Base surfaces -----------------------------------------------------
    public static final Color BG = new Color(0x0A0B12);
    public static final Color PANEL = new Color(0x161824);
    public static final Color PANEL_LIGHT = new Color(0x1E2131);
    /** Hover states for the rounded components. */
    public static final Color PANEL_HOVER = new Color(0x2A2E42);

    // -- Accent ------------------------------------------------------------
    public static final Color ACCENT = new Color(0x8B5CF6);
    public static final Color ACCENT_HOVER = new Color(0xA78BFA);
    public static final Color ACCENT_DIM = new Color(0x4C3A8A);
    /** Gradient partner: the signature violet -> cyan ramp. */
    public static final Color ACCENT_2 = new Color(0x22D3EE);
    /** Rare emphasis. */
    public static final Color ACCENT_3 = new Color(0xF472B6);

    /** Header/brand gradient endpoints. */
    public static final Color HEADER_TOP = new Color(0x14172A);
    public static final Color HEADER_BOTTOM = new Color(0x0B0D15);

    // -- Animated background ----------------------------------------------
    public static final Color BG_TOP = new Color(0x0D0F1A);
    public static final Color BG_BOTTOM = new Color(0x05060A);

    /** Second bloom colour; the pairing is what makes it read as neon. */
    public static final Color NEON_CYAN = new Color(0x22D3EE);
    /** Magenta accent used sparingly for contrast. */
    public static final Color NEON_MAGENTA = new Color(0xF472B6);

    // -- Text --------------------------------------------------------------
    public static final Color TEXT = new Color(0xF6F7FC);
    public static final Color TEXT_DIM = new Color(0xA2A6BB);
    public static final Color TEXT_FAINT = new Color(0x666A80);
    public static final Color TEXT_MUTED = TEXT_DIM;

    // -- Status ------------------------------------------------------------
    public static final Color OK = new Color(0x4ADE80);
    public static final Color WARN = new Color(0xFBBF24);
    public static final Color ERROR = new Color(0xF87171);

    // -- Lines -------------------------------------------------------------
    public static final Color BORDER = new Color(255, 255, 255, 30);
    public static final Color BORDER_STRONG = new Color(255, 255, 255, 70);

    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    public static final Font SUBTITLE = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    /**
     * Translucent glass plate used by every panel over the photo backdrop.
     *
     * 232 was tuned for a flat neon background where anything lighter let the
     * bloom bleed through the labels. Over a photo the trade flips: at 232 the
     * art is invisible and the window looks like a dark form again. 196 keeps
     * every label readable (the scrim already does most of that work) while
     * the scene behind clearly shows.
     */
    public static final Color GLASS = new Color(11, 13, 22, 196);

    private Theme() {
    }

    /**
     * A flat panel for sitting on the animated backdrop.
     *
     * Non-opaque on purpose: an opaque panel would hide the neon behind it and
     * the whole window would look flat again. The border still gives it edges.
     */
    public static JPanel panel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = prepare(g);
                g2.setColor(GLASS);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder());
        return panel;
    }

    /** A primary action button (accent background). */
    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setFont(BODY_BOLD);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        return button;
    }

    /** A secondary action button (dark background). */
    public static JButton button(String text) {
        JButton button = new JButton(text);
        button.setBackground(PANEL_LIGHT);
        button.setForeground(TEXT);
        button.setFont(BODY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_HOVER),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        return button;
    }

    /** Applies the dark background and text colour to a component tree. */
    public static void style(JComponent component) {
        component.setBackground(PANEL);
        component.setForeground(TEXT);
    }

    /**
     * Styles a text field or spinner so it stops looking like a 2005 form.
     *
     * Swing's default field is a white box with a bevel; on a dark panel that
     * is the single most jarring element. Field colours are set through the
     * component's own properties (Nimbus ignores UIManager overrides for these),
     * and the caret colour has to be set separately or it becomes invisible.
     */
    public static void styleField(JComponent field) {
        field.setBackground(PANEL_LIGHT);
        field.setForeground(TEXT);
        field.setFont(BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_HOVER),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        if (field instanceof javax.swing.JSpinner spinner) {
            // Nimbus paints the arrow buttons itself; only a UI delegate swap
            // removes them. See VibeSpinnerUI for why.
            spinner.setUI(VibeSpinnerUI.create());
        }
        if (field instanceof javax.swing.text.JTextComponent text) {
            text.setCaretColor(TEXT);
            text.setSelectionColor(ACCENT_DIM);
            text.setSelectedTextColor(TEXT);
        }
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /**
     * Styles a tabbed pane into a flat dark strip.
     *
     * Nimbus draws tabs as pale rounded shapes with a light gradient, which is
     * the last obviously-dated piece once the buttons and fields are themed.
     * Only the keys that actually change the look are set here.
     */
    public static void styleTabs(javax.swing.JTabbedPane tabs) {
        tabs.setBackground(BG);
        tabs.setForeground(TEXT);
        tabs.setFont(BODY_BOLD);
        tabs.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    }

    /** Builds a label styled as a section caption. */
    public static javax.swing.JLabel caption(String text) {
        javax.swing.JLabel label = new javax.swing.JLabel(text);
        label.setFont(BODY_BOLD);
        label.setForeground(TEXT);
        return label;
    }

    /**
     * Replaces Nimbus' tab painters with flat dark ones.
     *
     * Nimbus paints tabs with baked-in gradients that ignore every colour key,
     * so the only way to theme them is to swap the painter itself. The painters
     * are installed through UIManager before any window is built.
     *
     * Fails silently: if a future JDK renames a key, the tabs simply keep their
     * default look rather than the launcher failing to start.
     */
    public static void installTabPainters() {
        try {
            javax.swing.Painter<Object> flat = (g, object, width, height) -> {
                g.setColor(PANEL);
                g.fillRect(0, 0, width, height);
            };
            javax.swing.Painter<Object> selected = (g, object, width, height) -> {
                g.setColor(PANEL_LIGHT);
                g.fillRect(0, 0, width, height);
                g.setColor(ACCENT);
                g.fillRect(0, height - 2, width, 2);
            };

            String base = "TabbedPane:TabbedPaneTab[Enabled].";
            UIManager.put(base + "backgroundPainter", flat);
            UIManager.put("TabbedPane:TabbedPaneTab[Selected]." + "backgroundPainter",
                selected);
            UIManager.put("TabbedPane:TabbedPaneTabArea[Enabled].backgroundPainter",
                (javax.swing.Painter<Object>) (g, object, width, height) -> {
                    g.setColor(BG);
                    g.fillRect(0, 0, width, height);
                });
        } catch (Exception ignored) {
            // Cosmetic only — the default painters are still usable.
        }
    }

    /**
     * Makes a checkbox blend into the dark panel.
     *
     * Setting `opaque(false)` is not enough: Nimbus still paints its own
     * background behind the label, which shows up as a bright rectangle around
     * the text. Clearing the painter is what actually removes it.
     */
    public static void styleCheckBox(javax.swing.JCheckBox box) {
        box.setOpaque(false);
        box.setForeground(TEXT);
        box.setFont(BODY);
        box.setFocusPainted(false);
        box.putClientProperty("Nimbus.Overrides", null);
        // The label area painters are the ones drawing the plate.
        javax.swing.UIManager.put("CheckBox[Enabled].textForeground", TEXT);
    }

    /** Turns on antialiasing for custom painting. */
    public static Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }
}
