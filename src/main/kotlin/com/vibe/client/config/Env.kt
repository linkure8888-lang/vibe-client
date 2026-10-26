package com.vibe.client.config

import com.vibe.client.VibeClient
import java.io.File

/**
 * Minimal .env loader.
 *
 * Lookup order (first hit wins, later sources only fill gaps):
 *  1. OS environment variables        (highest priority, for CI / launchers)
 *  2. .env next to the running jar    (development)
 *  3. .env in the game directory      (release: user drops it beside mods/)
 *
 * Format: KEY=VALUE per line. `#` starts a comment. Blank lines ignored.
 * Quotes around a value are stripped. A missing file is NOT an error.
 */
object Env {

    private val values = HashMap<String, String>()

    private var loaded = false

    /** Reads the .env files once; safe to call multiple times. */
    fun load(gameDir: File?) {
        if (loaded) return
        loaded = true

        // Lowest priority first; later loads overwrite earlier ones.
        gameDir?.let { readFile(File(it, ".env")) }
        readFile(File(".env"))

        // OS environment always wins.
        for ((key, value) in System.getenv()) {
            if (key in KNOWN_KEYS) values[key] = value
        }

        VibeClient.logger.info("Env: ${values.size} known keys present")
    }

    private fun readFile(file: File) {
        if (!file.isFile) return
        try {
            file.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEachLine
                val key = line.substring(0, eq).trim()
                var value = line.substring(eq + 1).trim()
                if (value.length >= 2 &&
                    ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'")))
                ) {
                    value = value.substring(1, value.length - 1)
                }
                // OS env wins, so only set if not already present from env pass.
                if (!values.containsKey(key)) values[key] = value
            }
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not read ${file.absolutePath}: ${t.message}")
        }
    }

    /** Returns the value, or an empty string when unset. */
    fun get(key: String): String = values[key] ?: ""

    fun getOr(key: String, fallback: String): String =
        values[key]?.takeIf { it.isNotBlank() } ?: fallback

    fun getBool(key: String, fallback: Boolean = false): Boolean =
        when (get(key).lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> fallback
        }

    /** True when a non-empty value exists for the key. */
    fun has(key: String): Boolean = !get(key).isNullOrBlank()

    private val KNOWN_KEYS = setOf(
        "MODRINTH_TOKEN",
        "CURSEFORGE_API_KEY",
        "VIBE_SERVER_HOST",
        "VIBE_SERVER_PORT",
        "DEFAULT_MC_VERSION",
        "VIBE_DEBUG"
    )
}
