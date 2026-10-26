package com.vibe.client.plugin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vibe.client.VibeClient
import java.io.File
import java.time.LocalDate

/**
 * Per-user, per-plugin daily request budget (`requestsPerDay`, default 300).
 *
 * Storage: `config/plugins/<id>.usage.json` -> `{ "day": "2026-10-17", "used": 42 }`.
 * The day is compared as a string, so no timers and no timezone math are
 * needed — a stale day simply means "start over".
 *
 * Every method is safe: a corrupt usage file is ignored (treated as 0 used)
 * instead of blocking the plugin forever.
 */
class PluginBudget(
    private val file: File,
    private val limit: Int
) {

    private val gson = Gson()

    private var day: String = today()
    private var used: Int = 0

    init {
        load()
    }

    val remaining: Int get() = (limit - used).coerceAtLeast(0)

    val usedToday: Int get() = used

    val dailyLimit: Int get() = limit

    /**
     * Spends one request. Returns true when it was allowed.
     * A limit of 0 means the plugin cannot use the network at all.
     */
    fun tryConsume(): Boolean {
        rolloverIfNeeded()
        if (used >= limit) return false
        used++
        save()
        return true
    }

    private fun rolloverIfNeeded() {
        val now = today()
        if (now != day) {
            day = now
            used = 0
            save()
        }
    }

    private fun load() {
        try {
            if (!file.isFile) return
            val root = gson.fromJson(file.readText(), JsonObject::class.java) ?: return
            val storedDay = runCatching { root.get("day")?.asString }.getOrNull() ?: return
            val storedUsed = runCatching { root.get("used")?.asInt }.getOrNull() ?: 0
            if (storedDay == today()) {
                day = storedDay
                used = storedUsed.coerceIn(0, limit)
            }
        } catch (t: Throwable) {
            VibeClient.logger.warn("Plugin usage unreadable (${file.name}): ${t.message}")
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val root = JsonObject()
            root.addProperty("day", day)
            root.addProperty("used", used)
            file.writeText(gson.toJson(root))
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not save plugin usage (${file.name}): ${t.message}")
        }
    }

    private fun today(): String = LocalDate.now().toString()
}
