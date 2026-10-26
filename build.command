#!/usr/bin/env bash
# ===========================================================================
#  Vibe Client - macOS double-click launcher
#
#  Finder runs .command files in Terminal when double-clicked. This file just
#  forwards to build.sh so there is exactly one build implementation.
# ===========================================================================
cd "$(dirname "$0")" || exit 1
exec ./build.sh