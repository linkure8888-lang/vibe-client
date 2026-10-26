package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.WorldRenderer
import net.minecraft.entity.LivingEntity

/**
 * Hit Color — draws a coloured box around any entity that has just taken damage.
 *
 * What this is, precisely: a damage indicator in a colour you choose, drawn as a
 * world-space box. It is not a repaint of vanilla's red flash. Recolouring that
 * flash would mean replacing the `overlay.png` damage texture, which is shared
 * by every entity in the game and would leak into unrelated rendering.
 *
 * Why a box is the honest choice here: it is purely additive (nothing vanilla
 * is touched), it is trivially reversible, and it reads at a glance in a fight —
 * which is the actual purpose of the feature.
 *
 * Cost control: the loop only runs when the feature is on and it skips every
 * entity that is not currently hurt, which is almost always all of them.
 */
object HitColor {

    fun register() {
        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            val config = ConfigManager.current
            if (!config.hitColor) return@register

            val client = MinecraftClient.getInstance()
            val world = client.world ?: return@register

            val consumers = context.consumers() ?: return@register
            val lines = consumers.getBuffer(RenderLayer.getLines())
            val matrices = context.matrixStack()
            val camera = context.camera().pos

            val color = config.hitColorValue
            val red = ((color shr 16) and 0xFF) / 255f
            val green = ((color shr 8) and 0xFF) / 255f
            val blue = (color and 0xFF) / 255f
            val alpha = ((color ushr 24) and 0xFF) / 255f

            for (entity in world.entities) {
                if (entity !is LivingEntity) continue
                // hurtTime counts down from 10 after a hit; anything at 0 is
                // untouched and costs nothing but this comparison.
                if (entity.hurtTime <= 0) continue
                // Skip our own entity: its own damage state is visible in the HUD.
                if (entity === client.player) continue

                val box = entity.boundingBox.expand(0.06)
                    .offset(-camera.x, -camera.y, -camera.z)

                WorldRenderer.drawBox(matrices, lines, box, red, green, blue, alpha)
            }
        }
    }
}
