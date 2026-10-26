package shot;

import com.vibe.launcher.ui.LauncherWindow;
import com.vibe.launcher.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders the launcher window to a PNG without a visible screen.
 *
 * Why this exists: the UI is custom-painted Java2D, and the only honest way to
 * review a custom-painted UI is to look at it. Colours described in a changelog
 * are not a review.
 *
 * Two traps, both found the hard way:
 *  - Painting the JFrame itself yields a white rectangle: the frame's own
 *    background paints, then invisible children are skipped. The content pane
 *    is what has to be painted.
 *  - The content pane is non-opaque (it shows the animated backdrop), so the
 *    image needs an explicit opaque base coat first, or every transparent
 *    pixel stays white instead of black.
 *
 * Usage: java -cp classes;shot;gson.jar shot.Shot <out.png> [w] [h]
 */
public final class Shot {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "shot.png";
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 1180;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 760;

        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                UIManager.setLookAndFeel(info.getClassName());
                break;
            }
        }
        UIManager.put("control", Theme.PANEL);
        UIManager.put("info", Theme.PANEL);
        UIManager.put("nimbusBase", Theme.PANEL);
        UIManager.put("nimbusLightBackground", Theme.PANEL);
        UIManager.put("text", Theme.TEXT);
        UIManager.put("controlText", Theme.TEXT);
        UIManager.put("infoText", Theme.TEXT);
        Theme.installTabPainters();

        Path tmp = Files.createTempDirectory("vibe-shot");
        final BufferedImage[] out2 = new BufferedImage[1];

        SwingUtilities.invokeAndWait(() -> {
            try {
                LauncherWindow window = new LauncherWindow(tmp, tmp.resolve("cfg.json"));
                window.setSize(w, h);
                // addNotify builds the peer and starts the backdrop timer.
                window.addNotify();
                window.validate();

                Container pane = window.getContentPane();
                pane.setSize(w, h);
                layoutDeep(pane);

                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                // Opaque base coat: the window background, not white.
                g.setColor(Theme.BG);
                g.fillRect(0, 0, w, h);
                pane.printAll(g);
                g.dispose();

                out2[0] = img;
                window.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        if (out2[0] == null) {
            System.err.println("nothing painted");
            System.exit(1);
        }
        ImageIO.write(out2[0], "png", new File(out));
        System.out.println("wrote " + out + " " + out2[0].getWidth() + "x" + out2[0].getHeight());
        System.exit(0);
    }

    /** validate() only walks visible/valid flags; force layout everywhere. */
    private static void layoutDeep(Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof Container cc) layoutDeep(cc);
        }
    }
}
