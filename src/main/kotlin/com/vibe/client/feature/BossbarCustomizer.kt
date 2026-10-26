package com.vibe.client.feature

import com.vibe.client.config.ConfigManager

/**
 * BossbarCustomizer — кастомизатор боссбаров (BossBarHud).
 *
 * Позволяет:
 *  - Полностью скрывать боссбары (дракон, визер, рейды)
 *  - Масштабировать размер
 *  - Настраивать прозрачность (alpha)
 */
object BossbarCustomizer {

    val enabled: Boolean
        get() = ConfigManager.current.bossbarCustomizer

    val hide: Boolean
        get() = ConfigManager.current.bossbarHide

    val scale: Float
        get() = ConfigManager.current.bossbarScale.coerceIn(0.2f, 2.0f)

    val opacity: Float
        get() = ConfigManager.current.bossbarOpacity.coerceIn(0.0f, 1.0f)
}
