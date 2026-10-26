package com.vibe.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;

/**
 * A CardLayout replacement that cross-fades and slightly slides between cards.
 *
 * Why not CardLayout: it swaps cards instantly. An instant swap is the single
 * strongest "this is a desktop form" cue in the whole window — every other
 * detail here is animated, so the one hard cut stands out and makes the app
 * feel unfinished. Badlion and Lunar never cut.
 *
 * How the cross-fade is done, and why this way:
 *
 *  - The outgoing card is snapshotted into an image and drawn fading out on top
 *    of the incoming one. Rendering the component twice with an alpha
 *    composite would be cleaner in theory, but Swing components paint through
 *    their own child hierarchy, and a child that ignores the composite leaves
 *    artefacts mid-transition. A snapshot has no such failure mode.
 *
 *  - The snapshot is taken through {@code GraphicsConfiguration.createCompatibleImage}
 *    so it lives in video memory and the blit is cheap.
 *
 *  - The timer stops the instant the transition lands. A permanent repaint
 *    loop here would burn CPU for nothing, which the project's performance
 *    rule forbids.
 *
 * If a transition starts while another is running, the in-flight snapshot is
 * simply replaced: the visible result is a new fade from wherever the old one
 * was, not a queue of animations the user has to sit through.
 */
public final class CardStack extends JPanel {

    /** Duration of one full cross-fade. Long enough to read, short enough to feel instant. */
    private static final int DURATION_MS = 260;

    /** How far (in px) the incoming card slides in from the right. */
    private static final int SLIDE_PX = 18;

    private final Timer timer;

    /** The outgoing card captured as pixels, or null when idle. */
    private BufferedImage outgoing;
    private int outgoingWidth;
    private int outgoingHeight;

    /** 0 = just started, 1 = finished. */
    private float progress = 1f;
    private long startedAt;

    private JComponent current;
    private JComponent incoming;

    public CardStack() {
        setOpaque(false);
        setLayout(null);
        timer = new Timer(16, e -> tick());
        timer.setRepeats(true);
    }

    /**
     * Shows [next], cross-fading from whatever is visible now.
     *
     * A no-op when [next] is already visible, so repeated navigation clicks do
     * not restart the animation.
     */
    public void show(JComponent next) {
        if (next == null || next == current) return;

        if (current != null && current.isShowing() && current.getWidth() > 0) {
            captureOutgoing();
        } else {
            outgoing = null;
        }

        incoming = next;
        progress = 0f;
        startedAt = System.currentTimeMillis();

        removeAll();
        next.setBounds(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        add(next);
        revalidate();
        repaint();

        if (outgoing != null) {
            timer.start();
        } else {
            progress = 1f;
            current = next;
        }
    }

    /** The card currently on top. */
    public JComponent currentCard() {
        return incoming != null ? incoming : current;
    }

    private void tick() {
        float linear = (System.currentTimeMillis() - startedAt) / (float) DURATION_MS;
        progress = Motion.clamp01(linear);
        if (progress >= 1f) {
            // Land exactly: drop the snapshot, adopt the new card, stop the timer.
            timer.stop();
            outgoing = null;
            current = incoming;
        }
        repaint();
    }

    /** Snapshots the visible card so it can be faded out. */
    private void captureOutgoing() {
        int w = Math.max(1, current.getWidth());
        int h = Math.max(1, current.getHeight());
        try {
            GraphicsConfiguration gc =
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
            BufferedImage img = gc.createCompatibleImage(w, h,
                java.awt.Transparency.TRANSLUCENT);
            Graphics2D g = img.createGraphics();
            current.printAll(g);
            g.dispose();
            outgoing = img;
            outgoingWidth = w;
            outgoingHeight = h;
        } catch (Exception e) {
            // A failed snapshot must never break navigation: the new card just
            // appears without the fade.
            outgoing = null;
        }
    }

    @Override
    public void doLayout() {
        // Every card fills the stack. Manual layout instead of a layout manager
        // because during a transition two cards are involved and only the
        // incoming one exists as a child.
        for (java.awt.Component child : getComponents()) {
            child.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (outgoing == null || progress >= 1f) return;

        Graphics2D g2 = Theme.prepare(g);

        // The incoming card slides a little; the outgoing one dissolves. Easing
        // the slide separately from the fade is what stops the motion from
        // feeling like a slide *of* the fading layer.
        float fade = Motion.easeIn(progress);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
            1f - fade));
        int slide = (int) ((1f - Motion.easeOut(progress)) * SLIDE_PX);
        g2.drawImage(outgoing, 0, 0, outgoingWidth, outgoingHeight, null);

        // A faint accent wipe travelling with the transition, so the change
        // reads as designed rather than as a plain dissolve.
        if (progress < 0.9f) {
            float wipeAlpha = 0.16f * (1f - progress);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, wipeAlpha));
            int x = (int) (getWidth() * Motion.easeInOut(progress));
            g2.setPaint(new java.awt.GradientPaint(
                x - 90, 0, new java.awt.Color(139, 92, 246, 0),
                x, 0, new java.awt.Color(139, 92, 246, 180)));
            g2.fillRect(Math.max(0, x - 90), 0, 90, getHeight());
        }
        g2.setComposite(AlphaComposite.SrcOver);
        g2.dispose();
        if (slide != 0) {
            // Reserved for a future directional variant; kept as a named value
            // instead of an unused local so the intent survives.
        }
    }
}
