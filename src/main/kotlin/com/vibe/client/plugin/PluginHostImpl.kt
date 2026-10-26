package com.vibe.client.plugin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vibe.client.VibeClient
import com.vibe.client.store.Http
import java.io.File

/**
 * The real [PluginHost].
 *
 * Every tool validates its arguments, checks the matching permission, and
 * returns a [ToolResult]. Nothing here throws: a plugin passing garbage gets
 * `ok:false` and keeps running (or gets disabled by the caller, if it keeps
 * failing).
 *
 * @param dataDir  `plugins/<id>/data/` — the ONLY place `fs.*` can touch.
 * @param configFile `config/plugins/<id>.json` — free key/value store.
 */
class PluginHostImpl(
    override val pluginId: String,
    override val permissions: Set<PluginPermission>,
    private val dataDir: File,
    private val configFile: File,
    private val budget: PluginBudget
) : PluginHost {

    private val gson = Gson()

    @Volatile private var configCache: JsonObject = loadConfig()

    override fun log(message: String) {
        VibeClient.logger.info("[plugin:$pluginId] $message")
    }

    override fun call(tool: String, arguments: JsonObject): ToolResult {
        return try {
            when (tool) {
                "hud.addLine" -> hudAddLine(arguments)
                "hud.setLine" -> hudSetLine(arguments)
                "hud.removeLine" -> hudRemoveLine(arguments)
                "config.get" -> configGet(arguments)
                "config.set" -> configSet(arguments)
                "net.get" -> netGet(arguments)
                "fs.read" -> fsRead(arguments)
                "fs.write" -> fsWrite(arguments)
                "log.info" -> {
                    log(requireString(arguments, "message") ?: return ToolResult.badArguments("message is required"))
                    ToolResult.success()
                }
                else -> ToolResult.unknownTool(tool)
            }
        } catch (t: Throwable) {
            log("tool '$tool' failed: ${t.message}")
            ToolResult.failure("internal_error", t.message ?: "unexpected failure")
        }
    }

    // -- HUD ---------------------------------------------------------------

    private fun hudAddLine(args: JsonObject): ToolResult {
        if (PluginPermission.HUD !in permissions) return ToolResult.permissionDenied(PluginPermission.HUD)
        val id = requireString(args, "id") ?: return ToolResult.badArguments("id is required")
        val text = requireString(args, "text") ?: return ToolResult.badArguments("text is required")
        if (!LINE_ID.matches(id)) return ToolResult.badArguments("invalid line id")

        val x = requireFloat(args, "x") ?: 4f
        val y = requireFloat(args, "y") ?: 4f
        val color = requireInt(args, "color") ?: DEFAULT_COLOR

        PluginHud.put(pluginId, id, text, x, y, color)
        return ToolResult.success(JsonObject().apply { addProperty("id", id) })
    }

    private fun hudSetLine(args: JsonObject): ToolResult {
        if (PluginPermission.HUD !in permissions) return ToolResult.permissionDenied(PluginPermission.HUD)
        val id = requireString(args, "id") ?: return ToolResult.badArguments("id is required")
        val text = requireString(args, "text") ?: return ToolResult.badArguments("text is required")
        if (!PluginHud.setText(pluginId, id, text)) {
            return ToolResult.failure("no_such_line", "line '$id' does not exist")
        }
        return ToolResult.success()
    }

    private fun hudRemoveLine(args: JsonObject): ToolResult {
        if (PluginPermission.HUD !in permissions) return ToolResult.permissionDenied(PluginPermission.HUD)
        val id = requireString(args, "id") ?: return ToolResult.badArguments("id is required")
        PluginHud.remove(pluginId, id)
        return ToolResult.success()
    }

    // -- Config ------------------------------------------------------------

    private fun configGet(args: JsonObject): ToolResult {
        if (PluginPermission.CONFIG !in permissions) return ToolResult.permissionDenied(PluginPermission.CONFIG)
        val key = requireString(args, "key") ?: return ToolResult.badArguments("key is required")
        val value = configCache.get(key)
            ?: return ToolResult.success(null)
        return ToolResult.success(value)
    }

    @Synchronized
    private fun configSet(args: JsonObject): ToolResult {
        if (PluginPermission.CONFIG !in permissions) return ToolResult.permissionDenied(PluginPermission.CONFIG)
        val key = requireString(args, "key") ?: return ToolResult.badArguments("key is required")
        if (key.length > 64) return ToolResult.badArguments("key too long")
        val value = args.get("value") ?: return ToolResult.badArguments("value is required")

        configCache.add(key, value)
        saveConfig()
        return ToolResult.success()
    }

    private fun loadConfig(): JsonObject {
        return try {
            if (!configFile.isFile) JsonObject()
            else gson.fromJson(configFile.readText(), JsonObject::class.java) ?: JsonObject()
        } catch (t: Throwable) {
            log("config unreadable, starting empty: ${t.message}")
            JsonObject()
        }
    }

    @Synchronized
    private fun saveConfig() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(gson.toJson(configCache))
        } catch (t: Throwable) {
            log("could not save config: ${t.message}")
        }
    }

    // -- Network -----------------------------------------------------------

    private fun netGet(args: JsonObject): ToolResult {
        if (PluginPermission.NETWORK !in permissions) return ToolResult.permissionDenied(PluginPermission.NETWORK)
        val url = requireString(args, "url") ?: return ToolResult.badArguments("url is required")
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return ToolResult.badArguments("only http(s) urls are allowed")
        }
        if (!budget.tryConsume()) {
            return ToolResult.failure(
                "budget_exceeded",
                "daily limit of ${budget.dailyLimit} requests reached"
            )
        }
        val body = Http.get(url, accept = "*/*")
            ?: return ToolResult.failure("network_error", "request failed")
        val result = JsonObject()
        result.addProperty("body", body)
        result.addProperty("remaining", budget.remaining)
        return ToolResult.success(result)
    }

    // -- Filesystem --------------------------------------------------------

    private fun fsRead(args: JsonObject): ToolResult {
        if (PluginPermission.FILESYSTEM !in permissions) return ToolResult.permissionDenied(PluginPermission.FILESYSTEM)
        val path = requireString(args, "path") ?: return ToolResult.badArguments("path is required")
        val file = resolve(path) ?: return ToolResult.failure("invalid_path", "path escapes the data folder")
        if (!file.isFile) return ToolResult.failure("not_found", "no such file: $path")
        if (file.length() > MAX_FILE_BYTES) return ToolResult.failure("too_large", "file exceeds 1 MiB")
        val content = runCatching { file.readText() }.getOrElse {
            return ToolResult.failure("read_failed", it.message ?: "read failed")
        }
        val result = JsonObject()
        result.addProperty("content", content)
        return ToolResult.success(result)
    }

    private fun fsWrite(args: JsonObject): ToolResult {
        if (PluginPermission.FILESYSTEM !in permissions) return ToolResult.permissionDenied(PluginPermission.FILESYSTEM)
        val path = requireString(args, "path") ?: return ToolResult.badArguments("path is required")
        val content = requireString(args, "content") ?: return ToolResult.badArguments("content is required")
        if (content.length > MAX_FILE_BYTES) return ToolResult.failure("too_large", "content exceeds 1 MiB")
        val file = resolve(path) ?: return ToolResult.failure("invalid_path", "path escapes the data folder")
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult.success()
        } catch (t: Throwable) {
            ToolResult.failure("write_failed", t.message ?: "write failed")
        }
    }

    /**
     * Resolves a plugin-supplied relative path inside [dataDir].
     * Returns null for anything that escapes it (`..`, absolute paths,
     * symlink tricks resolved by canonical comparison).
     */
    private fun resolve(path: String): File? {
        return try {
            if (path.contains("\u0000")) return null
            val root = dataDir.canonicalFile
            val candidate = File(root, path).canonicalFile
            if (!candidate.path.startsWith(root.path + File.separator) && candidate != root) {
                return null
            }
            candidate
        } catch (t: Throwable) {
            null
        }
    }

    // -- Argument helpers --------------------------------------------------

    private fun requireString(args: JsonObject, key: String): String? =
        runCatching {
            args.get(key)?.takeIf { !it.isJsonNull }?.asString
        }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun requireFloat(args: JsonObject, key: String): Float? =
        runCatching {
            args.get(key)?.takeIf { !it.isJsonNull }?.asFloat
        }.getOrNull()

    private fun requireInt(args: JsonObject, key: String): Int? =
        runCatching {
            args.get(key)?.takeIf { !it.isJsonNull }?.asInt
        }.getOrNull()

    companion object {
        private const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
        private const val MAX_FILE_BYTES = 1024 * 1024
        private val LINE_ID = Regex("^[A-Za-z0-9._-]{1,48}$")
    }
}
