package com.vibe.launcher.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The artwork and labels for one version card.
 *
 * Why a class and not just fields on the grid: the same card is painted many
 * times while scrolling, and each paint needs its image, title and type.
 * Loading an image inside a paint method would be a disk read per frame, so
 * everything here is resolved once when the card is created and the decoded
 * images are shared through a static cache.
 *
 * Card art rule: a version id maps to a scene by a stable hash, so the same
 * version always shows the same picture across restarts. A card that changed
 * art between launches would look like a bug.
 */
public final class VersionCard {

    /** Card art, 480x270 JPEGs under /cards/. */
    private static final String[] SCENES = {
        "c-mountain.jpg", "c-ocean.jpg", "c-plains.jpg", "c-jungle.jpg",
        "c-village.jpg", "c-castle.jpg", "c-cave.jpg", "c-nether.jpg"
    };

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    public final String id;
    public final String label;
    public final String suffix;
    public final BufferedImage art;

    public VersionCard(String id, String label, String suffix) {
        this.id = id;
        this.label = label;
        this.suffix = suffix;
        this.art = artFor(id);
    }

    /**
     * Stable scene pick. Math.abs guards the one negative case,
     * Integer.MIN_VALUE, whose absolute value is still negative.
     */
    private static BufferedImage artFor(String id) {
        int h = Math.abs(id.hashCode() % SCENES.length);
        return load(SCENES[h]);
    }

    private static synchronized BufferedImage load(String name) {
        BufferedImage cached = CACHE.get(name);
        if (cached != null) return cached;
        try (InputStream in = VersionCard.class.getResourceAsStream("/cards/" + name)) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            CACHE.put(name, img);
            return img;
        } catch (Exception e) {
            return null;
        }
    }
}
