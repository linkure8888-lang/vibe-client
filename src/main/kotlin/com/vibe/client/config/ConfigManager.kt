package com.vibe.client.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vibe.client.VibeClient
import com.vibe.client.module.ModuleManager
import java.io.File

/**
 * Loads and saves `config/vibeclient.json`.
 *
 * Design rules:
 *  - A missing / corrupt / partial file NEVER crashes the client; every failure
 *    falls back to defaults and logs one warning.
 *  - Saves are debounced: the HUD editor calls [requestSave] on every mouse
 *    move, but the disk is touched at most once per [SAVE_DELAY_MS].
 *  - Uses Gson, which already ships with Minecraft — zero extra dependencies.
 */
object ConfigManager {

    private const val SAVE_DELAY_MS = 1500L

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Volatile private var config = ClientConfig()

    private var configFile: File? = null
    private var lastSave = 0L
    private var dirty = false

    val current: ClientConfig get() = config

    val language: String get() = config.language

    fun load(configDir: File) {
        val file = File(configDir, "vibeclient.json")
        configFile = file

        if (!file.exists()) {
            config = ClientConfig()
            applyToModules()
            dirty = true
            return
        }

        config = try {
            gson.fromJson(file.readText(), ClientConfig::class.java) ?: ClientConfig()
        } catch (t: Throwable) {
            VibeClient.logger.warn("Config unreadable, using defaults: ${t.message}")
            ClientConfig()
        }

        // Defensive: Gson can leave a null map on `{}` or on type mismatches.
        if (config.modules == null) config.modules = LinkedHashMap()
        if (config.binds == null) config.binds = ArrayList()
        if (config.language !in SUPPORTED_LANGUAGES) config.language = "en"

        // Drop entries that are not a usable ModuleState before anything reads
        // them. Gson can put a `null` into the map when the file says
        // `"FPS": null`, and a half-edited file can produce one too.
        config.modules.entries.removeIf { it.key == null || it.value == null }

        applyToModules()
    }

    /**
     * Copies saved state onto the live module singletons.
     *
     * Every field is sanitised here rather than at the point of use: a bad
     * coordinate is a broken HUD for the whole session, and the HUD has no
     * other place to notice that `x` is `NaN`. One bad module must not stop
     * the rest from loading, so each module is applied independently and an
     * unusable one simply keeps its compiled-in default.
     */
    private fun applyToModules() {
        ModuleManager.registerAll()
        for (module in ModuleManager.all()) {
            val state = config.modules[module.name] ?: continue
            module.enabled = state.enabled

            // NaN/Infinity make every later `x > y` comparison false, which is
            // how a module silently disappears off screen and cannot be
            // dragged back. Reject them instead of clamping: `coerceIn` on NaN
            // returns NaN.
            val sx = state.x
            val sy = state.y
            if (sx.isFinite()) module.x = sx.coerceIn(MIN_HUD_X, MAX_HUD_X)
            if (sy.isFinite()) module.y = sy.coerceIn(MIN_HUD_Y, MAX_HUD_Y)
        }
        ModuleManager.setPvpPreset(config.pvpPreset)
    }

    /** Marks the config dirty; the disk write happens later. */
    fun requestSave() {
        dirty = true
        val now = System.currentTimeMillis()
        if (now - lastSave >= SAVE_DELAY_MS) flush()
    }

    /** Called on world exit / client stop: writes immediately. */
    fun flush() {
        val file = configFile ?: return
        if (!dirty) return

        for (module in ModuleManager.all()) {
            config.modules[module.name] =
                ModuleState(module.enabled, module.x, module.y)
        }
        config.pvpPreset = ModuleManager.pvpPreset

        try {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(config))
            lastSave = System.currentTimeMillis()
            dirty = false
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not save config: ${t.message}")
        }
    }

    fun setLanguage(lang: String) {
        if (lang !in SUPPORTED_LANGUAGES) return
        config.language = lang
        flush()
    }

    fun setLowFpsProtection(on: Boolean) {
        config.lowFpsProtection = on
        requestSave()
    }

    /** Called from a slow tick loop so debounced saves actually land. */
    fun tick() {
        if (dirty && System.currentTimeMillis() - lastSave >= SAVE_DELAY_MS) flush()
    }

    val SUPPORTED_LANGUAGES = listOf("en", "ru")

    /**
     * Absolute HUD coordinate limits.
     *
     * Deliberately generous and resolution-independent: a large GUI scale on a
     * 4K screen legitimately puts a module at a few thousand units, so a tight
     * bound would throw away real layouts. The only values rejected are ones
     * no window could ever show — a hand-edited file or a corrupt save can
     * hold `1e9`, and a module parked there is unrecoverable through the UI.
     */
    private const val MAX_HUD_X = 20000f
    private const val MAX_HUD_Y = 20000f
    private const val MIN_HUD_X = -20000f
    private const val MIN_HUD_Y = -20000f
}