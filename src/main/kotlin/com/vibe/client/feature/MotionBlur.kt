package com.vibe.client.feature

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.vibe.client.config.ConfigManager
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat.DrawMode
import net.minecraft.client.render.VertexFormats
import org.lwjgl.opengl.GL11

/**
 * Motion blur, the honest way: frame accumulation.
 *
 * Why it was missing until now: the obvious implementation copies the
 * vanilla post-effect chain (JSON shader passes), which is heavy and
 * version-fragile. The classic alternative needs no custom shaders at all:
 *
 *  - keep a history framebuffer the size of the screen;
 *  - every frame, draw the history over the fresh frame with a small alpha
 *    (so the past ghosts over the present);
 *  - then copy the composed frame into history for the next tick.
 *
 * The result is the same trailing smear Lunar/Badlion ship, driven only by
 * the game's own position-tex shader and two fullscreen quads.
 *
 * Cost: one texture-sized blit per frame. On anything that runs 1.21 this
 * is noise; the [strength] slider keeps it tunable anyway.
 *
 * Threading/state rules honoured: everything runs on the render thread
 * inside WorldRenderEvents.LAST, GL state is restored after both passes
 * (blend, shader, viewport), and the effect is skipped entirely when the
 * GUI is open - blurring under an inventory screen reads as a bug, not a
 * feature.
 */
object MotionBlur {

    /** How strongly the previous frame ghosts over the current one. */
    private val strength: Float
        get() = ConfigManager.current.motionBlurStrength.coerceIn(0f, 0.9f)

    private val enabled: Boolean
        get() = ConfigManager.current.motionBlur && strength > 0.01f

    /** The previous frame, kept between ticks. Null until first use. */
    private var history: SimpleFramebuffer? = null

    fun register() {
        WorldRenderEvents.LAST.register { context ->
            if (!enabled) {
                release()
                return@register
            }
            val client = MinecraftClient.getInstance()
            // Never blur under an open GUI; the blur is a world effect.
            if (client.currentScreen != null) {
                release()
                return@register
            }
            val main = client.framebuffer
            val width = main.textureWidth
            val height = main.textureHeight

            var hist = history
            if (hist == null || hist.textureWidth != width || hist.textureHeight != height) {
                release()
                hist = SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC)
                history = hist
            }

            // Pass 1: copy the composed frame into history (no blending).
            hist.beginWrite(true)
            drawQuad(main.getColorAttachment(), 1f)

            // Pass 2: draw history back over the live frame with the blur
            // alpha. SRC_ALPHA / ONE_MINUS_SRC_ALPHA so the past fades in
            // rather than adding brightness.
            main.beginWrite(false)
            RenderSystem.enableBlend()
            RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA.value,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SrcFactor.ONE.value,
                GlStateManager.DstFactor.ZERO.value
            )
            drawQuad(hist.getColorAttachment(), strength)

            // Restore the state the rest of the frame expects.
            RenderSystem.disableBlend()
            RenderSystem.bindTexture(0)
            RenderSystem.setShader(GameRenderer::getPositionTexProgram)
        }
    }

    /** One fullscreen quad of [texture] at [alpha], in pixel space. */
    private fun drawQuad(texture: Int, alpha: Float) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram)
        RenderSystem.bindTextureForSetup(texture)
        val w = MinecraftClient.getInstance().framebuffer.textureWidth.toFloat()
        val h = MinecraftClient.getInstance().framebuffer.textureHeight.toFloat()

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE)
        buffer.vertex(0f, h, 0f).texture(0f, 0f)
        buffer.vertex(w, h, 0f).texture(1f, 0f)
        buffer.vertex(w, 0f, 0f).texture(1f, 1f)
        buffer.vertex(0f, 0f, 0f).texture(0f, 1f)

        // The fade is the blend weight below; the color modulator carries the
        // per-pass strength into the shader's alpha slot. position_tex has
        // no vertex color, so this uniform is the only way to scale it.
        val program = GameRenderer.getPositionTexProgram()
        program?.colorModulator?.set(alpha, alpha, alpha, alpha)
        BufferRenderer.drawWithGlobalProgram(buffer.end())
    }

    /** Frees the history buffer; called when the effect is off. */
    private fun release() {
        history?.delete()
        history = null
    }
}