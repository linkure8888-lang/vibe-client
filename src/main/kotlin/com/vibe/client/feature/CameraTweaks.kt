package com.vibe.client.feature

import com.vibe.client.config.ConfigManager

/**
 * Small, high-value PvP camera tweaks that every competitive client ships.
 *
 * Each one answers a specific annoyance, and each is independently switchable —
 * bundling them under one "PvP" switch would force a player who dislikes one
 * of them to give up the others.
 *
 * Everything here is read on the render path, so the flags are plain field
 * reads with no lookups.
 */
object CameraTweaks {

    /** Vanilla tilts the camera when you take a hit; disliked in PvP. */
    val noHurtCam: Boolean get() = ConfigManager.current.noHurtCam

    /** View bobbing while walking; some find it nauseating, some dislike it. */
    val noViewBob: Boolean get() = ConfigManager.current.noViewBob

    /**
     * Hides the first-person hand. Used for clean screenshots and by players
     * who find the hand blocks the crosshair area.
     */
    val hideHand: Boolean get() = ConfigManager.current.hideHand
}
