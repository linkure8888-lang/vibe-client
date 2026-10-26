package com.vibe.launcher;

import com.vibe.launcher.ui.LauncherWindow;
import com.vibe.launcher.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point.
 *
 * Kept deliberately tiny: the window does the work. All this class decides is
 * where the app lives on disk and how Swing should look.
 */
public final class LauncherMain {

    private LauncherMain() {
    }

    public static void main(String[] args) {
        Path appDir = resolveAppDir();
        Path configFile = resolveConfigFile(appDir);

        // A dark, flat look everywhere; Nimbus behaves best across platforms.
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // Any look and feel works; the theme paints over it.
        }

        UIManager.put("control", Theme.PANEL);
        UIManager.put("info", Theme.PANEL);
        UIManager.put("nimbusBase", Theme.PANEL);
        UIManager.put("nimbusLightBackground", Theme.PANEL);
        UIManager.put("text", Theme.TEXT);
        UIManager.put("controlText", Theme.TEXT);
        UIManager.put("infoText", Theme.TEXT);

        // Nimbus' tab shapes are baked in, so they are replaced, not recoloured.
        Theme.installTabPainters();

        final Path finalAppDir = appDir;
        final Path finalConfig = configFile;
        SwingUtilities.invokeLater(() -> new LauncherWindow(finalAppDir, finalConfig).setVisible(true));
    }

    /**
     * The folder the app was installed into. jpackage exposes it through
     * `jpackage.app-path`; running from an IDE falls back to the working dir.
     */
    private static Path resolveAppDir() {
        try {
            String appPath = System.getProperty("jpackage.app-path");
            if (appPath != null && !appPath.isBlank()) {
                Path parent = Path.of(appPath).getParent();
                if (parent != null) return parent;
            }
        } catch (Exception ignored) {
            // Fall through to the working directory.
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * Config lives next to the app when that folder is writable, otherwise in
     * the user's home. An installer under Program Files is read-only for a
     * normal user, and silently losing settings would be worse than this check.
     */
    private static Path resolveConfigFile(Path appDir) {
        try {
            if (Files.isDirectory(appDir) && Files.isWritable(appDir)) {
                return appDir.resolve("launcher-config.json");
            }
        } catch (Exception ignored) {
            // Fall through.
        }
        return Path.of(System.getProperty("user.home"), ".vibeclient", "launcher-config.json");
    }
}
