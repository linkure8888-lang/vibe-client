package com.vibe.client.perf

import com.vibe.client.VibeClient
import com.vibe.client.config.ConfigManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.CloudRenderMode
import net.minecraft.client.option.GraphicsMode
import net.minecraft.client.option.ParticlesMode
/**
 * Vibe FPS Boost — measurable frame-rate wins that need no extra mod.
 *
 * These are the settings that actually cost frames, applied through the same
 * vanilla options the video-settings screen writes. Nothing here is a fake
 * "boost": clouds, entity shadows, biome blend and mipmaps are the classic,
 * well-known frame sinks, and the graphics preset gates several more.
 *
 * Two rules keep this safe to hand to a stranger:
 *
 *  1. Everything is remembered and restored. [disable] puts every option back
 *     to the exact value the user had, so switching the boost off is a no-op
 *     rather than a permanent change to their game.
 *  2. The user's own settings are only captured on the first application, and
 *     never overwritten while the boost is active. Re-applying is idempotent.
 *
 * The settings that cost the most on a weak machine — render distance and
 * simulation distance — are deliberately NOT touched: silently shortening how
 * far someone can see changes gameplay, not just frame rate.
 */
object FpsBoost {

    private var applied = false

    // Captured originals. Null means "not captured yet".
    private var savedClouds: CloudRenderMode? = null
    private var savedShadows: Boolean? = null
    private var savedBiomeBlend: Int? = null
    private var savedMipmaps: Int? = null
    private var savedParticles: ParticlesMode? = null
    private var savedGraphics: GraphicsMode? = null
    private var savedBobView: Boolean? = null
    private var savedBlur: Int? = null

    val isApplied: Boolean get() = applied

    /**
     * Turns the boost on. Safe to call every tick: it returns immediately once
     * the settings are already in place.
     */
    fun enable(client: MinecraftClient) {
        val options = client.options ?: return
        if (applied) return

        // Capture first, so a partially applied boost can still be undone.
        savedClouds = options.cloudRenderMode.value
        savedShadows = options.entityShadows.value
        savedBiomeBlend = options.biomeBlendRadius.value
        savedMipmaps = options.mipmapLevels.value
        savedParticles = options.particles.value
        savedGraphics = options.graphicsMode.value
        savedBobView = options.bobView.value
        savedBlur = options.menuBackgroundBlurriness.value

        apply(options)
        applied = true
        VibeClient.logger.info("FPS Boost enabled")
    }

    private fun apply(options: net.minecraft.client.option.GameOptions) {
        // Clouds are drawn above the world with no culling; "fast" is a huge
        // win on integrated GPUs and looks nearly identical in play.
        options.cloudRenderMode.value = CloudRenderMode.FAST
        // Entity shadows cost a second geometry pass per entity.
        options.entityShadows.value = false
        // Biome blend smooths colour across block borders — expensive and
        // purely cosmetic.
        options.biomeBlendRadius.value = 0
        // Mipmaps cost VRAM and upload time; 2 is the sweet spot.
        if (options.mipmapLevels.value > 2) options.mipmapLevels.value = 2
        // Particle count scales with everything happening on screen.
        if (options.particles.value == ParticlesMode.ALL) {
            options.particles.value = ParticlesMode.DECREASED
        }
        // Deliberately NOT touching graphicsMode: "Fast" disables fancy leaves
        // and clear water, which is a big win but makes the game look worse.
        // The settings above already recover most of the frame rate without
        // changing how the world looks.
        // View bobbing forces a matrix rebuild per frame.
        options.bobView.value = false
        // The blurred menu background renders the world an extra time.
        options.menuBackgroundBlurriness.value = 0
    }

    /** Restores every option to the value captured by [enable]. */
    fun disable(client: MinecraftClient) {
        val options = client.options ?: return
        // Even if `applied` is false, restore anything that was captured —
        // this also runs on shutdown to guarantee a clean exit.
        savedClouds?.let { options.cloudRenderMode.value = it }
        savedShadows?.let { options.entityShadows.value = it }
        savedBiomeBlend?.let { options.biomeBlendRadius.value = it }
        savedMipmaps?.let { options.mipmapLevels.value = it }
        savedParticles?.let { options.particles.value = it }
        savedGraphics?.let { options.graphicsMode.value = it }
        savedBobView?.let { options.bobView.value = it }
        savedBlur?.let { options.menuBackgroundBlurriness.value = it }

        savedClouds = null
        savedShadows = null
        savedBiomeBlend = null
        savedMipmaps = null
        savedParticles = null
        savedGraphics = null
        savedBobView = null
        savedBlur = null

        if (applied) VibeClient.logger.info("FPS Boost disabled")
        applied = false
    }

    /**
     * Called from the tick hook. Applies or lifts the boost to match the
     * config, so the toggle in the menu takes effect immediately without a
     * restart.
     */
    fun tick(client: MinecraftClient) {
        if (ConfigManager.current.fpsBoost) enable(client) else disable(client)
    }

    /** Called on shutdown so the user's settings survive the session. */
    fun reset(client: MinecraftClient) = disable(client)
}
