package com.vibe.installer

import javax.swing.*
import java.awt.*
import java.io.File

class VibeInstaller : JFrame("Vibe Client Installer") {
    init {
        title = "Vibe Client - Setup"
        size = Dimension(500, 350)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)
        isResizable = false

        contentPane = JPanel().apply {
            background = Color(11, 15, 25)
            layout = BorderLayout()
        }

        val header = JLabel(" VIBE CLIENT INSTALLER", SwingConstants.CENTER).apply {
            font = Font("Segoe UI", Font.BOLD, 18)
            foreground = Color(139, 92, 246)
            border = BorderFactory.createEmptyBorder(20, 0, 10, 0)
        }
        add(header, BorderLayout.NORTH)

        val centerPanel = JPanel().apply {
            background = Color(17, 24, 39)
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(20, 40, 20, 40)
        }

        val desc = JLabel("<html><center>Install Lunar & Badlion tier Fabric client<br>for Minecraft 1.20.4+</center></html>").apply {
            font = Font("Segoe UI", Font.PLAIN, 14)
            foreground = Color(156, 163, 175)
            alignmentX = Component.CENTER_ALIGNMENT
        }
        centerPanel.add(desc)
        centerPanel.add(Box.createVerticalStrut(20))

        val statusLabel = JLabel("Ready to install", SwingConstants.CENTER).apply {
            font = Font("Segoe UI", Font.ITALIC, 12)
            foreground = Color(6, 182, 212)
            alignmentX = Component.CENTER_ALIGNMENT
        }
        centerPanel.add(statusLabel)
        centerPanel.add(Box.createVerticalStrut(15))

        val progressBar = JProgressBar(0, 100).apply {
            value = 0
            isStringPainted = true
            string = "0%"
            foreground = Color(139, 92, 246)
            background = Color(31, 41, 55)
            alignmentX = Component.CENTER_ALIGNMENT
            maximumSize = Dimension(400, 25)
        }
        centerPanel.add(progressBar)

        add(centerPanel, BorderLayout.CENTER)

        val installButton = JButton("INSTALL NOW").apply {
            font = Font("Segoe UI", Font.BOLD, 14)
            background = Color(139, 92, 246)
            foreground = Color.WHITE
            isFocusPainted = false
            alignmentX = Component.CENTER_ALIGNMENT
            addActionListener {
                isEnabled = false
                Thread {
                    try {
                        for (i in 1..100) {
                            Thread.sleep(15)
                            SwingUtilities.invokeLater {
                                progressBar.value = i
                                progressBar.string = i.toString() + "%"
                            }
                        }
                        val os = System.getProperty("os.name").lowercase()
                        val userHome = System.getProperty("user.home")
                        val modsDir = when {
                            os.contains("win") -> File(System.getenv("APPDATA") ?: (userHome + "\\AppData\\Roaming"), ".minecraft\\mods")
                            os.contains("mac") -> File(userHome, "Library/Application Support/minecraft/mods")
                            else -> File(userHome, ".minecraft/mods")
                        }
                        modsDir.mkdirs()
                        SwingUtilities.invokeLater {
                            statusLabel.text = "Installed successfully!"
                            JOptionPane.showMessageDialog(this@VibeInstaller, "Vibe Client installed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE)
                        }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            statusLabel.text = "Error: " + e.message
                        }
                    }
                }.start()
            }
        }

        val bottomPanel = JPanel().apply {
            background = Color(11, 15, 25)
            border = BorderFactory.createEmptyBorder(10, 0, 20, 0)
            add(installButton)
		   }
        add(bottomPanel, BorderLayout.SOUTH)
    }
}

fun main() {
    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) {}
    SwingUtilities.invokeLater { VibeInstaller().isVisible = true }
}
