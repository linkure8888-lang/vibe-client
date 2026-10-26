package com.vibe.client.binds

import com.vibe.client.VibeClient
import com.vibe.client.config.ConfigManager
import com.vibe.client.module.ModuleManager
import com.vibe.client.ui.ToastManager
import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW

/**
 * A single key binding: one key, one action.
 *
 * Kept as a plain serializable data class so it round-trips through the config
 * without any custom adapter. [key] is a raw GLFW key code, which keeps the
 * binding independent of the user's keyboard layout *and* lets it survive a
 * language switch — matching on characters would break on a Russian layout.
 */
data class Bind(
    /** GLFW key code, e.g. GLFW_KEY_G. */
    var key: Int = GLFW.GLFW_KEY_UNKNOWN,
    /** What to do when the key is pressed. */
    var action: BindAction = BindAction.CHAT,
    /**
     * Action payload, by type:
     *  - CHAT: the message or command to send (leading `/` optional)
     *  - TOGGLE_MODULE: the module name
     *  - OPEN_MENU / ZOOM: unused
     */
    var payload: String = "",
    /** Human label shown in the menu; empty means "derive from the action". */
    var label: String = "",
    var enabled: Boolean = true
) {
    /** Short description for the menu row. */
    fun describe(): String {
        if (label.isNotBlank()) return label
        return when (action) {
            BindAction.CHAT -> if (payload.startsWith("/")) payload else "/$payload"
            BindAction.TOGGLE_MODULE -> "Toggle $payload"
            BindAction.OPEN_MENU -> "Open client menu"
            BindAction.ZOOM -> "Zoom"
            BindAction.WAYPOINT -> "Set waypoint"
        }
    }
}

/** The kinds of action a bind can run. */
enum class BindAction {
    /** Send a chat message or command. */
    CHAT,

    /** Flip a HUD module on or off. */
    TOGGLE_MODULE,

    /** Open the client menu. */
    OPEN_MENU,

    /** Hold-to-zoom (same as the built-in key). */
    ZOOM,

    /** Save the current position as the waypoint (press again to clear). */
    WAYPOINT
}

/**
 * User-defined key bindings, plus a conflict detector.
 *
 * Scope, deliberately: every action here is either local to the client or a
 * normal chat command. There is no auto-clicker, no repeating attack, no
 * input automation for combat — those are cheats, they get accounts banned,
 * and a client that ships them is a client that gets its users banned. What is
 * left is the genuinely useful half of a macro system: one key for `/spawn`,
 * one key to toggle the FPS counter, and so on.
 *
 * Execution model: key state is polled once per client tick (20 Hz) and
 * compared against the previous tick, so a bind fires on the press edge — not
 * repeatedly while the key is held, which would spam chat.
 */
object BindManager {

    /** Hard cap; the config file is user-editable and must stay bounded. */
    const val MAX_BINDS = 64

    private val binds = ArrayList<Bind>()

    /** Key state from the previous tick, indexed by GLFW key code. */
    private val previous = BooleanArray(GLFW.GLFW_KEY_LAST + 1)

    /** True once the config has been applied, so we never overwrite it. */
    private var loaded = false

    val all: List<Bind> get() = binds

    // -- Lifecycle ---------------------------------------------------------

    /** Pulls the saved binds out of the config. Called once at startup. */
    fun load() {
        binds.clear()
        for (saved in ConfigManager.current.binds) {
            // Defensive: the file is user-editable, so validate every field
            // rather than trusting it and crashing on a bad key code.
            if (saved.key !in 0..GLFW.GLFW_KEY_LAST) continue
            binds.add(saved)
            if (binds.size >= MAX_BINDS) break
        }
        loaded = true
        VibeClient.logger.info("Binds loaded: ${binds.size}")
    }

    /** Writes the current binds back into the config and marks it dirty. */
    fun persist() {
        if (!loaded) return
        ConfigManager.current.binds = ArrayList(binds)
        ConfigManager.requestSave()
    }

    // -- Editing -----------------------------------------------------------

    fun add(bind: Bind) {
        if (binds.size >= MAX_BINDS) return
        binds.add(bind)
        persist()
    }

    fun removeAt(index: Int) {
        if (index !in binds.indices) return
        binds.removeAt(index)
        persist()
    }

    fun setEnabled(index: Int, enabled: Boolean) {
        binds.getOrNull(index)?.enabled = enabled
        persist()
    }

    fun setKey(index: Int, key: Int) {
        binds.getOrNull(index)?.key = key
        persist()
    }

    // -- Smart assistant ---------------------------------------------------

    /**
     * Returns the bind that already uses [key], or null.
     *
     * This is the core of the "smart" part: two binds on one key means one of
     * them silently never fires, which looks like a broken macro rather than a
     * configuration mistake. Detecting it up front turns a confusing bug into a
     * clear message.
     */
    fun conflictFor(key: Int, ignoringIndex: Int = -1): Bind? {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return null
        return binds.withIndex().firstOrNull { (index, bind) ->
            index != ignoringIndex && bind.enabled && bind.key == key
        }?.value
    }

    /** All duplicated keys, as "key -> [labels]". Used by the assistant view. */
    fun conflicts(): Map<Int, List<Bind>> =
        binds.filter { it.enabled && it.key != GLFW.GLFW_KEY_UNKNOWN }
            .groupBy { it.key }
            .filterValues { it.size > 1 }

