package com.vibe.client.lang

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vibe.client.VibeClient
import com.vibe.client.config.ConfigManager
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * RU / EN string dictionary.
 *
 * Strings live in `assets/vibeclient/lang/<code>.json` — a flat key → text map.
 * A missing key falls back to English, then to the key itself, so a broken
 * translation file can never blank out the UI.
 */
object Lang {

    private val gson = Gson()

    private var en: Map<String, String> = emptyMap()
    private var ru: Map<String, String> = emptyMap()

    fun load() {
        en = read("en")
        ru = read("ru")
        VibeClient.logger.info("Lang loaded: en=${en.size} keys, ru=${ru.size} keys")
    }

    private fun read(code: String): Map<String, String> {
        val path = "/assets/vibeclient/lang/$code.json"
        return try {
            val stream = Lang::class.java.getResourceAsStream(path)
                ?: return emptyMap()
            InputStreamReader(stream, StandardCharsets.UTF_8).use {
                gson.fromJson(it, object : TypeToken<Map<String, String>>() {}.type)
                    ?: emptyMap()
            }
        } catch (t: Throwable) {
            VibeClient.logger.warn("Lang '$code' unreadable: ${t.message}")
            emptyMap()
        }
    }

    /** Translates a key using the currently selected language. */
    fun t(key: String): String {
        val current = ConfigManager.language
        if (current == "ru") {
            ru[key]?.let { return it }
        }
        return en[key] ?: key
    }

    /** Cycles en → ru → en. Returns the new code. */
    fun toggle(): String {
        val next = if (ConfigManager.language == "ru") "en" else "ru"
        ConfigManager.setLanguage(next)
        return next
    }
}