package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.minecraft.client.MinecraftClient

/**
 * FreeLook — look around 360° without changing where the player is facing.
 *
 * How it works, and why this way:
 *
 * The game turns the player inside [net.minecraft.client.Mouse.updateMouse],
 * using the mouse delta and the user's sensitivity. Rather than duplicate that
 * maths (which would have to guess the sensitivity curve, the raw-input factor
 * and the invert-Y preference), the mouse mixin brackets the call:
 *
 *   1. HEAD  — remember the player's yaw/pitch.
 *   2. TAIL  — read the values the game just wrote, compute the difference,
 *              put the player's own rotation back, and add the difference to
 *              the free-look angles instead.
 *
 * The result is exact: the camera turns by precisely the amount the game would
 * have turned the player, with every vanilla setting respected automatically.
 * The player entity never rotates, so movement keeps its original direction.
 */
object FreeLook {

    /**
     * True while the free camera is engaged. Read by the camera mixin as
     * `getActive()` — Kotlin generates the Java getter from this property.
     */
    @Volatile
    var active: Boolean = false
        private set

    /** Camera angles used while free-looking. */
    @Volatile
    private var freeYaw: Float = 0f

    @Volatile
    private var freePitch: Float = 0f

    /** The player's rotation captured at the start of the bracketed turn. */
    @Volatile
    private var beforeYaw: Float = 0f

    @Volatile
    private var beforePitch: Float = 0f

    /** False when the bracket did not open (no player, or not active). */
    @Volatile
    private var hasBefore: Boolean = false

    val yaw: Float get() = freeYaw

    val pitch: Float get() = freePitch

    val enabled: Boolean get() = ConfigManager.current.freeLook

    /** Called every tick with the current state of the free-look key. */
    fun setHeld(client: MinecraftClient, held: Boolean) {
        if (!enabled) {
            if (active) stop()
            return
        }
        if (held && !active) begin(client) else if (!held && active) stop()
    }

    /** Starts free-looking, anchored at wherever the player is looking now. */
    private fun begin(client: MinecraftClient) {
        val player = client.player ?: return
        freeYaw = player.yaw
        freePitch = player.pitch
        active = true
    }

    /** Returns the camera to the player's own rotation. */
    fun stop() {
        active = false
        hasBefore = false
    }

    /** Mouse.updateMouse HEAD: remember the player's rotation. */
    fun captureBeforeTurn() {
        if (!active) return
        val player = MinecraftClient.getInstance().player ?: return
        beforeYaw = player.yaw
        beforePitch = player.pitch
        hasBefore = true
    }

    /**
     * Mouse.updateMouse TAIL: steal the rotation change the game just applied,
     * hand it to the free-look angles, and restore the player's own rotation.
     */
    fun consumeTurn() {
        if (!active || !hasBefore) return
        hasBefore = false

        val player = MinecraftClient.getInstance().player ?: return
        val deltaYaw = player.yaw - beforeYaw
        val deltaPitch = player.pitch - beforePitch

        player.yaw = beforeYaw
        player.pitch = beforePitch

        freeYaw += deltaYaw
        // Same clamp vanilla applies, so the free camera cannot flip over.
        freePitch = (freePitch + deltaPitch).coerceIn(-90f, 90f)
    }
}
