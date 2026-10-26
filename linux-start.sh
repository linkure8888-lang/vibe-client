#!/bin/bash
# Vibe Client Linux Launcher (AppImage/Tarball Runner)
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Starting Vibe Client on Linux..."
JAVA_EXEC="java"
if [ -n "$JAVA_HOME" ]; then
    JAVA_EXEC="$JAVA_HOME/bin/java"
fi
exec "$JAVA_EXEC" -cp "$DIR/launcher/classes:$DIR/launcher/assets" com.vibe.launcher.LauncherMain "$@"
