package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.WorldRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box

/**
 * Chunk Borders - the F3+G grid, but always on and in our accent colours.
 *
 * Lunar ships this as a toggleable visual and it is one of the most
 * useful building aids: chunk boundaries decide where farms, portals and
 * spawn-proofing have to line up.
 *
 * Implementation: one outlined box per chunk in a RADIUS-chunk area around
 * the player, drawn on the AFTER_TRANSLUCENT pass through the game's own
 * line consumer. Each box spans the full world height and its own chunk
 * footprint, so adjacent boxes share edges and the result reads as one
 * continuous grid rather than separate cages.
 *
 * Cost: (2*R+1)^2 boxes * 12 edges. R=2 keeps that at 225 line segments,
 * which is trivial for the tessellator; the feature is off by default so
 * it costs nothing until it is wanted.
 */
object ChunkBorders {

    private const val RADIUS = 2
    private const val ALPHA = 0.55f

    fun register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register { context ->
            if (!ConfigManager.current.chunkBorders) return@register
            val client = MinecraftClient.getInstance()
            val player = client.player ?: return@register
            val world = client.world ?: return@register

            val camera = context.camera().pos
            val matrix = context.matrixStack() ?: return@register
            val consumers = context.consumers() ?: return@register
            val consumer = consumers.getBuffer(RenderLayer.getLines())

            // Full-height columns around the player, aligned to chunks.
            val playerChunkX = player.chunkPos.x
            val playerChunkZ = player.chunkPos.z
            val minY = world.bottomY + 8
            val height = world.height.toDouble()

            for (dx in -RADIUS..RADIUS) {
                for (dz in -RADIUS..RADIUS) {
                    val cx = playerChunkX + dx
                    val cz = playerChunkZ + dz
                    val startX = cx * 16 - camera.x
                    val startZ = cz * 16 - camera.z


                    // The active chunk gets the accent colour; neighbours
                    // get a dimmer tint so the grid has hierarchy.
                    val accent = dx == 0 && dz == 0
                    val r: Float
                    val g: Float
                    val b: Float
                    if (accent) {
                        r = 0.54f; g = 0.36f; b = 0.96f
                    } else {
                        r = 0.13f; g = 0.83f; b = 0.93f
                    }

                    WorldRenderer.drawBox(
                        matrix, consumer,
                        startX, minY - camera.y, startZ,
                        startX + 16, minY - camera.y + height, startZ + 16,
                        r, g, b, if (accent) 0.9f else ALPHA
                    )
                }
            }
        }
    }
}