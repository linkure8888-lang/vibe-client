package com.vibe.client.cosmetics

import com.vibe.client.VibeClient
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * Procedural cape textures, one per cosmetic, registered as dynamic textures.
 *
 * The vanilla cape model samples a 64x32 sheet. We generate that sheet at 8x
 * resolution so the patterns are crisp, and hand the registered [Identifier]
 * to the renderer. Generation happens once per cosmetic on first use; every
 * failure degrades to "no cape" (a null identifier) rather than throwing,
 * because a broken texture must never kick the player out of the world.
 */
object VibeCapeTextureRegistry {

    /** Scale factor over the vanilla 64x32 cape sheet. */
    private const val SCALE = 8

    /** id -> registered texture identifier. */
    private val cache = ConcurrentHashMap<String, Identifier>()

    /** The registered texture for [cosmetic], generating it on first use. */
    @JvmStatic
    fun identifier(cosmetic: CosmeticsManager.Cosmetic): Identifier? {
        cache[cosmetic.id]?.let { return it }
        val generated = generate(cosmetic) ?: return null
        cache[cosmetic.id] = generated
        return generated
    }

    private fun generate(cosmetic: CosmeticsManager.Cosmetic): Identifier? {
        return try {
            val image = NativeImage(NativeImage.Format.RGBA, 64 * SCALE, 32 * SCALE, true)
            paint(image, cosmetic)
            val texture = NativeImageBackedTexture(image)
            MinecraftClient.getInstance().textureManager.registerDynamicTexture(
                "vibecape/" + cosmetic.id, texture
            )
        } catch (t: Throwable) {
            VibeClient.logger.warn("Cape texture failed for {}: {}", cosmetic.id, t.message)
            null
        }
    }

    // -- painting -----------------------------------------------------------

    private fun paint(image: NativeImage, cosmetic: CosmeticsManager.Cosmetic) {
        val w = image.width
        val h = image.height

        // Base: vertical gradient, base colour shading to obsidian.
        val top = cosmetic.base
        val bottom = 0xFF0A0B12.toInt()
        for (y in 0 until h) {
            val t = y.toFloat() / h
            val color = lerpColor(top, bottom, t)
            for (x in 0 until w) {
                image.setColor(x, y, color)
            }
        }

        // The cape body region the model UVs actually sample:
        // x 1..11, y 1..17 of the 64x32 sheet, scaled up.
        val bx = 1 * SCALE
        val by = 1 * SCALE
        val bw = 10 * SCALE
        val bh = 16 * SCALE

        when (cosmetic.pattern) {
            CosmeticsManager.Pattern.NONE -> { }
            CosmeticsManager.Pattern.STAR ->
                paintStars(image, cosmetic, bx, by, bw, bh)
            CosmeticsManager.Pattern.STREAK ->
                paintStreaks(image, cosmetic, bx, by, bw, bh)
            CosmeticsManager.Pattern.GRID ->
                paintGrid(image, cosmetic, bx, by, bw, bh)
            CosmeticsManager.Pattern.WAVE ->
                paintWaves(image, cosmetic, bx, by, bw, bh)
        }

        // Accent wash on the lower third of the body — the same "light from
        // below" cue the launcher preview shows, so the two stay honest
        // about being the same item.
        val accent = cosmetic.accent
        for (y in (by + bh * 55 / 100) until (by + bh)) {
            val t = (y - by - bh * 0.55f) / (bh * 0.45f)
            val a = (t * 90f).toInt().coerceIn(0, 255)
            for (x in bx until (bx + bw)) {
                val under = image.getColor(x, y)
                image.setColor(x, y, blendOver(under, accent and 0x00FFFFFF or (a shl 24)))
            }
        }
    }

