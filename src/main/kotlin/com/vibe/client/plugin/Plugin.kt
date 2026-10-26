package com.vibe.client.plugin

/**
 * The only type a third-party plugin is allowed to implement.
 *
 * Deliberate design: a plugin NEVER receives the Minecraft client, a class
 * loader, or a File handle. Everything it can do goes through [PluginHost]
 * with a JSON tool call — the same shape the DSH tool API uses.
 *
 * All callbacks are invoked from the game thread. A callback that throws is
 * caught by PluginManager, the plugin is disabled, and the game keeps running.
 */
interface Plugin {

    /** Called once after the jar is loaded and validated. */
    fun onLoad(host: PluginHost) {}

    /** Called when the user (or config) enables the plugin. */
    fun onEnable(host: PluginHost) {}

    /** Called when the plugin is disabled. Undo HUD lines here if needed. */
    fun onDisable(host: PluginHost) {}

    /** Called before the plugin is dropped from memory. */
    fun onUnload(host: PluginHost) {}
}
