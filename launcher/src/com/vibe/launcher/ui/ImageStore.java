package com.vibe.launcher.ui;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches launcher artwork.
 *
 * Two sources, tried in order, because the launcher runs in two worlds:
 *  - inside the packaged jar, images are classpath resources under /cards
 *    and /art;
 *  - from the source tree (screenshot tool, dev runs) the same files sit in
 *    launcher/assets, which is a working directory, not a jar.
 *
 * Every image is decoded once and kept as the original BufferedImage; scaled
 * copies are cached per requested size, so resizing the window reuses the
 * same scaled instance instead of re-decoding megabytes of PNG.
 */
final class ImageStore {

    /** original path -> decoded image; null marks a confirmed-missing file. */
    private static final Map<String, BufferedImage> originals = new ConcurrentHashMap<>();
    /** path + size -> scaled copy. */
    private static final Map<String, BufferedImage> scaled = new ConcurrentHashMap<>();

    private ImageStore() {
    }

    /** Decodes [path] like "/cards/c-plains.jpg", or null when unavailable. */
    static BufferedImage original(String path) {
        return originals.computeIfAbsent(path, ImageStore::decode);
    }

    /** True when the artwork exists and could be decoded. */
    static boolean available(String path) {
        return original(path) != null;
    }

    /**
     * [path] scaled to fit [w] x [h], keeping the aspect ratio and cropping
     * the overflow (a cover fit, like CSS). Null when the original is absent.
     */
    static BufferedImage cover(String path, int w, int h) {
        BufferedImage img = original(path);
        if (img == null || w <= 0 || h <= 0) return null;
        String key = path + "@" + w + "x" + h;
        return scaled.computeIfAbsent(key, k -> scaleCover(img, w, h));
    }

    private static BufferedImage decode(String path) {
        try (InputStream in = ImageStore.class.getResourceAsStream(path)) {
            if (in != null) return ImageIO.read(in);
        } catch (Exception ignored) {
            // Fall through to the file system.
        }
        // Dev/screenshot mode: same file on disk under launcher/assets,
        // resolved with forward slashes so it works from any working dir.
        try {
            Path file = Path.of("launcher", "assets",
                path.replaceFirst("^/", "").replace('\\', '/'));
            if (Files.isRegularFile(file)) {
                BufferedImage img = ImageIO.read(Files.newInputStream(file));
                if (img != null) return img;
            }
        } catch (Exception ignored) {
            // Missing art must never break the launcher.
        }
        return null;
    }

    /** Cover-fit scale + center crop with bilinear smoothing. */
    private static BufferedImage scaleCover(BufferedImage img, int w, int h) {
        double scale = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int sw = Math.max(1, (int) Math.round(img.getWidth() * scale));
        int sh = Math.max(1, (int) Math.round(img.getHeight() * scale));
        BufferedImage scaledImg = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, sw, sh, null);
        g.dispose();
        int x = (sw - w) / 2;
        int y = (sh - h) / 2;
        return scaledImg.getSubimage(Math.max(0, x), Math.max(0, y),
            Math.min(w, sw), Math.min(h, sh));
    }
}