    private fun paintStars(
        image: NativeImage, cosmetic: CosmeticsManager.Cosmetic,
        x: Int, y: Int, w: Int, h: Int
    ) {
        val rng = Random(cosmetic.id.hashCode().toLong())
        val accent = cosmetic.accent
        repeat(90) {
            val sx = x + rng.nextInt(w)
            val sy = y + rng.nextInt(h)
            val r = 1 + rng.nextInt(SCALE / 2)
            for (dx in -r..r) {
                for (dy in -r..r) {
                    if (dx * dx + dy * dy <= r * r) {
                        val px = sx + dx
                        val py = sy + dy
                        if (px in x until (x + w) && py in y until (y + h)) {
                            image.setColor(px, py, accent)
                        }
                    }
                }
            }
        }
        // Focal star in the upper third.
        val fx = x + w / 2
        val fy = y + h / 3
        val r = SCALE * 2
        for (dx in -r..r) {
            for (dy in -r..r) {
                val d = kotlin.math.abs(dx) + kotlin.math.abs(dy)
                if (d <= r && (dx == 0 || dy == 0 || d < r * 2 / 3)) {
                    val px = fx + dx
                    val py = fy + dy
                    if (px in x until (x + w) && py in y until (y + h)) {
                        image.setColor(px, py, accent)
                    }
                }
            }
        }
    }

    private fun paintStreaks(
        image: NativeImage, cosmetic: CosmeticsManager.Cosmetic,
        x: Int, y: Int, w: Int, h: Int
    ) {
        val accent = cosmetic.accent
        for (i in 0 until 3) {
            val a = (90 + i * 50).coerceAtMost(255)
            val color = accent and 0x00FFFFFF or (a shl 24)
            val sx = x + w / 6 + i * w / 3
            var py = y
            var px = sx
            while (py < y + h) {
                image.setColor(px.coerceIn(x, x + w - 1), py, color)
                image.setColor((px + 1).coerceIn(x, x + w - 1), py, color)
                py += 1
                px += if ((py / 24) % 2 == 0) 1 else -1
            }
        }
    }

    private fun paintGrid(
        image: NativeImage, cosmetic: CosmeticsManager.Cosmetic,
        x: Int, y: Int, w: Int, h: Int
    ) {
        val accent = cosmetic.accent
        val color = accent and 0x00FFFFFF or (140 shl 24)
        var gx = x
        while (gx < x + w) {
            var gy = y
            while (gy < y + h) {
                image.setColor(gx, gy, color)
                gy += 1
            }
            gx += SCALE
        }
        var gy = y
        while (gy < y + h) {
            var gx2 = x
            while (gx2 < x + w) {
                image.setColor(gx2, gy, color)
                gx2 += 1
            }
            gy += SCALE
        }
    }

    private fun paintWaves(
        image: NativeImage, cosmetic: CosmeticsManager.Cosmetic,
        x: Int, y: Int, w: Int, h: Int
    ) {
        val accent = cosmetic.accent
        for (i in 0 until 4) {
            val a = (70 + i * 35).coerceAtMost(255)
            val color = accent and 0x00FFFFFF or (a shl 24)
            val wy = y + h * (14 + i * 40) / 160
            var gx = x
            while (gx < x + w) {
                val t = (gx - x).toFloat() / w
                val phase = t * Math.PI.toFloat() * 2 + i * 0.7f
                val dy = (kotlin.math.sin(phase) * SCALE).toInt()
                val py = (wy + dy).coerceIn(y, y + h - 1)
                image.setColor(gx, py, color)
                image.setColor(gx, (py + 1).coerceIn(y, y + h - 1), color)
                gx += 1
            }
        }
    }

    // -- colour helpers -----------------------------------------------------

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val fr = (from ushr 16) and 0xFF
        val fg = (from ushr 8) and 0xFF
        val fb = from and 0xFF
        val tr = (to ushr 16) and 0xFF
        val tg = (to ushr 8) and 0xFF
        val tb = to and 0xFF
        val r = (fr + (tr - fr) * t).toInt().coerceIn(0, 255)
        val g = (fg + (tg - fg) * t).toInt().coerceIn(0, 255)
        val b = (fb + (tb - fb) * t).toInt().coerceIn(0, 255)
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }

    /** Simple alpha-over composite; [over] is the upper colour. */
    private fun blendOver(under: Int, over: Int): Int {
        val a = (over ushr 24) and 0xFF
        if (a == 0) return under
        if (a == 255) return over
        val ia = 255 - a
        val ur = (under ushr 16) and 0xFF
        val ug = (under ushr 8) and 0xFF
        val ub = under and 0xFF
        val or = (over ushr 16) and 0xFF
        val og = (over ushr 8) and 0xFF
        val ob = over and 0xFF
        val r = ((or * a + ur * ia) / 255).coerceIn(0, 255)
        val g = ((og * a + ug * ia) / 255).coerceIn(0, 255)
        val b = ((ob * a + ub * ia) / 255).coerceIn(0, 255)
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }
}
