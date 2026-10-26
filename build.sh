#!/usr/bin/env bash
# ===========================================================================
#  Vibe Client - Linux / macOS build script
#
#  Usage:
#    chmod +x build.sh      (once)
#    ./build.sh
#
#  On macOS you can also double-click build.command (see below).
#
#  Gradle is downloaded INTO the project (tools/), dependency caches go to
#  gradle-home/. Nothing is installed globally.
# ===========================================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

export GRADLE_USER_HOME="$PROJECT_DIR/gradle-home"

GRADLE_VER="8.9"
GRADLE_DIR="$PROJECT_DIR/tools/gradle-$GRADLE_VER"
GRADLE_BIN="$GRADLE_DIR/bin/gradle"

echo
echo "=========================================================="
echo "  Vibe Client - build"
echo "=========================================================="
echo "  project : $PROJECT_DIR"
echo "  gradle  : $GRADLE_VER"
echo

# --- 1. Fetch Gradle into the project if missing ---------------------------
if [ ! -x "$GRADLE_BIN" ]; then
    echo "[1/3] Downloading Gradle $GRADLE_VER into tools/ ..."
    mkdir -p "$PROJECT_DIR/tools"

    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 -o "$PROJECT_DIR/tools/gradle.zip" \
            "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$PROJECT_DIR/tools/gradle.zip" \
            "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
    else
        echo "ERROR: neither curl nor wget is available." >&2
        exit 1
    fi

    unzip -q -o "$PROJECT_DIR/tools/gradle.zip" -d "$PROJECT_DIR/tools"
    rm -f "$PROJECT_DIR/tools/gradle.zip"
    chmod +x "$GRADLE_BIN" || true
    echo "      done."
else
    echo "[1/3] Gradle already present."
fi

# --- 2. Build --------------------------------------------------------------
echo "[2/3] Building (first run downloads Minecraft deps, be patient)..."
"$GRADLE_BIN" build --no-daemon --console=plain

# --- 3. Copy the jar into the game's mods folder ---------------------------
JAR=""
for candidate in "$PROJECT_DIR"/build/libs/*.jar; do
    case "$candidate" in
        *-sources.jar) continue ;;
        *) JAR="$candidate" ;;
    esac
done

if [ -z "$JAR" ]; then
    echo "[3/3] ERROR: no jar found in build/libs" >&2
    exit 1
fi

# Detect the usual Minecraft directories per OS.
case "$(uname -s)" in
    Darwin) DEFAULT_MC="$HOME/Library/Application Support/minecraft" ;;
    *)      DEFAULT_MC="$HOME/.minecraft" ;;
esac

if [ -d "$DEFAULT_MC" ]; then
    MODS_DIR="$DEFAULT_MC/mods"
else
    MODS_DIR="$PROJECT_DIR/mods"
fi
mkdir -p "$MODS_DIR"

cp -f "$JAR" "$MODS_DIR/"
echo "[3/3] Copied to: $MODS_DIR"
echo
echo "=========================================================="
echo "  BUILD OK"
echo "=========================================================="
echo "  jar : $JAR"
echo "  mods: $MODS_DIR"
echo
echo "  Next: install Fabric Loader for Minecraft and start the game."
echo