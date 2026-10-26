package com.vibe.client.plugin

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * What a plugin is allowed to talk to.
 *
 * One entry point, one shape: `call(tool, arguments)` -> [ToolResult].
 * Everything is validated here, so a plugin cannot reach the game, the disk,
 * or the network except through the tools its permissions allow.
 */
interface PluginHost {

    val pluginId: String

    val permissions: Set<PluginPermission>

    /** Runs one tool call. NEVER throws — failures come back as [ToolResult]. */
    fun call(tool: String, arguments: JsonObject): ToolResult

    /** Convenience: writes to the client log prefixed with the plugin id. */
    fun log(message: String)
}

/** One tool result, shaped like the DSH tool protocol. */
data class ToolResult(
    val success: Boolean,
    val result: JsonElement? = null,
    val error: String? = null,
    val message: String? = null
) {

    fun toJson(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("ok", success)
        result?.let { obj.add("result", it) }
        error?.let { obj.addProperty("error", it) }
        message?.let { obj.addProperty("message", it) }
        return obj
    }

    companion object {

        fun success(result: JsonElement? = null): ToolResult =
            ToolResult(success = true, result = result)

        fun failure(code: String, message: String): ToolResult =
            ToolResult(success = false, error = code, message = message)

        fun permissionDenied(permission: PluginPermission): ToolResult =
            failure("permission_denied", "permission '${permission.id}' is not granted")

        fun badArguments(message: String): ToolResult =
            failure("bad_arguments", message)

        fun unknownTool(tool: String): ToolResult =
            failure("unknown_tool", "no such tool: $tool")
    }
}
