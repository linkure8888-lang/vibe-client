> ⚠️ **ПРЕДУПРЕЖДЕНИЕ: ТЕСТОВЫЙ РЕЖИМ (ALPHA / BETA)**
> Проект находится в стадии активной разработки и публичного тестирования!
> В этой версии **может быть дофига багов, вылетов и нестабильных фич**. 
> Мы выложили клиент для демонстрации фундамента и сбора фидбека. 
> Если нашли баг — создавайте **Issue** или пишите нам. Вы предупреждены! 🧪💥

---
# 🌌 Vibe Client

> **The Ultimate Lunar & Badlion Tier Minecraft Client & Launcher**  
> Топовый майнкрафт клиент и лаунчер в стиле Lunar и Badlion с продвинутым интерфейсом и оптимизацией.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blueviolet?style=for-the-badge&logo=fabric)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

---

## 🌐 Languages / Языки
- [English](#-features) | [Русский](#-возможности)

---

## ✨ Features

### 💎 Lunar & Badlion Tier UI & ClickGUI
- **Obsidian Glass Aesthetics**: Deep slate backgrounds, frosted glass panels, and neon violet-to-cyan gradient blooms (`#00e5ff` / `#7c3aed`).
- **Interactive ClickGUI**: Sleek category navigation rails, instant module search filtering, smooth pill toggles, and live keybind/status overlays.
- **Immersive Launcher**: Hero cosmos background, animated tab switcher, version cards in 16:9, and quick account management.

### 🔄 Smart Version Management & Vanilla Fallback
- **Supported (Client) Tab**: Automatically filters for the supported Minecraft 1.21 family (`1.21.1`+), bundling all Vibe Client optimizations, Fabric loader, and mods.
- **Unsupported (Vanilla) Tab**: Displays older/other Minecraft versions with a warning banner, allowing players to launch pure **Vanilla mode** without client mods directly through the launcher.

### 🚀 Extreme Performance & FPS Boost
- **Sodium & Lithium Integration**: Built-in performance packs delivering massive framerate improvements on weak hardware.
- **Low FPS Protection**: Dynamic render adjustments to prevent stuttering during intense combat.

### 📦 Cross-Platform Installers
- **Windows**: Swing GUI Installer & MSI package (`VibeClient-1.2.msi`).
- **Linux & macOS**: Automated shell scripts (`.sh` and `.command`).

---

## 🇷🇺 Возможности

### 💎 Интерфейс и ClickGUI уровня Lunar / Badlion
- **Obsidian Glass**: Темный элегантный дизайн со стеклом и неоновыми фиолетово-циан градиентами.
- **Интерактивный ClickGUI**: Удобные категории, мгновенный поиск по модулям, плавные переключатели и оверлеи.
- **Современный Лаунчер**: Космический фон, анимированные табы, карточки версий 16:9 и управление аккаунтами.

### 🔄 Умное управление версиями и Vanilla-режим
- **Вкладка Supported (Client)**: Отображает поддерживаемое семейство 1.21.x с модами и оптимизациями клиента.
- **Вкладка Unsupported (Vanilla)**: Отображает старые версии с предупреждением и возможностью игры в чистом **Vanilla-режиме** без клиентских модов.

### 🚀 Экстремальная оптимизация (FPS Boost)
- Встроенные оптимизации (в стиле Sodium/Lithium) и защита от просадок FPS (Low FPS Protection).

### 📦 Кроссплатформенные установщики
- Готовые инсталлеры для Windows (Swing GUI / MSI), Linux и macOS.

---

## 🚀 Quick Start / Быстрый старт

1. **Windows**: Скачайте **`VibeClient-Setup.msi`** из корня репозитория или релизов, запустите и играйте!
2. **macOS**: Пакеты `.dmg` и архивы `.app` находятся в разработке (планируются в будущих релизах).
3. **Linux**: Пакеты `.AppImage` и `.deb` находятся в разработке (планируются в будущих релизах).
4. Выберите версию во вкладке **Versions** (Поддерживаемая 1.21+ или Vanilla) и нажмите **PLAY**!

---

## 📦 Установка и запуск / Installation Guide

> Все релизные сборки доступны на странице [GitHub Releases](../../releases).

### 🪟 Windows (MSI Installer)
1. Скачайте `VibeClient-Setup.msi` из раздела **Releases**.
2. Запустите файл и следуйте инструкциям установщика.
3. Запустите Vibe Client с рабочего стола или меню Пуск.

### 🍎 macOS (`VibeClient-macOS.zip`)
1. Скачайте `VibeClient-macOS.zip` из раздела **Releases**.
2. Распакуйте архив (вы получите приложение `VibeClient.app`).
3. Перетащите `VibeClient.app` в папку **«Программы» (Applications)**.
4. Запустите клиент (если macOS заблокирует запуск из-за неподписанного разработчика, нажмите ПКМ по приложению -> **Открыть**).

### 🐧 Linux (`VibeClient-Linux.zip`)
1. Скачайте `VibeClient-Linux.zip` из раздела **Releases**.
2. Распакуйте архив в удобную папку.
3. Откройте терминал в папке с распакованным клиентом и сделайте скрипт запуска исполняемым:
   ```bash
   chmod +x vibe-client.sh
   ```
4. Запустите клиент:
   ```bash
   ./vibe-client.sh
   ```
   *(Убедитесь, что у вас установлен Java 21: `sudo apt install openjdk-21-jre` или аналогичный пакет).*
