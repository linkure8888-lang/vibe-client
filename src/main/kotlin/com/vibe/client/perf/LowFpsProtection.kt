package com.vibe.client.perf

import com.vibe.client.config.ConfigManager
import com.vibe.client.hud.FpsCounter
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.ParticlesMode

/**
 * Auto Low-FPS Protection.
 *
 * While the user is playing (no screen open) and the FPS stays below the
 * threshold, particles are downgraded step by step:
 *
 *   ALL -> DECREASED -> MINIMAL
 *
 * The ORIGINAL value is remembered and restored as soon as the FPS recovers,
 * so the user's own video settings are never silently destroyed.
 *
 * Runs once per tick from ClientHooks. The FPS sampling itself is 1 Hz.
 */
object LowFpsProtection {

    private const val LOW_THRESHOLD = 45
    private const val HIGH_THRESHOLD = 70
    private const val LOW_TICKS_NEEDED = 60   // ~3 s below threshold
    private const val HIGH_TICKS_NEEDED = 200 // ~10 s comfortable before restore

    /** The user's own particles setting, captured on the first downgrade. */
    private var original: ParticlesMode? = null

    private var lowTicks = 0
    private var highTicks = 0

    @Volatile var lastAction: String = ""

    fun tick(client: MinecraftClient) {
        if (!ConfigManager.current.lowFpsProtection) {
            restore(client)
            return
        }
        if (client.world == null || client.currentScreen != null) return

        val fps = FpsCounter.fps
        if (fps <= 0) return

        if (fps < LOW_THRESHOLD) {
            lowTicks++
            highTicks = 0
            if (lowTicks >= LOW_TICKS_NEEDED) {
                lowTicks = 0
                downgrade(client)
            }
        } else if (fps > HIGH_THRESHOLD) {
            highTicks++
            lowTicks = 0
            if (highTicks >= HIGH_TICKS_NEEDED) {
                highTicks = 0
                restore(client)
            }
        } else {
            // Between thresholds: stay put, decay slowly.
            if (lowTicks > 0) lowTicks--
            if (highTicks > 0) highTicks--
        }
    }

    private fun downgrade(client: MinecraftClient) {
        val option = client.options.particles
        val current = option.value
        val next = when (current) {
            ParticlesMode.ALL -> ParticlesMode.DECREASED
            ParticlesMode.DECREASED -> ParticlesMode.MINIMAL
            ParticlesMode.MINIMAL -> return
        }
        if (original == null) original = current
        option.setValue(next)
        lastAction = "particles -> ${next.name}"
    }

    private fun restore(client: MinecraftClient) {
        val saved = original ?: return
        if (client.options.particles.value != saved) {
            client.options.particles.setValue(saved)
            lastAction = "particles restored -> ${saved.name}"
        }
        original = null
    }

    /** Called when the user changes the setting manually / on shutdown. */
    fun reset(client: MinecraftClient) {
        restore(client)
        lowTicks = 0
        highTicks = 0
    }
}