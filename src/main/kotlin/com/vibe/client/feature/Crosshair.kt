package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext

/**
 * Custom crosshair — расширенная версия с пресетами, настройками цвета/размера и динамикой.
 *
 * Пресеты:
 *  0 = Default (vanilla + color tint)
 *  1 = Dot (центральная точка)
 *  2 = Cross (классический прицел)
 *  3 = Circle (окружность)
 *  4 = T-shape (Т-образный)
 *  5 = CS-style (CS:GO style gap crosshair)
 *
 * Цвет: configurable ARGB, по умолчанию белый.
 * Размер: 1..20, по умолчанию 6.
 * Толщина: 1..5, по умолчанию 1.
 * Динамика при атаке: кратковременное увеличение зазора (gap) при ударе.
 */
object Crosshair {

    // -- Цвета для entity-тин ---
    private const val COLOR_PLAYER  = 0xFFFF4444.toInt()
    private const val COLOR_HOSTILE = 0xFFFFAA33.toInt()
    private const val COLOR_PASSIVE = 0xFF55FF55.toInt()
    const val COLOR_DEFAULT          = 0xFFFFFFFF.toInt()

    // -- Анимация атаки ---
    private const val ATTACK_EXPAND_MS = 200L
    @Volatile private var lastAttackMs = 0L

    @Volatile private var entityColor: Int = COLOR_DEFAULT

    // -- Публичный API ---
    val enabled: Boolean get() = ConfigManager.current.customCrosshair

    /** Цвет с учётом target-tint (если нет тинта — берём cfg.crosshairColor). */
    val drawColor: Int get() {
        val cfg = ConfigManager.current
        return if (cfg.crosshairEntityTint) entityColor else cfg.crosshairColor
    }

    /** Текущий пресет (0..5). */
    val preset: Int get() = ConfigManager.current.crosshairPreset

    val size: Int get() = ConfigManager.current.crosshairSize.coerceIn(1, 20)

    val thickness: Int get() = ConfigManager.current.crosshairThickness.coerceIn(1, 5)

    /** Дополнительный зазор при атаке. */
    private val attackExpand: Int
        get() {
            val elapsed = System.currentTimeMillis() - lastAttackMs
            if (elapsed > ATTACK_EXPAND_MS) return 0
            val t = 1f - elapsed.toFloat() / ATTACK_EXPAND_MS
            return (t * 6f).toInt()
        }

    // -- Callbacks ---

    fun tick(client: MinecraftClient) {
        if (!enabled) {
            entityColor = COLOR_DEFAULT
            return
        }
        val target = client.crosshairTarget
        val entity = (target as? net.minecraft.util.hit.EntityHitResult)?.entity
        entityColor = when (entity) {
            is net.minecraft.entity.player.PlayerEntity -> COLOR_PLAYER
            is net.minecraft.entity.mob.HostileEntity   -> COLOR_HOSTILE
            is net.minecraft.entity.passive.PassiveEntity -> COLOR_PASSIVE
            else                                         -> ConfigManager.current.crosshairColor
        }
    }

    /** Вызывается при атаке — запускает анимацию расширения. */
    fun onAttack() {
        if (enabled) lastAttackMs = System.currentTimeMillis()
    }

    fun reset() {
        entityColor = COLOR_DEFAULT
        lastAttackMs = 0L
    }

    // -- Рисование ---

    /**
     * Рисует кастомный прицел поверх экрана.
     * Вызывается из InGameHudMixin после render-хука (на TAIL).
     * При preset == 0 (Default) vanilla-прицел тинтируется в миксине,
     * дополнительного рендера не требуется.
     */
    fun draw(context: DrawContext) {
        if (!enabled) return
        if (preset == 0) return   // vanilla рисует сам, миксин меняет цвет

        val client = MinecraftClient.getInstance()
        if (client.options.hudHidden) return
        if (client.currentScreen != null) return

        val cx = client.window.scaledWidth  / 2
        val cy = client.window.scaledHeight / 2

        val color = drawColor
        val sz    = size
        val th    = thickness
        val gap   = when (preset) {
            5    -> sz / 2 + attackExpand       // CS-style: gap по-умолчанию
            else -> 0 + attackExpand
        }

        when (preset) {
            1    -> drawDot(context, cx, cy, th, color)
            2    -> drawCross(context, cx, cy, sz, th, gap, color)
            3    -> drawCircle(context, cx, cy, sz, th, color)
            4    -> drawTShape(context, cx, cy, sz, th, gap, color)
            5    -> drawCsStyle(context, cx, cy, sz, th, gap, color)
        }
    }

