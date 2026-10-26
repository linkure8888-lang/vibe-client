package com.vibe.client.plugin

import com.google.gson.JsonObject
import com.vibe.client.VibeClient

/**
 * Parsed `vibe-plugin.json`.
 *
 * Everything here is untrusted input: [parse] never throws, and missing or
 * mistyped fields fall back to a safe value. Validation problems are reported
 * through [problems] so the UI can show a readable reason instead of a stack
 * trace.
 */
/** One HUD line declared in the manifest (no code needed). */
data class DeclarativeLine(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val color: Int
)

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val apiVersion: Int,
    val mainClass: String?,
    val permissions: Set<PluginPermission>,
    val requestsPerDay: Int,
    val lines: List<DeclarativeLine>
) {

    /** Non-fatal issues found while parsing (unknown permissions, ...). */
    var problems: List<String> = emptyList()

    val isDeclarative: Boolean get() = mainClass == null

    companion object {

        const val FILE_NAME = "vibe-plugin.json"
        const val SUPPORTED_API = 1
        const val DEFAULT_REQUESTS_PER_DAY = 300

        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

        /**
         * Parses a manifest object. Returns null only when the input is so
         * broken that no id can be recovered.
         */
        fun parse(json: JsonObject?): PluginManifest? {
            if (json == null) return null

            val id = readString(json, "id")?.lowercase() ?: return null
            if (!ID_PATTERN.matches(id)) {
                VibeClient.logger.warn("Plugin manifest has invalid id: '$id'")
                return null
            }

            val issues = mutableListOf<String>()

            val rawPermissions = json.getAsJsonArray("permissions")
            val permissions = LinkedHashSet<PluginPermission>()
            if (rawPermissions != null) {
                for (element in rawPermissions) {
                    val text = runCatching { element.asString }.getOrNull()
                    if (text == null) {
                        issues += "permission entry is not a string"
                        continue
                    }
                    val permission = PluginPermission.fromId(text)
                    if (permission == null) {
                        issues += "unknown permission '$text' ignored"
                    } else {
                        permissions += permission
                    }
                }
            }

            val apiVersion = readInt(json, "apiVersion") ?: SUPPORTED_API
            if (apiVersion != SUPPORTED_API) {
                issues += "apiVersion $apiVersion is not supported (need $SUPPORTED_API)"
            }

            val requests = readInt(json, "requestsPerDay") ?: DEFAULT_REQUESTS_PER_DAY
            val boundedRequests = requests.coerceIn(0, DEFAULT_REQUESTS_PER_DAY)
            if (requests > DEFAULT_REQUESTS_PER_DAY) {
                issues += "requestsPerDay capped to $DEFAULT_REQUESTS_PER_DAY"
            }

            return PluginManifest(
                id = id,
                name = readString(json, "name") ?: id,
                version = readString(json, "version") ?: "0.0.0",
                author = readString(json, "author") ?: "unknown",
                description = readString(json, "description") ?: "",
                apiVersion = apiVersion,
                mainClass = readString(json, "mainClass")?.takeIf { it.isNotBlank() },
                permissions = permissions,
                requestsPerDay = boundedRequests,
                lines = parseLines(json, issues)
            ).also { it.problems = issues }
        }

        /**
         * Declarative HUD lines: `"lines": [{ "id": "hi", "text": "Hello" }]`.
         * Lines without a valid id/text are skipped with an issue, never fatal.
         */
        private fun parseLines(json: JsonObject, issues: MutableList<String>): List<DeclarativeLine> {
            val array = runCatching { json.getAsJsonArray("lines") }.getOrNull() ?: return emptyList()
            val result = ArrayList<DeclarativeLine>(array.size())
            for (element in array) {
                if (!element.isJsonObject) {
                    issues += "a line entry is not an object"
                    continue
                }
                val obj = element.asJsonObject
                val lineId = readString(obj, "id")
                val text = readString(obj, "text")
                if (lineId == null || text == null) {
                    issues += "a line is missing id or text"
                    continue
                }
                result += DeclarativeLine(
                    id = lineId,
                    text = text,
                    x = readFloat(obj, "x") ?: 4f,
                    y = readFloat(obj, "y") ?: 4f,
                    color = readInt(obj, "color") ?: 0xFFFFFFFF.toInt()
                )
            }
            return result
        }

        private fun readFloat(json: JsonObject, key: String): Float? =
            runCatching { json.get(key)?.takeIf { !it.isJsonNull }?.asFloat }.getOrNull()

        private fun readString(json: JsonObject, key: String): String? =
            runCatching { json.get(key)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()

        private fun readInt(json: JsonObject, key: String): Int? =
            runCatching { json.get(key)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()
    }
}
