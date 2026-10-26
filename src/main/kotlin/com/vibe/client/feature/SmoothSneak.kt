package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.EntityPose
import net.minecraft.entity.player.PlayerEntity

/**
 * Smooth Sneak — the camera eases down when you crouch instead of snapping.
 *
 * Vanilla teleports the eye position the instant you press Shift, which reads
 * as a jarring jump. Badlion/Lunar interpolate it, and so do we.
 *
 * How it works: Minecraft's own camera maths stay untouched. A mixin runs at
 * the tail of `Camera.update` and adds a small vertical correction:
 *
 *     correction = (target - eased) * (standingEye - sneakingEye)
 *
 *  - `target` is 1 while crouching, 0 while standing;
 *  - `eased` follows `target` with a per-tick lerp;
 *  - when you start crouching, vanilla has already dropped the camera, so the
 *    correction is positive and the view starts high, then settles;
 *  - when you stand up, the correction is negative and the view rises smoothly.
 *
 * The eased value advances in [tick] (20 Hz), so the render path is a plain
 * field read — no timing maths and no allocation per frame.
 */
object SmoothSneak {

    /** How fast the camera settles, per tick. 1.0 would be instant. */
    private const val SPEED = 0.35f

    /** Below this distance to the target the animation snaps and stops. */
    private const val SNAP = 0.01f

    /** Vanilla eye heights for a standing and a crouching player. */
    private const val STANDING_EYE = 1.62f
    private const val CROUCHING_EYE = 1.27f

    @Volatile private var eased = 0f
    @Volatile private var target = 0f
    @Volatile private var standingEye = STANDING_EYE
    @Volatile private var sneakingEye = CROUCHING_EYE

    /** True when the feature should be applied at all. */
    val enabled: Boolean get() = ConfigManager.current.smoothSneak

    /** Advances the animation. Called from the client tick hook. */
    fun tick(client: MinecraftClient) {
        if (!enabled) {
            eased = 0f
            target = 0f
            return
        }

        val player = client.player ?: return
        // 1.21.1 calls the pose CROUCHING; SNEAKING does not exist there.
        target = if (player.pose == EntityPose.CROUCHING) 1f else 0f

        // Vanilla player eye heights. Kept as constants because the pose-aware
        // getEyeHeight overload is not public in this mapping set.
        standingEye = STANDING_EYE
        sneakingEye = CROUCHING_EYE

        val next = eased + (target - eased) * SPEED
        eased = if (kotlin.math.abs(next - target) < SNAP) target else next
    }

    /**
     * Vertical offset to add to the camera this frame, in blocks.
     * Zero when the feature is off or the animation has settled.
     */
    fun correction(player: PlayerEntity): Float {
        if (!enabled) return 0f
        val delta = standingEye - sneakingEye
        if (delta == 0f) return 0f
        return (target - eased) * delta
    }
}
