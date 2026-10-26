package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.minecraft.client.MinecraftClient

/**
 * Client-side world tweaks: fullbright, locked time and clear weather.
 *
 * All three are purely visual and local — nothing is sent to the server, so
 * they cannot get anyone banned. They are applied every tick because the game
 * (or the server) keeps writing the vanilla values back:
 *
 *  - Fullbright is applied through the gamma option, the same value the vanilla
 *    "Brightness" slider uses. Restoring the previous gamma when the feature is
 *    switched off matters: leaving it at max permanently would be a bug the
 *    user never asked for.
 *  - Time and weather are applied through the world's client-side fields, which
 *    is exactly what the vanilla `/time set` does locally.
 */
object WorldTweaks {

    /** Gamma used while fullbright is on. Vanilla caps the slider at 1.0. */
    private const val FULLBRIGHT_GAMMA = 15.0

    /** Player's own brightness, remembered so it can be restored. */
    private var savedGamma: Double? = null

    fun tick(client: MinecraftClient) {
        applyFullbright(client)
        applyTime(client)
        applyWeather(client)
    }

    // -- Fullbright --------------------------------------------------------

    private fun applyFullbright(client: MinecraftClient) {
        val options = client.options ?: return
        val wanted = ConfigManager.current.fullbright

        if (wanted) {
            if (savedGamma == null) savedGamma = options.gamma.value
            if (options.gamma.value != FULLBRIGHT_GAMMA) {
                options.gamma.value = FULLBRIGHT_GAMMA
            }
        } else {
            val previous = savedGamma ?: return
            if (options.gamma.value != previous) options.gamma.value = previous
            savedGamma = null
        }
    }

    /** Restores the user's brightness — called on shutdown. */
    fun reset(client: MinecraftClient) {
        val previous = savedGamma ?: return
        client.options?.gamma?.value = previous
        savedGamma = null
    }

    // -- Locked time -------------------------------------------------------

    private fun applyTime(client: MinecraftClient) {
        val locked = ConfigManager.current.lockedTime
        if (locked < 0) return
        val world = client.world ?: return
        // setTimeOfDay is client-side only; the server keeps its own clock.
        if (world.timeOfDay.toInt() != locked) {
            world.timeOfDay = locked.toLong()
        }
    }

    // -- Clear weather -----------------------------------------------------

    private fun applyWeather(client: MinecraftClient) {
        if (!ConfigManager.current.clearWeather) return
        val world = client.world ?: return
        if (world.isRaining) world.setRainGradient(0f)
        if (world.isThundering) world.setThunderGradient(0f)
    }

    // -- Zoom --------------------------------------------------------------

    /** True while the zoom key is held. */
    @Volatile
    var zooming: Boolean = false

    /**
     * The FOV divisor to apply right now, or 1.0 for "leave it alone".
     *
     * A divisor rather than an absolute FOV: vanilla has already folded in
     * sprinting, potion effects and the "FOV Effects" slider, and dividing that
     * result preserves all of them.
     */
    fun zoomDivisor(anyPlayer: Any?): Double {
        if (!zooming) return 1.0
        return ConfigManager.current.zoomFactor.coerceIn(1.0, 20.0)
    }
}