    // ── Пресеты ──────────────────────────────────────────────────────────────

    /** Dot — точка в центре. */
    private fun drawDot(ctx: DrawContext, cx: Int, cy: Int, th: Int, color: Int) {
        ctx.fill(cx - th, cy - th, cx + th, cy + th, color)
    }

    /** Cross — классический 2D крест с зазором. */
    private fun drawCross(ctx: DrawContext, cx: Int, cy: Int,
                          sz: Int, th: Int, gap: Int, color: Int) {
        val half = th / 2
        // горизонталь левая
        ctx.fill(cx - sz - gap, cy - half, cx - gap, cy + half + 1, color)
        // горизонталь правая
        ctx.fill(cx + gap + 1, cy - half, cx + sz + gap + 1, cy + half + 1, color)
        // вертикаль верхняя
        ctx.fill(cx - half, cy - sz - gap, cx + half + 1, cy - gap, color)
        // вертикаль нижняя
        ctx.fill(cx - half, cy + gap + 1, cx + half + 1, cy + sz + gap + 1, color)
        // центральная точка
        ctx.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, color)
    }

    /** Circle — рисуем приближение окружности пиксельными шагами. */
    private fun drawCircle(ctx: DrawContext, cx: Int, cy: Int,
                           sz: Int, th: Int, color: Int) {
        val r = sz.toFloat()
        var x = 0
        var y = r.toInt()
        var d = 1 - y
        while (x <= y) {
            fillPixelThick(ctx, cx + x, cy + y, th, color)
            fillPixelThick(ctx, cx - x, cy + y, th, color)
            fillPixelThick(ctx, cx + x, cy - y, th, color)
            fillPixelThick(ctx, cx - x, cy - y, th, color)
            fillPixelThick(ctx, cx + y, cy + x, th, color)
            fillPixelThick(ctx, cx - y, cy + x, th, color)
            fillPixelThick(ctx, cx + y, cy - x, th, color)
            fillPixelThick(ctx, cx - y, cy - x, th, color)
            if (d < 0) d += 2 * x + 3
            else { d += 2 * (x - y) + 5; y-- }
            x++
        }
    }

    private fun fillPixelThick(ctx: DrawContext, px: Int, py: Int, th: Int, color: Int) {
        ctx.fill(px - th / 2, py - th / 2, px + th / 2 + 1, py + th / 2 + 1, color)
    }

    /** T-shape — горизонталь + нижняя вертикаль (снайперский стиль). */
    private fun drawTShape(ctx: DrawContext, cx: Int, cy: Int,
                           sz: Int, th: Int, gap: Int, color: Int) {
        val half = th / 2
        // горизонталь
        ctx.fill(cx - sz - gap, cy - half, cx - gap, cy + half + 1, color)
        ctx.fill(cx + gap + 1, cy - half, cx + sz + gap + 1, cy + half + 1, color)
        // вертикаль нижняя
        ctx.fill(cx - half, cy + gap + 1, cx + half + 1, cy + sz + gap + 1, color)
        // центр
        ctx.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, color)
    }

    /** CS-style — 4 линии с зазором, без центральной точки. */
    private fun drawCsStyle(ctx: DrawContext, cx: Int, cy: Int,
                            sz: Int, th: Int, gap: Int, color: Int) {
        val half = th / 2
        ctx.fill(cx - sz - gap, cy - half, cx - gap, cy + half + 1, color)
        ctx.fill(cx + gap + 1, cy - half, cx + sz + gap + 1, cy + half + 1, color)
        ctx.fill(cx - half, cy - sz - gap, cx + half + 1, cy - gap, color)
        ctx.fill(cx - half, cy + gap + 1, cx + half + 1, cy + sz + gap + 1, color)
    }
}
