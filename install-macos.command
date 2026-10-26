#!/bin/bash
echo "Installing Vibe Client for macOS..."
MODS_DIR="$HOME/Library/Application Support/minecraft/mods"
mkdir -p "$MODS_DIR"
cp ../../build/libs/*.jar "$MODS_DIR/"
echo "Vibe Client installed successfully on macOS!"
read -p "Press any key to exit..."
