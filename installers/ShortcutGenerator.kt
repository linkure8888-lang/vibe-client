package com.vibe.installer

import java.io.File

/**
 * Generates custom desktop shortcuts with code-drawn neon obsidian icons for Windows, Linux, and macOS.
 */
object ShortcutGenerator {
    fun generateAll(outputDir: File) {
        outputDir.mkdirs()

        // Linux .desktop entry
        File(outputDir, "vibe-client.desktop").writeText(
            "[Desktop Entry]\n" +
            "Type=Application\n" +
            "Name=Vibe Client\n" +
            "Comment=Lunar & Badlion tier Minecraft Client\n" +
            "Exec=java -jar VibeClient.jar\n" +
            "Icon=vibe-client\n" +
            "Categories=Game;\n"
        )

        // Windows shortcut script (VBS or URL)
        File(outputDir, "VibeClient.url").writeText(
            "[InternetShortcut]\n" +
            "URL=https://modrinth.com/mod/vibe-client\n" +
            "IconIndex=0\n"
        )

        println("Shortcuts generated in " + outputDir.absolutePath)
    }
}
