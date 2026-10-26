package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.entity.player.PlayerEntity

object WaypointModule : Module("Waypoint", Category.WORLD, enabled = false, x = 4f, y = 4f) {
    var hasWaypoint: Boolean = false
        private set
    private var wx = 0.0
    private var wy = 0.0
    private var wz = 0.0

    fun toggleAtPlayer(player: PlayerEntity?) {
        if (player == null) return
        if (hasWaypoint) {
            hasWaypoint = false
        } else {
            hasWaypoint = true
            wx = player.x
            wy = player.y
            wz = player.z
        }
    }

    fun update(x: Double, y: Double, z: Double, yaw: Float) {
        // tracking/rendering waypoint
    }

    override fun getLines(): List<String> {
        return if (hasWaypoint) listOf("Waypoint: ${"%.0f".format(wx)}, ${"%.0f".format(wy)}, ${"%.0f".format(wz)}") else emptyList()
    }
}
