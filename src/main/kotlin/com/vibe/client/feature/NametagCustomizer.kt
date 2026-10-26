package com.vibe.client.feature

import com.vibe.client.config.ConfigManager

/**
 * NametagCustomizer — кастомизатор ников игроков (Nametags).
 *
 * Позволяет:
 *  - Менять цвет никнейма
 *  - Показывать/скрывать HP игрока рядом с ником
 *  - Масштабировать текст
 *  - Рендерить сквозь стены (с уменьшением прозрачности)
 */
object NametagCustomizer {

    val enabled: Boolean
        get() = ConfigManager.current.nametagCustomizer

    val showHealth: Boolean
        get() = ConfigManager.current.nametagShowHp

    val color: Int
        get() = ConfigManager.current.nametagColor

    val scale: Float
        get() = ConfigManager.current.nametagScale.coerceIn(0.5f, 2.5f)

    val throughWalls: Boolean
        get() = ConfigManager.current.nametagThroughWalls
}
