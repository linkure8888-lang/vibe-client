package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.WorldRenderer

/**
 * Block Overlay — a custom-coloured outline on the block being looked at.
 *
 * Implemented by taking over the vanilla block-outline event: the handler draws
 * its own shape and returns `false` to suppress the default outline, so the two
 * never draw on top of each other.
 *
 * The geometry is deliberately identical to vanilla (same outline shape, same
 * camera-relative offset) — only the colour and alpha change. That keeps the
 * overlay pixel-aligned with the real block at every distance, which a
 * hand-rolled box around the block position would not be: stairs, slabs and
 * fences have their own outline shapes and must be respected.
 */
object BlockOverlay {

    fun register() {
        WorldRenderEvents.BLOCK_OUTLINE.register { context, outline ->
            val config = ConfigManager.current
            if (!config.blockOverlay) return@register true

            val world = MinecraftClient.getInstance().world ?: return@register true
            val state = outline.blockState()
            val pos = outline.blockPos()

            // Empty for blocks with no outline (grass, air, torches).
            val shape = state.getOutlineShape(world, pos)
            if (shape.isEmpty) return@register true

            val camera = context.camera().pos
            val color = config.blockOverlayColor

            WorldRenderer.drawShapeOutline(
                context.matrixStack(),
                outline.vertexConsumer(),
                shape,
                pos.x - camera.x,
                pos.y - camera.y,
                pos.z - camera.z,
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                ((color ushr 24) and 0xFF) / 255f,
                false
            )

            false
        }
    }
}
