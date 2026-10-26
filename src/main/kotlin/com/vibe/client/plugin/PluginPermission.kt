package com.vibe.client.plugin

/**
 * The complete permission vocabulary of apiVersion 1.
 *
 * A plugin gets exactly the permissions listed in its manifest. Unknown
 * strings in a manifest are ignored (and logged) rather than crashing the
 * loader, because manifests are untrusted input.
 */
enum class PluginPermission(val id: String) {

    /** Register lines on the in-game HUD. */
    HUD("hud"),

    /** Read/write its own config section. */
    CONFIG("config"),

    /** Perform HTTP GETs through the request budget. */
    NETWORK("network"),

    /** Read/write files inside `plugins/<id>/data/` only. */
    FILESYSTEM("filesystem");

    companion object {

        /** Parses manifest entries; unknown ids are dropped by the caller. */
        fun fromId(id: String): PluginPermission? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}
