#!/bin/bash
echo "Installing Vibe Client for Linux..."
MODS_DIR="$HOME/.minecraft/mods"
mkdir -p "$MODS_DIR"
cp ../../build/libs/*.jar "$MODS_DIR/"
echo "Vibe Client installed successfully on Linux!"
