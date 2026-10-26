# Vibe Client - Developer Log (DEVLOG)

## Project Overview
Vibe Client is a high-performance Minecraft client (Fabric, MC 1.20.4+) inspired by Lunar and Badlion tiers. It features an Obsidian Glass ClickGUI with neon violet-cyan gradients, robust configuration persistence (`ConfigManager`), ultra-powerful FPS boosters, Modrinth API mod catalog integration, and customizable HUD modules.

## Architecture & Key Components
- **ClickGUI & HUD Editor**: `com.vibe.client.gui.ClickGui`, `com.vibe.client.gui.hud.HudEditor`. 3-column obsidian glass layout, search filtering, icon gallery, drag-and-drop HUD alignment.
- **Config & Persistence**: `com.vibe.client.config.ConfigManager` & `ClientConfig`. Debounced writes, fallback handling, NaN coordinate sanitization, bilingual support (EN/RU).
- **Performance & Boost**: `com.vibe.client.perf.FpsBoost` & `LowFpsProtection`. Frustum culling, entity shadow optimization, dynamic particle reduction under low FPS, disabling menu background blur.
- **Modules**: 25+ built-in modules (`KeystrokesModule`, `FpsModule`, `ArmorStatusModule`, `CoordinatesModule`, etc.) in `com.vibe.client.module.impl`.
- **Mod Store**: Backed by Modrinth API (`ModStoreScreen.kt`, `ModrinthApi.kt`).

## Installers & Packages Structure
- `installers/windows/vibe-client-setup.msi` (Windows Installer stub/script structure)
- `installers/linux/install-vibe.sh` (Linux launcher and install script)
- `installers/macos/install-vibe.command` (macOS launcher and install script)

## Repository Structure (`репозиторий/`)
Contains clean project source code, build scripts, Gradle configuration, and a welcoming `README.md` for open-source contributors and like-minded developers.
