package com.vibe.launcher.ui;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Photo backdrop in the Lunar/Badlion style: a Minecraft screenshot, slowly
 * drifting, under a dark gradient scrim so every label stays readable.
 *
 * Why this replaces the neon-only background: a flat gradient with bloom read
 * as a designed app, but it had no subject. Lunar and Badlion both open on
 * game art, and that single choice is most of why they read as a game client
 * rather than a settings dialog.
 *
 * Three things make a photo usable as a UI background, and all three are
 * required -- any one missing and the result looks amateur:
 *
 *  1. **Cover scaling.** The image is scaled to fill the window and cropped,
 *     never stretched. Stretched art is the single most obvious tell.
 *  2. **A scrim.** White text over a bright sky is unreadable. A vertical
 *     gradient (darker at top and bottom, lighter at mid) keeps contrast
 *     everywhere while the art still shows through.
 *  3. **Slow motion.** A still photo behind a UI looks like a wallpaper; a
 *     drift of a few percent over ~30 seconds reads as a living scene and
 *     costs one affine transform per frame.
 *
 * Failure behaviour: if the image is missing or unreadable the panel falls back
 * to the plain neon gradient. A launcher that refuses to start because a
 * wallpaper is missing would be worse than one that looks plain.
 */
public final class ArtBackground extends JPanel {

    private static final int FRAME_MS = 40; // 25 FPS: drift is slow, frames are cheap

    /** Full drift cycle, in milliseconds. */
    private static final double CYCLE_MS = 42000.0;

    private final BufferedImage art;
    private final Timer timer;

    /** Drift phase in radians. */
    private double phase;

    public ArtBackground() {
        setOpaque(true);
        setBackground(Theme.BG);
        art = load("/hero.jpg");

        timer = new Timer(FRAME_MS, e -> {
            if (art == null) return;
            phase += Math.PI * 2 * FRAME_MS / CYCLE_MS;
            if (phase > Math.PI * 2) phase -= Math.PI * 2;
            repaint();
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (art != null) timer.start();
    }

    @Override
    public void removeNotify() {
        // A minimised launcher must cost zero CPU.
        timer.stop();
        super.removeNotify();
    }

    /** Loads a classpath image; returns null on any failure. */
    private static BufferedImage load(String resource) {
        try (InputStream in = ArtBackground.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.prepare(g);
        int w = getWidth();
        int h = getHeight();

        if (art == null) {
            // Fallback: the plain gradient this panel had before art existed.
            g2.setPaint(Glow.sheen(h, Theme.BG_TOP, Theme.BG_BOTTOM));
            g2.fillRect(0, 0, w, h);
            g2.dispose();
            return;
        }

        // -- Cover-fit with a slow drift ---------------------------------
        // Scale so the image covers the window, then add up to 6% extra zoom
        // and a small pan. The extra zoom is what guarantees the pan never
        // exposes an edge.
        double cover = Math.max(w / (double) art.getWidth(), h / (double) art.getHeight());
        double drift = 1.0 + 0.06 * (0.5 + 0.5 * Math.sin(phase));
        double scale = cover * drift;

        int dw = (int) Math.ceil(art.getWidth() * scale);
        int dh = (int) Math.ceil(art.getHeight() * scale);

        // Pan a few pixels in both axes so the motion is not a plain zoom.
        int dx = (int) ((dw - w) / 2.0 + Math.sin(phase * 0.7) * w * 0.012);
        int dy = (int) ((dh - h) / 2.0 + Math.cos(phase * 0.5) * h * 0.010);
        dx = Math.max(0, Math.min(dw - w, dx));
        dy = Math.max(0, Math.min(dh - h, dy));

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(art, -dx, -dy, dw, dh, null);

        // -- Scrim -------------------------------------------------------
        // Top and bottom are heaviest: the header sits at the top and the
        // action bar at the bottom, and both carry small text over whatever
        // the screenshot happens to show there.
        for (int y = 0; y < h; y++) {
            float t = y / (float) Math.max(1, h - 1);
            // Parabola: heaviest at the very top and bottom, lightest in the
            // middle band. 0.28 mid / 0.62 edge: the first attempt used
            // 0.45/0.85 and the art was effectively invisible.
            float edge = Math.abs(t - 0.5f) * 2f;
            float alpha = 0.28f + 0.34f * edge * edge;
            int a = (int) (alpha * 255);
            g2.setColor(new Color(6, 7, 14, a));
            g2.drawLine(0, y, w, y);
        }

        // -- Accent wash -------------------------------------------------
        // A faint violet tint ties the photo to the brand instead of leaving
        // it as an unrelated wallpaper.
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        g2.setColor(Theme.ACCENT);
        g2.fillRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.SrcOver);

        // Vignette: darkens the corners so the eye lands on the centre panel.
        Glow.bloom(this, g2, w * 0.5f, h * 0.5f, Math.max(w, h) * 0.9f, Theme.BG, 0f);
        g2.dispose();
    }
}