    /**
     * Suggests useful binds the user has not made yet.
     *
     * The suggestions are the commands and toggles people actually bind, and
     * they are filtered against what is already configured, so the list shrinks
     * as the user works through it instead of repeating itself.
     */
    fun suggestions(): List<Bind> {
        val takenKeys = binds.map { it.key }.toSet()
        val takenPayloads = binds.map { it.payload.lowercase() }.toSet()
        val freeKeys = FREE_KEY_CANDIDATES.filter { it !in takenKeys }

        val wanted = buildList {
            add(Bind(action = BindAction.OPEN_MENU, payload = "", label = "Open Vibe menu"))
            add(Bind(action = BindAction.CHAT, payload = "spawn", label = "Command: /spawn"))
            add(Bind(action = BindAction.CHAT, payload = "home", label = "Command: /home"))
            add(Bind(action = BindAction.TOGGLE_MODULE, payload = "Keystrokes",
                label = "Toggle Keystrokes"))
            add(Bind(action = BindAction.TOGGLE_MODULE, payload = "Coordinates",
                label = "Toggle Coordinates"))
            add(Bind(action = BindAction.TOGGLE_MODULE, payload = "FPS",
                label = "Toggle FPS counter"))
            add(Bind(action = BindAction.WAYPOINT, payload = "",
                label = "Set waypoint"))
        }

        val result = ArrayList<Bind>()
        var keyIndex = 0
        for (candidate in wanted) {
            // Skip anything the user already has, by payload for commands and
            // modules, by action for the single-instance actions.
            val duplicate = when (candidate.action) {
                BindAction.CHAT, BindAction.TOGGLE_MODULE ->
                    candidate.payload.lowercase() in takenPayloads
                else -> binds.any { it.action == candidate.action }
            }
            if (duplicate) continue
            if (keyIndex >= freeKeys.size) break
            candidate.key = freeKeys[keyIndex++]
            result.add(candidate)
        }
        return result
    }

    /** Keys offered to the assistant, in the order it should try them. */
    private val FREE_KEY_CANDIDATES = listOf(
        GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K,
        GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_X
    )

    // -- Execution ---------------------------------------------------------

    /**
     * Polls every bind once per tick.
     *
     * Called from the client tick hook. Screens block execution on purpose: a
     * bind firing while the user is typing in chat would send half-typed
     * commands.
     */
    fun tick(client: MinecraftClient) {
        if (client.currentScreen != null) {
            captureState(client)
            return
        }

        for (bind in binds) {
            if (!bind.enabled || bind.key == GLFW.GLFW_KEY_UNKNOWN) continue
            val pressed = GLFW.glfwGetKey(client.window.handle, bind.key) == GLFW.GLFW_PRESS
            // Fire on the press edge only.
            if (pressed && !previous.getOrElse(bind.key) { false }) {
                runCatching { execute(client, bind) }.onFailure { t ->
                    VibeClient.logger.warn("Bind '${bind.describe()}' failed: ${t.message}")
                }
            }
        }
        captureState(client)
    }

    /** Remembers this tick's key state so the next tick can detect edges. */
    private fun captureState(client: MinecraftClient) {
        for (bind in binds) {
            if (bind.key !in 0..GLFW.GLFW_KEY_LAST) continue
            previous[bind.key] =
                GLFW.glfwGetKey(client.window.handle, bind.key) == GLFW.GLFW_PRESS
        }
    }

    private fun execute(client: MinecraftClient, bind: Bind) {
        when (bind.action) {
            BindAction.CHAT -> {
                var message = bind.payload.trim()
                if (message.isEmpty()) return
                val handler = client.player?.networkHandler ?: return
                if (message.startsWith("/")) {
                    // Commands and chat go through different packet types; using
                    // the chat path for a command makes the server reject it.
                    handler.sendChatCommand(message.removePrefix("/"))
                } else {
                    handler.sendChatMessage(message)
                }
            }

            BindAction.TOGGLE_MODULE -> {
                val module = ModuleManager.byName(bind.payload)
                if (module == null) {
                    ToastManager.show("No module named '${bind.payload}'")
                    return
                }
                module.toggle()
                ConfigManager.requestSave()
                val state = if (module.enabled) "on" else "off"
                ToastManager.show("${module.name}: $state")
            }

            BindAction.OPEN_MENU -> {
                client.setScreen(com.vibe.client.gui.ClickGui(null))
            }

            BindAction.ZOOM -> {
                // Toggle-style zoom for people who prefer it to holding a key.
                com.vibe.client.feature.WorldTweaks.zooming =
                    !com.vibe.client.feature.WorldTweaks.zooming
            }

            BindAction.WAYPOINT -> {
                // One slot, like Lunar's quick waypoint: press to drop it at
                // the current position, press again to pick it up.
                val module = ModuleManager.byName("Waypoint")
                    as? com.vibe.client.module.impl.WaypointModule ?: return
                val wasSet = module.hasWaypoint
                module.toggleAtPlayer(client.player)
                val nowSet = module.hasWaypoint
                if (wasSet != nowSet) {
                    ToastManager.show(
                        if (nowSet) com.vibe.client.lang.Lang.t("waypoint.set")
                        else com.vibe.client.lang.Lang.t("waypoint.cleared")
                    )
                }
            }
        }
    }

    /** GLFW key code to a readable name, for the menu. */
    fun keyName(key: Int): String {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "-"
        val name = GLFW.glfwGetKeyName(key, 0)
        if (name != null) return name.uppercase()
        // Named keys have no character; fall back to the GLFW constant number,
        // which is still enough to rebind by.
        return "Key $key"
    }
}
