package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.minecraft.client.MinecraftClient

/**
 * Fullbright — полная яркость через gamma tweak.
 *
 * Устанавливает options.gamma = FULLBRIGHT_GAMMA пока включён.
 * При выключении восстанавливает сохранённое значение пользователя.
 * Без теней, без постэффектов — чисто гамма, как у vanilla Brightness slider.
 *
 * Уже реализован в WorldTweaks.kt. Этот файл — самостоятельная версия
 * с отдельным состоянием, для прямого использования как feature-объект.
 */
object Fullbright {

    /** Гамма в режиме fullbright. Vanilla слайдер ограничен 1.0, мы ставим 15.0. */
    private const val FULLBRIGHT_GAMMA = 15.0

    /** Сохранённая гамма игрока, восстанавливается при выключении. */
    private var savedGamma: Double? = null

    val enabled: Boolean
        get() = ConfigManager.current.fullbright

    fun tick(client: MinecraftClient) {
        val options = client.options ?: return
        val wanted = enabled

        if (wanted) {
            if (savedGamma == null) {
                savedGamma = options.gamma.value
            }
            if (options.gamma.value != FULLBRIGHT_GAMMA) {
                options.gamma.value = FULLBRIGHT_GAMMA
            }
        } else {
            val previous = savedGamma ?: return
            if (options.gamma.value != previous) {
                options.gamma.value = previous
            }
            savedGamma = null
        }
    }

    /** Восстанавливает гамму при выходе из игры. */
    fun reset(client: MinecraftClient) {
        val previous = savedGamma ?: return
        client.options?.gamma?.value = previous
        savedGamma = null
    }
}
